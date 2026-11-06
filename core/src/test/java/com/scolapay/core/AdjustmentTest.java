package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

class AdjustmentTest extends BillingScenarioTest {

    private static String entryId(Response applied) {
        assertThat(applied.status()).as(applied.raw()).isEqualTo(201);
        assertThat(applied.text("outcome")).isEqualTo("APPLIED");
        return applied.text("entryId");
    }

    private void assertDue(Tenant tenant, UUID obligation, long due, String status) {
        JsonNode view = obligationView(tenant, obligation);
        assertThat(view.path("dueMinor").asString()).isEqualTo(String.valueOf(due));
        assertThat(view.path("status").asString()).isEqualTo(status);
        assertThat(view.path("issuedAmountMinor").asString()).as("Le montant émis ne change jamais").isEqualTo(String.valueOf(AMOUNT));
    }

    // Opérations courantes : directes

    @Test
    void unePetiteRemiseSAppliqueDirectementAvecSonMotifDansLeJournal() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String accountant = newStaff(tenant, "ACCOUNTANT");

        entryId(discount(tenant, accountant, obligation, 10_000));

        assertDue(tenant, obligation, 90_000, "OPEN");
        JsonNode entry = entries(tenant, obligation).get(1);
        assertThat(entry.path("kind").asString()).isEqualTo("DISCOUNT");
        assertThat(entry.path("amountMinor").asString()).isEqualTo("-10000");
        assertThat(entry.path("reason").asString()).isEqualTo("Remise accordée");
        assertThat(entry.path("recordedBy").asString()).isEqualTo(get("/api/v2/me", accountant).text("id"));
        assertThat(entry.path("approvedBy").isNull()).as("Aucune seconde personne pour une petite remise").isTrue();
    }

    @Test
    void uneOperationExigeUnMotifUnMontantValideEtUneCle() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String path = tenant.path("/billing/obligations/" + obligation + "/discounts");

        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("amountMinor", "1000", "idempotencyKey", key()),
                Map.of("amountMinor", "1000", "reason", "  ", "idempotencyKey", key()),
                Map.of("amountMinor", "1000", "reason", "Motif"),
                Map.of("amountMinor", "0", "reason", "Motif", "idempotencyKey", key()),
                Map.of("amountMinor", "-5", "reason", "Motif", "idempotencyKey", key()),
                Map.of("amountMinor", "12.5", "reason", "Motif", "idempotencyKey", key()))) {
            assertThat(post(path, tenant.adminToken(), body).status()).as(body.toString()).isEqualTo(400);
        }
        assertThat(journalSize(obligation)).isEqualTo(1);
    }

    @Test
    void uneRemiseNePeutPasDepasserLeDu() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        Response refused = discount(tenant, tenant.adminToken(), obligation, AMOUNT + 1);

        assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("AMOUNT_EXCEEDS_DUE");
    }

    @Test
    void uneCorrectionPeutAugmenterOuReduireLeDu() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        entryId(correction(tenant, tenant.adminToken(), obligation, 5_000));
        assertDue(tenant, obligation, 105_000, "OPEN");

        entryId(correction(tenant, tenant.adminToken(), obligation, -8_000));
        assertDue(tenant, obligation, 97_000, "OPEN");
        assertThat(entries(tenant, obligation).get(1).path("kind").asString()).isEqualTo("CORRECTION");
    }

    // Protection renforcée : proportionnée

    @Test
    void uneRemiseTotaleAttendUneSecondePersonneEtNeChangeRienDIciLa() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String accountant = newStaff(tenant, "ACCOUNTANT");

        Response pending = discount(tenant, accountant, obligation, AMOUNT);
        String request = requestIdOf(pending);
        assertThat(pending.text("outcome")).isEqualTo("PENDING_APPROVAL");

        assertDue(tenant, obligation, AMOUNT, "OPEN");
        assertThat(journalSize(obligation)).as("Rien au journal tant que ce n'est pas confirmé").isEqualTo(1);
        assertThat(get(tenant.path("/billing/adjustment-requests"), tenant.adminToken()).body().get(0).path("id").asString()).isEqualTo(request);

        Response self = approve(tenant, accountant, request);
        assertThat(self.status()).as(self.raw()).isEqualTo(403);
        assertThat(self.code()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
        assertDue(tenant, obligation, AMOUNT, "OPEN");

        Response approved = approve(tenant, tenant.adminToken(), request);
        assertThat(approved.status()).as(approved.raw()).isEqualTo(200);
        assertThat(approved.text("kind")).isEqualTo("DISCOUNT");
        assertThat(approved.text("approvedBy")).isEqualTo(get("/api/v2/me", tenant.adminToken()).text("id"));
        assertThat(approved.text("recordedBy")).isEqualTo(get("/api/v2/me", accountant).text("id"));
        assertDue(tenant, obligation, 0, "WAIVED");
    }

    /** Sinon, il suffirait de découper une grosse remise pour échapper au contrôle. */
    @Test
    void leSeuilPorteSurLeCumulEtNeSeContournePasEnDecoupant() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        entryId(discount(tenant, tenant.adminToken(), obligation, 30_000));
        entryId(discount(tenant, tenant.adminToken(), obligation, 20_000));
        assertDue(tenant, obligation, 50_000, "OPEN");

        requestIdOf(discount(tenant, tenant.adminToken(), obligation, 1));
        assertDue(tenant, obligation, 50_000, "OPEN");
    }

    @Test
    void uneGrosseCorrectionDansUnSensOuLAutreEstSensible() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();

        requestIdOf(correction(tenant, tenant.adminToken(), issuedObligation(school), 50_001));
        requestIdOf(correction(tenant, tenant.adminToken(), issuedObligation(school), -50_001));
        entryId(correction(tenant, tenant.adminToken(), issuedObligation(school), 50_000));
    }

    @Test
    void uneAnneeClotureeRendSensibleMemeUnePetiteOperation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        assertThat(patch(tenant.path("/academic-years/" + school.year()), tenant.adminToken(), Map.of("status", "CLOSED")).status()).isEqualTo(200);

        requestIdOf(discount(tenant, tenant.adminToken(), obligation, 1));
    }

    @Test
    void uneAnnulationAttendToujoursUneSecondePersonneEtPorteSurLeResteDu() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String accountant = newStaff(tenant, "ACCOUNTANT");

        String request = requestIdOf(cancellation(tenant, tenant.adminToken(), obligation));
        assertDue(tenant, obligation, AMOUNT, "OPEN");

        entryId(discount(tenant, tenant.adminToken(), obligation, 10_000));

        Response approved = approve(tenant, accountant, request);
        assertThat(approved.status()).as(approved.raw()).isEqualTo(200);
        assertThat(approved.text("kind")).isEqualTo("CANCELLATION");
        assertThat(approved.text("amountMinor")).as("Ce qui reste dû au moment de la confirmation").isEqualTo("-90000");
        assertDue(tenant, obligation, 0, "CANCELLED");

        Response nothingLeft = cancellation(tenant, tenant.adminToken(), obligation);
        assertThat(nothingLeft.status()).as(nothingLeft.raw()).isEqualTo(409);
        assertThat(nothingLeft.code()).isEqualTo("NOTHING_DUE");
    }

    /** Le même solde nul, trois sens différents : la remise, la correction et l'annulation ne se confondent pas. */
    @Test
    void troisOperationsAuMemeSoldeGardentDesStatutsDistincts() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        String accountant = newStaff(tenant, "ACCOUNTANT");
        UUID discounted = issuedObligation(school);
        UUID corrected = issuedObligation(school);
        UUID cancelled = issuedObligation(school);

        approve(tenant, accountant, requestIdOf(discount(tenant, tenant.adminToken(), discounted, AMOUNT)));
        approve(tenant, accountant, requestIdOf(correction(tenant, tenant.adminToken(), corrected, -AMOUNT)));
        approve(tenant, accountant, requestIdOf(cancellation(tenant, tenant.adminToken(), cancelled)));

        assertDue(tenant, discounted, 0, "WAIVED");
        assertDue(tenant, corrected, 0, "CORRECTED");
        assertDue(tenant, cancelled, 0, "CANCELLED");
    }

    @Test
    void refuserUneDemandeNeTouchePasAuJournalEtExigeUnMotif() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String accountant = newStaff(tenant, "ACCOUNTANT");
        String request = requestIdOf(discount(tenant, accountant, obligation, AMOUNT));
        String path = tenant.path("/billing/adjustment-requests/" + request + "/rejection");

        assertThat(post(path, tenant.adminToken(), Map.of()).status()).isEqualTo(400);
        assertThat(post(path, tenant.adminToken(), Map.of("note", "Dossier incomplet")).status()).isEqualTo(204);

        assertDue(tenant, obligation, AMOUNT, "OPEN");
        assertThat(journalSize(obligation)).isEqualTo(1);
        JsonNode rejected = get(tenant.path("/billing/adjustment-requests?status=REJECTED"), tenant.adminToken()).body().get(0);
        assertThat(rejected.path("decisionNote").asString()).isEqualTo("Dossier incomplet");

        Response late = approve(tenant, tenant.adminToken(), request);
        assertThat(late.status()).as(late.raw()).isEqualTo(409);
        assertThat(late.code()).isEqualTo("REQUEST_ALREADY_DECIDED");
    }

    @Test
    void uneSeuleDemandeAttendParObligation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        requestIdOf(cancellation(tenant, tenant.adminToken(), obligation));

        Response second = discount(tenant, tenant.adminToken(), obligation, AMOUNT);

        assertThat(second.status()).as(second.raw()).isEqualTo(409);
        assertThat(second.code()).isEqualTo("PENDING_REQUEST_EXISTS");
    }

    @Test
    void uneDemandeDevenueImpossibleEstRefuseeSansEcrireDansLeJournal() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String accountant = newStaff(tenant, "ACCOUNTANT");
        String request = requestIdOf(discount(tenant, accountant, obligation, 90_000));
        entryId(discount(tenant, tenant.adminToken(), obligation, 20_000));

        Response outdated = approve(tenant, tenant.adminToken(), request);

        assertThat(outdated.status()).as(outdated.raw()).isEqualTo(409);
        assertThat(outdated.code()).isEqualTo("REQUEST_OUTDATED");
        assertDue(tenant, obligation, 80_000, "OPEN");
    }

    // Corriger sans jamais modifier l'historique

    @Test
    void contrepasserGardeLesDeuxEcrituresEtRetablitLeDu() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String entry = entryId(discount(tenant, tenant.adminToken(), obligation, 10_000));

        String reversal = entryId(reversal(tenant, tenant.adminToken(), obligation, entry));

        assertDue(tenant, obligation, AMOUNT, "OPEN");
        JsonNode journal = entries(tenant, obligation);
        assertThat(journal.size()).as("L'erreur et sa correction restent visibles").isEqualTo(3);
        JsonNode last = journal.get(2);
        assertThat(last.path("id").asString()).isEqualTo(reversal);
        assertThat(last.path("kind").asString()).isEqualTo("REVERSAL");
        assertThat(last.path("amountMinor").asString()).isEqualTo("10000");
        assertThat(last.path("reversesEntryId").asString()).isEqualTo(entry);
    }

    @Test
    void uneEcritureNeSeContrepasseQuUneFoisEtNiLEmissionNiUneContrepasseeNeSeContrepassent() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String issued = entries(tenant, obligation).get(0).path("id").asString();
        String entry = entryId(discount(tenant, tenant.adminToken(), obligation, 10_000));
        String reversal = entryId(reversal(tenant, tenant.adminToken(), obligation, entry));

        assertThat(reversal(tenant, tenant.adminToken(), obligation, entry).code()).isEqualTo("ENTRY_ALREADY_REVERSED");
        assertThat(reversal(tenant, tenant.adminToken(), obligation, issued).code()).isEqualTo("ENTRY_NOT_REVERSIBLE");
        assertThat(reversal(tenant, tenant.adminToken(), obligation, reversal).code()).isEqualTo("ENTRY_NOT_REVERSIBLE");
        assertThat(reversal(tenant, tenant.adminToken(), obligation, UUID.randomUUID().toString()).code()).isEqualTo("ENTRY_NOT_FOUND");

        UUID other = issuedObligation(school);
        assertThat(reversal(tenant, tenant.adminToken(), other, entry).code()).as("Une écriture d'une autre obligation").isEqualTo("ENTRY_NOT_FOUND");
    }

    /** Défaire une opération protégée reste protégé, et le statut retrouve le sens du journal. */
    @Test
    void contrepasserUneAnnulationExigeUneSecondePersonneEtRouvreLObligation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String accountant = newStaff(tenant, "ACCOUNTANT");
        String cancel = approve(tenant, accountant, requestIdOf(cancellation(tenant, tenant.adminToken(), obligation))).text("id");
        assertDue(tenant, obligation, 0, "CANCELLED");

        String request = requestIdOf(reversal(tenant, accountant, obligation, cancel));
        assertDue(tenant, obligation, 0, "CANCELLED");

        Response approved = approve(tenant, tenant.adminToken(), request);
        assertThat(approved.status()).as(approved.raw()).isEqualTo(200);
        assertDue(tenant, obligation, AMOUNT, "OPEN");
    }

    @Test
    void uneRemiseTotaleContrepasseeNeLaissePasLeStatutWaived() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String accountant = newStaff(tenant, "ACCOUNTANT");
        String total = approve(tenant, accountant, requestIdOf(discount(tenant, tenant.adminToken(), obligation, AMOUNT))).text("id");
        approve(tenant, accountant, requestIdOf(reversal(tenant, tenant.adminToken(), obligation, total)));
        assertDue(tenant, obligation, AMOUNT, "OPEN");

        approve(tenant, accountant, requestIdOf(correction(tenant, tenant.adminToken(), obligation, -AMOUNT)));

        assertDue(tenant, obligation, 0, "CORRECTED");
    }

    /** Une annulation défaite ne compte plus : le statut suit l'opération qui a réellement soldé le dû. */
    @Test
    void uneAnnulationContrepasseeNeLaissePasLeStatutCancelled() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        String accountant = newStaff(tenant, "ACCOUNTANT");
        UUID obligation = issuedObligation(school);
        String cancelled = approve(tenant, accountant, requestIdOf(cancellation(tenant, tenant.adminToken(), obligation))).text("id");
        approve(tenant, accountant, requestIdOf(reversal(tenant, tenant.adminToken(), obligation, cancelled)));

        approve(tenant, accountant, requestIdOf(discount(tenant, tenant.adminToken(), obligation, AMOUNT)));

        assertDue(tenant, obligation, 0, "WAIVED");
    }

    // Aucune de ces opérations n'est un encaissement, ni ne permet de refacturer

    @Test
    void aucunStatutNiEcritureNeSimuleUnReglement() throws Exception {
        assertThat(Arrays.stream(com.scolapay.core.billing.internal.ObligationService.ObligationStatus.values()).map(Enum::name))
                .doesNotContain("SETTLED", "PAID");
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO obligation_entries (id, establishment_id, obligation_id, kind, amount_minor, reason, idempotency_key, recorded_at, recorded_by)
                VALUES (gen_random_uuid(), :establishment, :obligation, 'SETTLEMENT', -1, 'Paiement fictif', :key, now(), :actor)
                """)
                .param("establishment", tenant.id())
                .param("obligation", obligation)
                .param("key", key())
                .param("actor", get("/api/v2/me", tenant.adminToken()).id("id"))
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Une charge annulée reste émise : la réinscription ne la refacture pas, seule une contrepassée la rétablit. */
    @Test
    void uneChargeAnnuleeNEstPasRefactureeParUneReinscriptionNiParUnLot() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(cancellation(tenant, tenant.adminToken(), obligation)));

        endEnrollment(tenant, student, "WITHDRAWN");
        assertThat(enroll(tenant, student, school.year(), school.level(), school.catalog()).status()).isEqualTo(201);

        Response preview = get(tenant.path("/billing/issuance-preview?academicYearId=" + school.year()), tenant.adminToken());
        assertThat(preview.body().path("obligationsToCreate").asLong()).isZero();
        assertThat(obligationsOf(student)).isEqualTo(1);
        assertDue(tenant, obligation, 0, "CANCELLED");
    }

    // Idempotence et concurrence

    @Test
    void rejouerLaMemeCleNeCreeRienDePlus() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        Map<String, Object> body = Map.of("amountMinor", "10000", "reason", "Remise", "idempotencyKey", key());
        String path = tenant.path("/billing/obligations/" + obligation + "/discounts");

        Response first = post(path, tenant.adminToken(), body);
        Response again = post(path, tenant.adminToken(), body);

        assertThat(first.status()).isEqualTo(201);
        assertThat(again.status()).as("Le rejeu renvoie le résultat existant").isEqualTo(200);
        assertThat(again.text("entryId")).isEqualTo(first.text("entryId"));
        assertDue(tenant, obligation, 90_000, "OPEN");

        Response elsewhere = post(tenant.path("/billing/obligations/" + issuedObligation(school) + "/discounts"), tenant.adminToken(), body);
        assertThat(elsewhere.status()).as(elsewhere.raw()).isEqualTo(409);
        assertThat(elsewhere.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void rejouerUneDemandeRenvoieSonEtatReel() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        Map<String, Object> body = Map.of("reason", "Dossier créé par erreur", "idempotencyKey", key());
        String path = tenant.path("/billing/obligations/" + obligation + "/cancellation");
        String request = requestIdOf(post(path, tenant.adminToken(), body));

        Response pending = post(path, tenant.adminToken(), body);
        assertThat(pending.status()).isEqualTo(200);
        assertThat(pending.text("outcome")).isEqualTo("PENDING_APPROVAL");
        assertThat(pending.body().path("request").path("id").asString()).isEqualTo(request);

        approve(tenant, newStaff(tenant, "ACCOUNTANT"), request);

        Response decided = post(path, tenant.adminToken(), body);
        assertThat(decided.status()).isEqualTo(200);
        assertThat(decided.text("outcome")).isEqualTo("APPROVED");
        assertDue(tenant, obligation, 0, "CANCELLED");
    }

    @Test
    void lesMemesOperationsSimultaneesNeSAppliquentQuUneFois() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        Map<String, Object> body = Map.of("amountMinor", "10000", "reason", "Remise", "idempotencyKey", key());
        String path = tenant.path("/billing/obligations/" + obligation + "/discounts");

        List<Response> responses = concurrently(8, () -> post(path, tenant.adminToken(), body));

        assertThat(responses.stream().filter(response -> response.status() == 201).count()).as(responses.toString()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.status() == 200).count()).as(responses.toString()).isEqualTo(7);
        assertDue(tenant, obligation, 90_000, "OPEN");
    }

    /** Le verrou fait que le cumul reste juste : huit remises en parallèle ne franchissent pas le seuil à l'insu du contrôle. */
    @Test
    void desRemisesSimultaneesRespectentLeSeuilCumule() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        List<Response> responses = concurrently(8, () -> discount(tenant, tenant.adminToken(), obligation, 10_000));

        assertThat(responses.stream().filter(response -> response.status() == 201).count()).as(responses.toString()).isEqualTo(5);
        assertThat(responses.stream().filter(response -> response.status() == 202).count()).as(responses.toString()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.status() == 409).count()).as(responses.toString()).isEqualTo(2);
        assertDue(tenant, obligation, 50_000, "OPEN");
    }

    @Test
    void uneDemandeNEstConfirmeeQuUneFoisMemeEnConcurrence() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String request = requestIdOf(discount(tenant, newStaff(tenant, "ACCOUNTANT"), obligation, AMOUNT));

        List<Response> responses = concurrently(8, () -> approve(tenant, tenant.adminToken(), request));

        assertThat(responses.stream().filter(response -> response.status() == 200).count()).as(responses.toString()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.status() == 409).count()).as(responses.toString()).isEqualTo(7);
        assertThat(journalSize(obligation)).isEqualTo(2);
        assertDue(tenant, obligation, 0, "WAIVED");
    }

    @Test
    void confirmerEtRefuserEnMemeTempsNeLaissentQuUneDecision() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String request = requestIdOf(discount(tenant, newStaff(tenant, "ACCOUNTANT"), obligation, AMOUNT));
        AtomicInteger turn = new AtomicInteger();

        List<Response> responses = concurrently(2, () -> turn.getAndIncrement() == 0
                ? approve(tenant, tenant.adminToken(), request)
                : post(tenant.path("/billing/adjustment-requests/" + request + "/rejection"), tenant.adminToken(), Map.of("note", "Refus motivé")));

        assertThat(responses.stream().filter(response -> response.status() == 409).count()).as(responses.toString()).isEqualTo(1);
        long journal = journalSize(obligation);
        String status = jdbc.sql("SELECT status FROM adjustment_requests WHERE id = :id").param("id", UUID.fromString(request)).query(String.class).single();
        assertThat(journal).isEqualTo(status.equals("APPROVED") ? 2 : 1);
    }

    // Permissions

    @Test
    void lesPermissionsSeparentLaLectureDeLOperationEtDeLaConfirmation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String accountant = newStaff(tenant, "ACCOUNTANT");
        String cashier = newStaff(tenant, "CASHIER");
        String auditor = newStaff(tenant, "AUDITOR");
        String registrar = newStaff(tenant, "REGISTRAR");

        String request = requestIdOf(discount(tenant, accountant, obligation, AMOUNT));

        for (String token : List.of(cashier, auditor, registrar)) {
            assertThat(discount(tenant, token, obligation, 1).status()).isEqualTo(403);
            assertThat(correction(tenant, token, obligation, 1).status()).isEqualTo(403);
            assertThat(cancellation(tenant, token, obligation).status()).isEqualTo(403);
            assertThat(approve(tenant, token, request).status()).isEqualTo(403);
            assertThat(post(tenant.path("/billing/adjustment-requests/" + request + "/rejection"), token, Map.of("note", "Refus motivé")).status()).isEqualTo(403);
        }
        assertThat(get(tenant.path("/billing/adjustment-requests"), cashier).status()).isEqualTo(200);
        assertThat(get(tenant.path("/billing/adjustment-requests"), registrar).status()).isEqualTo(403);
        assertThat(approve(tenant, tenant.adminToken(), request).status()).as("L'administrateur confirme le comptable").isEqualTo(200);

        UUID other = issuedObligation(school);
        assertThat(approve(tenant, accountant, requestIdOf(discount(tenant, tenant.adminToken(), other, AMOUNT))).status())
                .as("Le comptable confirme l'administrateur")
                .isEqualTo(200);
    }

    @Test
    void chaqueOperationEstAuditeeSansRecopierLeMotif() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        entryId(discount(tenant, tenant.adminToken(), obligation, 1_000));
        String request = requestIdOf(cancellation(tenant, tenant.adminToken(), obligation));
        approve(tenant, newStaff(tenant, "ACCOUNTANT"), request);

        List<String> actions = jdbc.sql("""
                SELECT action FROM audit_events
                WHERE establishment_id = :id AND action LIKE 'billing.adjustment.%'
                """)
                .param("id", tenant.id())
                .query(String.class)
                .list();
        List<String> details = jdbc.sql("SELECT CAST(details AS text) FROM audit_events WHERE establishment_id = :id AND action LIKE 'billing.adjustment.%'")
                .param("id", tenant.id())
                .query(String.class)
                .list();

        assertThat(actions).containsExactlyInAnyOrder("billing.adjustment.applied", "billing.adjustment.requested", "billing.adjustment.approved");
        assertThat(details).as("Le motif reste dans le journal, il n'est pas recopié").noneMatch(text -> text.contains("erreur") || text.contains("Remise"));
    }

    // Isolation

    @Test
    void unEtablissementNAgitNiSurLesObligationsNiSurLesDemandesDUnAutre() throws Exception {
        School a = school();
        School b = school();
        UUID obligation = issuedObligation(a);
        String entry = entryId(discount(a.tenant(), a.tenant().adminToken(), obligation, 1_000));
        String request = requestIdOf(discount(a.tenant(), newStaff(a.tenant(), "ACCOUNTANT"), obligation, 99_000));
        Tenant other = b.tenant();
        String token = other.adminToken();

        assertThat(discount(other, token, obligation, 1).code()).isEqualTo("OBLIGATION_NOT_FOUND");
        assertThat(correction(other, token, obligation, 1).code()).isEqualTo("OBLIGATION_NOT_FOUND");
        assertThat(cancellation(other, token, obligation).code()).isEqualTo("OBLIGATION_NOT_FOUND");
        assertThat(reversal(other, token, obligation, entry).code()).isEqualTo("OBLIGATION_NOT_FOUND");
        assertThat(approve(other, token, request).code()).isEqualTo("ADJUSTMENT_REQUEST_NOT_FOUND");
        assertThat(post(other.path("/billing/adjustment-requests/" + request + "/rejection"), token, Map.of("note", "Refus motivé")).status()).isEqualTo(404);
        assertThat(get(other.path("/billing/adjustment-requests"), token).body().size()).isZero();
        assertThat(discount(a.tenant(), token, obligation, 1).status()).as("Par l'établissement A, sans en être membre").isEqualTo(404);
        assertThat(approve(a.tenant(), token, request).status()).isEqualTo(404);
    }

    // Garanties portées par PostgreSQL, même si le code applicatif se trompe

    private UUID actor(Tenant tenant) {
        return get("/api/v2/me", tenant.adminToken()).id("id");
    }

    private UUID accountantId(Tenant tenant) {
        return get("/api/v2/me", newStaff(tenant, "ACCOUNTANT")).id("id");
    }

    private void insertEntry(Tenant tenant, UUID obligation, String kind, long amount, UUID reverses, UUID approvedBy) {
        jdbc.sql("""
                INSERT INTO obligation_entries
                  (id, establishment_id, obligation_id, kind, amount_minor, reason, reverses_entry_id, idempotency_key, recorded_at, recorded_by, approved_by)
                VALUES (gen_random_uuid(), :establishment, :obligation, :kind, :amount, 'Motif direct', :reverses, :key, now(), :actor, :approvedBy)
                """)
                .param("establishment", tenant.id())
                .param("obligation", obligation)
                .param("kind", kind)
                .param("amount", amount)
                .param("reverses", reverses)
                .param("key", key())
                .param("actor", actor(tenant))
                .param("approvedBy", approvedBy)
                .update();
    }

    @Test
    void laBaseRefuseDAmenerLeDuAZeroSansSecondePersonne() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        for (String kind : List.of("DISCOUNT", "CORRECTION", "CANCELLATION")) {
            assertThatThrownBy(() -> insertEntry(tenant, obligation, kind, -AMOUNT, null, null))
                    .as(kind)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("seconde personne");
        }
    }

    @Test
    void laBaseRefuseUneAnnulationPartielleEtUneAutoConfirmation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        assertThatThrownBy(() -> insertEntry(tenant, obligation, "CANCELLATION", -1, null, accountantId(tenant)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("tout le montant");
        assertThatThrownBy(() -> insertEntry(tenant, obligation, "DISCOUNT", -AMOUNT, null, actor(tenant)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("obligation_entries_second_person");
    }

    @Test
    void laBaseRefuseUneContrepasseeIncoherente() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID issued = UUID.fromString(entries(tenant, obligation).get(0).path("id").asString());
        UUID discount = UUID.fromString(entryId(discount(tenant, tenant.adminToken(), obligation, 10_000)));
        UUID other = issuedObligation(school);

        assertThatThrownBy(() -> insertEntry(tenant, obligation, "REVERSAL", 5_000, discount, null))
                .as("Autre montant que l'écriture d'origine")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("exact opposé");
        assertThatThrownBy(() -> insertEntry(tenant, obligation, "REVERSAL", -AMOUNT, issued, accountantId(tenant)))
                .as("L'émission ne se contrepasse pas")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertEntry(tenant, other, "REVERSAL", 10_000, discount, null))
                .as("Une écriture d'une autre obligation")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertEntry(tenant, obligation, "REVERSAL", 10_000, null, null))
                .as("Sans écriture d'origine")
                .isInstanceOf(DataIntegrityViolationException.class);

        insertEntry(tenant, obligation, "REVERSAL", 10_000, discount, null);
        assertThatThrownBy(() -> insertEntry(tenant, obligation, "REVERSAL", 10_000, discount, null))
                .as("Une seule contrepassée par écriture")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Montants exacts et dû suffisant : seule la règle sur la nature de l'écriture peut refuser ici. */
    @Test
    void laBaseRefuseDeContrepasserUneEmissionOuUneContrepassee() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID issued = UUID.fromString(entries(tenant, obligation).get(0).path("id").asString());
        UUID approver = accountantId(tenant);

        assertThatThrownBy(() -> insertEntry(tenant, obligation, "REVERSAL", -AMOUNT, issued, approver))
                .as("L'émission")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ne se contrepasse pas");

        UUID discount = UUID.fromString(entryId(discount(tenant, tenant.adminToken(), obligation, 10_000)));
        UUID reversal = UUID.fromString(entryId(reversal(tenant, tenant.adminToken(), obligation, discount.toString())));

        assertThatThrownBy(() -> insertEntry(tenant, obligation, "REVERSAL", -10_000, reversal, null))
                .as("Une contrepassée")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ne se contrepasse pas");
    }

    @Test
    void laBaseFigeUneDemandeDecideeEtLeContenuDUneDemande() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID pendingObligation = issuedObligation(school);
        UUID pending = UUID.fromString(requestIdOf(discount(tenant, tenant.adminToken(), pendingObligation, AMOUNT)));
        UUID decidedObligation = issuedObligation(school);
        String decided = requestIdOf(discount(tenant, tenant.adminToken(), decidedObligation, AMOUNT));
        approve(tenant, newStaff(tenant, "ACCOUNTANT"), decided);

        assertThatThrownBy(() -> jdbc.sql("UPDATE adjustment_requests SET amount_minor = -1 WHERE id = :id").param("id", pending).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.sql("UPDATE adjustment_requests SET reason = 'Autre' WHERE id = :id").param("id", pending).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.sql("UPDATE adjustment_requests SET decision_note = 'Autre' WHERE id = :id").param("id", UUID.fromString(decided)).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM adjustment_requests WHERE id = :id").param("id", pending).update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void laBaseRefuseQueLAuteurDecideSaPropreDemande() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID pending = UUID.fromString(requestIdOf(discount(tenant, tenant.adminToken(), issuedObligation(school), AMOUNT)));

        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE adjustment_requests SET status = 'REJECTED', decided_at = now(), decided_by = :actor, decision_note = 'Refus'
                WHERE id = :id
                """)
                .param("actor", actor(tenant))
                .param("id", pending)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("adjustment_requests_second_person");
    }

    @Test
    void laBaseRefuseDeuxDemandesEnAttente() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        requestIdOf(cancellation(tenant, tenant.adminToken(), obligation));

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO adjustment_requests (id, establishment_id, obligation_id, kind, reason, idempotency_key, requested_at, requested_by)
                VALUES (gen_random_uuid(), :establishment, :obligation, 'CANCELLATION', 'Doublon', :key, now(), :actor)
                """)
                .param("establishment", tenant.id())
                .param("obligation", obligation)
                .param("key", key())
                .param("actor", actor(tenant))
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("adjustment_requests_one_pending");
    }

    @Test
    void laBaseGardeLeJournalImmuableAvecLesNouvellesEcritures() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String entry = entryId(discount(tenant, tenant.adminToken(), obligation, 1_000));

        assertThatThrownBy(() -> jdbc.sql("UPDATE obligation_entries SET reason = 'Réécrit' WHERE id = :id").param("id", UUID.fromString(entry)).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM obligation_entries WHERE id = :id").param("id", UUID.fromString(entry)).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
    }
}
