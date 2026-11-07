package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

/** Personnes physiques et morales, rôles explicites, et aucun redevable déduit. */
class BillingPartyTest extends BillingScenarioTest {

    private static final String DEBTOR = "CONTRACTUAL_DEBTOR";
    private static final String RESPONSIBLE = "FINANCIAL_RESPONSIBLE";
    private static final String CONTRIBUTOR = "VOLUNTARY_CONTRIBUTOR";

    private static long count(List<Response> responses, int status) {
        return responses.stream().filter(response -> response.status() == status).count();
    }

    private JsonNode designationsOf(Tenant tenant, UUID obligation) {
        return get(tenant.path("/billing/obligations/" + obligation + "/parties"), tenant.adminToken()).body();
    }

    private long byState(School school, String state) {
        Response listed = get(
                school.tenant().path("/billing/obligations-by-debtor-state?academicYearId=" + school.year() + "&state=" + state), school.tenant().adminToken());
        assertThat(listed.status()).as(listed.raw()).isEqualTo(200);
        return listed.body().path("total").asLong();
    }

    // Rien n'est jamais déduit

    /** Ni un parent lié à un mineur, ni l'âge d'un majeur ne désignent un redevable. */
    @Test
    void aucunRedevableNEstDeduitDeLAgeNiDUnLienDeParente() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID minor = newStudent(tenant, LocalDate.now().minusYears(14));
        Linked parent = linkedThirdParty(tenant, minor, true);
        UUID adult = newStudent(tenant, LocalDate.now().minusYears(20));

        enrolledAndBilled(school, minor);
        enrolledAndBilled(school, adult);

        assertThat(designationsOf(tenant, obligationOf(minor)).size()).isZero();
        assertThat(designationsOf(tenant, obligationOf(adult)).size()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM billing_parties WHERE establishment_id = :id").param("id", tenant.id()).query(Long.class).single())
                .as("Aucune personne créée d'office")
                .isZero();
        assertThat(byState(school, "NONE")).as("Cela reste visible, sans bloquer l'émission").isEqualTo(2);
        assertThat(get("/api/v2/me/billing/obligations", parent.token()).body().size()).isZero();
    }

    @Test
    void personnesPhysiquesEtMoralesSontRepresentablesAvecOuSansCompte() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        Linked linked = linkedThirdParty(tenant, student, false);

        Response person = createParty(tenant, "PERSON", "Awa Djimé", null, null);
        Response company = createParty(tenant, "ORGANIZATION", "Société Sahel SARL", "RCCM-2026-001", null);
        Response withAccount = createParty(tenant, "PERSON", "Tiers Lié", null, linked.grant());

        assertThat(person.body().path("kind").asString()).isEqualTo("PERSON");
        assertThat(person.body().path("hasAccount").asBoolean()).isFalse();
        assertThat(company.body().path("kind").asString()).isEqualTo("ORGANIZATION");
        assertThat(company.text("identifier")).isEqualTo("RCCM-2026-001");
        assertThat(withAccount.body().path("hasAccount").asBoolean()).isTrue();
        assertThat(withAccount.raw()).as("L'identifiant du compte ne circule pas").doesNotContain(linked.account().toString());

