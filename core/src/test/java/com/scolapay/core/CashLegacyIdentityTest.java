package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class CashLegacyIdentityTest extends CashIdentityProbeScenario {
    @Test void uneAncienneReceptionConserveSesFaitsEtExigeUneVerificationIndependante() throws Exception{
        Legacy legacy=legacy();Instance current=start(null,"",true);var school=legacy.school();
        var before=jdbc.sql("SELECT * FROM cash_receptions WHERE id=:id").param("id",legacy.source()).query().singleRow();
        var pending=declare(current.api(),legacy,UUID.randomUUID());assertThat(pending.status()).as(pending.raw()).isEqualTo(201);
        String path=school.path("/payments/cash-receptions/"+legacy.source()+"/remitter-reviews");
        assertThat(current.api().post(path,school.adminToken(),review(UUID.randomUUID())).status()).isEqualTo(403);
        assertThat(current.api().get(school.path("/payments/cash-receptions/"+legacy.source()+"/remitter"),school.adminToken()).text("state"))
                .isEqualTo("LEGACY_PENDING_REVIEW");
        var checked=current.api().post(path,reviewer(current.api(),school),review(UUID.randomUUID()));
        assertThat(checked.status()).as(checked.raw()).isEqualTo(201);
        assertThat(current.api().get(school.path("/payments/cash-receptions/"+legacy.source()+"/remitter"),school.adminToken()).text("state"))
                .isEqualTo("LEGACY_VERIFIED");
        assertThat(jdbc.sql("SELECT * FROM cash_receptions WHERE id=:id").param("id",legacy.source()).query().singleRow()).isEqualTo(before);
        var replay=current.api().post(school.path("/payments/cash-receptions"),school.adminToken(),Map.of("idempotencyKey",legacy.originalKey(),
                "amountReceivedMinor","20000","changeGivenMinor","0","cashReceivedConfirmed",true));
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.text("unallocatedMinor")).isEqualTo("20000");
        assertThat(before).containsAllEntriesOf(legacyReceipt).containsEntry("identification_policy","LEGACY_UNIDENTIFIED");
        assertThat(history().subList(0,legacyHistory.size())).isEqualTo(legacyHistory);
        assertThat(history().get(legacyHistory.size())).containsEntry("script","V11__remitter_identity_and_restitution_authorization.sql");
        assertThat(history().getLast()).containsEntry("script","V12__digital_payment_preparations.sql");
    }
    @Test void lesAnciennesApprobationsExigentUneNouvelleAutorisationApresVerificationDeLaSource() throws Exception{
        Legacy l=legacy();Instance current=start(null,"",true);String root=l.school().path("/payments"),actor=l.school().adminToken();
        Map<String,Object> check=Map.of("idempotencyKey",UUID.randomUUID(),"beneficiaryIsRemitter",true,"identityCheckedConfirmed",true,"verificationDetails","Remettant rapproché du reçu et de sa déclaration signée");
        String beneficiaryPath=root+"/excess-requests/"+l.requestId()+"/beneficiary-verifications";
        assertThat(current.api().post(beneficiaryPath,actor,check).code()).isEqualTo("REMITTER_VERIFICATION_REQUIRED");
        UUID declarationKey=UUID.randomUUID();assertThat(declare(current.api(),l,declarationKey).status()).isEqualTo(201);
        assertThat(current.api().post(beneficiaryPath,actor,check).code()).isEqualTo("REMITTER_VERIFICATION_REQUIRED");
        assertThat(current.api().post(root+"/cash-receptions/"+l.source()+"/remitter-reviews",l.reviewerToken(),review(UUID.randomUUID())).status()).isEqualTo(201);
        var verified=current.api().post(beneficiaryPath,actor,check);assertThat(verified.status()).as(verified.raw()).isEqualTo(201);
        var authorized=current.api().post(root+"/excess-requests/"+l.requestId()+"/restitution-authorizations",l.reviewerToken(),Map.of(
                "idempotencyKey",UUID.randomUUID(),"beneficiaryVerificationId",verified.id("id"),"beneficiaryReviewedConfirmed",true));
        assertThat(authorized.status()).as(authorized.raw()).isEqualTo(201);assertThat(authorized.text("policy")).isEqualTo("NAMED_REMITTER_V1");
        assertThat(authorized.text("restitutedMinor")).isEqualTo("0");
        assertThat(jdbc.sql("SELECT * FROM cash_excess_requests WHERE id=:id").param("id",l.requestId()).query().singleRow()).isEqualTo(legacyRequest);
        assertThat(jdbc.sql("SELECT * FROM cash_excess_decisions WHERE request_id=:id").param("id",l.requestId()).query().singleRow()).isEqualTo(legacyDecision);
        var funds=current.api().get(root+"/cash-receptions/"+l.source(),actor);assertThat(funds.text("reservedMinor")).isEqualTo("15000");
        assertThat(funds.text("availableForAllocationMinor")).isEqualTo("5000");
        assertThat(current.api().post(root+"/excess-requests/"+l.requestId()+"/handover-confirmations",actor,Map.of()).status()).isEqualTo(404);
    }
    @Test void laDeclarationEtSonExamenSontIdempotentsMaisAucunControleNePeutEtreOmis() throws Exception{
        Legacy l=legacy();Instance current=start(null,"",true);String path=l.school().path("/payments/cash-receptions/"+l.source()+"/remitter-reviews");
        UUID key=UUID.randomUUID(),reviewKey=UUID.randomUUID();
        assertThat(declare(current.api(),l,key).status()).isEqualTo(201);assertThat(declare(current.api(),l,key).status()).isEqualTo(200);
        assertThat(declare(current.api(),l,UUID.randomUUID()).status()).isEqualTo(409);
        var incomplete=new java.util.HashMap<>(review(reviewKey));incomplete.put("signedStatementCheckedConfirmed",false);
        assertThat(current.api().post(path,l.reviewerToken(),incomplete).status()).isEqualTo(422);
        var checked=current.api().post(path,l.reviewerToken(),review(reviewKey));assertThat(checked.status()).as(checked.raw()).isEqualTo(201);
        assertThat(current.api().post(path,l.reviewerToken(),review(reviewKey)).body()).isEqualTo(checked.body());
        assertThat(current.api().post(path,l.reviewerToken(),review(UUID.randomUUID())).status()).isEqualTo(409);
        assertThatThrownBy(()->jdbc.sql("UPDATE cash_remitter_reviews SET reviewed_by=:actor WHERE identification_id=:id")
                .param("actor",current.api().get("/api/v2/me",l.school().adminToken()).id("id")).param("id",l.source()).update()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.sql("DELETE FROM cash_remitter_reviews WHERE identification_id=:id").param("id",l.source()).update()).isInstanceOf(RuntimeException.class);
        assertThat(current.api().get(l.school().path("/payments/cash-receptions/"+l.source()+"/remitter"),l.school().adminToken()).text("state")).isEqualTo("LEGACY_VERIFIED");
    }
}
