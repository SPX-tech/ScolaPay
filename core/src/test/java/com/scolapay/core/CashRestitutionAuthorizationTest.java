package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties="scolapay.payments.cash-identities-enabled=true")
class CashRestitutionAuthorizationTest extends CashExcessScenario {
    UUID approved(Tenant t,UUID source,String reviewer){
        Response requested=request(t,source,UUID.randomUUID(),15000,"Excédent à restituer");
        assertThat(requested.status()).as(requested.raw()).isEqualTo(201);UUID id=requested.id("id");
        assertThat(decision(t,reviewer,id,UUID.randomUUID(),"approval",null).status()).isEqualTo(201);return id;
    }
    Response verify(Tenant t,UUID id,UUID key){
        return post(t.path("/payments/excess-requests/"+id+"/beneficiary-verifications"),t.adminToken(),Map.of(
                "idempotencyKey",key,"beneficiaryIsRemitter",true,"identityCheckedConfirmed",true,"verificationDetails","Concordance vérifiée sans copie intégrale"));
    }
    Response authorize(Tenant t,String actor,UUID request,UUID verification,UUID key){
        return post(t.path("/payments/excess-requests/"+request+"/restitution-authorizations"),actor,Map.of(
                "idempotencyKey",key,"beneficiaryVerificationId",verification,"beneficiaryReviewedConfirmed",true));
    }
    @Test void uneNouvelleAutorisationNominativeNeTransformePasLApprobationHistoriqueNiLeBudget(){
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);String reviewer=newStaff(t,"DIRECTOR");
        UUID request=approved(t,source,reviewer);Response verified=verify(t,request,UUID.randomUUID());
        assertThat(verified.status()).as(verified.raw()).isEqualTo(201);
        Response authorized=authorize(t,reviewer,request,verified.id("id"),UUID.randomUUID());
        assertThat(authorized.status()).as(authorized.raw()).isEqualTo(201);
        assertThat(authorized.text("policy")).isEqualTo("NAMED_REMITTER_V1");
        assertThat(authorized.body().path("physicalHandoverAvailable").asBoolean()).isFalse();
        assertThat(authorized.body().path("finalIdentityCheckRequired").asBoolean()).isTrue();
        assertThat(excess(t,request).text("authorizationScope")).isEqualTo("RESERVATION_ONLY_V1");
        assertThat(funds(t,source).text("reservedMinor")).isEqualTo("15000");
        assertThat(funds(t,source).text("unallocatedMinor")).isEqualTo("20000");
    }
    @Test void leDemandeurNePeutPasSAutoAutoriserEtUneRepriseNeCreePasDAutorisationSupplementaire(){
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);String reviewer=newStaff(t,"DIRECTOR");
        UUID request=approved(t,source,reviewer);Response verified=verify(t,request,UUID.randomUUID());
        assertThat(verified.status()).as(verified.raw()).isEqualTo(201);
        UUID verification=verified.id("id"),key=UUID.randomUUID();
        assertThat(authorize(t,t.adminToken(),request,verification,UUID.randomUUID()).status()).isEqualTo(403);
        Response first=authorize(t,reviewer,request,verification,key);assertThat(first.status()).as(first.raw()).isEqualTo(201);
        assertThat(authorize(t,reviewer,request,verification,key).body()).isEqualTo(first.body());
        assertThat(count("cash_restitution_authorizations",t)).isEqualTo(1);
        assertThat(post(t.path("/payments/excess-requests/"+request+"/handover-confirmations"),t.adminToken(),Map.of(
                "idempotencyKey",UUID.randomUUID(),"physicalHandoverConfirmed",true)).status()).isEqualTo(404);
    }
    @Test void aucunTiersNiCompteConnecteNeDevientImplicitementBeneficiaire(){
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),request=approved(t,source,newStaff(t,"DIRECTOR"));
        Response third=post(t.path("/payments/excess-requests/"+request+"/beneficiary-verifications"),t.adminToken(),Map.of(
                "idempotencyKey",UUID.randomUUID(),"beneficiaryIsRemitter",false,"identityCheckedConfirmed",true,"verificationDetails","Mandat allégué"));
        assertThat(third.code()).isEqualTo("ALTERNATE_BENEFICIARY_UNAVAILABLE");
        assertThat(post(t.path("/payments/excess-requests/"+request+"/beneficiary-verifications"),t.adminToken(),Map.of(
                "idempotencyKey",UUID.randomUUID(),"beneficiaryIsRemitter",true,"identityCheckedConfirmed",true,"verificationDetails","Compte allégué",
                "beneficiaryAccountId",UUID.randomUUID())).status()).isEqualTo(400);
        assertThat(count("cash_beneficiary_verifications",t)).isZero();assertThat(funds(t,source).text("reservedMinor")).isEqualTo("15000");
    }
    @Test void uneReservationSansApprobationTechniqueOuRejeteeNePeutEtrePreparee(){
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),request=request(t,source,UUID.randomUUID(),15000,"En attente").id("id");
        assertThat(verify(t,request,UUID.randomUUID()).code()).isEqualTo("APPROVED_RESERVATION_REQUIRED");
        assertThat(decision(t,newStaff(t,"DIRECTOR"),request,UUID.randomUUID(),"rejection","Examen refusé").status()).isEqualTo(201);
        assertThat(verify(t,request,UUID.randomUUID()).code()).isEqualTo("APPROVED_RESERVATION_REQUIRED");
        assertThat(count("cash_beneficiary_verifications",t)).isZero();
    }
    @Test void leRapprochementEstIdempotentEtSonIdentiteNePeutEtreChangee(){
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),request=approved(t,source,newStaff(t,"DIRECTOR")),key=UUID.randomUUID();
        Response first=verify(t,request,key);assertThat(first.status()).as(first.raw()).isEqualTo(201);
        assertThat(verify(t,request,key).body()).isEqualTo(first.body());
        Response changed=post(t.path("/payments/excess-requests/"+request+"/beneficiary-verifications"),t.adminToken(),Map.of(
                "idempotencyKey",key,"beneficiaryIsRemitter",true,"identityCheckedConfirmed",true,"verificationDetails","AUTRE-ELEMENT-PRIVE"));
        assertThat(changed.code()).isEqualTo("CASH_RESTITUTION_KEY_CONFLICT");assertThat(changed.raw()).doesNotContain("AUTRE-ELEMENT");
        assertThat(verify(t,request,UUID.randomUUID()).code()).isEqualTo("BENEFICIARY_ALREADY_RECORDED");
        assertThat(count("cash_beneficiary_verifications",t)).isEqualTo(1);
    }
    @Test void lesRejeuxConcurrentesNAutorisentQuUneFoisEtDeuxApprobateursNePrennentPasDeuxDecisions() throws Exception{
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);String director=newStaff(t,"DIRECTOR");
        UUID request=approved(t,source,director),verification=verify(t,request,UUID.randomUUID()).id("id"),key=UUID.randomUUID();
        var replies=concurrently(8,()->authorize(t,director,request,verification,key));
        assertThat(replies).allSatisfy(r->assertThat(r.status()).as(r.raw()).isIn(200,201));
        assertThat(replies.stream().filter(r->r.status()==201)).hasSize(1);assertThat(count("cash_restitution_authorizations",t)).isEqualTo(1);
        UUID other=receive(t,t.adminToken(),20000),r=approved(t,other,director),v=verify(t,r,UUID.randomUUID()).id("id");
        String accountant=newStaff(t,"ACCOUNTANT");CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->{barrier.await();return authorize(t,director,r,v,UUID.randomUUID());});
            var b=executor.submit(()->{barrier.await();return authorize(t,accountant,r,v,UUID.randomUUID());});
            barrier.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS).status(),b.get(15,TimeUnit.SECONDS).status())).containsExactlyInAnyOrder(201,409);
        }
        assertThat(count("cash_restitution_authorizations",t)).isEqualTo(2);
    }
    @Test void lesIdentitesEtLesAutorisationsRestentIsoleesEtAucunDroitFamilialNEstCree(){
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);String director=newStaff(t,"DIRECTOR");
        UUID request=approved(t,source,director),v=verify(t,request,UUID.randomUUID()).id("id");
        Response authorized=authorize(t,director,request,v,UUID.randomUUID());assertThat(authorized.status()).isEqualTo(201);
        String b=t.path("/payments/beneficiary-verifications/"+v),a=t.path("/payments/restitution-authorizations/"+authorized.id("id"));
        String cashier=newStaff(t,"CASHIER"),auditor=newStaff(t,"AUDITOR"),foreign=newTenant().adminToken(),guardian=newGuardian(t,newStudent(t));
        assertThat(get(b,cashier).status()).isEqualTo(403);assertThat(get(a,cashier).status()).isEqualTo(403);
        assertThat(get(b,auditor).status()).isEqualTo(200);assertThat(get(a,auditor).status()).isEqualTo(200);
        for(String outsider:List.of(foreign,guardian,operatorToken())){assertThat(get(b,outsider).status()).isEqualTo(404);assertThat(get(a,outsider).status()).isEqualTo(404);}
        assertThat(authorized.raw()).doesNotContain("Remettant de test","Concordance vérifiée");
    }
    @ParameterizedTest @ValueSource(strings={"CASHIER","REGISTRAR","AUDITOR"})
    void seulsLesExaminateursHabilitesPeuventAutoriser(String role){
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),r=approved(t,source,newStaff(t,"DIRECTOR")),v=verify(t,r,UUID.randomUUID()).id("id");
        assertThat(authorize(t,newStaff(t,role),r,v,UUID.randomUUID()).status()).isEqualTo(403);
    }
    @Test void uneAutorisationNePeutReprendreLeBeneficiaireDUneAutreReservation(){
        Tenant t=newTenant();String director=newStaff(t,"DIRECTOR");UUID source=receive(t,t.adminToken(),40000),r=approved(t,source,director),v=verify(t,r,UUID.randomUUID()).id("id");
        UUID other=approved(t,source,director);assertThat(authorize(t,director,other,v,UUID.randomUUID()).code()).isEqualTo("BENEFICIARY_REQUEST_MISMATCH");
        assertThat(authorize(t,director,r,UUID.randomUUID(),UUID.randomUUID()).status()).isEqualTo(404);
        assertThat(count("cash_restitution_authorizations",t)).isZero();assertThat(funds(t,source).text("reservedMinor")).isEqualTo("30000");
    }
    @Test void unAuditDAutorisationDefaillantNeLiberePasLaReservationNiNePerdLeRapprochement(){
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);String director=newStaff(t,"DIRECTOR");
        UUID r=approved(t,source,director),v=verify(t,r,UUID.randomUUID()).id("id"),key=UUID.randomUUID();
        String constraint="auth_audit_"+UUID.randomUUID().toString().replace("-","");
        jdbc.sql("ALTER TABLE audit_events ADD CONSTRAINT "+constraint+" CHECK (NOT (establishment_id='"+t.id()+"'::uuid AND action='payments.restitution.authorized')) NOT VALID").update();
        try{
            Response refused=authorize(t,director,r,v,key);assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
            assertThat(refused.raw()).doesNotContain("INSERT","SQLException","Concordance");
            assertThat(count("cash_restitution_authorizations",t)).isZero();assertThat(count("cash_beneficiary_verifications",t)).isEqualTo(1);
            assertThat(funds(t,source).text("reservedMinor")).isEqualTo("15000");
        }finally{jdbc.sql("ALTER TABLE audit_events DROP CONSTRAINT "+constraint).update();}
        assertThat(authorize(t,director,r,v,key).status()).isEqualTo(201);
    }
    @Test void uneAutorisationEtUneAffectationConcurrentesGardentLeBudgetReserve() throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s),source=receive(t,t.adminToken(),20000);String director=newStaff(t,"DIRECTOR");
        UUID r=approved(t,source,director),v=verify(t,r,UUID.randomUUID()).id("id");CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->{barrier.await();return authorize(t,director,r,v,UUID.randomUUID());});
            var b=executor.submit(()->{barrier.await();return allocate(t,t.adminToken(),source,o,UUID.randomUUID(),5000,1);});
            barrier.countDown();assertThat(a.get(15,TimeUnit.SECONDS).status()).isEqualTo(201);assertThat(b.get(15,TimeUnit.SECONDS).status()).isEqualTo(201);
        }
        assertThat(funds(t,source).text("reservedMinor")).isEqualTo("15000");assertThat(funds(t,source).text("historicalAllocatedMinor")).isEqualTo("5000");
        assertThat(funds(t,source).text("availableForAllocationMinor")).isEqualTo("0");
        assertThat(request(t,source,UUID.randomUUID(),1,"Aucun budget supplémentaire").status()).isEqualTo(422);
        assertThat(position(t,o).path("settledMinor").asString()).isEqualTo("5000");
    }
}
