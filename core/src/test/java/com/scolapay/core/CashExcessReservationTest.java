package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CashExcessReservationTest extends CashExcessScenario {
    @Test void uneReceptionPartiellementAffecteeConserveUnExcedentReservableSansSortieDEspeces() throws Exception {
        School school=school();Tenant t=school.tenant();UUID obligation=issuedObligation(school);
        UUID source=receive(t,t.adminToken(),120000);
        assertThat(allocate(t,t.adminToken(),source,obligation,UUID.randomUUID(),100000,1).status()).isEqualTo(201);
        Response requested=request(t,source,UUID.randomUUID(),20000,"Excédent jamais affecté");
        assertThat(requested.status()).as(requested.raw()).isEqualTo(201);
        assertThat(requested.text("state")).isEqualTo("REQUESTED");
        assertThat(requested.text("authorizationScope")).isEqualTo("RESERVATION_ONLY_V1");
        String director=newStaff(t,"DIRECTOR");
        Response approved=post(t.path("/payments/excess-requests/"+requested.id("id")+"/approval"),director,Map.of("idempotencyKey",UUID.randomUUID()));
        assertThat(approved.status()).as(approved.raw()).isEqualTo(201);
        assertThat(approved.text("restitutedMinor")).isEqualTo("0");
        Response reread=get(t.path("/payments/excess-requests/"+requested.id("id")),t.adminToken());
        assertThat(reread.text("state")).isEqualTo("APPROVED_RESERVED");
        assertThat(reread.body().path("physicalHandoverAvailable").asBoolean()).isFalse();
        Response funds=get(t.path("/payments/cash-receptions/"+source),t.adminToken());
        assertThat(funds.text("unallocatedMinor")).isEqualTo("20000");
        assertThat(funds.text("reservedMinor")).isEqualTo("20000");
        assertThat(funds.text("availableForAllocationMinor")).isEqualTo("0");
        assertThat(position(t,obligation).path("settledMinor").asString()).isEqualTo("100000");
    }
    @Test void unRejeuNeReservePasDeuxFoisEtNeChangePasLaDemande() {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),key=UUID.randomUUID();
        Response first=request(t,source,key,15000,"Excédent à examiner");
        assertThat(first.status()).as(first.raw()).isEqualTo(201);
        Response replay=request(t,source,key,15000,"Excédent à examiner");
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(request(t,source,key,15001,"Excédent à examiner").code()).isEqualTo("CASH_EXCESS_KEY_CONFLICT");
    }
    @Test void huitRejeuxNeReservenQuUneFoisEtDeuxDemandesNeDepassentPasLeBudget() throws Exception {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),key=UUID.randomUUID();
        var replies=concurrently(8,()->request(t,source,key,15000,"Excédent à examiner"));
        assertThat(replies).allSatisfy(r->assertThat(r.status()).as(r.raw()).isIn(200,201));
        assertThat(replies.stream().filter(r->r.status()==201)).hasSize(1);
        assertThat(count("cash_excess_requests",t)).isEqualTo(1);
        assertThat(funds(t,source).text("reservedMinor")).isEqualTo("15000");
        assertThat(request(t,source,UUID.randomUUID(),5001,"Nouvelle référence").code()).isEqualTo("EXCESS_BUDGET_EXCEEDED");
        UUID other=receive(t,t.adminToken(),20000);
        var concurrent=concurrently(2,()->request(t,other,UUID.randomUUID(),15000,"Deux demandes distinctes"));
        assertThat(concurrent.stream().map(Response::status)).containsExactlyInAnyOrder(201,422);
        assertThat(funds(t,other).text("availableForAllocationMinor")).isEqualTo("5000");
    }

    @Test void uneAffectationEtUneReservationConcurrentesNeConsommentPasLesMemesFonds() throws Exception {
        School s=school(200000);Tenant t=s.tenant();UUID o=issuedObligation(s),source=receive(t,t.adminToken(),120000);
        assertThat(allocate(t,t.adminToken(),source,o,UUID.randomUUID(),100000,1).status()).isEqualTo(201);
        CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->{barrier.await();return allocate(t,t.adminToken(),source,o,UUID.randomUUID(),15000,2);});
            var b=executor.submit(()->{barrier.await();return request(t,source,UUID.randomUUID(),15000,"Excédent réservé");});
            barrier.countDown();var ra=a.get(15,TimeUnit.SECONDS);var rb=b.get(15,TimeUnit.SECONDS);
            assertThat(List.of(ra.status(),rb.status())).containsExactlyInAnyOrder(201,422);
        }
        Response balance=funds(t,source);
        assertThat(Long.parseLong(balance.text("historicalAllocatedMinor"))+Long.parseLong(balance.text("reservedMinor")))
                .isEqualTo(115000);
        assertThat(balance.text("availableForAllocationMinor")).isEqualTo("5000");
    }

    @Test void seulesLesDemandesNonApprouveesPeuventEtreRetireesOuRejetees() {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);
        UUID pending=request(t,source,UUID.randomUUID(),15000,"Premier examen").id("id");
        UUID withdrawKey=UUID.randomUUID();
        Response withdrawn=decision(t,t.adminToken(),pending,withdrawKey,"withdrawal","Reformuler avant examen");
        assertThat(withdrawn.status()).as(withdrawn.raw()).isEqualTo(201);
        assertThat(decision(t,t.adminToken(),pending,withdrawKey,"withdrawal","Reformuler avant examen").body()).isEqualTo(withdrawn.body());
        assertThat(funds(t,source).text("reservedMinor")).isEqualTo("0");
        String reviewer=newStaff(t,"DIRECTOR");
        assertThat(decision(t,reviewer,pending,UUID.randomUUID(),"approval",null).status()).isEqualTo(409);
        UUID next=request(t,source,UUID.randomUUID(),15000,"Nouvelle demande").id("id");
        assertThat(decision(t,reviewer,next,UUID.randomUUID(),"rejection","Réservation non retenue").status()).isEqualTo(201);
        assertThat(funds(t,source).text("availableForAllocationMinor")).isEqualTo("20000");
        UUID last=request(t,source,UUID.randomUUID(),15000,"Réservation budgétaire").id("id");
        assertThat(decision(t,reviewer,last,UUID.randomUUID(),"approval",null).status()).isEqualTo(201);
        assertThat(decision(t,t.adminToken(),last,UUID.randomUUID(),"withdrawal","Après approbation").status()).isEqualTo(409);
        assertThat(funds(t,source).text("reservedMinor")).isEqualTo("15000");
    }

    @Test void uneApprobationEstIdempotenteMaisJamaisUneSortieEtNePeutEtreAutoApprouvee() throws Exception {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);
        UUID id=request(t,source,UUID.randomUUID(),15000,"Examen du budget").id("id");
        assertThat(decision(t,t.adminToken(),id,UUID.randomUUID(),"approval",null).status()).isEqualTo(403);
        String director=newStaff(t,"DIRECTOR");UUID key=UUID.randomUUID();
        var replies=concurrently(8,()->decision(t,director,id,key,"approval",null));
        assertThat(replies).allSatisfy(r->assertThat(r.status()).as(r.raw()).isIn(200,201));
        assertThat(replies.stream().filter(r->r.status()==201)).hasSize(1);
        assertThat(count("cash_excess_decisions",t)).isEqualTo(1);
        assertThat(excess(t,id).text("restitutedMinor")).isEqualTo("0");
        for(int i=0;i<2;i++){
            assertThat(post(t.path("/payments/excess-requests/"+id+"/handover-confirmations"),t.adminToken(),
                    Map.of("idempotencyKey",UUID.randomUUID(),"physicalHandoverConfirmed",true)).status()).isEqualTo(404);
        }
        assertThat(funds(t,source).text("unallocatedMinor")).isEqualTo("20000");
        assertThat(funds(t,source).text("reservedMinor")).isEqualTo("15000");
    }

    @Test void deuxApprobateursNePrennentQuUneDecisionEtUneNouvelleCleNeRecreePasLaProvenance() throws Exception {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);
        UUID id=request(t,source,UUID.randomUUID(),15000,"Deux examinateurs").id("id");
        String a=newStaff(t,"DIRECTOR"),b=newStaff(t,"ACCOUNTANT");CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var fa=executor.submit(()->{barrier.await();return decision(t,a,id,UUID.randomUUID(),"approval",null);});
            var fb=executor.submit(()->{barrier.await();return decision(t,b,id,UUID.randomUUID(),"approval",null);});
            barrier.countDown();assertThat(List.of(fa.get(15,TimeUnit.SECONDS).status(),fb.get(15,TimeUnit.SECONDS).status()))
                    .containsExactlyInAnyOrder(201,409);
        }
        assertThat(count("cash_excess_decisions",t)).isEqualTo(1);
        assertThat(request(t,source,UUID.randomUUID(),5001,"Autre référence").status()).isEqualTo(422);
    }

    @Test void lesFondsAyantEteAffectesNeReprennentJamaisUneEligibilite() throws Exception {
        School s=school(200000);Tenant t=s.tenant();UUID o=issuedObligation(s),source=receive(t,t.adminToken(),120000);
        assertThat(allocate(t,t.adminToken(),source,o,UUID.randomUUID(),100000,1).status()).isEqualTo(201);
        Response last=allocate(t,t.adminToken(),source,o,UUID.randomUUID(),10000,2);assertThat(last.status()).isEqualTo(201);
        assertThat(reversal(t,t.adminToken(),o,last.text("settlementEntryId")).status()).isIn(409,422);
        assertThat(request(t,source,UUID.randomUUID(),10001,"Tentative de reprise").status()).isEqualTo(422);
        assertThat(request(t,source,UUID.randomUUID(),10000,"Fraction jamais affectée").status()).isEqualTo(201);
        assertThat(funds(t,source).text("historicalAllocatedMinor")).isEqualTo("110000");
        assertThat(funds(t,source).text("availableForAllocationMinor")).isEqualTo("0");
        UUID fully=receive(t,t.adminToken(),10000);
        assertThat(allocate(t,t.adminToken(),fully,o,UUID.randomUUID(),10000,3).status()).isEqualTo(201);
        assertThat(request(t,fully,UUID.randomUUID(),1,"Entièrement affecté").status()).isEqualTo(422);
    }

    @ParameterizedTest
    @ValueSource(strings={"CASHIER","ACCOUNTANT","ESTABLISHMENT_ADMIN"})
    void lesOperateursReserventSeulementLeursPropresReceptions(String role) {
        Tenant t=newTenant();String actor=newStaff(t,role);UUID source=receive(t,actor,20000);
        assertThat(request(t,actor,source,UUID.randomUUID(),15000,"Réservation propre").status()).isEqualTo(201);
        assertThat(request(t,t.adminToken(),source,UUID.randomUUID(),1,"Autre déclarant").status()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings={"DIRECTOR","ACCOUNTANT","ESTABLISHMENT_ADMIN"})
    void lesExaminateursPeuventApprouverSansAutoApprobation(String role) {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),id=request(t,source,UUID.randomUUID(),10000,"Demande").id("id");
        assertThat(decision(t,newStaff(t,role),id,UUID.randomUUID(),"approval",null).status()).isEqualTo(201);
    }

    @ParameterizedTest
    @ValueSource(strings={"AUDITOR","REGISTRAR","CASHIER"})
    void lesRolesSansApprobationNeDecidentPas(String role) {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),id=request(t,source,UUID.randomUUID(),10000,"Demande").id("id");
        String actor=newStaff(t,role);
        assertThat(decision(t,actor,id,UUID.randomUUID(),"approval",null).status()).isEqualTo(403);
    }

    @Test void lesMotifsRestentPrivesEtUnNonMembreNePeutLireNiDecider() {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),key=UUID.randomUUID();
        UUID id=request(t,source,key,15000,"MOTIF-CONFIDENTIEL").id("id");
        Response cashier=get(t.path("/payments/excess-requests/"+id),newStaff(t,"CASHIER"));
        assertThat(cashier.status()).isEqualTo(200);
        assertThat(cashier.body().path("reason").isNull()).isTrue();assertThat(cashier.raw()).doesNotContain("MOTIF-CONFIDENTIEL");
        String auditor=newStaff(t,"AUDITOR");
        assertThat(get(t.path("/payments/excess-requests/"+id),auditor).text("reason")).isEqualTo("MOTIF-CONFIDENTIEL");
        String foreign=newTenant().adminToken();
        assertThat(get(t.path("/payments/excess-requests/by-key/"+key),foreign).status()).isEqualTo(404);
        assertThat(decision(t,foreign,id,UUID.randomUUID(),"approval",null).status()).isEqualTo(404);
        assertThat(request(t,operatorToken(),source,UUID.randomUUID(),1,"Opérateur sans adhésion").status()).isEqualTo(404);
    }

    @Test void unEchecDAuditNeCreePasDeReservationEtUnRetraitNonAuditeNeLiberePasLesFonds() throws Exception {
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s),source=receive(t,t.adminToken(),20000),key=UUID.randomUUID();
        String constraint="test_excess_"+UUID.randomUUID().toString().replace("-","");
        jdbc.sql("ALTER TABLE audit_events ADD CONSTRAINT "+constraint+" CHECK (NOT (establishment_id='"+t.id()
                +"'::uuid AND action='payments.excess.requested')) NOT VALID").update();
        try{
            Response failure=request(t,source,key,15000,"MOTIF-PRIVE");
            assertThat(failure.status()).as(failure.raw()).isEqualTo(409);
            assertThat(failure.raw()).doesNotContain("MOTIF-PRIVE","INSERT","SQLException");
            assertThat(count("cash_excess_requests",t)).isZero();
            assertThat(funds(t,source).text("availableForAllocationMinor")).isEqualTo("20000");
        }finally{jdbc.sql("ALTER TABLE audit_events DROP CONSTRAINT "+constraint).update();}
        UUID id=request(t,source,key,15000,"MOTIF-PRIVE").id("id"),withdrawKey=UUID.randomUUID();
        jdbc.sql("ALTER TABLE audit_events ADD CONSTRAINT "+constraint+" CHECK (NOT (establishment_id='"+t.id()
                +"'::uuid AND action='payments.excess.withdrawn')) NOT VALID").update();
        try{
            assertThat(decision(t,t.adminToken(),id,withdrawKey,"withdrawal","Retrait technique").status()).isEqualTo(409);
            assertThat(count("cash_excess_decisions",t)).isZero();
            assertThat(funds(t,source).text("reservedMinor")).isEqualTo("15000");
            assertThat(allocate(t,t.adminToken(),source,o,UUID.randomUUID(),15000,1).status()).isEqualTo(422);
        }finally{jdbc.sql("ALTER TABLE audit_events DROP CONSTRAINT "+constraint).update();}
        assertThat(decision(t,t.adminToken(),id,withdrawKey,"withdrawal","Retrait technique").status()).isEqualTo(201);
        assertThat(allocate(t,t.adminToken(),source,o,UUID.randomUUID(),15000,1).status()).isEqualTo(201);
    }

    @Test void laValidationRefuseUnBeneficiaireImpliciteLesMotifsVidesEtLesMontantsInvalides() {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);
        for(String amount:List.of("0","-1","1.5","1000000000000000")){
            assertThat(post(t.path("/payments/cash-receptions/"+source+"/excess-requests"),t.adminToken(),Map.of(
                    "idempotencyKey",UUID.randomUUID(),"amountMinor",amount,"reason","Motif")).status()).isEqualTo(400);
        }
        for(String reason:List.of(""," \t ","a".repeat(501))){
            assertThat(request(t,source,UUID.randomUUID(),1,reason).status()).isEqualTo(400);
        }
        assertThat(post(t.path("/payments/cash-receptions/"+source+"/excess-requests"),t.adminToken(),Map.of(
                "idempotencyKey",UUID.randomUUID(),"amountMinor","1","reason","Motif","beneficiaryAccountId",UUID.randomUUID())).status()).isEqualTo(400);
        assertThat(count("cash_excess_requests",t)).isZero();
    }
}
