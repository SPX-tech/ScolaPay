package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scolapay.core.schooling.StudentEnrolled;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

class ObligationTest extends IntegrationTest {

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    DataSource dataSource;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    /** Un établissement, une année, un niveau et un tarif publié d'une charge automatique. */
    private record School(Tenant tenant, UUID year, UUID level, UUID annual, UUID charge, UUID catalog) {}

    private School school() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID charge = charge(tenant, year, annual, "Scolarité");
        UUID catalog = draft(tenant, year);
        add(tenant, catalog, fee(charge, "SCOLARITE", null, 25_000, "AUTOMATIC"));
        publish(tenant, catalog);
        return new School(tenant, year, level, annual, charge, catalog);
    }

    private UUID period(Tenant tenant, UUID year, String code) {
        Response created = post(tenant.path("/billing/periods"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "code", code, "name", code, "startsOn", "2026-09-01", "endsOn", "2027-07-15"));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    private UUID charge(Tenant tenant, UUID year, UUID period, String name) {
        Response created = post(tenant.path("/billing/fee-definitions"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "billingPeriodId", period.toString(), "name", name));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    private UUID draft(Tenant tenant, UUID year) {
        Response created = post(tenant.path("/billing/fee-catalogs"), tenant.adminToken(), Map.of("academicYearId", year.toString()));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    /** Deux échéances : la seconde reçoit le reste d'une division qui ne tombe pas juste. */
    private static Map<String, Object> fee(UUID charge, String code, UUID unit, long amount, String application) {
        long first = amount / 2;
        Map<String, Object> body = new HashMap<>();
        body.put("feeDefinitionId", charge.toString());
        body.put("feeCode", code);
        body.put("label", "Frais");
        body.put("category", "TUITION");
        body.put("application", application);
        body.put("amountMinor", String.valueOf(amount));
        body.put("academicUnitId", unit == null ? null : unit.toString());
        body.put("installments", List.of(
                Map.of("dueOn", "2026-10-01", "amountMinor", String.valueOf(first)),
                Map.of("dueOn", "2099-01-15", "amountMinor", String.valueOf(amount - first))));
        return body;
    }

    private void add(Tenant tenant, UUID catalog, Map<String, Object> body) {
        Response added = post(tenant.path("/billing/fee-catalogs/" + catalog + "/items"), tenant.adminToken(), body);
        assertThat(added.status()).as(added.raw()).isEqualTo(201);
    }

    private void publish(Tenant tenant, UUID catalog) {
        Response published = post(tenant.path("/billing/fee-catalogs/" + catalog + "/publish"), tenant.adminToken(), Map.of());
        assertThat(published.status()).as(published.raw()).isEqualTo(200);
    }

    private Response enroll(Tenant tenant, UUID student, UUID year, UUID level, UUID confirmed) {
        Map<String, Object> body = new HashMap<>();
        body.put("academicYearId", year.toString());
        body.put("academicUnitId", level.toString());
        body.put("confirmedFeeCatalogVersionId", confirmed == null ? null : confirmed.toString());
        return post(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken(), body);
    }

    private UUID enrolled(School school, UUID confirmed) {
        UUID student = newStudent(school.tenant());
        Response response = enroll(school.tenant(), student, school.year(), school.level(), confirmed);
        assertThat(response.status()).as(response.raw()).isEqualTo(201);
        return student;
    }

    private long obligationsOf(UUID student) {
        return jdbc.sql("SELECT count(*) FROM obligations WHERE student_record_id = :id").param("id", student).query(Long.class).single();
    }

    private void awaitObligations(UUID student, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (obligationsOf(student) != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(obligationsOf(student)).isEqualTo(expected);
    }

    /** Barrière : plus aucune publication en attente, donc le listener a fini, y compris s'il n'a rien créé. */
    private void awaitOutboxDrained() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (pendingPublications() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(pendingPublications()).isZero();
    }

    private long pendingPublications() {
        return jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single();
    }

    private Response issuancePreview(Tenant tenant, UUID year) {
        Response preview = get(tenant.path("/billing/issuance-preview?academicYearId=" + year), tenant.adminToken());
        assertThat(preview.status()).as(preview.raw()).isEqualTo(200);
        return preview;
    }

    private Response confirm(Tenant tenant, UUID year, Response preview) {
        return post(tenant.path("/billing/issuance-runs"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(),
                "catalogVersionId", preview.text("catalogVersionId"),
                "previewToken", preview.text("previewToken")));
    }

    private JsonNode theOnlyObligation(Tenant tenant, UUID year, UUID student) {
        Response list = get(tenant.path("/billing/obligations?academicYearId=" + year + "&studentId=" + student), tenant.adminToken());
        assertThat(list.status()).as(list.raw()).isEqualTo(200);
        assertThat(list.body().size()).as(list.raw()).isEqualTo(1);
        return list.body().get(0);
    }

    private UUID accountOf(Tenant tenant) {
        return get("/api/v2/me", tenant.adminToken()).id("id");
    }

    private UUID enrollmentOf(Tenant tenant, UUID student) {
        return UUID.fromString(get(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken()).body().get(0).path("id").asString());
    }

    // Nouvelles inscriptions : aucune dette inattendue

    @Test
    void lapercuMontreCeQuiSeraGenereSansRienCreer() throws Exception {
        School school = school();

        Response preview = get(school.tenant().path("/billing/enrollment-preview?academicYearId=" + school.year() + "&academicUnitId=" + school.level()),
                school.tenant().adminToken());

        assertThat(preview.status()).as(preview.raw()).isEqualTo(200);
        assertThat(preview.id("catalogVersionId")).isEqualTo(school.catalog());
        assertThat(preview.text("currency")).isEqualTo("XAF");
        assertThat(preview.text("totalToIssueMinor")).isEqualTo("25000");
        assertThat(preview.body().path("fees").get(0).path("fee").path("feeCode").asString()).isEqualTo("SCOLARITE");
        assertThat(jdbc.sql("SELECT count(*) FROM obligations WHERE establishment_id = :id").param("id", school.tenant().id()).query(Long.class).single()).isZero();
    }

    @Test
    void lapercuIndiqueCeQuiEstDejaEmisPourUnEleve() throws Exception {
        School school = school();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);

        Response preview = get(school.tenant().path("/billing/enrollment-preview?academicYearId=" + school.year()
                + "&academicUnitId=" + school.level() + "&studentId=" + student), school.tenant().adminToken());

        assertThat(preview.body().path("fees").get(0).path("alreadyIssued").asBoolean()).isTrue();
        assertThat(preview.text("totalToIssueMinor")).isEqualTo("0");
    }

    @Test
    void uneInscriptionSansConfirmationNeCreeAucuneDette() throws Exception {
        School school = school();
        UUID student = enrolled(school, null);

        awaitOutboxDrained();

        assertThat(obligationsOf(student)).isZero();
        assertThat(issuancePreview(school.tenant(), school.year()).body().path("studentsConcerned").asLong())
                .as("L'élève reste visible, rien n'est perdu")
                .isEqualTo(1);
    }

    @Test
    void uneInscriptionConfirmeeEmetLeTarifVuAvecSonEcheancierEtSonJournal() throws Exception {
        School school = school();
        UUID student = enrolled(school, school.catalog());

        awaitObligations(student, 1);

        JsonNode listed = theOnlyObligation(school.tenant(), school.year(), student);
        assertThat(listed.path("reference").asString()).matches("OBL-[0-9A-HJKMNP-TV-Z]{10}");
        assertThat(listed.path("issuedAmountMinor").isString()).as("Chaîne décimale, décision 0012").isTrue();
        assertThat(listed.path("issuedAmountMinor").asString()).isEqualTo("25000");
        assertThat(listed.path("dueMinor").asString()).isEqualTo("25000");
        assertThat(listed.path("currency").asString()).isEqualTo("XAF");
        assertThat(listed.path("status").asString()).isEqualTo("OPEN");
        assertThat(listed.path("source").asString()).isEqualTo("ENROLLMENT");
        assertThat(listed.path("feeDefinitionId").asString()).isEqualTo(school.charge().toString());
        assertThat(listed.path("billingPeriodId").asString()).isEqualTo(school.annual().toString());

        Response detail = get(school.tenant().path("/billing/obligations/" + listed.path("id").asString()), school.tenant().adminToken());
        assertThat(detail.status()).as(detail.raw()).isEqualTo(200);
        JsonNode installments = detail.body().path("installments");
        assertThat(installments.size()).isEqualTo(2);
        assertThat(installments.get(0).path("amountMinor").asString()).isEqualTo("12500");
        assertThat(installments.get(1).path("amountMinor").asString()).isEqualTo("12500");
        JsonNode entries = detail.body().path("entries");
        assertThat(entries.size()).isEqualTo(1);
        assertThat(entries.get(0).path("kind").asString()).isEqualTo("ISSUED");
        assertThat(entries.get(0).path("amountMinor").asString()).isEqualTo("25000");
    }

    @Test
    void uneConfirmationPerimeeRefuseLInscriptionPlutotQueDEmettreUnAutreTarif() throws Exception {
        School school = school();
        UUID year = school.year();
        Tenant tenant = school.tenant();
        UUID next = draft(tenant, year);
        publish(tenant, next);

        UUID student = newStudent(tenant);
        Response outdated = enroll(tenant, student, year, school.level(), school.catalog());

        assertThat(outdated.status()).as(outdated.raw()).isEqualTo(409);
        assertThat(outdated.code()).isEqualTo("FEES_PREVIEW_OUTDATED");
        assertThat(get(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken()).body().size())
                .as("L'inscription est annulée avec la confirmation")
                .isZero();
    }

    @Test
    void onNeConfirmeNiUnBrouillonNiLeTarifDUneAutreAnneeNiCeluiDUnAutreEtablissement() {
        School school = school();
        Tenant tenant = school.tenant();
        UUID draft = draft(tenant, school.year());
        assertThat(enroll(tenant, newStudent(tenant), school.year(), school.level(), draft).code()).isEqualTo("FEES_PREVIEW_OUTDATED");

        School other = school();
        assertThat(enroll(tenant, newStudent(tenant), school.year(), school.level(), other.catalog()).code()).isEqualTo("FEES_PREVIEW_OUTDATED");

        UUID otherYear = openYear(tenant);
        UUID otherCatalog = draft(tenant, otherYear);
        add(tenant, otherCatalog, fee(charge(tenant, otherYear, period(tenant, otherYear, "ANNEE"), "Autre"), "AUTRE", null, 1_000, "AUTOMATIC"));
        publish(tenant, otherCatalog);
        assertThat(enroll(tenant, newStudent(tenant), school.year(), school.level(), otherCatalog).code()).isEqualTo("FEES_PREVIEW_OUTDATED");
    }

    @Test
    void unTarifPublieApresLaConfirmationNeChangePasCeQuiAEteVu() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);

        UUID next = draft(tenant, school.year());
        String item = get(tenant.path("/billing/fee-catalogs/" + next), tenant.adminToken()).body().path("items").get(0).path("id").asString();
        assertThat(put(tenant.path("/billing/fee-catalogs/" + next + "/items/" + item), tenant.adminToken(),
                fee(school.charge(), "SCOLARITE", null, 99_000, "AUTOMATIC")).status()).isEqualTo(200);
        publish(tenant, next);

        assertThat(theOnlyObligation(tenant, school.year(), student).path("issuedAmountMinor").asString())
                .as("L'obligation garde le montant du moment")
                .isEqualTo("25000");
    }

    // Émission en lot

    @Test
    void lesElevesInscritsAvantLaPublicationSontFacturesParUnLotConfirmeApresApercu() throws Exception {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID first = newStudent(tenant);
        UUID second = newStudent(tenant);
        assertThat(enroll(tenant, first, year, level, null).status()).isEqualTo(201);
        assertThat(enroll(tenant, second, year, level, null).status()).isEqualTo(201);

        UUID charge = charge(tenant, year, period(tenant, year, "ANNEE"), "Scolarité");
        UUID catalog = draft(tenant, year);
        add(tenant, catalog, fee(charge, "SCOLARITE", null, 25_001, "AUTOMATIC"));
        publish(tenant, catalog);

        awaitOutboxDrained();
        assertThat(obligationsOf(first) + obligationsOf(second)).as("Publier ne facture personne").isZero();

        Response preview = issuancePreview(tenant, year);
        assertThat(preview.body().path("activeEnrollments").asLong()).isEqualTo(2);
        assertThat(preview.body().path("studentsConcerned").asLong()).isEqualTo(2);
        assertThat(preview.body().path("obligationsToCreate").asLong()).isEqualTo(2);
        assertThat(preview.text("totalMinor")).isEqualTo("50002");
        assertThat(preview.body().path("fees").get(0).path("totalMinor").asString()).isEqualTo("50002");
        assertThat(obligationsOf(first)).as("L'aperçu n'écrit rien").isZero();

        Response run = confirm(tenant, year, preview);

        assertThat(run.status()).as(run.raw()).isEqualTo(201);
        assertThat(run.body().path("obligationsCreated").asInt()).isEqualTo(2);
        assertThat(run.text("totalMinor")).isEqualTo("50002");
        JsonNode obligation = theOnlyObligation(tenant, year, first);
        assertThat(obligation.path("source").asString()).isEqualTo("BATCH");
        assertThat(obligation.path("issuedAmountMinor").asString()).isEqualTo("25001");
        Response detail = get(tenant.path("/billing/obligations/" + obligation.path("id").asString()), tenant.adminToken());
        assertThat(detail.body().path("installments").get(0).path("amountMinor").asString()).isEqualTo("12500");
        assertThat(detail.body().path("installments").get(1).path("amountMinor").asString()).as("Le reste de la division n'est pas perdu").isEqualTo("12501");
        assertThat(get(tenant.path("/billing/issuance-runs?academicYearId=" + year), tenant.adminToken()).body().size()).isEqualTo(1);
    }

    @Test
    void unLotConfirmeSurUnApercuPerimeNEmetRien() {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, null);
        Response preview = issuancePreview(tenant, school.year());

        UUID newcomer = enrolled(school, null);
        Response refused = confirm(tenant, school.year(), preview);

        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("ISSUANCE_PREVIEW_OUTDATED");
        assertThat(obligationsOf(student) + obligationsOf(newcomer)).isZero();
    }

    /** Même effectif, mais plus les mêmes personnes : ce n'est plus ce que le personnel a confirmé. */
    @Test
    void unLotConfirmeApresUnRemplacementDEleveAEffectifConstantNEmetRien() {
        School school = school();
        Tenant tenant = school.tenant();
        UUID replaced = enrolled(school, null);
        Response preview = issuancePreview(tenant, school.year());
        assertThat(preview.body().path("obligationsToCreate").asLong()).isEqualTo(1);

        assertThat(post(tenant.path("/students/" + replaced + "/enrollments/" + enrollmentOf(tenant, replaced) + "/end"), tenant.adminToken(),
                Map.of("status", "WITHDRAWN")).status()).isEqualTo(204);
        UUID newcomer = enrolled(school, null);
        assertThat(issuancePreview(tenant, school.year()).body().path("obligationsToCreate").asLong()).as("L'effectif n'a pas changé").isEqualTo(1);

        Response refused = confirm(tenant, school.year(), preview);

        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("ISSUANCE_PREVIEW_OUTDATED");
        assertThat(obligationsOf(replaced) + obligationsOf(newcomer)).isZero();
    }

    @Test
    void unLotConfirmeSurUneVersionRemplaceeNEmetRien() {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, null);
        Response preview = issuancePreview(tenant, school.year());
        publish(tenant, draft(tenant, school.year()));

        Response refused = confirm(tenant, school.year(), preview);

        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(obligationsOf(student)).isZero();
    }

    @Test
    void unLotRejoueOuConcurrentNeFactureJamaisDeuxFois() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        List<UUID> students = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            students.add(enrolled(school, null));
        }
        Response preview = issuancePreview(tenant, school.year());

        List<Response> responses = concurrently(8, () -> confirm(tenant, school.year(), preview));

        assertThat(responses).as(responses.toString()).allSatisfy(response -> assertThat(response.status()).isIn(201, 409));
        assertThat(responses.stream().filter(response -> response.status() == 201).count()).isGreaterThanOrEqualTo(1);
        for (UUID student : students) {
            assertThat(obligationsOf(student)).isEqualTo(1);
        }
        long created = 0;
        for (Response response : responses) {
            if (response.status() == 201) {
                created += response.body().path("obligationsCreated").asLong();
            }
        }
        assertThat(created).as("Chaque lot rapporte ce qu'il a réellement créé").isEqualTo(5);
        assertThat(issuancePreview(tenant, school.year()).body().path("obligationsToCreate").asLong()).isZero();
        assertThat(issuancePreview(tenant, school.year()).body().path("alreadyIssued").asLong()).isEqualTo(5);
    }

    @Test
    void uneNouvellePublicationNeRefacturePasCeQuiEstDejaEmis() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);

        UUID next = draft(tenant, school.year());
        String item = get(tenant.path("/billing/fee-catalogs/" + next), tenant.adminToken()).body().path("items").get(0).path("id").asString();
        assertThat(put(tenant.path("/billing/fee-catalogs/" + next + "/items/" + item), tenant.adminToken(),
                fee(school.charge(), "SCOL-CORRIGE", null, 30_000, "AUTOMATIC")).status()).isEqualTo(200);
        publish(tenant, next);

        Response preview = issuancePreview(tenant, school.year());

        assertThat(preview.body().path("obligationsToCreate").asLong())
                .as("Ni le tarif modifié ni le code corrigé ne font une nouvelle charge")
                .isZero();
        assertThat(preview.body().path("alreadyIssued").asLong()).isEqualTo(1);
        assertThat(confirm(tenant, school.year(), preview).body().path("obligationsCreated").asInt()).isZero();
        assertThat(obligationsOf(student)).isEqualTo(1);
    }

    @Test
    void uneChargeCreeeIntentionnellementEstLaSeuleAEtreEmiseEnPlus() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);

        UUID added = charge(tenant, school.year(), school.annual(), "Assurance");
        UUID next = draft(tenant, school.year());
        add(tenant, next, fee(added, "ASSURANCE", null, 5_000, "AUTOMATIC"));
        publish(tenant, next);

        Response preview = issuancePreview(tenant, school.year());
        assertThat(preview.body().path("obligationsToCreate").asLong()).isEqualTo(1);
        assertThat(preview.body().path("fees").get(0).path("feeCode").asString()).isEqualTo("ASSURANCE");
        assertThat(confirm(tenant, school.year(), preview).status()).isEqualTo(201);

        assertThat(obligationsOf(student)).isEqualTo(2);
    }

    @Test
    void lApercuPeutSeLimiterAUneUniteEtSesDescendants() throws Exception {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        Response program = post(tenant.path("/academic-units"), tenant.adminToken(), Map.of("kind", "PROGRAM", "code", uniqueUnitCode(), "name", "Licence"));
        Response l1 = post(tenant.path("/academic-units"), tenant.adminToken(), Map.of(
                "parentId", program.text("id"), "kind", "LEVEL", "code", uniqueUnitCode(), "name", "L1"));
        UUID elsewhere = newLevel(tenant);
        UUID inside = newStudent(tenant);
        UUID outside = newStudent(tenant);
        assertThat(enroll(tenant, inside, year, l1.id("id"), null).status()).isEqualTo(201);
        assertThat(enroll(tenant, outside, year, elsewhere, null).status()).isEqualTo(201);
        UUID catalog = draft(tenant, year);
        add(tenant, catalog, fee(charge(tenant, year, period(tenant, year, "ANNEE"), "Scolarité"), "SCOLARITE", null, 1_000, "AUTOMATIC"));
        publish(tenant, catalog);

        Response preview = get(tenant.path("/billing/issuance-preview?academicYearId=" + year + "&academicUnitId=" + program.text("id")), tenant.adminToken());
        assertThat(preview.body().path("activeEnrollments").asLong()).isEqualTo(1);

        Response run = post(tenant.path("/billing/issuance-runs"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(),
                "academicUnitId", program.text("id"),
                "catalogVersionId", preview.text("catalogVersionId"),
                "previewToken", preview.text("previewToken")));
        assertThat(run.status()).as(run.raw()).isEqualTo(201);

        assertThat(obligationsOf(inside)).isEqualTo(1);
        assertThat(obligationsOf(outside)).isZero();
    }

    @Test
    void sansTarifPublieIlNyARienAEmettre() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        assertThat(enroll(tenant, newStudent(tenant), year, level, null).status()).isEqualTo(201);

        Response preview = issuancePreview(tenant, year);
        assertThat(preview.body().path("catalogVersionId").isNull()).isTrue();
        assertThat(preview.body().path("obligationsToCreate").asLong()).isZero();

        Response refused = post(tenant.path("/billing/issuance-runs"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "catalogVersionId", UUID.randomUUID().toString(), "previewToken", "0".repeat(64)));
        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("ISSUANCE_PREVIEW_OUTDATED");
    }

    @Test
    void uneAnneeClotureeNAccepteAucunLot() {
        School school = school();
        Tenant tenant = school.tenant();
        enrolled(school, null);
        Response preview = issuancePreview(tenant, school.year());
        assertThat(patch(tenant.path("/academic-years/" + school.year()), tenant.adminToken(), Map.of("status", "CLOSED")).status()).isEqualTo(200);

        Response refused = confirm(tenant, school.year(), preview);

        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("ACADEMIC_YEAR_CLOSED");
    }

    @Test
    void unEleveRetireNEstPasFacture() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, null);
        assertThat(post(tenant.path("/students/" + student + "/enrollments/" + enrollmentOf(tenant, student) + "/end"), tenant.adminToken(),
                Map.of("status", "WITHDRAWN")).status()).isEqualTo(204);

        assertThat(issuancePreview(tenant, school.year()).body().path("activeEnrollments").asLong()).isZero();
    }

    // Idempotence : rejeu d'événement, retrait puis réinscription

    @Test
    void unEvenementRejoueOuPublieEnDoubleNEmetQuUneFois() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);
        StudentEnrolled replay = new StudentEnrolled(
                tenant.id(), student, enrollmentOf(tenant, student), school.year(), school.level(), "Sixième",
                school.catalog(), accountOf(tenant), Instant.now());

        for (int i = 0; i < 5; i++) {
            transactions.executeWithoutResult(status -> publisher.publishEvent(replay));
        }
        awaitOutboxDrained();

        assertThat(obligationsOf(student)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM obligation_entries e JOIN obligations o ON o.id = e.obligation_id WHERE o.student_record_id = :id")
                        .param("id", student).query(Long.class).single())
                .as("Pas d'écriture en double non plus")
                .isEqualTo(1);
    }

    /** L'ordre n'est pas garanti : un retrait traité avant l'inscription ne doit pas faire naître de dette. */
    @Test
    void unEvenementArriveApresLeRetraitNEmetRien() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, null);
        UUID enrollment = enrollmentOf(tenant, student);
        assertThat(post(tenant.path("/students/" + student + "/enrollments/" + enrollment + "/end"), tenant.adminToken(),
                Map.of("status", "WITHDRAWN")).status()).isEqualTo(204);

        transactions.executeWithoutResult(status -> publisher.publishEvent(new StudentEnrolled(
                tenant.id(), student, enrollment, school.year(), school.level(), "Sixième", school.catalog(), accountOf(tenant), Instant.now())));
        awaitOutboxDrained();

        assertThat(obligationsOf(student)).isZero();
    }

    @Test
    void unRetraitPuisUneReinscriptionNeRefacturentPasLaMemeCharge() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);
        assertThat(post(tenant.path("/students/" + student + "/enrollments/" + enrollmentOf(tenant, student) + "/end"), tenant.adminToken(),
                Map.of("status", "WITHDRAWN")).status()).isEqualTo(204);
        assertThat(obligationsOf(student)).as("Le retrait ne supprime rien").isEqualTo(1);

        Response again = enroll(tenant, student, school.year(), newLevel(tenant), school.catalog());
        assertThat(again.status()).as(again.raw()).isEqualTo(201);
        awaitOutboxDrained();

        assertThat(obligationsOf(student)).isEqualTo(1);
    }

    /**
     * Le scénario critique : une obligation émise, un nouveau tarif publié avec un autre montant, un autre
     * libellé et un autre code, puis un retrait et une réinscription confirmée sur ce nouveau tarif.
     */
    @Test
    void uneReinscriptionApresUneNouvelleVersionNeRefacturePasLaCharge() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);
        JsonNode before = theOnlyObligation(tenant, school.year(), student);

        UUID next = draft(tenant, school.year());
        String item = get(tenant.path("/billing/fee-catalogs/" + next), tenant.adminToken()).body().path("items").get(0).path("id").asString();
        Map<String, Object> revised = fee(school.charge(), "SCOL-REVISE", null, 40_000, "AUTOMATIC");
        revised.put("label", "Scolarité révisée");
        assertThat(put(tenant.path("/billing/fee-catalogs/" + next + "/items/" + item), tenant.adminToken(), revised).status()).isEqualTo(200);
        publish(tenant, next);

        assertThat(post(tenant.path("/students/" + student + "/enrollments/" + enrollmentOf(tenant, student) + "/end"), tenant.adminToken(),
                Map.of("status", "WITHDRAWN")).status()).isEqualTo(204);
        Response again = enroll(tenant, student, school.year(), newLevel(tenant), next);
        assertThat(again.status()).as(again.raw()).isEqualTo(201);
        awaitOutboxDrained();

        assertThat(obligationsOf(student)).as("Jamais une deuxième obligation pour cette charge").isEqualTo(1);
        JsonNode after = theOnlyObligation(tenant, school.year(), student);
        assertThat(after.path("id").asString()).isEqualTo(before.path("id").asString());
        assertThat(after.path("reference").asString()).as("La référence reste stable").isEqualTo(before.path("reference").asString());
        assertThat(after.path("issuedAmountMinor").asString()).as("Le montant du moment").isEqualTo("25000");
        assertThat(after.path("feeCode").asString()).isEqualTo("SCOLARITE");
        assertThat(after.path("label").asString()).isEqualTo("Frais");
        assertThat(after.path("catalogVersionId").asString()).isEqualTo(school.catalog().toString());

        Response preview = issuancePreview(tenant, school.year());
        assertThat(preview.body().path("obligationsToCreate").asLong()).as("Le lot ne la voit pas non plus comme manquante").isZero();
        assertThat(preview.body().path("alreadyIssued").asLong()).isEqualTo(1);
        assertThat(confirm(tenant, school.year(), preview).body().path("obligationsCreated").asInt()).isZero();

        Response manual = post(tenant.path("/billing/obligations"), tenant.adminToken(), Map.of(
                "enrollmentId", UUID.fromString(again.text("id")).toString(), "feeDefinitionId", school.charge().toString()));
        assertThat(manual.status()).as("L'émission unitaire renvoie l'existante").isEqualTo(200);
        assertThat(manual.text("id")).isEqualTo(before.path("id").asString());

        assertThat(obligationsOf(student)).isEqualTo(1);
        assertThat(get(tenant.path("/billing/students/" + student + "/balances"), tenant.adminToken()).body().get(0).path("dueMinor").asString())
                .isEqualTo("25000");
    }

    /** Même charge, tarifée autrement pour le nouveau niveau : ce n'est toujours pas une nouvelle charge. */
    @Test
    void uneReinscriptionSurUnNiveauAuTarifSpecifiqueNeRefacturePasLaCharge() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);

        UUID terminale = newLevel(tenant);
        UUID next = draft(tenant, school.year());
        add(tenant, next, fee(school.charge(), "SCOLARITE", terminale, 60_000, "AUTOMATIC"));
        publish(tenant, next);
        assertThat(post(tenant.path("/students/" + student + "/enrollments/" + enrollmentOf(tenant, student) + "/end"), tenant.adminToken(),
                Map.of("status", "WITHDRAWN")).status()).isEqualTo(204);

        assertThat(enroll(tenant, student, school.year(), terminale, next).status()).isEqualTo(201);
        awaitOutboxDrained();

        assertThat(obligationsOf(student)).isEqualTo(1);
        assertThat(theOnlyObligation(tenant, school.year(), student).path("issuedAmountMinor").asString()).isEqualTo("25000");
    }

    /** La clé elle-même, lue dans le catalogue de PostgreSQL : ni la version ni l'inscription n'y figurent. */
    @Test
    void laCleDUniciteNeContientNiLaVersionNiLInscription() {
        List<String> columns = jdbc.sql("""
                SELECT a.attname
                FROM pg_constraint c
                JOIN unnest(c.conkey) WITH ORDINALITY AS k(attnum, position) ON true
                JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
                WHERE c.conname = 'obligations_one_per_charge'
                ORDER BY k.position
                """)
                .query(String.class)
                .list();

        assertThat(columns).containsExactly("establishment_id", "student_record_id", "fee_definition_id");
    }

    /** Écriture directe, sans passer par le code : une autre version et une autre inscription n'y changent rien. */
    @Test
    void laBaseRefuseUneDeuxiemeObligationSurUneAutreVersionEtUneAutreInscription() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);
        UUID next = draft(tenant, school.year());
        UUID nextItem = UUID.fromString(get(tenant.path("/billing/fee-catalogs/" + next), tenant.adminToken()).body().path("items").get(0).path("id").asString());
        publish(tenant, next);
        assertThat(post(tenant.path("/students/" + student + "/enrollments/" + enrollmentOf(tenant, student) + "/end"), tenant.adminToken(),
                Map.of("status", "WITHDRAWN")).status()).isEqualTo(204);
        UUID secondEnrollment = enroll(tenant, student, school.year(), school.level(), null).id("id");

        assertThatThrownBy(() -> insertBareObligation(school, next, student, secondEnrollment, nextItem, id -> {}))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("obligations_one_per_charge");
    }

    // Inscriptions restant à facturer : pas d'omission

    private Response pending(Tenant tenant, UUID year) {
        Response response = get(tenant.path("/billing/pending-issuances?academicYearId=" + year), tenant.adminToken());
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        return response;
    }

    @Test
    void uneInscriptionSansConfirmationResteIdentifiableNominativementJusquALEmission() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID unconfirmed = enrolled(school, null);
        UUID confirmed = enrolled(school, school.catalog());
        awaitObligations(confirmed, 1);

        Response before = pending(tenant, school.year());
        assertThat(before.body().path("pendingEnrollments").asLong()).as("Seule l'inscription sans confirmation").isEqualTo(1);
        assertThat(before.text("totalMinor")).isEqualTo("25000");
        JsonNode line = before.body().path("pending").get(0);
        assertThat(line.path("studentId").asString()).isEqualTo(unconfirmed.toString());
        assertThat(line.path("fullName").asString()).isEqualTo("Élève Test");
        assertThat(line.path("studentNumber").asString()).startsWith("ETU-");
        assertThat(line.path("totalMinor").asString()).isEqualTo("25000");
        assertThat(line.path("fees").get(0).path("feeCode").asString()).isEqualTo("SCOLARITE");

        assertThat(confirm(tenant, school.year(), issuancePreview(tenant, school.year())).status()).isEqualTo(201);

        Response after = pending(tenant, school.year());
        assertThat(after.body().path("pendingEnrollments").asLong()).as("Plus rien à facturer une fois émis").isZero();
        assertThat(after.body().path("pending").size()).isZero();
    }

    /** Publier après coup rend visibles les élèves déjà inscrits, sans rien leur facturer. */
    @Test
    void lesElevesInscritsAvantLaPublicationApparaissentCommeAFacturer() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID student = newStudent(tenant);
        assertThat(enroll(tenant, student, year, level, null).status()).isEqualTo(201);
        assertThat(pending(tenant, year).body().path("catalogVersionId").isNull()).as("Sans tarif, rien à facturer").isTrue();

        UUID catalog = draft(tenant, year);
        add(tenant, catalog, fee(charge(tenant, year, period(tenant, year, "ANNEE"), "Scolarité"), "SCOLARITE", null, 1_000, "AUTOMATIC"));
        publish(tenant, catalog);

        assertThat(pending(tenant, year).body().path("pending").get(0).path("studentId").asString()).isEqualTo(student.toString());
        assertThat(obligationsOf(student)).isZero();
    }

    @Test
    void uneNouvelleChargeRendANouveauIdentifiableUnEleveDejaFacture() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);
        assertThat(pending(tenant, school.year()).body().path("pendingEnrollments").asLong()).isZero();

        UUID added = charge(tenant, school.year(), school.annual(), "Assurance");
        UUID next = draft(tenant, school.year());
        add(tenant, next, fee(added, "ASSURANCE", null, 5_000, "AUTOMATIC"));
        publish(tenant, next);

        JsonNode line = pending(tenant, school.year()).body().path("pending").get(0);
        assertThat(line.path("fees").size()).as("Seule la charge manquante").isEqualTo(1);
        assertThat(line.path("fees").get(0).path("feeCode").asString()).isEqualTo("ASSURANCE");
        assertThat(line.path("totalMinor").asString()).isEqualTo("5000");
    }

    @Test
    void unEleveRetireNEstPasAFacturer() {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, null);
        assertThat(pending(tenant, school.year()).body().path("pendingEnrollments").asLong()).isEqualTo(1);

        assertThat(post(tenant.path("/students/" + student + "/enrollments/" + enrollmentOf(tenant, student) + "/end"), tenant.adminToken(),
                Map.of("status", "WITHDRAWN")).status()).isEqualTo(204);

        assertThat(pending(tenant, school.year()).body().path("pendingEnrollments").asLong()).isZero();
    }

    @Test
    void unFraisFacultatifNEstPasUneOmission() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID catalog = draft(tenant, year);
        add(tenant, catalog, fee(charge(tenant, year, period(tenant, year, "ANNEE"), "Cantine"), "CANTINE", null, 40_000, "ON_REQUEST"));
        publish(tenant, catalog);
        UUID student = newStudent(tenant);
        assertThat(enroll(tenant, student, year, level, null).status()).isEqualTo(201);
        assertThat(pending(tenant, year).body().path("pendingEnrollments").asLong()).as("Un frais facultatif n'est pas une omission").isZero();
    }

    @Test
    void laListeAFacturerSePagineSansFausserLeTotal() {
        School school = school();
        Tenant tenant = school.tenant();
        for (int i = 0; i < 3; i++) {
            enrolled(school, null);
        }

        Response page = get(tenant.path("/billing/pending-issuances?academicYearId=" + school.year() + "&limit=2&offset=0"), tenant.adminToken());
        assertThat(page.body().path("pendingEnrollments").asLong()).as("Le total ne dépend pas de la page").isEqualTo(3);
        assertThat(page.text("totalMinor")).isEqualTo("75000");
        assertThat(page.body().path("pending").size()).isEqualTo(2);

        Response rest = get(tenant.path("/billing/pending-issuances?academicYearId=" + school.year() + "&limit=2&offset=2"), tenant.adminToken());
        assertThat(rest.body().path("pending").size()).isEqualTo(1);
    }

    @Test
    void laListeAFacturerSuitLesPermissionsEtLIsolation() {
        School school = school();
        Tenant tenant = school.tenant();
        enrolled(school, null);
        String path = tenant.path("/billing/pending-issuances?academicYearId=" + school.year());

        assertThat(get(path, newStaff(tenant, "REGISTRAR")).status()).isEqualTo(403);
        assertThat(get(path, newStaff(tenant, "CASHIER")).status()).isEqualTo(200);
        assertThat(get(path, newStaff(tenant, "ACCOUNTANT")).status()).isEqualTo(200);

        School other = school();
        assertThat(get(path, other.tenant().adminToken()).status()).isEqualTo(404);
        assertThat(get(other.tenant().path("/billing/pending-issuances?academicYearId=" + school.year()), other.tenant().adminToken()).status()).isEqualTo(404);
    }

    @Test
    void uneUniversiteEmetUneObligationParSemestrePourUneInscriptionAnnuelle() throws Exception {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID s1 = charge(tenant, year, period(tenant, year, "S1"), "Scolarité S1");
        UUID s2 = charge(tenant, year, period(tenant, year, "S2"), "Scolarité S2");
        UUID catalog = draft(tenant, year);
        add(tenant, catalog, fee(s1, "SCOL-S1", null, 150_000, "AUTOMATIC"));
        add(tenant, catalog, fee(s2, "SCOL-S2", null, 170_000, "AUTOMATIC"));
        publish(tenant, catalog);
        UUID student = newStudent(tenant);

        assertThat(enroll(tenant, student, year, level, catalog).status()).isEqualTo(201);
        awaitObligations(student, 2);

        Response balances = get(tenant.path("/billing/students/" + student + "/balances"), tenant.adminToken());
        assertThat(balances.body().get(0).path("dueMinor").asString()).isEqualTo("320000");
    }

    // Frais facultatifs et émission unitaire

    @Test
    void unFraisFacultatifNEstJamaisEmisDOfficeMaisResteEmissibleUnitairement() throws Exception {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID annual = period(tenant, year, "ANNEE");
        UUID tuition = charge(tenant, year, annual, "Scolarité");
        UUID canteen = charge(tenant, year, annual, "Cantine");
        UUID catalog = draft(tenant, year);
        add(tenant, catalog, fee(tuition, "SCOLARITE", null, 25_000, "AUTOMATIC"));
        add(tenant, catalog, fee(canteen, "CANTINE", null, 40_000, "ON_REQUEST"));
        publish(tenant, catalog);

        Response enrollmentPreview = get(tenant.path("/billing/enrollment-preview?academicYearId=" + year + "&academicUnitId=" + level), tenant.adminToken());
        assertThat(enrollmentPreview.text("totalToIssueMinor")).isEqualTo("25000");

        UUID student = newStudent(tenant);
        assertThat(enroll(tenant, student, year, level, catalog).status()).isEqualTo(201);
        awaitObligations(student, 1);
        assertThat(issuancePreview(tenant, year).body().path("obligationsToCreate").asLong()).isZero();

        Map<String, Object> request = Map.of("enrollmentId", enrollmentOf(tenant, student).toString(), "feeDefinitionId", canteen.toString());
        Response created = post(tenant.path("/billing/obligations"), tenant.adminToken(), request);
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        assertThat(created.text("source")).isEqualTo("MANUAL");

        Response again = post(tenant.path("/billing/obligations"), tenant.adminToken(), request);
        assertThat(again.status()).as("Idempotent : l'obligation existante est renvoyée").isEqualTo(200);
        assertThat(again.id("id")).isEqualTo(created.id("id"));
        assertThat(obligationsOf(student)).isEqualTo(2);
    }

    @Test
    void lEmissionUnitaireConcurrenteNeCreeQuUneObligation() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, null);
        Map<String, Object> request = Map.of("enrollmentId", enrollmentOf(tenant, student).toString(), "feeDefinitionId", school.charge().toString());

        List<Response> responses = concurrently(8, () -> post(tenant.path("/billing/obligations"), tenant.adminToken(), request));

        assertThat(responses.stream().filter(response -> response.status() == 201).count()).as(responses.toString()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.status() == 200).count()).as(responses.toString()).isEqualTo(7);
        assertThat(obligationsOf(student)).isEqualTo(1);
    }

    /** L'inscription, le lot et l'émission unitaire visent la même charge en même temps. */
    @Test
    void lesTroisVoiesDEmissionEnConcurrenceNeFacturentQuUneFois() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, null);
        UUID enrollment = enrollmentOf(tenant, student);
        Response preview = issuancePreview(tenant, school.year());
        AtomicInteger turn = new AtomicInteger();

        List<Response> responses = concurrently(3, () -> switch (turn.getAndIncrement()) {
            case 0 -> confirm(tenant, school.year(), preview);
            case 1 -> post(tenant.path("/billing/obligations"), tenant.adminToken(), Map.of(
                    "enrollmentId", enrollment.toString(), "feeDefinitionId", school.charge().toString()));
            default -> {
                transactions.executeWithoutResult(status -> publisher.publishEvent(new StudentEnrolled(
                        tenant.id(), student, enrollment, school.year(), school.level(), "Sixième", school.catalog(), accountOf(tenant), Instant.now())));
                yield new Response(204, null, "");
            }
        });
        awaitOutboxDrained();

        assertThat(responses).as(responses.toString()).allSatisfy(response -> assertThat(response.status()).isIn(200, 201, 204, 409));
        assertThat(obligationsOf(student)).isEqualTo(1);
    }

    @Test
    void unFraisInapplicableOuUneInscriptionTermineeSontRefuses() {
        School school = school();
        Tenant tenant = school.tenant();
        UUID sixieme = newLevel(tenant);
        UUID otherCharge = charge(tenant, school.year(), school.annual(), "Examen");
        UUID next = draft(tenant, school.year());
        add(tenant, next, fee(otherCharge, "EXAMEN", sixieme, 5_000, "AUTOMATIC"));
        publish(tenant, next);
        UUID student = enrolled(school, null);
        UUID enrollment = enrollmentOf(tenant, student);

        Response notApplicable = post(tenant.path("/billing/obligations"), tenant.adminToken(), Map.of(
                "enrollmentId", enrollment.toString(), "feeDefinitionId", otherCharge.toString()));
        assertThat(notApplicable.status()).as(notApplicable.raw()).isEqualTo(422);
        assertThat(notApplicable.code()).isEqualTo("FEE_NOT_APPLICABLE");

        assertThat(post(tenant.path("/students/" + student + "/enrollments/" + enrollment + "/end"), tenant.adminToken(),
                Map.of("status", "WITHDRAWN")).status()).isEqualTo(204);
        Response ended = post(tenant.path("/billing/obligations"), tenant.adminToken(), Map.of(
                "enrollmentId", enrollment.toString(), "feeDefinitionId", school.charge().toString()));
        assertThat(ended.status()).as(ended.raw()).isEqualTo(409);
        assertThat(ended.code()).isEqualTo("ENROLLMENT_NOT_ACTIVE");
    }

    // Le dû vient du journal, et rien ne simule un encaissement

    private UUID issuedObligation(School school) throws InterruptedException {
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);
        return UUID.fromString(theOnlyObligation(school.tenant(), school.year(), student).path("id").asString());
    }

    private void appendEntry(School school, UUID obligation, String kind, long amount, String reason) {
        appendEntry(school, obligation, kind, amount, reason, null);
    }

    /** Amener le dû à zéro exige en base la confirmation d'une seconde personne. */
    private UUID secondPerson(School school) {
        return get("/api/v2/me", newStaff(school.tenant(), "ACCOUNTANT")).id("id");
    }

    private void appendEntry(School school, UUID obligation, String kind, long amount, String reason, UUID approvedBy) {
        jdbc.sql("""
                INSERT INTO obligation_entries (id, establishment_id, obligation_id, kind, amount_minor, reason, idempotency_key, recorded_at, recorded_by, approved_by)
                VALUES (gen_random_uuid(), :establishment, :obligation, :kind, :amount, :reason, :key, now(), :actor, :approvedBy)
                """)
                .param("establishment", school.tenant().id())
                .param("obligation", obligation)
                .param("kind", kind)
                .param("amount", amount)
                .param("reason", reason)
                .param("key", UUID.randomUUID().toString())
                .param("actor", accountOf(school.tenant()))
                .param("approvedBy", approvedBy)
                .update();
    }

    private JsonNode obligationView(School school, UUID obligation) {
        return get(school.tenant().path("/billing/obligations/" + obligation), school.tenant().adminToken()).body().path("obligation");
    }

    @Test
    void leDuEstLaSommeDuJournal() throws Exception {
        School school = school();
        UUID obligation = issuedObligation(school);

        appendEntry(school, obligation, "DISCOUNT", -5_000, "Bourse");
        appendEntry(school, obligation, "CORRECTION", 1_000, "Correction");

        assertThat(obligationView(school, obligation).path("dueMinor").asString()).isEqualTo("21000");
        assertThat(obligationView(school, obligation).path("issuedAmountMinor").asString()).as("Le montant émis ne change pas").isEqualTo("25000");
    }

    /** Une remise totale annule le dû, elle ne prétend jamais qu'un paiement a eu lieu. */
    @Test
    void uneRemiseTotaleDonneWaivedEtJamaisUnReglement() throws Exception {
        School school = school();
        UUID obligation = issuedObligation(school);

        appendEntry(school, obligation, "DISCOUNT", -25_000, "Bourse totale", secondPerson(school));

        JsonNode view = obligationView(school, obligation);
        assertThat(view.path("dueMinor").asString()).isEqualTo("0");
        assertThat(view.path("status").asString()).isEqualTo("WAIVED");
    }

    @Test
    void laBaseRefuseToutReglementEtToutEcritureSansMotif() throws Exception {
        School school = school();
        UUID obligation = issuedObligation(school);

        assertThatThrownBy(() -> appendEntry(school, obligation, "SETTLEMENT", -1_000, "Paiement fictif"))
                .as("Le règlement n'existe pas tant que le module de paiement n'est pas là")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> appendEntry(school, obligation, "DISCOUNT", -1_000, null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> appendEntry(school, obligation, "CORRECTION", 1_000, null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> appendEntry(school, obligation, "DISCOUNT", 1_000, "Remise positive")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> appendEntry(school, obligation, "ISSUED", 1_000, null)).as("Une seule émission").isInstanceOf(DataAccessException.class);
    }

    @Test
    void leDuNePeutPasDevenirNegatif() throws Exception {
        School school = school();
        UUID obligation = issuedObligation(school);

        assertThatThrownBy(() -> appendEntry(school, obligation, "DISCOUNT", -25_001, "Trop"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("négatif");
    }

    @Test
    void dixEcrituresSimultaneesDonnentLeBonTotalEtNeDepassentJamaisLeDu() throws Exception {
        School school = school();
        UUID obligation = issuedObligation(school);
        AtomicInteger refused = new AtomicInteger();

        List<Response> results = concurrently(10, () -> {
            try {
                appendEntry(school, obligation, "DISCOUNT", -3_000, "Remise");
            } catch (DataAccessException tooMuch) {
                refused.incrementAndGet();
            }
            return new Response(200, null, "");
        });

        assertThat(results).hasSize(10);
        assertThat(refused.get()).as("Seules 8 remises de 3 000 tiennent dans 25 000").isEqualTo(2);
        assertThat(obligationView(school, obligation).path("dueMinor").asString()).isEqualTo("1000");
    }

    /**
     * Déterministe, sur deux connexions directes : la seconde écriture attend la fin de la première, puis
     * voit le dû déjà consommé. Sans ce verrou, elle lirait un dû périmé et le rendrait négatif.
     */
    @Test
    void uneEcritureConcurrenteAttendPuisNeRendJamaisLeDuNegatif() throws Exception {
        School school = school();
        UUID obligation = issuedObligation(school);
        UUID actor = accountOf(school.tenant());
        UUID approver = secondPerson(school);

        try (java.sql.Connection first = dataSource.getConnection();
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            first.setAutoCommit(false);
            try (java.sql.PreparedStatement discount = first.prepareStatement("""
                    INSERT INTO obligation_entries (id, establishment_id, obligation_id, kind, amount_minor, reason, idempotency_key, recorded_at, recorded_by, approved_by)
                    VALUES (gen_random_uuid(), ?, ?, 'DISCOUNT', -25000, 'Remise totale', ?, now(), ?, ?)
                    """)) {
                discount.setObject(1, school.tenant().id());
                discount.setObject(2, obligation);
                discount.setString(3, UUID.randomUUID().toString());
                discount.setObject(4, actor);
                discount.setObject(5, approver);
                assertThat(discount.executeUpdate()).isEqualTo(1);
            }

            java.util.concurrent.Future<?> second = executor.submit(() -> appendEntry(school, obligation, "DISCOUNT", -1, "Remise concurrente"));

            Thread.sleep(500);
            assertThat(second.isDone()).as("La seconde écriture attend la première").isFalse();
            first.commit();

            assertThatThrownBy(second::get).hasMessageContaining("négatif");
        }
        assertThat(obligationView(school, obligation).path("dueMinor").asString()).isEqualTo("0");
    }

    private void assertImmutable(String sql, UUID id) {
        assertThatThrownBy(() -> jdbc.sql(sql).param("id", id).update())
                .as(sql)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
    }

    @Test
    void laBaseFigeObligationsEcheancesEtJournal() throws Exception {
        School school = school();
        UUID obligation = issuedObligation(school);

        assertImmutable("UPDATE obligations SET issued_amount_minor = 1 WHERE id = :id", obligation);
        assertImmutable("UPDATE obligations SET reference = 'OBL-0000000000' WHERE id = :id", obligation);
        assertImmutable("DELETE FROM obligations WHERE id = :id", obligation);
        assertImmutable("UPDATE obligation_installments SET amount_minor = 1 WHERE obligation_id = :id", obligation);
        assertImmutable("DELETE FROM obligation_installments WHERE obligation_id = :id", obligation);
        assertImmutable("UPDATE obligation_entries SET amount_minor = 1 WHERE obligation_id = :id", obligation);
        assertImmutable("DELETE FROM obligation_entries WHERE obligation_id = :id", obligation);
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE obligation_entries CASCADE").update()).isInstanceOf(DataAccessException.class);
    }

    @Test
    void laBaseRefuseUneEcheanceAjouteeApresCoup() throws Exception {
        School school = school();
        UUID obligation = issuedObligation(school);

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO obligation_installments (obligation_id, seq, establishment_id, due_on, amount_minor)
                VALUES (:id, 3, :establishment, '2027-01-01', 1)
                """)
                .param("id", obligation)
                .param("establishment", school.tenant().id())
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("somme des échéances");
    }

    /** Insère directement une obligation incomplète : la validation doit échouer. */
    private void insertBareObligation(School school, UUID student, UUID enrollment, UUID item, java.util.function.Consumer<UUID> completion) {
        insertBareObligation(school, school.catalog(), student, enrollment, item, completion);
    }

    private void insertBareObligation(School school, UUID catalog, UUID student, UUID enrollment, UUID item, java.util.function.Consumer<UUID> completion) {
        transactions.executeWithoutResult(status -> {
            UUID id = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO obligations
                      (id, establishment_id, academic_year_id, student_record_id, enrollment_id, fee_definition_id, catalog_version_id,
                       fee_item_id, reference, fee_code, label, category, currency, issued_amount_minor, source, issued_at, issued_by)
                    VALUES (:id, :establishment, :year, :student, :enrollment, :charge, :catalog, :item, :reference, 'C', 'L', 'OTHER', 'XAF', 100, 'MANUAL', now(), :actor)
                    """)
                    .param("id", id)
                    .param("establishment", school.tenant().id())
                    .param("year", school.year())
                    .param("student", student)
                    .param("enrollment", enrollment)
                    .param("charge", school.charge())
                    .param("catalog", catalog)
                    .param("item", item)
                    .param("reference", "OBL-" + "0123456789")
                    .param("actor", accountOf(school.tenant()))
                    .update();
            completion.accept(id);
        });
    }

    private UUID itemOf(School school) {
        return UUID.fromString(get(school.tenant().path("/billing/fee-catalogs/" + school.catalog()), school.tenant().adminToken())
                .body().path("items").get(0).path("id").asString());
    }

    @Test
    void uneObligationNeNaitQueCompleteAvecSonEcheancierEtSonEcriture() {
        School school = school();
        UUID student = enrolled(school, null);
        UUID enrollment = enrollmentOf(school.tenant(), student);
        UUID item = itemOf(school);

        assertThatThrownBy(() -> insertBareObligation(school, student, enrollment, item, id -> {}))
                .as("Sans échéancier ni écriture")
                .hasStackTraceContaining("somme des échéances");

        assertThatThrownBy(() -> insertBareObligation(school, student, enrollment, item, id -> jdbc.sql("""
                INSERT INTO obligation_installments (obligation_id, seq, establishment_id, due_on, amount_minor)
                VALUES (:id, 1, :establishment, '2027-01-01', 100)
                """).param("id", id).param("establishment", school.tenant().id()).update()))
                .as("Sans écriture d'émission")
                .hasStackTraceContaining("écriture d'émission");

        assertThat(obligationsOf(student)).isZero();
    }

    @Test
    void laBaseRefuseDeuxObligationsPourLaMemeCharge() throws Exception {
        School school = school();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);
        UUID enrollment = enrollmentOf(school.tenant(), student);
        UUID item = itemOf(school);

        // Sans ON CONFLICT : la contrainte elle-même, indépendamment du code applicatif.
        assertThatThrownBy(() -> insertBareObligation(school, student, enrollment, item, id -> {}))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("obligations_one_per_charge");
    }

    @Test
    void laBaseRefuseUneObligationIncoherente() {
        School a = school();
        School b = school();
        UUID studentA = enrolled(a, null);
        UUID enrollmentA = enrollmentOf(a.tenant(), studentA);
        UUID studentA2 = enrolled(a, null);

        assertThatThrownBy(() -> insertBareObligation(b, studentA, enrollmentA, itemOf(b), id -> {}))
                .as("Le dossier et l'inscription d'un autre établissement")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBareObligation(a, studentA2, enrollmentA, itemOf(a), id -> {}))
                .as("L'inscription d'un autre élève")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Soldes

    @Test
    void leSoldeRefleteLeJournalEtLaProchaineEcheance() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);
        UUID obligation = UUID.fromString(theOnlyObligation(tenant, school.year(), student).path("id").asString());
        appendEntry(school, obligation, "DISCOUNT", -5_000, "Bourse");

        Response balances = get(tenant.path("/billing/students/" + student + "/balances"), tenant.adminToken());

        assertThat(balances.status()).as(balances.raw()).isEqualTo(200);
        JsonNode balance = balances.body().get(0);
        assertThat(balance.path("currency").asString()).isEqualTo("XAF");
        assertThat(balance.path("issuedMinor").asString()).isEqualTo("25000");
        assertThat(balance.path("dueMinor").asString()).isEqualTo("20000");
        assertThat(LocalDate.parse(balance.path("nextDueOn").asString())).isEqualTo(LocalDate.of(2099, 1, 15));

        appendEntry(school, obligation, "DISCOUNT", -20_000, "Solde de la bourse", secondPerson(school));
        JsonNode cleared = get(tenant.path("/billing/students/" + student + "/balances"), tenant.adminToken()).body().get(0);
        assertThat(cleared.path("dueMinor").asString()).isEqualTo("0");
        assertThat(cleared.path("nextDueOn").isNull()).as("Plus rien n'est dû, donc plus d'échéance").isTrue();
    }

    @Test
    void unDossierSansObligationNaAucunSolde() {
        School school = school();
        UUID student = newStudent(school.tenant());

        Response balances = get(school.tenant().path("/billing/students/" + student + "/balances"), school.tenant().adminToken());

        assertThat(balances.status()).isEqualTo(200);
        assertThat(balances.body().size()).isZero();
        assertThat(get(school.tenant().path("/billing/students/" + UUID.randomUUID() + "/balances"), school.tenant().adminToken()).status()).isEqualTo(404);
    }

    // Surface de l'API : ce qui ne doit pas exister

    @Test
    void aucuneRouteNeModifieNiNeRegleUneObligation() {
        List<String> billing = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                if (pattern.contains("/billing/")) {
                    billing.add(info.getMethodsCondition().getMethods() + " " + pattern);
                    assertThat(pattern.toLowerCase())
                            .as("Ni encaissement, ni reçu, ni document fiscal dans ce lot")
                            .doesNotContain("pay", "settle", "receipt", "recu", "invoice", "factur");
                    if (pattern.contains("/obligations") || pattern.contains("/balances")) {
                        assertThat(info.getMethodsCondition().getMethods())
                                .as(pattern)
                                .doesNotContain(RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);
                    }
                }
            }
        }
        assertThat(billing).as("Le test porte bien sur de vraies routes").isNotEmpty();
        // Seules ces opérations ajoutent quelque chose à une obligation, et toutes passent par le journal.
        assertThat(billing)
                .filteredOn(route -> route.contains("/obligations/") && route.contains("POST"))
                .allSatisfy(route -> assertThat(route).containsAnyOf("/discounts", "/corrections", "/cancellation", "/reversal", "/parties"));
    }

    // Permissions

    @Test
    void lesPermissionsSeparentLaLectureDeLEmission() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        String registrar = newStaff(tenant, "REGISTRAR");
        String accountant = newStaff(tenant, "ACCOUNTANT");
        String cashier = newStaff(tenant, "CASHIER");
        String auditor = newStaff(tenant, "AUDITOR");
        UUID student = enrolled(school, null);
        String balances = tenant.path("/billing/students/" + student + "/balances");
        String list = tenant.path("/billing/obligations?academicYearId=" + school.year());
        String preview = tenant.path("/billing/issuance-preview?academicYearId=" + school.year());
        Map<String, Object> manual = Map.of("enrollmentId", enrollmentOf(tenant, student).toString(), "feeDefinitionId", school.charge().toString());

        // Le secrétariat voit l'aperçu d'inscription et confirme, sans accéder aux dettes.
        assertThat(get(tenant.path("/billing/enrollment-preview?academicYearId=" + school.year() + "&academicUnitId=" + school.level()), registrar).status()).isEqualTo(200);
        assertThat(get(balances, registrar).status()).isEqualTo(403);
        assertThat(get(list, registrar).status()).isEqualTo(403);
        assertThat(get(preview, registrar).status()).isEqualTo(403);
        assertThat(post(tenant.path("/billing/obligations"), registrar, manual).status()).isEqualTo(403);

        for (String reader : List.of(cashier, auditor)) {
            assertThat(get(balances, reader).status()).isEqualTo(200);
            assertThat(get(list, reader).status()).isEqualTo(200);
            assertThat(get(preview, reader).status()).isEqualTo(403);
            assertThat(post(tenant.path("/billing/obligations"), reader, manual).status()).isEqualTo(403);
        }

        assertThat(get(preview, accountant).status()).isEqualTo(200);
        Response issued = post(tenant.path("/billing/obligations"), accountant, manual);
        assertThat(issued.status()).as(issued.raw()).isEqualTo(201);

        Response confirmed = post(tenant.path("/students/" + newStudent(tenant) + "/enrollments"), registrar, Map.of(
                "academicYearId", school.year().toString(),
                "academicUnitId", school.level().toString(),
                "confirmedFeeCatalogVersionId", school.catalog().toString()));
        assertThat(confirmed.status()).as("Le secrétariat confirme les frais de l'inscription").isEqualTo(201);
    }

    @Test
    void lApercuDeLotEtSaConfirmationExigentLaMemePermission() {
        School school = school();
        Tenant tenant = school.tenant();
        enrolled(school, null);
        Response preview = issuancePreview(tenant, school.year());
        String cashier = newStaff(tenant, "CASHIER");

        assertThat(post(tenant.path("/billing/issuance-runs"), cashier, Map.of(
                "academicYearId", school.year().toString(),
                "catalogVersionId", preview.text("catalogVersionId"),
                "previewToken", preview.text("previewToken"))).status()).isEqualTo(403);
    }

    // Audit

    @Test
    void chaqueEmissionEstAuditeeUneSeuleFois() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolled(school, school.catalog());
        awaitObligations(student, 1);
        UUID obligation = UUID.fromString(theOnlyObligation(tenant, school.year(), student).path("id").asString());
        transactions.executeWithoutResult(status -> publisher.publishEvent(new StudentEnrolled(
                tenant.id(), student, enrollmentOf(tenant, student), school.year(), school.level(), "Sixième", school.catalog(), accountOf(tenant), Instant.now())));
        awaitOutboxDrained();

        Long audited = jdbc.sql("SELECT count(*) FROM audit_events WHERE action = 'billing.obligation.issued' AND target_id = :id")
                .param("id", obligation)
                .query(Long.class)
                .single();

        assertThat(audited).as("Le rejeu ne crée rien, donc n'audite rien").isEqualTo(1);
        UUID actor = accountOf(tenant);
        assertThat(jdbc.sql("SELECT issued_by FROM obligations WHERE id = :id").param("id", obligation).query(UUID.class).single())
                .as("La personne qui a confirmé l'inscription, même si l'émission est asynchrone")
                .isEqualTo(actor);
    }

    @Test
    void unLotConfirmeEstAudite() {
        School school = school();
        Tenant tenant = school.tenant();
        enrolled(school, null);
        Response run = confirm(tenant, school.year(), issuancePreview(tenant, school.year()));
        assertThat(run.status()).as(run.raw()).isEqualTo(201);

        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE action = 'billing.issuance.confirmed' AND target_id = :id")
                        .param("id", run.id("id")).query(Long.class).single())
                .isEqualTo(1);
    }

    // Isolation

    @Test
    void unEtablissementNAccedeNiAuxObligationsNiAuxLotsDUnAutre() throws Exception {
        School a = school();
        School b = school();
        UUID studentA = enrolled(a, a.catalog());
        awaitObligations(studentA, 1);
        UUID obligation = UUID.fromString(theOnlyObligation(a.tenant(), a.year(), studentA).path("id").asString());
        String token = b.tenant().adminToken();

        assertThat(get(b.tenant().path("/billing/obligations/" + obligation), token).status()).isEqualTo(404);
        assertThat(get(b.tenant().path("/billing/students/" + studentA + "/balances"), token).status()).isEqualTo(404);
        assertThat(get(b.tenant().path("/billing/obligations?academicYearId=" + a.year() + "&studentId=" + studentA), token).body().size()).isZero();
        assertThat(get(b.tenant().path("/billing/issuance-preview?academicYearId=" + a.year()), token).status()).isEqualTo(404);
        assertThat(get(b.tenant().path("/billing/enrollment-preview?academicYearId=" + a.year() + "&academicUnitId=" + a.level()), token).status()).isEqualTo(404);
        assertThat(post(b.tenant().path("/billing/obligations"), token, Map.of(
                "enrollmentId", enrollmentOf(a.tenant(), studentA).toString(), "feeDefinitionId", b.charge().toString())).status()).isEqualTo(404);
        assertThat(get(a.tenant().path("/billing/obligations/" + obligation), token).status()).as("Par l'établissement A, sans en être membre").isEqualTo(404);
    }
}
