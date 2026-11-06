package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

class FeeCatalogTest extends IntegrationTest {

    @Autowired
    DataSource dataSource;

    private UUID period(Tenant tenant, UUID year, String code) {
        Response created = post(tenant.path("/billing/periods"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(),
                "code", code,
                "name", "Période " + code,
                "startsOn", "2026-09-01",
                "endsOn", "2027-01-31"));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    private UUID draft(Tenant tenant, UUID year) {
        Response created = post(tenant.path("/billing/fee-catalogs"), tenant.adminToken(), Map.of("academicYearId", year.toString()));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    private static Map<String, Object> fee(String code, UUID period, UUID unit, long amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("feeCode", code);
        body.put("label", "Frais " + code);
        body.put("category", "TUITION");
        body.put("application", "AUTOMATIC");
        body.put("amountMinor", String.valueOf(amount));
        body.put("billingPeriodId", period.toString());
        body.put("academicUnitId", unit == null ? null : unit.toString());
        body.put("installments", List.of(Map.of("dueOn", "2026-10-01", "amountMinor", String.valueOf(amount))));
        return body;
    }

    private Response add(Tenant tenant, UUID version, Map<String, Object> body) {
        return post(tenant.path("/billing/fee-catalogs/" + version + "/items"), tenant.adminToken(), body);
    }

    private UUID addOk(Tenant tenant, UUID version, Map<String, Object> body) {
        Response added = add(tenant, version, body);
        assertThat(added.status()).as(added.raw()).isEqualTo(201);
        return added.id("id");
    }

    private Response publish(Tenant tenant, UUID version) {
        return post(tenant.path("/billing/fee-catalogs/" + version + "/publish"), tenant.adminToken(), Map.of());
    }

    private void publishOk(Tenant tenant, UUID version) {
        Response published = publish(tenant, version);
        assertThat(published.status()).as(published.raw()).isEqualTo(200);
    }

    private Response applicable(Tenant tenant, UUID year, UUID unit) {
        return get(tenant.path("/billing/applicable-fees?academicYearId=" + year + "&academicUnitId=" + unit), tenant.adminToken());
    }

    private static List<String> codes(Response applicable) {
        List<String> codes = new ArrayList<>();
        applicable.body().path("fees").forEach(fee -> codes.add(fee.path("feeCode").asString()));
        return codes;
    }

    private static String amountOf(Response applicable, String code) {
        for (JsonNode fee : applicable.body().path("fees")) {
            if (fee.path("feeCode").asString().equals(code)) {
                return fee.path("amountMinor").asString();
            }
        }
        throw new AssertionError(code + " absent de " + applicable.raw());
    }

    private UUID program(Tenant tenant) {
        Response created = post(tenant.path("/academic-units"), tenant.adminToken(), Map.of(
                "kind", "PROGRAM", "code", uniqueUnitCode(), "name", "Licence"));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    private UUID levelUnder(Tenant tenant, UUID parent) {
        Response created = post(tenant.path("/academic-units"), tenant.adminToken(), Map.of(
                "parentId", parent.toString(), "kind", "LEVEL", "code", uniqueUnitCode(), "name", "L1"));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    private void enrollOk(Tenant tenant, UUID year, UUID unit) {
        Response enrolled = post(tenant.path("/students/" + newStudent(tenant) + "/enrollments"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "academicUnitId", unit.toString()));
        assertThat(enrolled.status()).as(enrolled.raw()).isEqualTo(201);
    }

    // Écoles et universités

    @Test
    void uneEcoleFactureUneFoisParAnEtLesMontantsSuiventLaConventionMonetaire() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("SCOLARITE", annual, level, 25_000));

        publishOk(tenant, version);

        Response fees = applicable(tenant, year, level);
        assertThat(fees.status()).as(fees.raw()).isEqualTo(200);
        assertThat(fees.text("currency")).isEqualTo("XAF");
        assertThat(codes(fees)).containsExactly("SCOLARITE");
        JsonNode first = fees.body().path("fees").get(0);
        assertThat(first.path("amountMinor").isString()).as("Chaîne décimale, décision 0012").isTrue();
        assertThat(first.path("amountMinor").asString()).isEqualTo("25000");
    }

    /** La période de facturation est indépendante de l'inscription, qui reste unique et annuelle. */
    @Test
    void uneUniversiteFactureParSemestrePourUneInscriptionAnnuelle() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = levelUnder(tenant, program(tenant));
        UUID student = newStudent(tenant);
        assertThat(post(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "academicUnitId", level.toString())).status()).isEqualTo(201);

        UUID s1 = period(tenant, year, "S1");
        UUID s2 = period(tenant, year, "S2");
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("SCOL-S1", s1, level, 150_000));
        addOk(tenant, version, fee("SCOL-S2", s2, level, 170_000));
        publishOk(tenant, version);

