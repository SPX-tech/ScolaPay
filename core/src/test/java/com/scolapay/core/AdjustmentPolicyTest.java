package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

/** La politique du propriétaire : seuils, demandes périmées, retrait, directeur et statuts neutres. */
class AdjustmentPolicyTest extends BillingScenarioTest {

    private static String applied(Response response) {
        assertThat(response.status()).as(response.raw()).isEqualTo(201);
        return response.text("entryId");
    }

    private static long count(List<Response> responses, int status) {
        return responses.stream().filter(response -> response.status() == status).count();
    }

    private String due(Tenant tenant, UUID obligation) {
        return obligationView(tenant, obligation).path("dueMinor").asString();
    }

    private String status(Tenant tenant, UUID obligation) {
        return obligationView(tenant, obligation).path("status").asString();
    }

    private JsonNode pendingRequest(Tenant tenant) {
        return get(tenant.path("/billing/adjustment-requests"), tenant.adminToken()).body().get(0);
    }

    private UUID idOf(String token) {
        return get("/api/v2/me", token).id("id");
    }

    // Seuils : le premier atteint de 25 % ou 100 000 XAF

    @Test
    void lePourcentageSAppliqueAuPremierFrancAuDela() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        applied(discount(tenant, tenant.adminToken(), obligation, 25_000));

