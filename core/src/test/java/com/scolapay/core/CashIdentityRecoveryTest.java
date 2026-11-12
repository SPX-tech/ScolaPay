package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CashIdentityRecoveryTest extends CashIdentityProbeScenario {
    Map<String,Object> verify(UUID key){return Map.of("idempotencyKey",key,"beneficiaryIsRemitter",true,"identityCheckedConfirmed",true,"verificationDetails","Contrôle préparatoire privé");}
    UUID prepareAuthorization(Instance first,Legacy l){
        assertThat(declare(first.api(),l,UUID.randomUUID()).status()).isEqualTo(201);
        assertThat(first.api().post(l.school().path("/payments/cash-receptions/"+l.source()+"/remitter-reviews"),l.reviewerToken(),review(UUID.randomUUID())).status()).isEqualTo(201);
        var verified=first.api().post(l.school().path("/payments/excess-requests/"+l.requestId()+"/beneficiary-verifications"),l.school().adminToken(),verify(UUID.randomUUID()));
        assertThat(verified.status()).as(verified.raw()).isEqualTo(201);return verified.id("id");
    }
    Map<String,Object> authorize(UUID key,UUID verification){return Map.of("idempotencyKey",key,"beneficiaryVerificationId",verification,"beneficiaryReviewedConfirmed",true);}
    @ParameterizedTest @ValueSource(strings={"remitter-declarations","remitter-reviews","restitution-authorizations"})
    void unePerteDeReponseApresCommitSeReprendSansNouveauFaitOuMouvement(String operation) throws Exception{
        Legacy l=legacy();Instance first=start(null,"/"+operation,true);UUID key=UUID.randomUUID();
        String root=l.school().path("/payments"),path=root+"/cash-receptions/"+l.source()+"/"+operation,actor=l.school().adminToken();
        Map<String,Object> command=declaration(key);String table="cash_remitter_identifications",action="payments.remitter.recorded";
        if(operation.equals("remitter-reviews")){
            assertThat(declare(first.api(),l,UUID.randomUUID()).status()).isEqualTo(201);actor=l.reviewerToken();command=review(key);
            table="cash_remitter_reviews";action="payments.remitter.reviewed";
        }else if(operation.equals("restitution-authorizations")){
            UUID verification=prepareAuthorization(first,l);actor=l.reviewerToken();command=authorize(key,verification);
            table="cash_restitution_authorizations";action="payments.restitution.authorized";path=root+"/excess-requests/"+l.requestId()+"/"+operation;
        }
        String writePath=path,writer=actor,durableTable=table;Map<String,Object> body=command;var installed=history();
        assertThat(installed).extracting(row->row.get("version")).containsExactly("1","2","3","4","5","6","7","7.1","7.2","8","9","10","11");
        assertThat(installed.subList(0,legacyHistory.size())).isEqualTo(legacyHistory);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var pending=executor.submit(()->first.api().post(writePath,writer,body));
            CashSettlementRecoveryTest.await(()->jdbc.sql("SELECT count(*) FROM "+durableTable+" WHERE establishment_id=:tenant AND idempotency_key=:key")
                    .param("tenant",l.school().id()).param("key",key).query(Long.class).single()==1);
            first.kill();assertThatThrownBy(()->pending.get(10,TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
        }
        Instance disabled=start(null,"",false);String readPath=root+"/"+operation+"/by-key/"+key;
        var recovered=disabled.api().get(readPath,actor);assertThat(recovered.status()).as(recovered.raw()).isEqualTo(200);
        assertThat(recovered.body().path("physicalHandoverAvailable").asBoolean()).isFalse();
        assertThat(disabled.api().post(path,actor,command).status()).isEqualTo(404);assertThat(history()).isEqualTo(installed);
        assertThat(disabled.api().get("/api/v2/me/students",actor).status()).isEqualTo(404);disabled.kill();
        Instance enabled=start(null,"",true);var replay=enabled.api().post(path,actor,command);
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);assertThat(replay.body()).isEqualTo(recovered.body());
        assertThat(jdbc.sql("SELECT count(*) FROM "+table+" WHERE establishment_id=:tenant AND idempotency_key=:key")
                .param("tenant",l.school().id()).param("key",key).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id=:tenant AND action=:action")
                .param("tenant",l.school().id()).param("action",action).query(Long.class).single()).isEqualTo(1);
        var funds=enabled.api().get(root+"/cash-receptions/"+l.source(),l.school().adminToken());
        assertThat(funds.text("reservedMinor")).isEqualTo("15000");assertThat(funds.text("unallocatedMinor")).isEqualTo("20000");
        assertThat(history()).isEqualTo(installed);
        Flyway.configure().dataSource(url,TestDatabases.POSTGRES.getUsername(),TestDatabases.POSTGRES.getPassword()).locations("classpath:db/migration").load().validate();
    }
    @Test void deuxInstancesRetrouventLaMemeAutorisationSansModifierLesReservationsHistoriques() throws Exception{
        Legacy l=legacy();Instance first=start(null,"",true),second=start(null,"",true);UUID verification=prepareAuthorization(first,l),key=UUID.randomUUID();
        String path=l.school().path("/payments/excess-requests/"+l.requestId()+"/restitution-authorizations");CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->{barrier.await();return first.api().post(path,l.reviewerToken(),authorize(key,verification));});
            var b=executor.submit(()->{barrier.await();return second.api().post(path,l.reviewerToken(),authorize(key,verification));});
            barrier.countDown();var ra=a.get(15,TimeUnit.SECONDS);var rb=b.get(15,TimeUnit.SECONDS);
            assertThat(List.of(ra.status(),rb.status())).containsExactlyInAnyOrder(201,200);assertThat(ra.body()).isEqualTo(rb.body());
        }
        assertThat(jdbc.sql("SELECT count(*) FROM cash_restitution_authorizations WHERE establishment_id=:tenant").param("tenant",l.school().id()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT * FROM cash_excess_decisions WHERE request_id=:id").param("id",l.requestId()).query().singleRow()).isEqualTo(legacyDecision);
    }
}
