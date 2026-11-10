package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Une correction ne change ni la personne financière, ni son compte, ni ses désignations. */
class PartyCorrectionTest extends BillingScenarioTest {
    @Test
    void corrigerUnePersonneLieeConserveSonIdentiteEtSesDroits() throws Exception {
        School school=school();
        Tenant tenant=school.tenant();
        UUID student=enrolledAndBilled(school);
        Linked linked=linkedThirdParty(tenant,student,false);
        UUID party=partyId(createParty(tenant,"PERSON","Nom initial","REF-1",linked.grant()));
        String designation=designate(tenant,tenant.adminToken(),obligationOf(student),party,"CONTRACTUAL_DEBTOR").text("id");
        Response corrected=post(tenant.path("/billing/parties/"+party+"/corrections"),tenant.adminToken(),Map.of(
                "expectedRevision",0,"displayName","Nom corrigé","identifier","REF-2","reason","Correction de saisie validée"));
        assertThat(corrected.status()).as(corrected.raw()).isEqualTo(201);
        var stored=jdbc.sql("SELECT id,account_id,display_name,identifier FROM billing_parties WHERE id=:id").param("id",party).query().singleRow();
        assertThat(stored.get("id")).isEqualTo(party);
        assertThat(stored.get("account_id")).isEqualTo(linked.account());
        assertThat(stored.get("display_name")).isEqualTo("Nom corrigé");
        assertThat(stored.get("identifier")).isEqualTo("REF-2");
        Response history=get(tenant.path("/billing/parties/"+party+"/corrections"),tenant.adminToken());
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body().size()).isEqualTo(1);
        assertThat(history.body().get(0).path("previousName").asString()).isEqualTo("Nom initial");
        assertThat(history.body().get(0).path("newName").asString()).isEqualTo("Nom corrigé");
        assertThat(get(tenant.path("/billing/obligations/"+obligationOf(student)+"/parties"),tenant.adminToken()).body().get(0).path("id").asString()).isEqualTo(designation);
        assertThat(get("/api/v2/me/billing/obligations/"+obligationOf(student),linked.token()).status()).isEqualTo(200);
    }
}
