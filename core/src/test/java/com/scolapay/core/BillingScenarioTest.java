package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Un établissement avec un tarif publié et des obligations émises, pour les tests qui partent de là. */
abstract class BillingScenarioTest extends IntegrationTest {

    static final long AMOUNT = 100_000;

    record School(Tenant tenant, UUID year, UUID level, UUID annual, UUID charge, UUID catalog) {}

    School school() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID annual = ok(post(tenant.path("/billing/periods"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "code", "ANNEE", "name", "Année", "startsOn", "2026-09-01", "endsOn", "2027-07-15")));
        UUID charge = newCharge(tenant, year, annual, "Scolarité");
        UUID catalog = newDraft(tenant, year);
        addFee(tenant, catalog, fee(charge, "SCOLARITE", null, AMOUNT));
        publish(tenant, catalog);
        return new School(tenant, year, level, annual, charge, catalog);
    }

    static UUID ok(Response created) {
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    UUID newCharge(Tenant tenant, UUID year, UUID period, String name) {
        return ok(post(tenant.path("/billing/fee-definitions"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "billingPeriodId", period.toString(), "name", name)));
    }

    UUID newDraft(Tenant tenant, UUID year) {
        return ok(post(tenant.path("/billing/fee-catalogs"), tenant.adminToken(), Map.of("academicYearId", year.toString())));
    }

    static Map<String, Object> fee(UUID charge, String code, UUID unit, long amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("feeDefinitionId", charge.toString());
        body.put("feeCode", code);
        body.put("label", "Frais");
        body.put("category", "TUITION");
        body.put("application", "AUTOMATIC");
        body.put("amountMinor", String.valueOf(amount));
        body.put("academicUnitId", unit == null ? null : unit.toString());
        body.put("installments", List.of(Map.of("dueOn", "2026-10-01", "amountMinor", String.valueOf(amount))));
        return body;
    }

    void addFee(Tenant tenant, UUID catalog, Map<String, Object> body) {
        Response added = post(tenant.path("/billing/fee-catalogs/" + catalog + "/items"), tenant.adminToken(), body);
        assertThat(added.status()).as(added.raw()).isEqualTo(201);
    }

    void publish(Tenant tenant, UUID catalog) {
        Response published = post(tenant.path("/billing/fee-catalogs/" + catalog + "/publish"), tenant.adminToken(), Map.of());
        assertThat(published.status()).as(published.raw()).isEqualTo(200);
    }

    Response enroll(Tenant tenant, UUID student, UUID year, UUID level, UUID confirmed) {
        Map<String, Object> body = new HashMap<>();
        body.put("academicYearId", year.toString());
        body.put("academicUnitId", level.toString());
        body.put("confirmedFeeCatalogVersionId", confirmed == null ? null : confirmed.toString());
        return post(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken(), body);
    }

    UUID enrolledAndBilled(School school) throws InterruptedException {
        UUID student = newStudent(school.tenant());
        assertThat(enroll(school.tenant(), student, school.year(), school.level(), school.catalog()).status()).isEqualTo(201);
        long deadline = System.currentTimeMillis() + 30_000;
        while (obligationsOf(student) == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(obligationsOf(student)).isEqualTo(1);
        return student;
    }

    long obligationsOf(UUID student) {
        return jdbc.sql("SELECT count(*) FROM obligations WHERE student_record_id = :id").param("id", student).query(Long.class).single();
    }

    UUID obligationOf(UUID student) {
        return jdbc.sql("SELECT id FROM obligations WHERE student_record_id = :id").param("id", student).query(UUID.class).single();
    }

    UUID issuedObligation(School school) throws InterruptedException {
        return obligationOf(enrolledAndBilled(school));
    }

    UUID activeEnrollmentOf(Tenant tenant, UUID student) {
        for (JsonNode enrollment : get(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken()).body()) {
            if (enrollment.path("status").asString().equals("ACTIVE")) {
                return UUID.fromString(enrollment.path("id").asString());
            }
        }
        throw new AssertionError("Aucune inscription active");
    }

    void endEnrollment(Tenant tenant, UUID student, String status) {
        Response ended = post(tenant.path("/students/" + student + "/enrollments/" + activeEnrollmentOf(tenant, student) + "/end"), tenant.adminToken(),
                Map.of("status", status));
        assertThat(ended.status()).as(ended.raw()).isEqualTo(204);
    }

    JsonNode obligationView(Tenant tenant, UUID obligation) {
        Response detail = get(tenant.path("/billing/obligations/" + obligation), tenant.adminToken());
        assertThat(detail.status()).as(detail.raw()).isEqualTo(200);
        return detail.body().path("obligation");
    }

    JsonNode entries(Tenant tenant, UUID obligation) {
        return get(tenant.path("/billing/obligations/" + obligation), tenant.adminToken()).body().path("entries");
    }

    long journalSize(UUID obligation) {
        return jdbc.sql("SELECT count(*) FROM obligation_entries WHERE obligation_id = :id").param("id", obligation).query(Long.class).single();
    }

    static String key() {
        return UUID.randomUUID().toString();
    }

    Response discount(Tenant tenant, String token, UUID obligation, long amount) {
        return post(tenant.path("/billing/obligations/" + obligation + "/discounts"), token, Map.of(
                "amountMinor", String.valueOf(amount), "reason", "Remise accordée", "idempotencyKey", key()));
    }

    Response correction(Tenant tenant, String token, UUID obligation, long signedAmount) {
        return post(tenant.path("/billing/obligations/" + obligation + "/corrections"), token, Map.of(
                "amountMinor", String.valueOf(signedAmount), "reason", "Erreur de saisie", "idempotencyKey", key()));
    }

    Response cancellation(Tenant tenant, String token, UUID obligation) {
        return post(tenant.path("/billing/obligations/" + obligation + "/cancellation"), token, Map.of(
                "reason", "Dossier créé par erreur", "idempotencyKey", key()));
    }

    Response reversal(Tenant tenant, String token, UUID obligation, String entry) {
        return post(tenant.path("/billing/obligations/" + obligation + "/entries/" + entry + "/reversal"), token, Map.of(
                "reason", "Écriture passée par erreur", "idempotencyKey", key()));
    }

    Response approve(Tenant tenant, String token, String request) {
        return post(tenant.path("/billing/adjustment-requests/" + request + "/approval"), token, Map.of());
    }

    static String requestIdOf(Response pending) {
        assertThat(pending.status()).as(pending.raw()).isEqualTo(202);
        return pending.body().path("request").path("id").asString();
    }
}
