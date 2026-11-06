package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import tools.jackson.databind.JsonNode;

/** Rien n'est jamais modifié automatiquement : un écart ou un retrait se signale, et l'établissement décide. */
class ObligationReviewTest extends BillingScenarioTest {

    private Response reviews(Tenant tenant, UUID year) {
        Response response = get(tenant.path("/billing/reviews?academicYearId=" + year), tenant.adminToken());
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        return response;
    }

    private JsonNode onlySignal(School school) {
        Response reviews = reviews(school.tenant(), school.year());
        assertThat(reviews.body().path("total").asLong()).as(reviews.raw()).isEqualTo(1);
        return reviews.body().path("signals").get(0);
    }

    private void assertUntouched(School school, UUID obligation, long journal) {
        JsonNode view = obligationView(school.tenant(), obligation);
        assertThat(view.path("issuedAmountMinor").asString()).as("Le montant initial est conservé").isEqualTo(String.valueOf(AMOUNT));
        assertThat(view.path("dueMinor").asString()).isEqualTo(String.valueOf(AMOUNT));
        assertThat(view.path("status").asString()).isEqualTo("OPEN");
        assertThat(journalSize(obligation)).as("Rien n'est écrit automatiquement").isEqualTo(journal);
    }

    /** Un nouveau niveau tarifé autrement, et la réinscription de l'élève sur ce niveau. */
    private UUID reenrollOnLevelPricedAt(School school, UUID student, long amount) {
        Tenant tenant = school.tenant();
        UUID level = newLevel(tenant);
        UUID next = newDraft(tenant, school.year());
        addFee(tenant, next, fee(school.charge(), "SCOLARITE", level, amount));
        publish(tenant, next);
        endEnrollment(tenant, student, "WITHDRAWN");
        assertThat(enroll(tenant, student, school.year(), level, next).status()).isEqualTo(201);
        return next;
    }

    private Response decide(School school, JsonNode signal, String decision, UUID entry, String reason) {
        return decide(school, school.tenant().adminToken(), signal, decision, entry, reason);
    }

    private Response decide(School school, String token, JsonNode signal, String decision, UUID entry, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("obligationId", signal.path("obligation").path("id").asString());
        body.put("signal", signal.path("signal").asString());
        body.put("fingerprint", signal.path("fingerprint").asString());
        body.put("decision", decision);
        body.put("entryId", entry == null ? null : entry.toString());
        body.put("reason", reason);
        return post(school.tenant().path("/billing/reviews/decisions"), token, body);
    }

    @Test
    void sansChangementRienNestASignaler() throws Exception {
        School school = school();
        enrolledAndBilled(school);

        assertThat(reviews(school.tenant(), school.year()).body().path("total").asLong()).isZero();
    }

    // Écarts de tarif