        Response fees = applicable(tenant, year, level);
        assertThat(codes(fees)).containsExactly("SCOL-S1", "SCOL-S2");
        assertThat(fees.body().path("fees").get(0).path("billingPeriodId").asString()).isEqualTo(s1.toString());
        assertThat(fees.body().path("fees").get(1).path("billingPeriodId").asString()).isEqualTo(s2.toString());
        assertThat(get(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken()).body().size())
                .as("Toujours une seule inscription")
                .isEqualTo(1);
    }

    @Test
    void laLigneLaPlusProcheDeLUniteLEmporteEtUneUniteRecoitUneSeuleLigneParCode() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID program = program(tenant);
        UUID l1 = levelUnder(tenant, program);
        UUID l2 = levelUnder(tenant, program);
        UUID other = newLevel(tenant);
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("SCOLARITE", annual, null, 100_000));
        addOk(tenant, version, fee("SCOLARITE", annual, program, 200_000));
        addOk(tenant, version, fee("SCOLARITE", annual, l1, 300_000));
        addOk(tenant, version, fee("ASSURANCE", annual, null, 5_000));
        publishOk(tenant, version);

        assertThat(amountOf(applicable(tenant, year, l1), "SCOLARITE")).isEqualTo("300000");
        assertThat(amountOf(applicable(tenant, year, l2), "SCOLARITE")).isEqualTo("200000");
        assertThat(amountOf(applicable(tenant, year, other), "SCOLARITE")).isEqualTo("100000");
        assertThat(codes(applicable(tenant, year, l1))).containsExactly("ASSURANCE", "SCOLARITE");
    }

    @Test
    void unFraisViseUneUniteSansToucherLesAutres() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID sixieme = newLevel(tenant);
        UUID terminale = newLevel(tenant);
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("EXAMEN", annual, terminale, 15_000));
        publishOk(tenant, version);

        assertThat(codes(applicable(tenant, year, terminale))).containsExactly("EXAMEN");
        assertThat(codes(applicable(tenant, year, sixieme))).isEmpty();
    }

    @Test
    void unFraisFacultatifResteMarqueCommeTel() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        Map<String, Object> canteen = fee("CANTINE", period(tenant, year, "ANNEE"), null, 40_000);
        canteen.put("category", "CANTEEN");
        canteen.put("application", "ON_REQUEST");
        UUID version = draft(tenant, year);
        addOk(tenant, version, canteen);
        publishOk(tenant, version);

        JsonNode fee = applicable(tenant, year, level).body().path("fees").get(0);
        assertThat(fee.path("application").asString()).isEqualTo("ON_REQUEST");
        assertThat(fee.path("category").asString()).isEqualTo("CANTEEN");
    }

    // Publication et brouillons

    @Test
    void unBrouillonNAucunEffetTantQuIlNEstPasPublie() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("SCOLARITE", period(tenant, year, "ANNEE"), level, 25_000));

        Response fees = applicable(tenant, year, level);

        assertThat(fees.status()).isEqualTo(200);
        assertThat(codes(fees)).isEmpty();
        assertThat(fees.body().path("catalogVersionId").isNull()).isTrue();
    }

    @Test
    void lesModificationsDUnNouveauBrouillonNeChangentPasLeTarifEnVigueur() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID first = draft(tenant, year);
        addOk(tenant, first, fee("SCOLARITE", annual, level, 25_000));
        publishOk(tenant, first);

        UUID second = draft(tenant, year);
        addOk(tenant, second, fee("NOUVEAU", annual, level, 1_000));

        assertThat(codes(applicable(tenant, year, level))).containsExactly("SCOLARITE");
    }

    @Test
    void unNouveauBrouillonPartDuTarifEnVigueurEtLaPublicationRemplaceSansEffacer() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID first = draft(tenant, year);
        addOk(tenant, first, fee("SCOLARITE", annual, level, 25_000));
        publishOk(tenant, first);
        String firstBefore = get(tenant.path("/billing/fee-catalogs/" + first), tenant.adminToken()).raw();

        Response second = get(tenant.path("/billing/fee-catalogs/" + draft(tenant, year)), tenant.adminToken());
        assertThat(second.body().path("version").asInt()).isEqualTo(2);
        JsonNode copied = second.body().path("items").get(0);
        assertThat(copied.path("feeCode").asString()).isEqualTo("SCOLARITE");
        assertThat(copied.path("amountMinor").asString()).isEqualTo("25000");

        Map<String, Object> raised = fee("SCOLARITE", annual, level, 30_000);
        assertThat(put(tenant.path("/billing/fee-catalogs/" + second.id("id") + "/items/" + copied.path("id").asString()), tenant.adminToken(), raised).status()).isEqualTo(200);
        publishOk(tenant, second.id("id"));

        assertThat(amountOf(applicable(tenant, year, level), "SCOLARITE")).isEqualTo("30000");
        Response firstAfter = get(tenant.path("/billing/fee-catalogs/" + first), tenant.adminToken());
        assertThat(firstAfter.text("status")).isEqualTo("SUPERSEDED");
        assertThat(firstAfter.body().path("items").toString())
                .as("L'ancien tarif reste consultable tel quel")
                .isEqualTo(new tools.jackson.databind.json.JsonMapper().readTree(firstBefore).path("items").toString());

        Response list = get(tenant.path("/billing/fee-catalogs?academicYearId=" + year), tenant.adminToken());
        assertThat(list.body().get(0).path("status").asString()).isEqualTo("PUBLISHED");
        assertThat(list.body().get(1).path("status").asString()).isEqualTo("SUPERSEDED");
    }

    @Test
    void unTarifPublieNeSeModifiePlusParLApi() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID version = draft(tenant, year);
        UUID item = addOk(tenant, version, fee("SCOLARITE", annual, null, 25_000));
        publishOk(tenant, version);
        String path = tenant.path("/billing/fee-catalogs/" + version);

        for (Response refused : List.of(
                add(tenant, version, fee("AUTRE", annual, null, 1_000)),
                put(path + "/items/" + item, tenant.adminToken(), fee("SCOLARITE", annual, null, 1)),
                delete(path + "/items/" + item, tenant.adminToken()),
                delete(path, tenant.adminToken()),
                publish(tenant, version))) {
            assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
            assertThat(refused.code()).isEqualTo("CATALOG_NOT_DRAFT");
        }
    }

    @Test
    void unBrouillonSeSupprimeEtUnSeulExisteParAnnee() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("SCOLARITE", period(tenant, year, "ANNEE"), null, 25_000));

        Response second = post(tenant.path("/billing/fee-catalogs"), tenant.adminToken(), Map.of("academicYearId", year.toString()));
        assertThat(second.status()).as(second.raw()).isEqualTo(409);

        assertThat(delete(tenant.path("/billing/fee-catalogs/" + version), tenant.adminToken()).status()).isEqualTo(204);
        assertThat(get(tenant.path("/billing/fee-catalogs/" + version), tenant.adminToken()).status()).isEqualTo(404);
        assertThat(draft(tenant, year)).isNotEqualTo(version);
    }

    @Test
    void laPublicationExigeUnCatalogueCompletEtDesReferencesActives() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID level = newLevel(tenant);
        UUID version = draft(tenant, year);

        Response empty = publish(tenant, version);
        assertThat(empty.status()).as(empty.raw()).isEqualTo(422);
        assertThat(empty.code()).isEqualTo("CATALOG_EMPTY");

        addOk(tenant, version, fee("SCOLARITE", annual, level, 25_000));
        assertThat(patch(tenant.path("/academic-units/" + level), tenant.adminToken(), Map.of("status", "ARCHIVED")).status()).isEqualTo(200);
        Response archivedUnit = publish(tenant, version);
        assertThat(archivedUnit.status()).as(archivedUnit.raw()).isEqualTo(409);
        assertThat(archivedUnit.code()).isEqualTo("CATALOG_REFERENCES_ARCHIVED");

        assertThat(patch(tenant.path("/academic-units/" + level), tenant.adminToken(), Map.of("status", "ACTIVE")).status()).isEqualTo(200);
        assertThat(patch(tenant.path("/billing/periods/" + annual), tenant.adminToken(), Map.of("status", "ARCHIVED")).status()).isEqualTo(200);
        Response archivedPeriod = publish(tenant, version);
        assertThat(archivedPeriod.status()).as(archivedPeriod.raw()).isEqualTo(409);
        assertThat(archivedPeriod.code()).isEqualTo("CATALOG_REFERENCES_ARCHIVED");
    }

    @Test
    void uneAnneeClotureeNAccepteNiPeriodeNiBrouillonNiPublication() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("SCOLARITE", annual, null, 25_000));
        assertThat(patch(tenant.path("/academic-years/" + year), tenant.adminToken(), Map.of("status", "CLOSED")).status()).isEqualTo(200);

        Response refusedPublish = publish(tenant, version);
        assertThat(refusedPublish.status()).as(refusedPublish.raw()).isEqualTo(409);
        assertThat(refusedPublish.code()).isEqualTo("ACADEMIC_YEAR_CLOSED");
        assertThat(delete(tenant.path("/billing/fee-catalogs/" + version), tenant.adminToken()).status()).isEqualTo(204);

        Response refusedDraft = post(tenant.path("/billing/fee-catalogs"), tenant.adminToken(), Map.of("academicYearId", year.toString()));
        assertThat(refusedDraft.code()).isEqualTo("ACADEMIC_YEAR_CLOSED");

        Response refusedPeriod = post(tenant.path("/billing/periods"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "code", "S1", "name", "S1", "startsOn", "2026-09-01", "endsOn", "2027-01-31"));
        assertThat(refusedPeriod.code()).isEqualTo("ACADEMIC_YEAR_CLOSED");
    }

    // Identité d'un frais : ce qui empêchera la double facturation au lot 4

    @Test
    void unCodeDeFraisResteAttacheASaPeriodeDansUneVersion() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID s1 = period(tenant, year, "S1");
        UUID s2 = period(tenant, year, "S2");
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("SCOLARITE", s1, newLevel(tenant), 150_000));

        Response refused = add(tenant, version, fee("SCOLARITE", s2, newLevel(tenant), 150_000));

        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("FEE_CODE_BOUND_TO_PERIOD");
    }

    /** Sinon, déplacer un frais vers S2 le ferait passer pour une nouvelle charge et le facturerait deux fois. */
    @Test
    void unCodeDeFraisResteAttacheASaPeriodeDUneVersionALAutre() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID s1 = period(tenant, year, "S1");
        UUID s2 = period(tenant, year, "S2");
        UUID first = draft(tenant, year);
        addOk(tenant, first, fee("SCOLARITE", s1, null, 150_000));
        publishOk(tenant, first);
        UUID second = draft(tenant, year);
        delete(tenant.path("/billing/fee-catalogs/" + second + "/items/" + get(tenant.path("/billing/fee-catalogs/" + second), tenant.adminToken())
                .body().path("items").get(0).path("id").asString()), tenant.adminToken());

        Response refused = add(tenant, second, fee("SCOLARITE", s2, null, 150_000));

        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("FEE_CODE_BOUND_TO_PERIOD");
    }

    @Test
    void unBrouillonAbandonneNeRetientPasLeCodeSurSaPeriode() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID s1 = period(tenant, year, "S1");
        UUID s2 = period(tenant, year, "S2");
        UUID abandoned = draft(tenant, year);
        addOk(tenant, abandoned, fee("SCOLARITE", s1, null, 150_000));
        assertThat(delete(tenant.path("/billing/fee-catalogs/" + abandoned), tenant.adminToken()).status()).isEqualTo(204);

        addOk(tenant, draft(tenant, year), fee("SCOLARITE", s2, null, 150_000));
    }

    @Test
    void uneMemeUniteNeRecoitPasDeuxFoisLeMemeCodeDansUneVersion() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID level = newLevel(tenant);
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("SCOLARITE", annual, level, 1_000));
        addOk(tenant, version, fee("TOUS", annual, null, 1_000));

        assertThat(add(tenant, version, fee("SCOLARITE", annual, level, 2_000)).status()).isEqualTo(409);
        assertThat(add(tenant, version, fee("TOUS", annual, null, 2_000)).status())
                .as("Y compris pour une ligne visant tout l'établissement")
                .isEqualTo(409);
    }

    // Étudiants déjà inscrits

    @Test
    void lImpactMontreCeQuiChangePourLesInscritsSansRienEcrire() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID sixieme = newLevel(tenant);
        UUID terminale = newLevel(tenant);
        enrollOk(tenant, year, sixieme);
        enrollOk(tenant, year, sixieme);
        enrollOk(tenant, year, terminale);

        UUID first = draft(tenant, year);
        addOk(tenant, first, fee("SCOLARITE", annual, null, 25_000));
        addOk(tenant, first, fee("EXAMEN", annual, terminale, 15_000));
        addOk(tenant, first, fee("RETIRE", annual, null, 1_000));
        Response firstImpact = get(tenant.path("/billing/fee-catalogs/" + first + "/impact"), tenant.adminToken());
        assertThat(firstImpact.status()).as(firstImpact.raw()).isEqualTo(200);
        assertThat(firstImpact.body().path("activeEnrollments").asLong()).isEqualTo(3);
        assertThat(impactOf(firstImpact, "SCOLARITE").path("change").asString()).isEqualTo("NEW");
        assertThat(impactOf(firstImpact, "SCOLARITE").path("eligibleEnrollments").asLong()).isEqualTo(3);
        assertThat(impactOf(firstImpact, "EXAMEN").path("eligibleEnrollments").asLong()).isEqualTo(1);
        publishOk(tenant, first);

        Response draftV2 = get(tenant.path("/billing/fee-catalogs/" + draft(tenant, year)), tenant.adminToken());
        UUID second = draftV2.id("id");
        for (JsonNode item : draftV2.body().path("items")) {
            String code = item.path("feeCode").asString();
            if (code.equals("RETIRE")) {
                assertThat(delete(tenant.path("/billing/fee-catalogs/" + second + "/items/" + item.path("id").asString()), tenant.adminToken()).status()).isEqualTo(204);
            }
            if (code.equals("SCOLARITE")) {
                assertThat(put(tenant.path("/billing/fee-catalogs/" + second + "/items/" + item.path("id").asString()), tenant.adminToken(), fee("SCOLARITE", annual, null, 30_000)).status()).isEqualTo(200);
            }
        }
        addOk(tenant, second, fee("NOUVEAU", annual, sixieme, 2_000));

        Response impact = get(tenant.path("/billing/fee-catalogs/" + second + "/impact"), tenant.adminToken());

        assertThat(impact.body().path("previousVersion").asInt()).isEqualTo(1);
        assertThat(impactOf(impact, "SCOLARITE").path("change").asString()).isEqualTo("CHANGED");
        assertThat(impactOf(impact, "EXAMEN").path("change").asString()).isEqualTo("UNCHANGED");
        assertThat(impactOf(impact, "RETIRE").path("change").asString()).isEqualTo("REMOVED");
        assertThat(impactOf(impact, "NOUVEAU").path("change").asString()).isEqualTo("NEW");
        assertThat(impactOf(impact, "NOUVEAU").path("eligibleEnrollments").asLong()).isEqualTo(2);
    }

    private static JsonNode impactOf(Response impact, String code) {
        for (JsonNode fee : impact.body().path("fees")) {
            if (fee.path("feeCode").asString().equals(code)) {
                return fee;
            }
        }
        throw new AssertionError(code + " absent de " + impact.raw());
    }

    /** La publication fixe un prix. Émettre une dette est un acte distinct, qui n'existe pas encore. */
    @Test
    void publierPourDesElevesDejaInscritsNeCreeRienEtNePublieAucunEvenement() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        enrollOk(tenant, year, level);
        int eventsBefore = events.published.size();
        List<String> tablesBefore = tables();
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("SCOLARITE", period(tenant, year, "ANNEE"), level, 25_000));

        publishOk(tenant, version);

        assertThat(events.published.size()).isEqualTo(eventsBefore);
        assertThat(jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single()).isZero();
        assertThat(tables()).as("Aucune table de dette n'existe à ce stade").isEqualTo(tablesBefore).noneMatch(table -> table.contains("obligation"));
    }

    private List<String> tables() {
        return jdbc.sql("SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename").query(String.class).list();
    }

    // Validation des montants

    @Test
    void lesEcheancesDoiventCouvrirExactementLeMontantDansLOrdre() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID version = draft(tenant, year);

        Map<String, Object> mismatch = fee("A", annual, null, 100);
        mismatch.put("installments", List.of(Map.of("dueOn", "2026-10-01", "amountMinor", "99")));
        Response wrongSum = add(tenant, version, mismatch);
        assertThat(wrongSum.status()).as(wrongSum.raw()).isEqualTo(422);
        assertThat(wrongSum.code()).isEqualTo("INSTALLMENTS_MISMATCH");

        Map<String, Object> unordered = fee("B", annual, null, 100);
        unordered.put("installments", List.of(
                Map.of("dueOn", "2026-11-01", "amountMinor", "50"),
                Map.of("dueOn", "2026-10-01", "amountMinor", "50")));
        Response wrongOrder = add(tenant, version, unordered);
        assertThat(wrongOrder.status()).as(wrongOrder.raw()).isEqualTo(422);
        assertThat(wrongOrder.code()).isEqualTo("INSTALLMENTS_NOT_ORDERED");

        Map<String, Object> split = fee("C", annual, null, 100_000);
        split.put("installments", List.of(
                Map.of("dueOn", "2026-10-01", "amountMinor", "33334"),
                Map.of("dueOn", "2026-11-01", "amountMinor", "33333"),
                Map.of("dueOn", "2026-12-01", "amountMinor", "33333")));
        addOk(tenant, version, split);
    }

    @Test
    void unMontantInvalideEstRefuse() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID version = draft(tenant, year);

        for (String amount : List.of("0", "-5", "12.50", "1e3", "abc", "1000000000000000")) {
            Map<String, Object> body = fee("A", annual, null, 1);
            body.put("amountMinor", amount);
            assertThat(add(tenant, version, body).status()).as(amount).isEqualTo(400);
        }
        Map<String, Object> noInstallment = fee("A", annual, null, 1);
        noInstallment.put("installments", List.of());
        assertThat(add(tenant, version, noInstallment).status()).isEqualTo(400);
    }

    @Test
    void uneReferenceInconnueOuArchiveeEstRefuseeALaSaisie() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID otherYear = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID version = draft(tenant, year);

        Response periodOfAnotherYear = add(tenant, version, fee("A", period(tenant, otherYear, "ANNEE"), null, 1));
        assertThat(periodOfAnotherYear.status()).as(periodOfAnotherYear.raw()).isEqualTo(404);
        assertThat(periodOfAnotherYear.code()).isEqualTo("BILLING_PERIOD_NOT_FOUND");

        assertThat(add(tenant, version, fee("A", annual, UUID.randomUUID(), 1)).code()).isEqualTo("ACADEMIC_UNIT_NOT_FOUND");

        UUID archived = newLevel(tenant);
        patch(tenant.path("/academic-units/" + archived), tenant.adminToken(), Map.of("status", "ARCHIVED"));
        assertThat(add(tenant, version, fee("A", annual, archived, 1)).code()).isEqualTo("ACADEMIC_UNIT_ARCHIVED");

        UUID closedPeriod = period(tenant, year, "FERMEE");
        patch(tenant.path("/billing/periods/" + closedPeriod), tenant.adminToken(), Map.of("status", "ARCHIVED"));
        assertThat(add(tenant, version, fee("A", closedPeriod, null, 1)).code()).isEqualTo("BILLING_PERIOD_ARCHIVED");
    }

    // Permissions et audit

    @Test
    void leComptablePrepareLAdministrateurPublieEtLesAutresLisent() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        String accountant = newStaff(tenant, "ACCOUNTANT");
        String registrar = newStaff(tenant, "REGISTRAR");
        String cashier = newStaff(tenant, "CASHIER");

        Map<String, Object> newPeriod = Map.of(
                "academicYearId", year.toString(), "code", "ANNEE", "name", "Année", "startsOn", "2026-09-01", "endsOn", "2027-07-15");
        assertThat(post(tenant.path("/billing/periods"), registrar, newPeriod).status()).isEqualTo(403);
        Response created = post(tenant.path("/billing/periods"), accountant, newPeriod);
        assertThat(created.status()).as(created.raw()).isEqualTo(201);

        Map<String, Object> newDraft = Map.of("academicYearId", year.toString());
        assertThat(post(tenant.path("/billing/fee-catalogs"), cashier, newDraft).status()).isEqualTo(403);
        Response draft = post(tenant.path("/billing/fee-catalogs"), accountant, newDraft);
        assertThat(draft.status()).as(draft.raw()).isEqualTo(201);
        String path = tenant.path("/billing/fee-catalogs/" + draft.text("id"));

        assertThat(post(path + "/items", registrar, fee("A", created.id("id"), null, 1)).status()).isEqualTo(403);
        assertThat(post(path + "/items", accountant, fee("A", created.id("id"), null, 1)).status()).isEqualTo(201);

        assertThat(post(path + "/publish", accountant, Map.of()).status()).as("Le comptable prépare, il ne publie pas").isEqualTo(403);
        assertThat(post(path + "/publish", registrar, Map.of()).status()).isEqualTo(403);
        assertThat(post(path + "/publish", tenant.adminToken(), Map.of()).status()).isEqualTo(200);

        assertThat(get(path, cashier).status()).isEqualTo(200);
        assertThat(get(path + "/impact", cashier).status()).isEqualTo(403);
        assertThat(get(path + "/impact", accountant).status()).isEqualTo(200);
    }

    @Test
    void chaqueEtapeDuCatalogueEstAuditee() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID version = draft(tenant, year);
        UUID annual = period(tenant, year, "ANNEE");
        UUID item = addOk(tenant, version, fee("A", annual, null, 1));
        assertThat(put(tenant.path("/billing/fee-catalogs/" + version + "/items/" + item), tenant.adminToken(), fee("A", annual, null, 2)).status()).isEqualTo(200);
        addOk(tenant, version, fee("B", annual, null, 1));
        publishOk(tenant, version);

        List<String> actions = jdbc.sql("SELECT action FROM audit_events WHERE establishment_id = :id AND action LIKE 'billing.%'")
                .param("id", tenant.id())
                .query(String.class)
                .list();

        assertThat(actions).contains(
                "billing.period.created",
                "billing.catalog.draft.created",
                "billing.catalog.item.added",
                "billing.catalog.item.updated",
                "billing.catalog.published");
    }

    // Isolation

    @Test
    void unEtablissementNAccedeNiAuxPeriodesNiAuxCataloguesNiAuxUnitesDUnAutre() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID yearA = openYear(a);
        UUID yearB = openYear(b);
        UUID periodA = period(a, yearA, "ANNEE");
        UUID periodB = period(b, yearB, "ANNEE");
        UUID levelA = newLevel(a);
        UUID versionA = draft(a, yearA);
        UUID itemA = addOk(a, versionA, fee("A", periodA, null, 1));
        UUID versionB = draft(b, yearB);
        String token = b.adminToken();

        assertThat(get(b.path("/billing/fee-catalogs/" + versionA), token).status()).isEqualTo(404);
        assertThat(post(b.path("/billing/fee-catalogs/" + versionA + "/publish"), token, Map.of()).status()).isEqualTo(404);
        assertThat(post(b.path("/billing/fee-catalogs/" + versionA + "/items"), token, fee("B", periodB, null, 1)).status()).isEqualTo(404);
        assertThat(delete(b.path("/billing/fee-catalogs/" + versionB + "/items/" + itemA), token).status()).isEqualTo(404);
        assertThat(patch(b.path("/billing/periods/" + periodA), token, Map.of("name", "Volée")).status()).isEqualTo(404);
        assertThat(post(b.path("/billing/fee-catalogs"), token, Map.of("academicYearId", yearA.toString())).status()).isEqualTo(404);

        assertThat(add(b, versionB, fee("B", periodA, null, 1)).code()).isEqualTo("BILLING_PERIOD_NOT_FOUND");
        assertThat(add(b, versionB, fee("B", periodB, levelA, 1)).code()).isEqualTo("ACADEMIC_UNIT_NOT_FOUND");
        assertThat(get(b.path("/billing/periods?academicYearId=" + yearA), token).body().size()).isZero();
        assertThat(get(b.path("/billing/fee-catalogs?academicYearId=" + yearA), token).body().size()).isZero();
        assertThat(applicable(b, yearA, levelA).status()).isEqualTo(404);
    }

    // Garanties portées par PostgreSQL : elles tiennent même si le code applicatif se trompe.

    private void assertRefused(String sql, UUID id, String message) {
        assertThatThrownBy(() -> jdbc.sql(sql).param("id", id).update())
                .as(sql)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(message);
    }

    private UUID accountOf(Tenant tenant) {
        return get("/api/v2/me", tenant.adminToken()).id("id");
    }

    @Test
    void laBaseRefuseToutEcritureSurUnTarifPublie() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID version = draft(tenant, year);
        UUID item = addOk(tenant, version, fee("A", period(tenant, year, "ANNEE"), null, 100));
        publishOk(tenant, version);

        assertRefused("UPDATE fee_items SET amount_minor = 1 WHERE id = :id", item, "immuable");
        assertRefused("UPDATE fee_items SET label = 'Autre' WHERE id = :id", item, "immuable");
        assertRefused("DELETE FROM fee_items WHERE id = :id", item, "immuable");
        assertRefused("UPDATE fee_item_installments SET amount_minor = 1 WHERE fee_item_id = :id", item, "immuable");
        assertRefused("DELETE FROM fee_item_installments WHERE fee_item_id = :id", item, "immuable");
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO fee_items (id, establishment_id, academic_year_id, catalog_version_id, fee_code, label, category, application, amount_minor, billing_period_id)
                SELECT gen_random_uuid(), establishment_id, academic_year_id, catalog_version_id, 'INTRUS', label, category, application, 1, billing_period_id
                FROM fee_items WHERE id = :id
                """).param("id", item).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
    }

    /**
     * Le service verrouille déjà la version : ce test passe directement par PostgreSQL pour prouver
     * que la base attend seule la fin d'une publication, puis refuse l'écriture. Sans ce verrou, la
     * ligne entrerait dans un tarif en train de se figer.
     */
    @Test
    void laBaseFaitAttendreUneEcriturePendantUnePublicationPuisLaRefuse() throws Exception {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID version = draft(tenant, year);
        UUID item = addOk(tenant, version, fee("A", period(tenant, year, "ANNEE"), null, 100));
        UUID actor = accountOf(tenant);

        try (java.sql.Connection publisher = dataSource.getConnection();
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            publisher.setAutoCommit(false);
            try (java.sql.PreparedStatement publish = publisher.prepareStatement(
                    "UPDATE fee_catalog_versions SET status = 'PUBLISHED', published_at = now(), published_by = ? WHERE id = ?")) {
                publish.setObject(1, actor);
                publish.setObject(2, version);
                assertThat(publish.executeUpdate()).isEqualTo(1);
            }

            java.util.concurrent.Future<Integer> intruder = executor.submit(() -> jdbc.sql("""
                    INSERT INTO fee_items (id, establishment_id, academic_year_id, catalog_version_id, fee_code, label, category, application, amount_minor, billing_period_id)
                    SELECT gen_random_uuid(), establishment_id, academic_year_id, catalog_version_id, 'INTRUS', label, category, application, 1, billing_period_id
                    FROM fee_items WHERE id = :id
                    """).param("id", item).update());

            Thread.sleep(500);
            assertThat(intruder.isDone()).as("L'écriture attend la fin de la publication").isFalse();
            publisher.commit();

            assertThatThrownBy(intruder::get).hasMessageContaining("immuable");
        }
        assertThat(jdbc.sql("SELECT count(*) FROM fee_items WHERE catalog_version_id = :id").param("id", version).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void laBaseFigeLeCycleDeVieDUneVersion() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("A", period(tenant, year, "ANNEE"), null, 100));
        publishOk(tenant, version);

        assertRefused("DELETE FROM fee_catalog_versions WHERE id = :id", version, "ne se supprime pas");
        assertRefused("UPDATE fee_catalog_versions SET status = 'DRAFT', published_at = NULL, published_by = NULL WHERE id = :id", version, "Transition");
        assertRefused("UPDATE fee_catalog_versions SET version = 99 WHERE id = :id", version, "immuable");
        assertRefused("UPDATE fee_catalog_versions SET currency = 'EUR' WHERE id = :id", version, "immuable");
        assertRefused("UPDATE fee_catalog_versions SET published_at = now() - interval '1 year' WHERE id = :id", version, "Transition");
    }

    @Test
    void laBaseRefuseDePublierUnCatalogueInvalide() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID s2 = period(tenant, year, "S2");
        UUID actor = accountOf(tenant);
        String publishSql = "UPDATE fee_catalog_versions SET status = 'PUBLISHED', published_at = now(), published_by = '" + actor + "' WHERE id = :id";

        UUID empty = draft(tenant, year);
        assertRefused(publishSql, empty, "vide");

        UUID item = addOk(tenant, empty, fee("A", annual, null, 100));
        jdbc.sql("UPDATE fee_item_installments SET amount_minor = 99 WHERE fee_item_id = :id").param("id", item).update();
        assertRefused(publishSql, empty, "somme des échéances");
        jdbc.sql("UPDATE fee_item_installments SET amount_minor = 100 WHERE fee_item_id = :id").param("id", item).update();

        publishOk(tenant, empty);

        UUID next = draft(tenant, year);
        jdbc.sql("UPDATE fee_items SET billing_period_id = :period WHERE catalog_version_id = :version")
                .param("period", s2)
                .param("version", next)
                .update();
        jdbc.sql("UPDATE fee_catalog_versions SET status = 'SUPERSEDED', superseded_at = now() WHERE id = :id").param("id", empty).update();
        assertRefused(publishSql, next, "une seule période");
    }

    @Test
    void laBaseRefuseDeuxVersionsPublieesOuDeuxBrouillons() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID actor = accountOf(tenant);
        UUID first = draft(tenant, year);
        addOk(tenant, first, fee("A", period(tenant, year, "ANNEE"), null, 100));
        publishOk(tenant, first);
        UUID second = draft(tenant, year);

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO fee_catalog_versions (id, establishment_id, academic_year_id, version, status, currency, created_at, created_by)
                VALUES (gen_random_uuid(), :establishment, :year, 50, 'DRAFT', 'XAF', now(), :actor)
                """)
                .param("establishment", tenant.id())
                .param("year", year)
                .param("actor", actor)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fee_catalog_versions_one_draft");

        assertThatThrownBy(() -> jdbc.sql("UPDATE fee_catalog_versions SET status = 'PUBLISHED', published_at = now(), published_by = :actor WHERE id = :id")
                .param("actor", actor)
                .param("id", second)
                .update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void laBaseRefuseUnePeriodeOuUneUniteDUnAutreEtablissementEtUnePeriodeDUneAutreAnnee() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID yearA = openYear(a);
        UUID otherYearA = openYear(a);
        UUID periodA = period(a, yearA, "ANNEE");
        UUID periodOtherYear = period(a, otherYearA, "ANNEE");
        UUID levelB = newLevel(b);
        UUID version = draft(a, yearA);
        String insert = """
                INSERT INTO fee_items (id, establishment_id, academic_year_id, catalog_version_id, fee_code, label, category, application, amount_minor, billing_period_id, academic_unit_id)
                VALUES (gen_random_uuid(), :establishment, :year, :version, :code, 'L', 'OTHER', 'AUTOMATIC', 1, :period, :unit)
                """;

        assertThatThrownBy(() -> jdbc.sql(insert)
                .param("establishment", a.id()).param("year", yearA).param("version", version).param("code", "X")
                .param("period", periodOtherYear).param("unit", null)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("billing_perio");

        assertThatThrownBy(() -> jdbc.sql(insert)
                .param("establishment", a.id()).param("year", yearA).param("version", version).param("code", "Y")
                .param("period", periodA).param("unit", levelB)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("academic_unit");

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO billing_periods (id, establishment_id, academic_year_id, code, name, starts_on, ends_on, created_at, updated_at)
                VALUES (gen_random_uuid(), :establishment, :year, 'X', 'X', '2026-09-01', '2027-01-31', now(), now())
                """)
                .param("establishment", b.id())
                .param("year", yearA)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseFigeLesDatesEtLIdentiteDUnePeriode() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID period = period(tenant, year, "S1");

        assertRefused("UPDATE billing_periods SET starts_on = '2030-01-01', ends_on = '2030-02-01' WHERE id = :id", period, "immuables");
        assertRefused("UPDATE billing_periods SET code = 'AUTRE' WHERE id = :id", period, "immuables");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM billing_periods WHERE id = :id").param("id", period).update())
                .isInstanceOf(DataAccessException.class);
    }

    // Concurrence

    @Test
    void uneSeulePublicationAboutitEnConcurrence() throws Exception {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID version = draft(tenant, year);
        addOk(tenant, version, fee("A", period(tenant, year, "ANNEE"), null, 100));

        List<Response> responses = concurrently(8, () -> publish(tenant, version));

        assertThat(responses.stream().filter(response -> response.status() == 200).count()).as(responses.toString()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.status() == 409).count()).as(responses.toString()).isEqualTo(7);
    }

    @Test
    void unSeulBrouillonNaitEnConcurrence() throws Exception {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);

        List<Response> responses = concurrently(8, () -> post(tenant.path("/billing/fee-catalogs"), tenant.adminToken(), Map.of("academicYearId", year.toString())));

        assertThat(responses.stream().filter(response -> response.status() == 201).count()).as(responses.toString()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.status() == 409).count()).as(responses.toString()).isEqualTo(7);
    }

    /** Aucune ligne ne peut se glisser dans un tarif au moment où il se fige. */
    @Test
    void uneLigneAjouteePendantLaPublicationNeModifiePasLeTarifFige() throws Exception {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        for (int round = 0; round < 10; round++) {
            UUID version = draft(tenant, year);
            addOk(tenant, version, fee("BASE-" + round, annual, null, 100));
            int currentRound = round;
            java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);

            List<Response> responses = concurrently(2, () -> first.getAndSet(false)
                    ? publish(tenant, version)
                    : add(tenant, version, fee("TARDIF-" + currentRound, annual, null, 100)));

            Response published = responses.stream().filter(response -> response.body().has("items")).findFirst().orElseThrow();
            assertThat(published.status()).as(responses.toString()).isEqualTo(200);
            Response after = get(tenant.path("/billing/fee-catalogs/" + version), tenant.adminToken());
            assertThat(after.body().path("items").size())
                    .as("La réponse de publication et l'état final décrivent le même tarif")
                    .isEqualTo(published.body().path("items").size());
        }
    }
}
