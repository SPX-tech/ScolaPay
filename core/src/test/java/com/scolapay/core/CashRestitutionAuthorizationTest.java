package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
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
}
