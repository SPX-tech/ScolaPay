package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/** Ce que chacun voit, comparé champ par champ : un champ ajouté par mégarde fait échouer ces tests. */
class FamilyBillingViewTest extends BillingScenarioTest {

    private static final String DEBTOR = "CONTRACTUAL_DEBTOR";
    private static final String RESPONSIBLE = "FINANCIAL_RESPONSIBLE";
    private static final String CONTRIBUTOR = "VOLUNTARY_CONTRIBUTOR";
    private static final LocalDate MINOR = LocalDate.now().minusYears(14);
    private static final LocalDate ADULT = LocalDate.now().minusYears(20);
    private static final String SECRET = "BOURSE-CONFIDENTIELLE";
    private static final String NOTE = "NOTE-INTERNE";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    private record Billed(School school, UUID student, UUID obligation, Linked linked) {}

    /** Un élève facturé, un tiers lié, et une personne associée à ce tiers qui tient le rôle demandé. */
    private Billed billedWith(LocalDate birthDate, boolean academic, String role) throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = newStudent(tenant, birthDate);
        enrolledAndBilled(school, student);
        UUID obligation = obligationOf(student);
        Linked linked = linkedThirdParty(tenant, student, academic);
        UUID party = partyId(createParty(tenant, "PERSON", "Tiers Désigné", null, linked.grant()));
        Response designated = designate(tenant, tenant.adminToken(), obligation, party, role);
        assertThat(designated.status()).as(designated.raw()).isEqualTo(201);
        return new Billed(school, student, obligation, linked);
    }

    private JsonNode mine(Billed billed) {
        Response detail = get("/api/v2/me/billing/obligations/" + billed.obligation(), billed.linked().token());
        assertThat(detail.status()).as(detail.raw()).isEqualTo(200);
        return detail.body();
    }

    private static java.util.Set<String> fields(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private void adjustments(Billed billed) {
        Tenant tenant = billed.school().tenant();
        String path = tenant.path("/billing/obligations/" + billed.obligation());
        assertThat(post(path + "/discounts", tenant.adminToken(), Map.of("amountMinor", "10000", "reason", SECRET, "idempotencyKey", key())).status()).isEqualTo(201);
        assertThat(post(path + "/corrections", tenant.adminToken(), Map.of("amountMinor", "-5000", "reason", NOTE, "idempotencyKey", key())).status()).isEqualTo(201);
    }

    // Contributeur : le strict nécessaire au règlement

    @Test
    void leContributeurNeRecoitQueLesChampsAutorises() throws Exception {
        Billed billed = billedWith(MINOR, false, CONTRIBUTOR);
        adjustments(billed);

        JsonNode view = mine(billed);

        assertThat(fields(view)).containsExactlyInAnyOrder(
                "view", "id", "establishmentName", "reference", "currency", "dueMinor", "nextDueOn", "studentName");
        assertThat(view.path("view").asString()).isEqualTo("CONTRIBUTOR");
        assertThat(view.path("dueMinor").asString()).isEqualTo("85000");
        assertThat(view.path("nextDueOn").asString()).isEqualTo("");
        assertThat(view.toString()).doesNotContain(SECRET, NOTE, "Terminale", "DISCOUNT", "Tiers Désigné");
    }

    @Test
    void unContributeurNeVoitPlusUneObligationDontRienNestDu() throws Exception {
        Billed billed = billedWith(MINOR, false, CONTRIBUTOR);
        Tenant tenant = billed.school().tenant();
        assertThat(get("/api/v2/me/billing/obligations", billed.linked().token()).body().size()).isEqualTo(1);

        approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(discount(tenant, tenant.adminToken(), billed.obligation(), AMOUNT)));

        assertThat(get("/api/v2/me/billing/obligations", billed.linked().token()).body().size()).isZero();
        assertThat(get("/api/v2/me/billing/obligations/" + billed.obligation(), billed.linked().token()).status()).as("Son statut révélerait la remise").isEqualTo(404);
    }

    @Test
    void revoquerLeLienFermeAussitotLaVueDuContributeur() throws Exception {
        Billed billed = billedWith(MINOR, false, CONTRIBUTOR);
        Tenant tenant = billed.school().tenant();
        mine(billed);

        assertThat(delete(tenant.path("/students/" + billed.student() + "/access-grants/" + billed.linked().grant()), tenant.adminToken()).status()).isEqualTo(204);

        assertThat(get("/api/v2/me/billing/obligations/" + billed.obligation(), billed.linked().token()).status()).isEqualTo(404);
        assertThat(get("/api/v2/me/billing/obligations", billed.linked().token()).body().size()).isZero();
    }

    // Responsable : montants et échéances, jamais de motif

    @Test
    void leResponsableVoitLesMontantsSansMotifNiDetailDesRemises() throws Exception {
        Billed billed = billedWith(MINOR, true, RESPONSIBLE);
        adjustments(billed);

        JsonNode view = mine(billed);

        assertThat(fields(view)).containsExactlyInAnyOrder(
                "view", "id", "establishmentName", "reference", "currency", "studentName", "label", "issuedAmountMinor",
                "adjustmentsTotalMinor", "dueMinor", "status", "nextDueOn", "installments", "history");
        assertThat(view.path("view").asString()).isEqualTo("RESPONSIBLE");
        assertThat(view.path("issuedAmountMinor").asString()).isEqualTo("100000");
        assertThat(view.path("adjustmentsTotalMinor").asString()).as("Dans le total").isEqualTo("-15000");
        assertThat(view.path("dueMinor").asString()).isEqualTo("85000");
        assertThat(view.path("installments").size()).isEqualTo(1);
        assertThat(view.path("history").size()).isEqualTo(3);
        assertThat(fields(view.path("history").get(1))).containsExactlyInAnyOrder("kind", "recordedAt");
        assertThat(view.path("history").get(1).path("kind").asString()).isEqualTo("ADJUSTMENT");
        assertThat(view.toString()).doesNotContain(SECRET, NOTE, "DISCOUNT", "CORRECTION", "-10000", "-5000", "Tiers Désigné");
    }

    /** Le statut réel dirait si l'élève a été exonéré ou si la charge a été annulée. */
    @Test
    void leResponsableNeVoitPasPourquoiPlusRienNestDu() throws Exception {
        Billed billed = billedWith(MINOR, true, RESPONSIBLE);
        Tenant tenant = billed.school().tenant();
        assertThat(mine(billed).path("status").asString()).isEqualTo("OPEN");

        approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(discount(tenant, tenant.adminToken(), billed.obligation(), AMOUNT)));

        JsonNode view = mine(billed);
        assertThat(view.path("status").asString()).isEqualTo("NOTHING_DUE");
        assertThat(view.path("nextDueOn").isNull() || view.path("nextDueOn").asString().isEmpty()).isTrue();
        assertThat(view.toString()).doesNotContain("WAIVED");
    }

    // Redevable

    @Test
    void leRedevableVoitLeDetailEtLesMotifsDeRemiseSeulement() throws Exception {
        Billed billed = billedWith(MINOR, true, DEBTOR);
        adjustments(billed);

        JsonNode view = mine(billed);

        assertThat(fields(view)).containsExactlyInAnyOrder(
                "view", "id", "establishmentName", "reference", "currency", "studentName", "label", "issuedAmountMinor",
                "dueMinor", "status", "nextDueOn", "debtorStatus", "installments", "entries");
        assertThat(view.path("view").asString()).isEqualTo("DEBTOR");
        assertThat(view.path("debtorStatus").asString()).isEqualTo("UNCONFIRMED");
        JsonNode discount = view.path("entries").get(1);
        assertThat(fields(discount)).containsExactlyInAnyOrder("kind", "amountMinor", "reason", "recordedAt");
        assertThat(discount.path("kind").asString()).isEqualTo("DISCOUNT");
        assertThat(discount.path("reason").asString()).isEqualTo(SECRET);
        assertThat(view.path("entries").get(2).path("reason").isNull()).as("Une note interne de correction ne sort pas").isTrue();
        assertThat(view.toString()).doesNotContain(NOTE, "recordedBy", "approvedBy", "Tiers Désigné");
    }

    @Test
    void leRedevableVoitLeVraiStatutEtLaConfirmation() throws Exception {
        Billed billed = billedWith(MINOR, true, DEBTOR);
        Tenant tenant = billed.school().tenant();
        String designation = get(tenant.path("/billing/obligations/" + billed.obligation() + "/parties"), tenant.adminToken()).body().get(0).path("id").asString();
        assertThat(confirm(tenant, tenant.adminToken(), designation, "CONTRAT-7").status()).isEqualTo(200);
        approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(discount(tenant, tenant.adminToken(), billed.obligation(), AMOUNT)));

        JsonNode view = mine(billed);

        assertThat(view.path("debtorStatus").asString()).isEqualTo("CONFIRMED");
        assertThat(view.path("status").asString()).isEqualTo("WAIVED");
    }

    @Test
    void leRoleLePlusLargeLEmporte() throws Exception {
        Billed billed = billedWith(MINOR, true, CONTRIBUTOR);
        Tenant tenant = billed.school().tenant();
        String id = get(tenant.path("/billing/obligations/" + billed.obligation() + "/parties"), tenant.adminToken()).body().get(0).path("partyId").asString();
        assertThat(mine(billed).path("view").asString()).isEqualTo("CONTRIBUTOR");

        assertThat(designate(tenant, tenant.adminToken(), billed.obligation(), UUID.fromString(id), RESPONSIBLE).status()).isEqualTo(201);
        assertThat(mine(billed).path("view").asString()).isEqualTo("RESPONSIBLE");

        assertThat(designate(tenant, tenant.adminToken(), billed.obligation(), UUID.fromString(id), DEBTOR).status()).isEqualTo(201);
        assertThat(mine(billed).path("view").asString()).isEqualTo("DEBTOR");
    }

    @Test
    void terminerUneDesignationFermeLaVueAussitot() throws Exception {
        Billed billed = billedWith(MINOR, true, DEBTOR);
        Tenant tenant = billed.school().tenant();
        String designation = get(tenant.path("/billing/obligations/" + billed.obligation() + "/parties"), tenant.adminToken()).body().get(0).path("id").asString();
        mine(billed);

        assertThat(endDesignation(tenant, tenant.adminToken(), designation, "Engagement rompu").status()).isEqualTo(200);

        assertThat(get("/api/v2/me/billing/obligations/" + billed.obligation(), billed.linked().token()).status()).isEqualTo(404);
    }

    // Aucun rôle, aucun accès

    @Test
    void sansRoleRienNestVisibleMemeAvecUnLienOuUnCompteDuPersonnel() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = newStudent(tenant, MINOR);
        enrolledAndBilled(school, student);
        UUID obligation = obligationOf(student);
        Linked parent = linkedThirdParty(tenant, student, true);

        assertThat(get("/api/v2/me/billing/obligations", parent.token()).body().size()).as("Un lien de parenté ne suffit pas").isZero();
        assertThat(get("/api/v2/me/billing/obligations/" + obligation, parent.token()).status()).isEqualTo(404);
        assertThat(get("/api/v2/me/billing/obligations/" + obligation, tenant.adminToken()).status()).as("Être du personnel non plus").isEqualTo(404);
        assertThat(get("/api/v2/me/billing/obligations/" + UUID.randomUUID(), parent.token()).status()).isEqualTo(404);
        assertThat(get("/api/v2/me/billing/obligations", null).status()).isEqualTo(401);
    }

    @Test
    void uneFamilleNeVoitQueSesObligationsDansChaqueEtablissement() throws Exception {
        Billed first = billedWith(MINOR, true, DEBTOR);
        Billed other = billedWith(MINOR, true, DEBTOR);

        JsonNode list = get("/api/v2/me/billing/obligations", first.linked().token()).body();

        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("id").asString()).isEqualTo(first.obligation().toString());
        assertThat(get("/api/v2/me/billing/obligations/" + other.obligation(), first.linked().token()).status()).isEqualTo(404);
    }

    // Un rôle financier n'ouvre jamais l'accès académique

    @Test
    void unRoleFinancierNOuvreJamaisLeDossier() throws Exception {
        Billed billed = billedWith(ADULT, false, DEBTOR);

        Response record = get("/api/v2/me/students/" + billed.student(), billed.linked().token());

        assertThat(record.status()).isEqualTo(200);
        assertThat(record.body().path("academicAccess").asBoolean()).isFalse();
        assertThat(record.body().path("studentNumber").isNull()).isTrue();
        assertThat(record.body().path("enrollments").size()).isZero();
    }

    @Test
    void unMajeurSansConsentementNeVoitSonNiveauRevelePasParLeLibelle() throws Exception {
        for (String role : List.of(DEBTOR, RESPONSIBLE)) {
            JsonNode view = mine(billedWith(ADULT, false, role));

            assertThat(view.path("label").asString()).as(role).isEqualTo("Frais scolaires");
            assertThat(view.toString()).as(role).doesNotContain("Terminale", "SCOLARITE");
        }
    }

    @Test
    void unMineurSansAccesAcademiqueAUnLibelleNeutre() throws Exception {
        assertThat(mine(billedWith(MINOR, false, RESPONSIBLE)).path("label").asString()).isEqualTo("Frais scolaires");
    }

    @Test
    void lAccesAcademiqueDonneLeVraiLibelle() throws Exception {
        assertThat(mine(billedWith(MINOR, true, RESPONSIBLE)).path("label").asString()).isEqualTo("Frais de Terminale D");
    }

    /** La majorité prend effet le jour même, sans traitement différé : le libellé suit à la lecture suivante. */
    @Test
    void lePassageALaMajoriteRendLeLibelleNeutreLeJourMeme() throws Exception {
        Billed billed = billedWith(MINOR, true, DEBTOR);
        assertThat(mine(billed).path("label").asString()).isEqualTo("Frais de Terminale D");

        jdbc.sql("UPDATE student_records SET birth_date = :birthDate WHERE id = :id").param("birthDate", ADULT).param("id", billed.student()).update();

        JsonNode view = mine(billed);
        assertThat(view.path("label").asString()).isEqualTo("Frais scolaires");
        assertThat(view.path("view").asString()).as("La responsabilité financière demeure").isEqualTo("DEBTOR");
    }

    @Test
    void leConsentementDuMajeurRendLeVraiLibelle() throws Exception {
        Billed billed = billedWith(ADULT, false, DEBTOR);
        Tenant tenant = billed.school().tenant();
        String contact = uniqueEmail();
        Response invited = post(tenant.path("/students/" + billed.student() + "/self-access-invitations"), tenant.adminToken(), Map.of("contact", contact));
        assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
        acceptAsNewAccount(invited.text("token"), "Étudiant Majeur");
        String self = login(contact, PASSWORD);

        Response consent = patch("/api/v2/me/students/" + billed.student() + "/third-parties/" + billed.linked().grant(), self, Map.of("academicAccess", true));
        assertThat(consent.status()).as(consent.raw()).isEqualTo(204);

        assertThat(mine(billed).path("label").asString()).isEqualTo("Frais de Terminale D");
    }

    @Test
    void unRedevableDontLeLienEstRevoqueGardeSaVueAvecUnLibelleNeutre() throws Exception {
        Billed billed = billedWith(MINOR, true, DEBTOR);
        Tenant tenant = billed.school().tenant();

        assertThat(delete(tenant.path("/students/" + billed.student() + "/access-grants/" + billed.linked().grant()), tenant.adminToken()).status()).isEqualTo(204);

        JsonNode view = mine(billed);
        assertThat(view.path("view").asString()).as("Son engagement ne dépend pas du lien").isEqualTo("DEBTOR");
        assertThat(view.path("label").asString()).isEqualTo("Frais scolaires");
    }

    // Lecture seule

    @Test
    void laSurfaceDesFamillesNeFaitQueLire() {
        List<String> routes = mappings.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPatternValues().stream().anyMatch(pattern -> pattern.startsWith("/api/v2/me/billing")))
                .map(RequestMappingInfo::toString)
                .toList();
        assertThat(routes).isNotEmpty();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (info.getPatternValues().stream().anyMatch(pattern -> pattern.startsWith("/api/v2/me/billing"))) {
                assertThat(info.getMethodsCondition().getMethods()).containsExactly(RequestMethod.GET);
            }
        }
    }
}