    @Test
    void unNiveauPlusCherEstSignaleSansFacturerNiModifierLObligation() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);

        reenrollOnLevelPricedAt(school, student, 130_000);

        JsonNode signal = onlySignal(school);
        assertThat(signal.path("signal").asString()).isEqualTo("TARIFF_DIFFERENCE");
        assertThat(signal.path("cause").asString()).isEqualTo("LEVEL_CHANGED");
        assertThat(signal.path("applicableAmountMinor").asString()).isEqualTo("130000");
        assertThat(signal.path("differenceMinor").asString()).isEqualTo("30000");
        assertThat(signal.path("obligation").path("id").asString()).isEqualTo(obligation.toString());
        assertThat(signal.path("currentEnrollmentId").asString()).isEqualTo(activeEnrollmentOf(school.tenant(), student).toString());
        assertThat(obligationsOf(student)).isEqualTo(1);
        assertUntouched(school, obligation, 1);
    }

    /** L'écart n'est pas présenté comme un remboursement dû : c'est un fait, à l'établissement d'en décider. */
    @Test
    void unNiveauMoinsCherEstSignaleSansSupposerUnRemboursement() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);

        reenrollOnLevelPricedAt(school, student, 70_000);

        JsonNode signal = onlySignal(school);
        assertThat(signal.path("differenceMinor").asString()).isEqualTo("-30000");
        assertThat(signal.toString()).doesNotContain("refund", "REFUND", "supplement", "SUPPLEMENT");
        assertUntouched(school, obligationOf(student), 1);
    }

    @Test
    void unNouveauTarifSurLeMemeNiveauEstSignaleCommeUnChangementDeCatalogue() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID next = newDraft(tenant, school.year());
        String item = get(tenant.path("/billing/fee-catalogs/" + next), tenant.adminToken()).body().path("items").get(0).path("id").asString();
        assertThat(put(tenant.path("/billing/fee-catalogs/" + next + "/items/" + item), tenant.adminToken(),
                fee(school.charge(), "SCOLARITE", null, 110_000)).status()).isEqualTo(200);
        publish(tenant, next);

        JsonNode signal = onlySignal(school);

        assertThat(signal.path("cause").asString()).isEqualTo("CATALOG_CHANGED");
        assertThat(signal.path("differenceMinor").asString()).isEqualTo("10000");
        assertUntouched(school, obligationOf(student), 1);
    }

    @Test
    void uneChargeQuiNeSAppliquePlusEstSignaleeSansMontant() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID next = newDraft(tenant, school.year());
        JsonNode items = get(tenant.path("/billing/fee-catalogs/" + next), tenant.adminToken()).body().path("items");
        assertThat(delete(tenant.path("/billing/fee-catalogs/" + next + "/items/" + items.get(0).path("id").asString()), tenant.adminToken()).status()).isEqualTo(204);
        addFee(tenant, next, fee(newCharge(tenant, school.year(), school.annual(), "Autre"), "AUTRE", null, 1_000));
        publish(tenant, next);

        JsonNode signal = onlySignal(school);

        assertThat(signal.path("signal").asString()).isEqualTo("TARIFF_DIFFERENCE");
        assertThat(signal.path("applicableAmountMinor").isNull()).isTrue();
        assertThat(signal.path("differenceMinor").isNull()).isTrue();
        assertUntouched(school, obligationOf(student), 1);
    }

    @Test
    void unMemeMontantNeProduitAucunSignal() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);

        reenrollOnLevelPricedAt(school, student, AMOUNT);

        assertThat(reviews(school.tenant(), school.year()).body().path("total").asLong()).isZero();
    }

    // Retrait

    @Test
    void unRetraitEstSignaleSansSupprimerNiAnnuler() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        UUID enrollment = activeEnrollmentOf(school.tenant(), student);

        endEnrollment(school.tenant(), student, "WITHDRAWN");

        JsonNode signal = onlySignal(school);
        assertThat(signal.path("signal").asString()).isEqualTo("WITHDRAWAL");
        assertThat(signal.path("fingerprint").asString()).isEqualTo(enrollment.toString());
        assertUntouched(school, obligation, 1);
    }

    @Test
    void uneScolariteMeneeASonTermeNeDemandeAucunReexamen() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);

        endEnrollment(school.tenant(), student, "COMPLETED");

        assertThat(reviews(school.tenant(), school.year()).body().path("total").asLong()).isZero();
    }

    @Test
    void uneReinscriptionFaitDisparaitreLeSignalDeRetrait() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);
        endEnrollment(school.tenant(), student, "WITHDRAWN");
        assertThat(onlySignal(school).path("signal").asString()).isEqualTo("WITHDRAWAL");

        assertThat(enroll(school.tenant(), student, school.year(), school.level(), school.catalog()).status()).isEqualTo(201);

        assertThat(reviews(school.tenant(), school.year()).body().path("total").asLong()).isZero();
        assertThat(obligationsOf(student)).isEqualTo(1);
    }

    @Test
    void uneObligationDontPlusRienNestDuNestPasAReexaminer() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(cancellation(tenant, tenant.adminToken(), obligation)));

        endEnrollment(tenant, student, "WITHDRAWN");

        assertThat(reviews(tenant, school.year()).body().path("total").asLong()).isZero();
    }

    // Décision explicite

    @Test
    void decideDeNeRienChangerEstMotiveEtFermeLeSignal() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        reenrollOnLevelPricedAt(school, student, 130_000);
        JsonNode signal = onlySignal(school);

        Response decided = decide(school, signal, "NO_CHANGE", null, "Tarif d'origine maintenu pour l'année");

        assertThat(decided.status()).as(decided.raw()).isEqualTo(204);
        assertThat(reviews(school.tenant(), school.year()).body().path("total").asLong()).isZero();
        assertUntouched(school, obligation, 1);
        assertThat(jdbc.sql("SELECT reason FROM obligation_review_decisions WHERE obligation_id = :id").param("id", obligation).query(String.class).single())
                .isEqualTo("Tarif d'origine maintenu pour l'année");
    }

    @Test
    void uneDecisionExigeUnMotif() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);
        endEnrollment(school.tenant(), student, "WITHDRAWN");

        assertThat(decide(school, onlySignal(school), "NO_CHANGE", null, " ").status()).isEqualTo(400);
        assertThat(onlySignal(school).path("signal").asString()).isEqualTo("WITHDRAWAL");
    }

    @Test
    void decideDAjusterRenvoieAUneEcritureReelleDeCetteObligation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        UUID otherObligation = issuedObligation(school);
        reenrollOnLevelPricedAt(school, student, 130_000);
        JsonNode signal = reviews(tenant, school.year()).body().path("signals").get(0);
        UUID issued = UUID.fromString(entries(tenant, obligation).get(0).path("id").asString());
        UUID foreign = UUID.fromString(discount(tenant, tenant.adminToken(), otherObligation, 1_000).text("entryId"));

        assertThat(decide(school, signal, "ADJUSTED", null, "Sans écriture").code()).isEqualTo("ENTRY_REQUIRED_IF_ADJUSTED");
        assertThat(decide(school, signal, "NO_CHANGE", issued, "Écriture superflue").code()).isEqualTo("ENTRY_REQUIRED_IF_ADJUSTED");
        assertThat(decide(school, signal, "ADJUSTED", issued, "L'émission n'est pas un ajustement").code()).isEqualTo("ENTRY_NOT_AN_ADJUSTMENT");
        assertThat(decide(school, signal, "ADJUSTED", foreign, "Autre obligation").code()).isEqualTo("ENTRY_NOT_AN_ADJUSTMENT");

        Response correction = correction(tenant, tenant.adminToken(), obligation, 30_000);
        assertThat(correction.status()).as(correction.raw()).isEqualTo(201);

        Response decided = decide(school, signal, "ADJUSTED", UUID.fromString(correction.text("entryId")), "Supplément décidé par la direction");

        assertThat(decided.status()).as(decided.raw()).isEqualTo(204);
        assertThat(obligationView(tenant, obligation).path("dueMinor").asString()).isEqualTo("130000");
        assertThat(obligationView(tenant, obligation).path("issuedAmountMinor").asString()).isEqualTo(String.valueOf(AMOUNT));
    }

    /** Une décision porte sur une situation : si les faits changent, le signal doit revenir. */
    @Test
    void uneSituationNouvelleRedevientVisibleApresUneDecision() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID next = reenrollOnLevelPricedAt(school, student, 130_000);
        assertThat(decide(school, onlySignal(school), "NO_CHANGE", null, "Maintenu").status()).isEqualTo(204);
        UUID level = UUID.fromString(get(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken()).body().get(0).path("academicUnitId").asString());

        UUID same = newDraft(tenant, school.year());
        publish(tenant, same);
        assertThat(reviews(tenant, school.year()).body().path("total").asLong()).as("Même montant : déjà décidé").isZero();

        UUID raised = newDraft(tenant, school.year());
        for (JsonNode item : get(tenant.path("/billing/fee-catalogs/" + raised), tenant.adminToken()).body().path("items")) {
            if (item.path("academicUnitId").asString("").equals(level.toString())) {
                assertThat(put(tenant.path("/billing/fee-catalogs/" + raised + "/items/" + item.path("id").asString()), tenant.adminToken(),
                        fee(school.charge(), "SCOLARITE", level, 150_000)).status()).isEqualTo(200);
            }
        }
        publish(tenant, raised);

        JsonNode again = onlySignal(school);
        assertThat(again.path("applicableAmountMinor").asString()).isEqualTo("150000");
        assertThat(next).isNotNull();
    }

    @Test
    void uneDecisionSurUneSituationPerimeeOuDejaTrancheeEstRefusee() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);
        endEnrollment(school.tenant(), student, "WITHDRAWN");
        JsonNode signal = onlySignal(school);

        assertThat(decide(school, signal, "NO_CHANGE", null, "Maintenu").status()).isEqualTo(204);

        Response twice = decide(school, signal, "NO_CHANGE", null, "Encore");
        assertThat(twice.status()).as(twice.raw()).isEqualTo(409);
        assertThat(twice.code()).isEqualTo("REVIEW_OUTDATED");
    }

    @Test
    void uneDecisionDevientPerimeeSiLaSituationAChange() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);
        endEnrollment(school.tenant(), student, "WITHDRAWN");
        JsonNode signal = onlySignal(school);
        assertThat(enroll(school.tenant(), student, school.year(), school.level(), school.catalog()).status()).isEqualTo(201);

        Response stale = decide(school, signal, "NO_CHANGE", null, "Trop tard");

        assertThat(stale.status()).as(stale.raw()).isEqualTo(409);
        assertThat(stale.code()).isEqualTo("REVIEW_OUTDATED");
    }

    @Test
    void desDecisionsSimultaneesNEnregistrentQuUneDecision() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);
        endEnrollment(school.tenant(), student, "WITHDRAWN");
        JsonNode signal = onlySignal(school);

        List<Response> responses = concurrently(8, () -> decide(school, signal, "NO_CHANGE", null, "Maintenu"));

        assertThat(responses.stream().filter(response -> response.status() == 204).count()).as(responses.toString()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.status() == 409).count()).as(responses.toString()).isEqualTo(7);
    }

    @Test
    void laListeSePagineSansFausserLeTotal() throws Exception {
        School school = school();
        for (int i = 0; i < 3; i++) {
            endEnrollment(school.tenant(), enrolledAndBilled(school), "WITHDRAWN");
        }

        Response page = get(school.tenant().path("/billing/reviews?academicYearId=" + school.year() + "&limit=2&offset=0"), school.tenant().adminToken());

        assertThat(page.body().path("total").asLong()).isEqualTo(3);
        assertThat(page.body().path("signals").size()).isEqualTo(2);
    }

    // Permissions, isolation, audit

    @Test
    void lesPermissionsSeparentLaLectureDeLaDecision() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        endEnrollment(tenant, student, "WITHDRAWN");
        JsonNode signal = onlySignal(school);
        String path = tenant.path("/billing/reviews?academicYearId=" + school.year());

        String registrar = newStaff(tenant, "REGISTRAR");
        assertThat(get(path, registrar).status()).isEqualTo(403);
        assertThat(decide(school, registrar, signal, "NO_CHANGE", null, "Interdit").status()).isEqualTo(403);

        String cashier = newStaff(tenant, "CASHIER");
        assertThat(get(path, cashier).status()).isEqualTo(200);
        assertThat(decide(school, cashier, signal, "NO_CHANGE", null, "Interdit").status()).isEqualTo(403);

        assertThat(decide(school, newStaff(tenant, "ACCOUNTANT"), signal, "NO_CHANGE", null, "Autorisé").status()).isEqualTo(204);
    }

    @Test
    void unEtablissementNeVoitNiNeTrancheLesReexamensDUnAutre() throws Exception {
        School a = school();
        School b = school();
        UUID student = enrolledAndBilled(a);
        endEnrollment(a.tenant(), student, "WITHDRAWN");
        JsonNode signal = onlySignal(a);
        Tenant other = b.tenant();

        assertThat(get(other.path("/billing/reviews?academicYearId=" + a.year()), other.adminToken()).status()).isEqualTo(404);
        assertThat(get(a.tenant().path("/billing/reviews?academicYearId=" + a.year()), other.adminToken()).status()).isEqualTo(404);
        assertThat(decide(b, signal, "NO_CHANGE", null, "Volée").code()).isEqualTo("OBLIGATION_NOT_FOUND");
        assertThat(onlySignal(a).path("signal").asString()).isEqualTo("WITHDRAWAL");
    }

    @Test
    void uneDecisionEstAuditeeEtImmuable() throws Exception {
        School school = school();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        endEnrollment(school.tenant(), student, "WITHDRAWN");
        assertThat(decide(school, onlySignal(school), "NO_CHANGE", null, "Maintenu").status()).isEqualTo(204);

        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE action = 'billing.review.decided' AND target_id = :id")
                        .param("id", obligation).query(Long.class).single())
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("UPDATE obligation_review_decisions SET reason = 'Réécrit' WHERE obligation_id = :id").param("id", obligation).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM obligation_review_decisions WHERE obligation_id = :id").param("id", obligation).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
    }
}
