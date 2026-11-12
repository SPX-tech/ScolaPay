package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.scolapay.core.payments.internal.CashIdentityFixtures;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties="scolapay.payments.cash-identities-enabled=true")
class CashIdentitySqlTest extends CashExcessScenario {
    @Autowired PlatformTransactionManager transactions;
    record Raw(Tenant tenant,UUID source,UUID request,UUID beneficiary,UUID owner,UUID reviewer){}
    Raw raw(boolean verified){
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);String director=newStaff(t,"DIRECTOR");
        UUID request=request(t,source,UUID.randomUUID(),15000,"Excédent").id("id");
        assertThat(decision(t,director,request,UUID.randomUUID(),"approval",null).status()).isEqualTo(201);
        UUID beneficiary=UUID.randomUUID();
        if(verified){
            Response b=post(t.path("/payments/excess-requests/"+request+"/beneficiary-verifications"),t.adminToken(),Map.of(
                    "idempotencyKey",UUID.randomUUID(),"beneficiaryIsRemitter",true,"identityCheckedConfirmed",true,"verificationDetails","Contrôle SQL"));
            assertThat(b.status()).as(b.raw()).isEqualTo(201);beneficiary=b.id("id");
        }
        return new Raw(t,source,request,beneficiary,get("/api/v2/me",t.adminToken()).id("id"),get("/api/v2/me",director).id("id"));
    }
    void audit(Raw r,UUID id,UUID actor,String action,String target,String reference){
        jdbc.sql("""
                INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                VALUES (:event,now(),:tenant,:actor,:action,:target,:id,
                  jsonb_build_object('reference',CAST(:reference AS text),'receptionId',CAST(:source AS text),'requestId',CAST(:request AS text)))
                """).param("event",UUID.randomUUID()).param("tenant",r.tenant().id()).param("actor",actor).param("action",action).param("target",target)
                .param("id",id).param("reference",reference).param("source",r.source().toString()).param("request",r.request().toString()).update();
    }
    void authorization(Raw r,UUID id,Map<String,Object> changes,boolean audited){
        var p=new HashMap<String,Object>(Map.ofEntries(Map.entry("id",id),Map.entry("tenant",r.tenant().id()),Map.entry("request",r.request()),
                Map.entry("beneficiary",r.beneficiary()),Map.entry("policy","NAMED_REMITTER_V1"),Map.entry("confirmed",true),Map.entry("key",UUID.randomUUID()),
                Map.entry("reference","CRA-"+id),Map.entry("at",Instant.now().toString()),Map.entry("actor",r.reviewer())));p.putAll(changes);
        jdbc.sql("""
                INSERT INTO cash_restitution_authorizations(id,establishment_id,request_id,beneficiary_verification_id,policy,
                  beneficiary_reviewed_confirmed,idempotency_key,reference,authorized_at,authorized_by)
                VALUES (:id,:tenant,:request,:beneficiary,:policy,:confirmed,:key,:reference,CAST(:at AS timestamptz),:actor)
                """).params(p).update();
        if(audited)audit(r,id,r.reviewer(),"payments.restitution.authorized","cash_restitution_authorization","CRA-"+id);
    }
    void commitAuthorization(Raw r,Map<String,Object> changes){UUID id=UUID.randomUUID();new TransactionTemplate(transactions).executeWithoutResult(tx->authorization(r,id,changes,true));}
    void beneficiary(Raw r,Map<String,Object> changes){
        var p=new HashMap<String,Object>(Map.ofEntries(Map.entry("id",r.beneficiary()),Map.entry("tenant",r.tenant().id()),Map.entry("request",r.request()),
                Map.entry("source",r.source()),Map.entry("kind","REMITTER_ONLY"),Map.entry("confirmed",true),Map.entry("key",UUID.randomUUID()),Map.entry("reference","CBV-"+r.beneficiary()),
                Map.entry("at",Instant.now().toString()),Map.entry("actor",r.owner()),Map.entry("payload",CashIdentityFixtures.encrypt(r.tenant().id(),r.beneficiary(),"beneficiary","Contrôle SQL privé"))));p.putAll(changes);
        jdbc.sql("""
                INSERT INTO cash_beneficiary_verifications(id,establishment_id,request_id,identification_id,beneficiary_kind,identity_checked_confirmed,
                  encrypted_payload,idempotency_key,reference,verified_at,verified_by)
                VALUES (:id,:tenant,:request,:source,:kind,:confirmed,:payload,:key,:reference,CAST(:at AS timestamptz),:actor)
                """).params(p).update();
        audit(r,r.beneficiary(),r.owner(),"payments.beneficiary.verified","cash_beneficiary_verification","CBV-"+r.beneficiary());
    }
    void commitBeneficiary(Raw r,Map<String,Object> changes){new TransactionTemplate(transactions).executeWithoutResult(tx->beneficiary(r,changes));}

    @Test void lesAutorisationsSqlExigentLaSourceLeBeneficiaireLaSecondePersonneEtLesValeursNonNulles(){
        Raw r=raw(true),other=raw(true);
        for(var bad:List.of(Map.<String,Object>of("actor",r.owner()),Map.<String,Object>of("beneficiary",other.beneficiary()),
                Map.<String,Object>of("tenant",other.tenant().id()),Map.<String,Object>of("policy","RESERVATION_ONLY_V1"),
                Map.<String,Object>of("policy","COMPLETED"),Map.<String,Object>of("confirmed",false),Map.<String,Object>of("reference","AUTRE"),Map.<String,Object>of("at","infinity"))){
            assertThatThrownBy(()->commitAuthorization(r,bad)).isInstanceOf(RuntimeException.class);
        }
        for(String field:List.of("request","beneficiary","policy","confirmed","key","reference","at","actor")){
            var bad=new HashMap<String,Object>();bad.put(field,null);assertThatThrownBy(()->commitAuthorization(r,bad)).as(field).isInstanceOf(RuntimeException.class);
        }
        assertThat(count("cash_restitution_authorizations",r.tenant())).isZero();commitAuthorization(r,Map.of());
        assertThat(count("cash_restitution_authorizations",r.tenant())).isEqualTo(1);assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("15000");
    }
    @Test void leSqlNePeutChangerLaPersonneLaSourceNiOmettreLeControleDuBeneficiaire(){
        Raw r=raw(false),other=raw(false);
        for(var bad:List.of(Map.<String,Object>of("source",other.source()),Map.<String,Object>of("actor",r.reviewer()),Map.<String,Object>of("kind","FAMILY_MEMBER"),
                Map.<String,Object>of("confirmed",false),Map.<String,Object>of("payload","texte clair"),Map.<String,Object>of("reference","AUTRE"))){
            assertThatThrownBy(()->commitBeneficiary(r,bad)).isInstanceOf(RuntimeException.class);
        }
        for(String field:List.of("source","request","actor","kind","confirmed","payload","key","reference","at")){
            var bad=new HashMap<String,Object>();bad.put(field,null);assertThatThrownBy(()->commitBeneficiary(r,bad)).as(field).isInstanceOf(RuntimeException.class);
        }
        assertThat(count("cash_beneficiary_verifications",r.tenant())).isZero();commitBeneficiary(r,Map.of());commitAuthorization(r,Map.of());
    }
    @Test void uneAutorisationSansAuditNePeutCommitterEtNeLibereAucunFond(){
        Raw r=raw(true);
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->authorization(r,UUID.randomUUID(),Map.of(),false)))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("audit lie");
        assertThat(count("cash_restitution_authorizations",r.tenant())).isZero();assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("15000");
        commitAuthorization(r,Map.of());
    }
    @Test void lesFaitsEtLeurBudgetNePeuventEtreReassociesSupprimesOuTronques(){
        Raw r=raw(true);commitAuthorization(r,Map.of());
        for(String table:List.of("cash_remitter_identifications","cash_beneficiary_verifications","cash_restitution_authorizations")){
            assertThatThrownBy(()->jdbc.sql("UPDATE "+table+" SET establishment_id=gen_random_uuid() WHERE establishment_id=:tenant").param("tenant",r.tenant().id()).update()).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(()->jdbc.sql("DELETE FROM "+table+" WHERE establishment_id=:tenant").param("tenant",r.tenant().id()).update()).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(()->jdbc.sql("TRUNCATE "+table+" CASCADE").update()).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(()->jdbc.sql("TRUNCATE cash_remitter_reviews CASCADE").update()).isInstanceOf(RuntimeException.class);
        assertThat(count("cash_restitution_authorizations",r.tenant())).isEqualTo(1);assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("15000");
    }
    @ParameterizedTest @ValueSource(ints={TransactionDefinition.ISOLATION_REPEATABLE_READ,TransactionDefinition.ISOLATION_SERIALIZABLE})
    void uneIsolationNonPriseEnChargeNePeutPreparerOuAutoriser(int isolation){
        Raw r=raw(false);var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(isolation);
        assertThatThrownBy(()->tx.executeWithoutResult(s->beneficiary(r,Map.of()))).isInstanceOf(RuntimeException.class).hasStackTraceContaining("READ COMMITTED");
        commitBeneficiary(r,Map.of());
        assertThatThrownBy(()->tx.executeWithoutResult(s->authorization(r,UUID.randomUUID(),Map.of(),true))).isInstanceOf(RuntimeException.class).hasStackTraceContaining("READ COMMITTED");
        commitAuthorization(r,Map.of());
    }
    @Test void deuxTransactionsSqlNeCreentQuUneAutorisationPourLaReservation() throws Exception{
        Raw r=raw(true);CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->{barrier.await();return attempt(r);});var b=executor.submit(()->{barrier.await();return attempt(r);});
            barrier.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(count("cash_restitution_authorizations",r.tenant())).isEqualTo(1);assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("15000");
    }
    boolean attempt(Raw r){try{commitAuthorization(r,Map.of());return true;}catch(org.springframework.dao.DataIntegrityViolationException conflict){
        assertThat(((java.sql.SQLException)conflict.getMostSpecificCause()).getSQLState()).isEqualTo("23505");return false;}}

    void initialReceipt(Raw r,String policy,boolean identified,boolean identityAudit){
        UUID id=UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            jdbc.sql("""
                    INSERT INTO cash_receptions(id,establishment_id,reference,idempotency_key,currency,amount_received_minor,change_given_minor,
                      cash_received_confirmed,recorded_at,recorded_by_account_id,business_date,timezone,identification_policy)
                    VALUES (:id,:tenant,:reference,:key,'XAF',20000,0,true,'2026-11-11T09:00:00Z',:actor,'2026-11-11','Africa/Ndjamena',:policy)
                    """).param("id",id).param("tenant",r.tenant().id()).param("reference","CR-"+id).param("key",UUID.randomUUID()).param("actor",r.owner()).param("policy",policy).update();
            jdbc.sql("""
                    INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                    VALUES (:event,now(),:tenant,:actor,'payments.cash.received','cash_reception',:id,jsonb_build_object('reference',CAST(:reference AS text)))
                    """).param("event",UUID.randomUUID()).param("tenant",r.tenant().id()).param("actor",r.owner()).param("id",id).param("reference","CR-"+id).update();
            if(identified)CashIdentityFixtures.identifyRaw(jdbc,r.tenant().id(),id,r.owner(),identityAudit);
        });
    }
    @Test void uneNouvelleReceptionSqlNePeutEmprunterLeRegimeHistoriqueOuOmettreSonIdentite(){
        Raw r=raw(false);
        for(String policy:new String[]{null,"LEGACY_UNIDENTIFIED","AUTRE"}){
            assertThatThrownBy(()->initialReceipt(r,policy,true,true)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("identite de remettant");
        }
        assertThatThrownBy(()->initialReceipt(r,"IDENTIFIED_V1",false,false)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("identification liee");
        assertThat(count("cash_receptions",r.tenant())).isEqualTo(1);initialReceipt(r,"IDENTIFIED_V1",true,true);
        assertThat(count("cash_receptions",r.tenant())).isEqualTo(2);
    }
    @Test void unAuditDIdentificationManquantAnnuleAussiLaNouvelleReception(){
        Raw r=raw(false);
        assertThatThrownBy(()->initialReceipt(r,"IDENTIFIED_V1",true,false)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("audit lie");
        assertThat(count("cash_receptions",r.tenant())).isEqualTo(1);assertThat(count("cash_remitter_identifications",r.tenant())).isEqualTo(1);
        initialReceipt(r,"IDENTIFIED_V1",true,true);assertThat(count("cash_remitter_identifications",r.tenant())).isEqualTo(2);
    }
}