        requestIdOf(discount(tenant, tenant.adminToken(), obligation, 1));
        assertThat(due(tenant, obligation)).isEqualTo("75000");
    }

    /** Sur une grosse obligation, 25 % laisserait passer 250 000 XAF : le montant absolu arrive le premier. */
    @Test
    void leMontantAbsoluSAppliqueSurUneGrosseObligation() throws Exception {
        School school = school(1_000_000);
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        applied(discount(tenant, tenant.adminToken(), obligation, 100_000));
        requestIdOf(discount(tenant, tenant.adminToken(), obligation, 1));

        requestIdOf(discount(tenant, tenant.adminToken(), issuedObligation(school), 100_001));
        requestIdOf(correction(tenant, tenant.adminToken(), issuedObligation(school), 100_001));
    }

    @Test
    void surUnePetiteObligationLePourcentageArriveLePremier() throws Exception {
        School school = school(200_000);
        Tenant tenant = school.tenant();

        applied(discount(tenant, tenant.adminToken(), issuedObligation(school), 50_000));
        requestIdOf(discount(tenant, tenant.adminToken(), issuedObligation(school), 50_001));
    }

    /** Un cumul net verrait ici 24 999 francs : c'est le contournement à fermer. */
    @Test
    void unHaussePuisUneBaisseNeSeCompensentPasPourEchapperAuSeuil() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        applied(correction(tenant, tenant.adminToken(), obligation, 25_000));

        requestIdOf(discount(tenant, tenant.adminToken(), obligation, 1));
        assertThat(due(tenant, obligation)).isEqualTo("125000");
    }

    @Test
    void uneAlternanceDeHaussesEtDeBaissesConsommeLeSeuil() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        applied(correction(tenant, tenant.adminToken(), obligation, 10_000));
        applied(correction(tenant, tenant.adminToken(), obligation, -10_000));

        requestIdOf(correction(tenant, tenant.adminToken(), obligation, 10_000));
        assertThat(due(tenant, obligation)).as("Le solde net n'a pas bougé, le contrôle se déclenche quand même").isEqualTo("100000");
    }

    @Test
    void fractionnerNeRepousseJamaisLeSeuil() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        for (int i = 0; i < 5; i++) {
            applied(discount(tenant, tenant.adminToken(), obligation, 5_000));
        }

        requestIdOf(discount(tenant, tenant.adminToken(), obligation, 1));
        assertThat(due(tenant, obligation)).isEqualTo("75000");
    }

    /** Une contrepassée libère le seuil, mais le dû reste alors à moins de 25 % de l'émis, sans seconde personne. */
    @Test
    void contrepasserPuisRecommencerNeFaitJamaisDepasserLeSeuil() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        for (int i = 0; i < 3; i++) {
            String entry = applied(discount(tenant, tenant.adminToken(), obligation, 25_000));
            assertThat(due(tenant, obligation)).isEqualTo("75000");
            applied(reversal(tenant, tenant.adminToken(), obligation, entry));
            assertThat(due(tenant, obligation)).isEqualTo("100000");
        }
        applied(discount(tenant, tenant.adminToken(), obligation, 25_000));

        requestIdOf(discount(tenant, tenant.adminToken(), obligation, 1));
    }

    @Test
    void uneOperationDejaConfirmeeCompteDansLeCumul() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        Response confirmed = approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(discount(tenant, tenant.adminToken(), obligation, 30_000)));
        assertThat(confirmed.status()).as(confirmed.raw()).isEqualTo(200);

        requestIdOf(discount(tenant, tenant.adminToken(), obligation, 1));
        assertThat(due(tenant, obligation)).isEqualTo("70000");
    }

    /** Sous verrou, le cumul brut reste juste, même quand hausses et baisses arrivent ensemble. */
    @Test
    void desHaussesEtDesBaissesSimultaneesRespectentLeSeuil() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        AtomicInteger turn = new AtomicInteger();

        List<Response> responses = concurrently(8, () -> correction(
                tenant, tenant.adminToken(), obligation, turn.getAndIncrement() % 2 == 0 ? 10_000 : -10_000));

        assertThat(count(responses, 201)).as(responses.toString()).isEqualTo(2);
        assertThat(count(responses, 202)).as(responses.toString()).isEqualTo(1);
        assertThat(count(responses, 409)).as(responses.toString()).isEqualTo(5);
    }

    // Une demande présente un montant précis, et rien d'autre ne se confirme

    @Test
    void uneDemandePresenteSonMontantEtLeDuQuElleLaisse() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        applied(discount(tenant, tenant.adminToken(), obligation, 10_000));
        requestIdOf(cancellation(tenant, tenant.adminToken(), obligation));

        JsonNode request = pendingRequest(tenant);

        assertThat(request.path("amountMinor").asString()).isEqualTo("-90000");
        assertThat(request.path("dueAtRequestMinor").asString()).isEqualTo("90000");
        assertThat(request.path("resultingDueMinor").asString()).isEqualTo("0");
        assertThat(request.path("outdated").asBoolean()).isFalse();
    }

    /** Le cas critique : l'approbateur a vu 100 000 francs, il n'en confirmera pas 90 000 sans le savoir. */
    @Test
    void uneAnnulationDontLeDuAChangeExigeUneNouvelleConfirmation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String accountant = newStaff(tenant, "ACCOUNTANT");
        String stale = requestIdOf(cancellation(tenant, tenant.adminToken(), obligation));

        applied(discount(tenant, tenant.adminToken(), obligation, 10_000));
        assertThat(pendingRequest(tenant).path("outdated").asBoolean()).as("Visible avant même d'essayer").isTrue();

        Response refused = approve(tenant, accountant, stale);
        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("REQUEST_OUTDATED");
        assertThat(due(tenant, obligation)).isEqualTo("90000");
        assertThat(status(tenant, obligation)).isEqualTo("OPEN");

        Response blocked = cancellation(tenant, tenant.adminToken(), obligation);
        assertThat(blocked.code()).as("Elle se retire explicitement avant toute nouvelle demande").isEqualTo("PENDING_REQUEST_EXISTS");
        assertThat(withdraw(tenant, tenant.adminToken(), stale, "Montant périmé").status()).isEqualTo(204);

        String fresh = requestIdOf(cancellation(tenant, tenant.adminToken(), obligation));
        assertThat(pendingRequest(tenant).path("amountMinor").asString()).isEqualTo("-90000");
        Response confirmed = approve(tenant, accountant, fresh);
        assertThat(confirmed.status()).as(confirmed.raw()).isEqualTo(200);
        assertThat(confirmed.text("amountMinor")).isEqualTo("-90000");
        assertThat(status(tenant, obligation)).isEqualTo("CANCELLED");
    }

    @Test
    void uneRemiseDontLaPorteeAChangeExigeUneNouvelleConfirmation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String stale = requestIdOf(discount(tenant, newStaff(tenant, "ACCOUNTANT"), obligation, 90_000));

        applied(correction(tenant, tenant.adminToken(), obligation, -10_000));

        Response refused = approve(tenant, tenant.adminToken(), stale);
        assertThat(refused.code()).as("Elle aurait soldé l'obligation, alors qu'elle en laissait 10 000").isEqualTo("REQUEST_OUTDATED");
        assertThat(due(tenant, obligation)).isEqualTo("90000");
    }

    /** Le solde redevient identique, mais le journal n'est plus celui qui a été présenté. */
    @Test
    void unSoldeRevenuIdentiqueNeSuffitPasAConfirmer() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String stale = requestIdOf(discount(tenant, newStaff(tenant, "ACCOUNTANT"), obligation, AMOUNT));

        String raised = applied(correction(tenant, tenant.adminToken(), obligation, 1_000));
        applied(reversal(tenant, tenant.adminToken(), obligation, raised));
        assertThat(due(tenant, obligation)).isEqualTo("100000");

        assertThat(approve(tenant, tenant.adminToken(), stale).code()).isEqualTo("REQUEST_OUTDATED");
    }

    @Test
    void uneEcritureEtUneConfirmationSimultaneesRestentCoherentes() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String request = requestIdOf(discount(tenant, newStaff(tenant, "ACCOUNTANT"), obligation, AMOUNT));
        AtomicInteger turn = new AtomicInteger();
        String[] order = new String[2];

        List<Response> responses = concurrently(2, () -> {
            int mine = turn.getAndIncrement();
            Response response = mine == 0 ? approve(tenant, tenant.adminToken(), request) : discount(tenant, tenant.adminToken(), obligation, 10_000);
            order[mine] = String.valueOf(response.status());
            return response;
        });

        assertThat(order[0].equals("200") ^ order[1].equals("201")).as(responses.toString()).isTrue();
        assertThat(due(tenant, obligation)).as(responses.toString()).isEqualTo(order[0].equals("200") ? "0" : "90000");
    }

    @Test
    void confirmerEtRetirerEnMemeTempsNeLaissentQuUneDecision() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String author = newStaff(tenant, "ACCOUNTANT");
        String request = requestIdOf(discount(tenant, author, obligation, AMOUNT));
        AtomicInteger turn = new AtomicInteger();

        List<Response> responses = concurrently(2, () -> turn.getAndIncrement() == 0
                ? approve(tenant, tenant.adminToken(), request)
                : withdraw(tenant, author, request, "Je renonce"));

        assertThat(count(responses, 409)).as(responses.toString()).isEqualTo(1);
        String decided = jdbc.sql("SELECT status FROM adjustment_requests WHERE id = :id").param("id", UUID.fromString(request)).query(String.class).single();
        assertThat(journalSize(obligation)).isEqualTo(decided.equals("APPROVED") ? 2 : 1);
    }

    // Retrait et refus : rien ne bloque indéfiniment, et tout reste tracé

    @Test
    void lAuteurRetireSaDemandeAvecUnMotifEtLaTraceReste() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String author = newStaff(tenant, "ACCOUNTANT");
        Map<String, Object> body = Map.of("amountMinor", String.valueOf(AMOUNT), "reason", "Bourse demandée", "idempotencyKey", key());
        String path = tenant.path("/billing/obligations/" + obligation + "/discounts");
        String request = requestIdOf(post(path, author, body));

        assertThat(post(tenant.path("/billing/adjustment-requests/" + request + "/withdrawal"), author, Map.of()).status()).as("Sans motif").isEqualTo(400);
        assertThat(withdraw(tenant, author, request, "Saisie par erreur").status()).isEqualTo(204);

        assertThat(journalSize(obligation)).isEqualTo(1);
        JsonNode withdrawn = get(tenant.path("/billing/adjustment-requests?status=WITHDRAWN"), tenant.adminToken()).body().get(0);
        assertThat(withdrawn.path("decisionNote").asString()).isEqualTo("Saisie par erreur");
        assertThat(withdrawn.path("decidedBy").asString()).isEqualTo(idOf(author).toString());
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE action = 'billing.adjustment.withdrawn' AND target_id = :id")
                        .param("id", UUID.fromString(request)).query(Long.class).single())
                .isEqualTo(1);

        Response replay = post(path, author, body);
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.text("outcome")).as("La même clé renvoie son état réel").isEqualTo("WITHDRAWN");

        assertThat(approve(tenant, tenant.adminToken(), request).code()).isEqualTo("REQUEST_ALREADY_DECIDED");
        assertThat(withdraw(tenant, author, request, "Encore").code()).isEqualTo("REQUEST_ALREADY_DECIDED");
        requestIdOf(discount(tenant, author, obligation, AMOUNT));
    }

    @Test
    void seulLAuteurRetireEtUnAutreRefuse() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String author = newStaff(tenant, "ACCOUNTANT");
        String other = newStaff(tenant, "ACCOUNTANT");
        String request = requestIdOf(discount(tenant, author, obligation, AMOUNT));

        assertThat(withdraw(tenant, other, request, "Pas la mienne").code()).isEqualTo("NOT_REQUEST_AUTHOR");
        Response selfRejected = reject(tenant, author, request, "Mon propre refus");
        assertThat(selfRejected.status()).as(selfRejected.raw()).isEqualTo(403);
        assertThat(selfRejected.code()).isEqualTo("SELF_DECISION_FORBIDDEN");

        // Le code d'erreur compte : sans lui, un défaut de permission se cacherait derrière NOT_REQUEST_AUTHOR, aussi en 403.
        for (String role : List.of("CASHIER", "AUDITOR", "REGISTRAR", "DIRECTOR")) {
            Response refused = withdraw(tenant, newStaff(tenant, role), request, "Interdit");
            assertThat(refused.status()).as(role).isEqualTo(403);
            assertThat(refused.code()).as(role).isEqualTo("PERMISSION_DENIED");
        }

        assertThat(reject(tenant, other, request, "Dossier incomplet").status()).isEqualTo(204);
    }

    @Test
    void unAutreEtablissementNeRetireRien() throws Exception {
        School a = school();
        School b = school();
        String request = requestIdOf(discount(a.tenant(), a.tenant().adminToken(), issuedObligation(a), AMOUNT));

        assertThat(withdraw(b.tenant(), b.tenant().adminToken(), request, "Volée").code()).isEqualTo("ADJUSTMENT_REQUEST_NOT_FOUND");
        assertThat(withdraw(a.tenant(), b.tenant().adminToken(), request, "Volée").status()).isEqualTo(404);
    }

    /** Aucun mécanisme d'auto-approbation : seule une seconde personne habilitée débloque l'opération. */
    @Test
    void uneEcoleAUnSeulUtilisateurNePeutPasSeConfirmerElleMeme() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String request = requestIdOf(cancellation(tenant, tenant.adminToken(), obligation));

        assertThat(approve(tenant, tenant.adminToken(), request).code()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
        assertThat(reject(tenant, tenant.adminToken(), request, "Auto-refus").code()).isEqualTo("SELF_DECISION_FORBIDDEN");
        assertThat(status(tenant, obligation)).isEqualTo("OPEN");
        assertThat(journalSize(obligation)).isEqualTo(1);

        applied(discount(tenant, tenant.adminToken(), obligation, 1_000));
        assertThat(withdraw(tenant, tenant.adminToken(), request, "Pas de seconde personne").status()).as("Elle ne reste pas bloquée").isEqualTo(204);
    }

    // Directeur : confirme, ne gère pas

    @Test
    void leDirecteurConfirmeOuRefuseSansPouvoirOperer() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String director = newStaff(tenant, "DIRECTOR");

        assertThat(discount(tenant, director, obligation, 1).status()).isEqualTo(403);
        assertThat(correction(tenant, director, obligation, 1).status()).isEqualTo(403);
        assertThat(cancellation(tenant, director, obligation).status()).isEqualTo(403);
        assertThat(post(tenant.path("/billing/fee-catalogs"), director, Map.of("academicYearId", school.year().toString())).status()).isEqualTo(403);
        assertThat(get(tenant.path("/billing/reviews?academicYearId=" + school.year()), director).status()).isEqualTo(200);

        String refused = requestIdOf(discount(tenant, tenant.adminToken(), obligation, AMOUNT));
        assertThat(reject(tenant, director, refused, "Bourse non justifiée").status()).isEqualTo(204);

        Response confirmed = approve(tenant, director, requestIdOf(discount(tenant, tenant.adminToken(), obligation, AMOUNT)));
        assertThat(confirmed.status()).as(confirmed.raw()).isEqualTo(200);
        assertThat(confirmed.text("approvedBy")).isEqualTo(idOf(director).toString());
        assertThat(status(tenant, obligation)).isEqualTo("WAIVED");
    }

    @Test
    void lAdministrateurEtLeDirecteurSuffisentAUnePetiteEcole() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        approve(tenant, newStaff(tenant, "DIRECTOR"), requestIdOf(cancellation(tenant, tenant.adminToken(), obligation)));

        assertThat(status(tenant, obligation)).isEqualTo("CANCELLED");
    }

    // Statuts : un dû nul ne prétend pas à une cause unique

    @Test
    void uneCombinaisonDeRemisesEtDeCorrectionsResteNeutre() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        String accountant = newStaff(tenant, "ACCOUNTANT");

        UUID lowered = issuedObligation(school);
        applied(correction(tenant, tenant.adminToken(), lowered, -10_000));
        approve(tenant, accountant, requestIdOf(discount(tenant, tenant.adminToken(), lowered, 90_000)));
        assertThat(status(tenant, lowered)).isEqualTo("ADJUSTED_TO_ZERO");

        UUID raised = issuedObligation(school);
        applied(correction(tenant, tenant.adminToken(), raised, 10_000));
        approve(tenant, accountant, requestIdOf(discount(tenant, tenant.adminToken(), raised, 110_000)));
        assertThat(status(tenant, raised)).isEqualTo("ADJUSTED_TO_ZERO");

        List<String> kinds = jdbc.sql("SELECT kind FROM obligation_entries WHERE obligation_id = :id").param("id", lowered).query(String.class).list();
        assertThat(kinds).as("Les écritures gardent leur nature").containsExactlyInAnyOrder("ISSUED", "CORRECTION", "DISCOUNT");
    }

    @Test
    void uneCorrectionContrepasseeNeRendPasLeStatutNeutre() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        applied(reversal(tenant, tenant.adminToken(), obligation, applied(correction(tenant, tenant.adminToken(), obligation, -1_000))));

        approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(discount(tenant, tenant.adminToken(), obligation, AMOUNT)));

        assertThat(status(tenant, obligation)).isEqualTo("WAIVED");
    }

    @Test
    void uneAnnulationApresUneRemiseResteUneAnnulation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        applied(discount(tenant, tenant.adminToken(), obligation, 10_000));

        approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(cancellation(tenant, tenant.adminToken(), obligation)));

        assertThat(status(tenant, obligation)).isEqualTo("CANCELLED");
    }

    // Garanties portées par PostgreSQL

    private void insertEntry(Tenant tenant, UUID id, UUID obligation, long amount, UUID recordedBy, UUID approvedBy) {
        jdbc.sql("""
                INSERT INTO obligation_entries
                  (id, establishment_id, obligation_id, kind, amount_minor, reason, idempotency_key, recorded_at, recorded_by, approved_by)
                VALUES (:id, :establishment, :obligation, 'DISCOUNT', :amount, 'Motif direct', :key, now(), :recordedBy, :approvedBy)
                """)
                .param("id", id)
                .param("establishment", tenant.id())
                .param("obligation", obligation)
                .param("amount", amount)
                .param("key", key())
                .param("recordedBy", recordedBy)
                .param("approvedBy", approvedBy)
                .update();
    }

    private int markApproved(String request, UUID decidedBy, UUID entry) {
        return jdbc.sql("""
                UPDATE adjustment_requests SET status = 'APPROVED', decided_at = now(), decided_by = :decidedBy, entry_id = :entry
                WHERE id = :id
                """)
                .param("id", UUID.fromString(request))
                .param("decidedBy", decidedBy)
                .param("entry", entry)
                .update();
    }

    @Test
    void laBaseRefuseDeConfirmerAvecUneEcritureDifferenteDeLaDemande() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID author = idOf(tenant.adminToken());
        UUID approver = idOf(newStaff(tenant, "ACCOUNTANT"));
        String request = requestIdOf(discount(tenant, tenant.adminToken(), obligation, 30_000));
        UUID entry = UUID.randomUUID();
        insertEntry(tenant, entry, obligation, -29_000, author, approver);

        assertThatThrownBy(() -> markApproved(request, approver, entry))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ne correspond pas");
    }

    @Test
    void laBaseRefuseDeConfirmerUneDemandePerimee() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID author = idOf(tenant.adminToken());
        UUID approver = idOf(newStaff(tenant, "ACCOUNTANT"));
        String request = requestIdOf(discount(tenant, tenant.adminToken(), obligation, 30_000));
        applied(discount(tenant, tenant.adminToken(), obligation, 1_000));
        UUID entry = UUID.randomUUID();
        insertEntry(tenant, entry, obligation, -30_000, author, approver);

        assertThatThrownBy(() -> markApproved(request, approver, entry))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("journal a changé");
    }

    @Test
    void laBaseReserveLeRetraitASonAuteurEtExigeUnMotif() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID request = UUID.fromString(requestIdOf(discount(tenant, tenant.adminToken(), issuedObligation(school), AMOUNT)));
        UUID other = idOf(newStaff(tenant, "ACCOUNTANT"));

        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE adjustment_requests SET status = 'WITHDRAWN', decided_at = now(), decided_by = :other, decision_note = 'Retrait' WHERE id = :id
                """).param("other", other).param("id", request).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("adjustment_requests_withdrawn_by_author");
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE adjustment_requests SET status = 'WITHDRAWN', decided_at = now(), decided_by = requested_by WHERE id = :id
                """).param("id", request).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("adjustment_requests_note_required");
    }

    @Test
    void laBaseExigeQuUneAnnulationDemandeeCouvreToutLeDuPresente() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO adjustment_requests
                  (id, establishment_id, obligation_id, kind, amount_minor, due_at_request_minor, journal_size_at_request, reason, idempotency_key, requested_at, requested_by)
                VALUES (gen_random_uuid(), :establishment, :obligation, 'CANCELLATION', -1, 100000, 1, 'Partielle', :key, now(), :actor)
                """)
                .param("establishment", tenant.id())
                .param("obligation", obligation)
                .param("key", key())
                .param("actor", idOf(tenant.adminToken()))
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("adjustment_requests_cancels_all");
    }
}