        Response invalid = createParty(tenant, "FAMILY", "Inconnu", null, null);
        assertThat(invalid.status()).isEqualTo(400);
    }

    @Test
    void unRedevableMoralSansCompteSeDesigneEtResteNonConfirme() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        assertThat(byState(school, "NONE")).isEqualTo(1);

        Response designated = designate(tenant, tenant.adminToken(), obligation, partyId(createParty(tenant, "ORGANIZATION", "Société Sahel", null, null)), DEBTOR);

        assertThat(designated.status()).as(designated.raw()).isEqualTo(201);
        assertThat(designated.text("debtorStatus")).isEqualTo("UNCONFIRMED");
        assertThat(designated.text("partyName")).isEqualTo("Société Sahel");
        assertThat(byState(school, "NONE")).isZero();
        assertThat(byState(school, "UNCONFIRMED")).as("L'état reste visible").isEqualTo(1);
    }

    @Test
    void uneObligationPlusRienDueNeResteARegulariser() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(cancellation(tenant, tenant.adminToken(), obligation)));

        assertThat(byState(school, "NONE")).isZero();
    }

    // Confirmation

    @Test
    void confirmerExigeUneReferenceEtResteIdempotent() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String designation = designate(tenant, tenant.adminToken(), obligation, partyId(createParty(tenant, "PERSON", "Awa Djimé", null, null)), DEBTOR).text("id");

        assertThat(confirm(tenant, tenant.adminToken(), designation, " ").status()).isEqualTo(400);

        Response confirmed = confirm(tenant, tenant.adminToken(), designation, "CONTRAT-42");
        assertThat(confirmed.status()).as(confirmed.raw()).isEqualTo(200);
        assertThat(confirmed.text("debtorStatus")).isEqualTo("CONFIRMED");
        assertThat(confirmed.text("commitmentReference")).isEqualTo("CONTRAT-42");
        assertThat(byState(school, "UNCONFIRMED")).isZero();

        assertThat(confirm(tenant, tenant.adminToken(), designation, "CONTRAT-42").status()).isEqualTo(200);
        assertThat(confirm(tenant, tenant.adminToken(), designation, "AUTRE").code()).isEqualTo("DEBTOR_ALREADY_CONFIRMED");
    }

    @Test
    void seulUnRedevableEnCoursSeConfirme() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String responsible = designate(tenant, tenant.adminToken(), obligation, partyId(createParty(tenant, "PERSON", "Responsable", null, null)), RESPONSIBLE).text("id");
        assertThat(confirm(tenant, tenant.adminToken(), responsible, "REF").code()).isEqualTo("NOT_A_DEBTOR");

        String debtor = designate(tenant, tenant.adminToken(), obligation, partyId(createParty(tenant, "PERSON", "Redevable", null, null)), DEBTOR).text("id");
        assertThat(endDesignation(tenant, tenant.adminToken(), debtor, "Erreur de saisie").status()).isEqualTo(200);

        assertThat(confirm(tenant, tenant.adminToken(), debtor, "REF").code()).isEqualTo("DESIGNATION_ENDED");
        assertThat(endDesignation(tenant, tenant.adminToken(), debtor, "Encore").code()).isEqualTo("DESIGNATION_ENDED");
    }

    // Unicité et historique

    @Test
    void unSeulRedevableEtUnSeulResponsableEnCoursEtPlusieursContributeurs() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        UUID first = partyId(createParty(tenant, "PERSON", "Premier", null, null));
        UUID second = partyId(createParty(tenant, "PERSON", "Second", null, null));

        for (String role : List.of(DEBTOR, RESPONSIBLE)) {
            assertThat(designate(tenant, tenant.adminToken(), obligation, first, role).status()).isEqualTo(201);
            assertThat(designate(tenant, tenant.adminToken(), obligation, first, role).status()).as("Rejouer ne crée rien").isEqualTo(200);
            assertThat(designate(tenant, tenant.adminToken(), obligation, second, role).code()).isEqualTo("ROLE_ALREADY_ASSIGNED");
        }

        for (int i = 0; i < 2; i++) {
            Linked linked = linkedThirdParty(tenant, student, false);
            UUID contributor = partyId(createParty(tenant, "PERSON", "Contributeur " + i, null, linked.grant()));
            assertThat(designate(tenant, tenant.adminToken(), obligation, contributor, CONTRIBUTOR).status()).isEqualTo(201);
        }
        assertThat(designationsOf(tenant, obligation).size()).isEqualTo(4);
    }

    @Test
    void terminerUneDesignationGardeLHistoriqueEtLaissePlaceAUneAutre() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID first = partyId(createParty(tenant, "PERSON", "Premier", null, null));
        String designation = designate(tenant, tenant.adminToken(), obligation, first, DEBTOR).text("id");

        assertThat(post(tenant.path("/billing/obligation-parties/" + designation + "/end"), tenant.adminToken(), Map.of()).status()).as("Sans motif").isEqualTo(400);
        Response ended = endDesignation(tenant, tenant.adminToken(), designation, "Engagement rompu");
        assertThat(ended.status()).as(ended.raw()).isEqualTo(200);
        assertThat(ended.text("endReason")).isEqualTo("Engagement rompu");

        assertThat(designate(tenant, tenant.adminToken(), obligation, partyId(createParty(tenant, "PERSON", "Second", null, null)), DEBTOR).status()).isEqualTo(201);
        assertThat(designate(tenant, tenant.adminToken(), obligation, first, RESPONSIBLE).status()).isEqualTo(201);

        JsonNode history = designationsOf(tenant, obligation);
        assertThat(history.size()).isEqualTo(3);
        assertThat(history.get(0).path("endedAt").isNull()).isFalse();
    }

    @Test
    void desDesignationsSimultaneesNeLaissentQuUnRedevable() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        List<UUID> parties = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            parties.add(partyId(createParty(tenant, "PERSON", "Personne " + i, null, null)));
        }
        java.util.concurrent.atomic.AtomicInteger turn = new java.util.concurrent.atomic.AtomicInteger();

        List<Response> responses = concurrently(8, () -> designate(tenant, tenant.adminToken(), obligation, parties.get(turn.getAndIncrement()), DEBTOR));

        assertThat(count(responses, 201)).as(responses.toString()).isEqualTo(1);
        assertThat(count(responses, 409)).as(responses.toString()).isEqualTo(7);
    }

    @Test
    void laMemeDesignationSimultaneeNeSeCreeQuUneFois() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID party = partyId(createParty(tenant, "PERSON", "Personne", null, null));

        List<Response> responses = concurrently(8, () -> designate(tenant, tenant.adminToken(), obligation, party, DEBTOR));

        assertThat(count(responses, 201)).as(responses.toString()).isEqualTo(1);
        assertThat(count(responses, 200)).as(responses.toString()).isEqualTo(7);
        assertThat(designationsOf(tenant, obligation).size()).isEqualTo(1);
    }

    // Contributeur

    @Test
    void unContributeurExigeUnCompteLieAuDossier() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        UUID elsewhere = newStudent(tenant);
        Linked linkedElsewhere = linkedThirdParty(tenant, elsewhere, false);

        Response withoutAccount = designate(tenant, tenant.adminToken(), obligation, partyId(createParty(tenant, "PERSON", "Sans Compte", null, null)), CONTRIBUTOR);
        assertThat(withoutAccount.status()).as(withoutAccount.raw()).isEqualTo(422);
        assertThat(withoutAccount.code()).isEqualTo("CONTRIBUTOR_ACCOUNT_REQUIRED");

        Response notLinked = designate(tenant, tenant.adminToken(), obligation, partyId(createParty(tenant, "PERSON", "Autre Dossier", null, linkedElsewhere.grant())), CONTRIBUTOR);
        assertThat(notLinked.status()).as(notLinked.raw()).isEqualTo(422);
        assertThat(notLinked.code()).isEqualTo("CONTRIBUTOR_LINK_REQUIRED");
    }

    @Test
    void unMemeCompteNeCorrespondQuAUnePersonneParEtablissement() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        Linked linked = linkedThirdParty(tenant, newStudent(tenant), false);
        assertThat(createParty(tenant, "PERSON", "Première", null, linked.grant()).status()).isEqualTo(201);

        assertThat(createParty(tenant, "PERSON", "Seconde", null, linked.grant()).code()).isEqualTo("PARTY_ALREADY_EXISTS");
    }

    @Test
    void unLienRevoqueNOuvrePlusRien() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = newStudent(tenant);
        Linked linked = linkedThirdParty(tenant, student, false);
        assertThat(delete(tenant.path("/students/" + student + "/access-grants/" + linked.grant()), tenant.adminToken()).status()).isEqualTo(204);

        assertThat(createParty(tenant, "PERSON", "Révoqué", null, linked.grant()).code()).isEqualTo("LINK_NOT_FOUND");
    }

    // Affectation à un élève

    @Test
    void affecterAUnElevePorteSurSesChargesSansEcraserNiPrevoirLesSuivantes() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID firstObligation = obligationOf(student);
        UUID secondObligation = secondCharge(school, student);
        UUID owner = partyId(createParty(tenant, "PERSON", "Déjà Redevable", null, null));
        UUID party = partyId(createParty(tenant, "PERSON", "Parent Payeur", null, null));
        assertThat(designate(tenant, tenant.adminToken(), firstObligation, owner, DEBTOR).status()).isEqualTo(201);
        String path = tenant.path("/billing/students/" + student + "/parties");
        Map<String, Object> body = Map.of("academicYearId", school.year().toString(), "partyId", party.toString(), "role", DEBTOR);

        Response assigned = post(path, tenant.adminToken(), body);
        assertThat(assigned.status()).as(assigned.raw()).isEqualTo(200);
        assertThat(assigned.body().path("assigned").asInt()).isEqualTo(1);
        assertThat(assigned.body().path("heldByAnother").asInt()).as("Jamais écrasé").isEqualTo(1);
        assertThat(designationsOf(tenant, firstObligation).get(0).path("partyId").asString()).isEqualTo(owner.toString());
        assertThat(designationsOf(tenant, secondObligation).get(0).path("partyId").asString()).isEqualTo(party.toString());

        Response replay = post(path, tenant.adminToken(), body);
        assertThat(replay.body().path("assigned").asInt()).isZero();
        assertThat(replay.body().path("alreadyHeld").asInt()).isEqualTo(1);

        UUID thirdObligation = secondCharge(school, student);
        assertThat(designationsOf(tenant, thirdObligation).size()).as("Une charge suivante n'hérite de personne").isZero();
    }

    // Permissions et confidentialité

    @Test
    void lesPermissionsSeparentLaLectureDeLaGestion() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID party = partyId(createParty(tenant, "PERSON", "Personne", null, null));
        String designation = designate(tenant, tenant.adminToken(), obligation, party, DEBTOR).text("id");

        for (String role : List.of("CASHIER", "AUDITOR", "DIRECTOR", "REGISTRAR")) {
            String token = newStaff(tenant, role);
            assertThat(createParty(tenant, token, "PERSON", "Interdit").status()).as(role).isEqualTo(403);
            assertThat(designate(tenant, token, obligation, party, RESPONSIBLE).status()).as(role).isEqualTo(403);
            assertThat(confirm(tenant, token, designation, "REF").status()).as(role).isEqualTo(403);
            assertThat(endDesignation(tenant, token, designation, "Interdit").status()).as(role).isEqualTo(403);
        }
        for (String role : List.of("CASHIER", "AUDITOR", "DIRECTOR", "ACCOUNTANT")) {
            String token = newStaff(tenant, role);
            assertThat(get(tenant.path("/billing/parties"), token).status()).as(role).isEqualTo(200);
            assertThat(get(tenant.path("/billing/obligations/" + obligation + "/parties"), token).status()).as(role).isEqualTo(200);
        }
        String registrar = newStaff(tenant, "REGISTRAR");
        assertThat(get(tenant.path("/billing/parties"), registrar).status()).isEqualTo(403);
        assertThat(get(tenant.path("/billing/obligations/" + obligation + "/parties"), registrar).status()).isEqualTo(403);

        String accountant = newStaff(tenant, "ACCOUNTANT");
        assertThat(designate(tenant, accountant, obligation, party, RESPONSIBLE).status()).isEqualTo(201);
    }

    @Test
    void leCaissierNeLitPasLeMotifDeFinDUneDesignationEtLAuditNeLeRecopiePas() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        String secret = "Motif-Confidentiel-Fin";
        String designation = designate(tenant, tenant.adminToken(), obligation, partyId(createParty(tenant, "PERSON", "Personne", null, null)), DEBTOR).text("id");
        assertThat(endDesignation(tenant, tenant.adminToken(), designation, secret).status()).isEqualTo(200);

        Response cashier = get(tenant.path("/billing/obligations/" + obligation + "/parties"), newStaff(tenant, "CASHIER"));
        assertThat(cashier.status()).isEqualTo(200);
        assertThat(cashier.raw()).doesNotContain(secret);
        assertThat(cashier.body().get(0).path("endedAt").isNull()).as("Le fait reste visible").isFalse();

        assertThat(get(tenant.path("/billing/obligations/" + obligation + "/parties"), newStaff(tenant, "AUDITOR")).raw()).contains(secret);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id = :id AND CAST(details AS text) LIKE :secret")
                        .param("id", tenant.id()).param("secret", "%" + secret + "%").query(Long.class).single())
                .isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id = :id AND action IN ('billing.party.created', 'billing.party.designated', 'billing.party.ended')")
                        .param("id", tenant.id()).query(Long.class).single())
                .isEqualTo(3);
    }

    // Isolation

    @Test
    void unEtablissementNeTouchePasAuxPersonnesNiAuxObligationsDUnAutre() throws Exception {
        School a = school();
        School b = school();
        UUID obligationA = issuedObligation(a);
        UUID obligationB = issuedObligation(b);
        UUID studentA = newStudent(a.tenant());
        Linked linkedA = linkedThirdParty(a.tenant(), studentA, false);
        UUID partyA = partyId(createParty(a.tenant(), "PERSON", "Personne de A", null, null));
        UUID partyB = partyId(createParty(b.tenant(), "PERSON", "Personne de B", null, null));
        String designationA = designate(a.tenant(), a.tenant().adminToken(), obligationA, partyA, DEBTOR).text("id");
        Tenant other = b.tenant();
        String token = other.adminToken();

        assertThat(designate(other, token, obligationB, partyA, DEBTOR).code()).isEqualTo("PARTY_NOT_FOUND");
        assertThat(designate(other, token, obligationA, partyB, DEBTOR).code()).isEqualTo("OBLIGATION_NOT_FOUND");
        assertThat(get(other.path("/billing/obligations/" + obligationA + "/parties"), token).status()).isEqualTo(404);
        assertThat(confirm(other, token, designationA, "REF").code()).isEqualTo("DESIGNATION_NOT_FOUND");
        assertThat(endDesignation(other, token, designationA, "Vol").code()).isEqualTo("DESIGNATION_NOT_FOUND");
        assertThat(createParty(other, "PERSON", "Compte de A", null, linkedA.grant()).code()).as("Un lien d'un autre établissement").isEqualTo("LINK_NOT_FOUND");
        assertThat(get(other.path("/billing/parties"), token).raw()).doesNotContain("Personne de A");
        assertThat(get(a.tenant().path("/billing/parties"), token).status()).as("Sans en être membre").isEqualTo(404);

        assertThatThrownBy(() -> insertDesignation(other, obligationB, partyA, DEBTOR, "UNCONFIRMED"))
                .as("La base refuse une personne d'un autre établissement")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Garanties portées par PostgreSQL

    private void insertDesignation(Tenant tenant, UUID obligation, UUID party, String role, String status) {
        jdbc.sql("""
                INSERT INTO obligation_parties (id, establishment_id, obligation_id, party_id, role, debtor_status, designated_at, designated_by)
                VALUES (gen_random_uuid(), :establishment, :obligation, :party, :role, :status, now(), :actor)
                """)
                .param("establishment", tenant.id())
                .param("obligation", obligation)
                .param("party", party)
                .param("role", role)
                .param("status", status)
                .param("actor", get("/api/v2/me", tenant.adminToken()).id("id"))
                .update();
    }

    @Test
    void laBaseRefuseUnSecondRedevableOuResponsableEtUnContributeurSansCompte() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID first = partyId(createParty(tenant, "PERSON", "Premier", null, null));
        UUID second = partyId(createParty(tenant, "PERSON", "Second", null, null));
        insertDesignation(tenant, obligation, first, DEBTOR, "UNCONFIRMED");
        insertDesignation(tenant, obligation, first, RESPONSIBLE, null);

        assertThatThrownBy(() -> insertDesignation(tenant, obligation, second, DEBTOR, "UNCONFIRMED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("obligation_parties_one_debtor");
        assertThatThrownBy(() -> insertDesignation(tenant, obligation, second, RESPONSIBLE, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("obligation_parties_one_responsible");
        assertThatThrownBy(() -> insertDesignation(tenant, obligation, second, CONTRIBUTOR, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("disposer d'un compte");
    }

    @Test
    void laBaseImposeLEtatDuRedevableEtLaReferenceDEngagement() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID party = partyId(createParty(tenant, "PERSON", "Personne", null, null));

        assertThatThrownBy(() -> insertDesignation(tenant, obligation, party, DEBTOR, null))
                .as("Un redevable porte un état")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("obligation_parties_debtor_status");
        assertThatThrownBy(() -> insertDesignation(tenant, obligation, party, RESPONSIBLE, "UNCONFIRMED"))
                .as("Un responsable n'en porte aucun")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("obligation_parties_debtor_status");
        assertThatThrownBy(() -> insertDesignation(tenant, obligation, party, DEBTOR, "CONFIRMED"))
                .as("Une confirmation exige sa référence")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("obligation_parties_commitment");
    }

    @Test
    void laBaseGardeLesPersonnesEtLesDesignationsDansLeTemps() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        UUID party = partyId(createParty(tenant, "PERSON", "Personne", null, null));
        UUID other = partyId(createParty(tenant, "PERSON", "Autre", null, null));
        UUID designation = UUID.fromString(designate(tenant, tenant.adminToken(), obligation, party, DEBTOR).text("id"));

        assertThatThrownBy(() -> jdbc.sql("UPDATE billing_parties SET display_name = 'Réécrit' WHERE id = :id").param("id", party).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM obligation_parties WHERE id = :id").param("id", designation).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
        assertThatThrownBy(() -> jdbc.sql("UPDATE obligation_parties SET party_id = :other WHERE id = :id").param("other", other).param("id", designation).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("identité");

        assertThat(confirm(tenant, tenant.adminToken(), designation.toString(), "CONTRAT").status()).isEqualTo(200);
        assertThatThrownBy(() -> jdbc.sql("UPDATE obligation_parties SET commitment_reference = 'Autre' WHERE id = :id").param("id", designation).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("confirmation est immuable");

        assertThat(endDesignation(tenant, tenant.adminToken(), designation.toString(), "Fin").status()).isEqualTo(200);
        assertThatThrownBy(() -> jdbc.sql("UPDATE obligation_parties SET end_reason = 'Réécrit' WHERE id = :id").param("id", designation).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("terminée");
    }

    @Test
    void leLienNePorteDesormaisPlusLeDroitFinancier() {
        long columns = jdbc.sql("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'record_access_grants' AND column_name = 'financial_contributor'
                """).query(Long.class).single();

        assertThat(columns).isZero();
    }

    private UUID secondCharge(School school, UUID student) {
        Tenant tenant = school.tenant();
        UUID charge = newCharge(tenant, school.year(), school.annual(), "Charge " + UUID.randomUUID());
        UUID draft = newDraft(tenant, school.year());
        addFee(tenant, draft, fee(charge, "C" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null, 1_000));
        publish(tenant, draft);
        Map<String, Object> body = new HashMap<>();
        body.put("enrollmentId", activeEnrollmentOf(tenant, student).toString());
        body.put("feeDefinitionId", charge.toString());
        Response issued = post(tenant.path("/billing/obligations"), tenant.adminToken(), body);
        assertThat(issued.status()).as(issued.raw()).isEqualTo(201);
        return issued.id("id");
    }
}
