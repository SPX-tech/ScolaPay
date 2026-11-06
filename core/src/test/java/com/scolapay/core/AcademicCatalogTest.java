package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scolapay.core.schooling.StudentEnrolled;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

class AcademicCatalogTest extends IntegrationTest {

    private Response createUnit(Tenant tenant, UUID parentId, String kind, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("parentId", parentId == null ? null : parentId.toString());
        body.put("kind", kind);
        body.put("code", uniqueUnitCode());
        body.put("name", name);
        return post(tenant.path("/academic-units"), tenant.adminToken(), body);
    }

    private UUID program(Tenant tenant) {
        Response created = createUnit(tenant, null, "PROGRAM", "Licence Informatique");
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    private Response enroll(Tenant tenant, UUID student, UUID year, UUID unit) {
        return post(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "academicUnitId", unit.toString()));
    }

    private Response archive(Tenant tenant, UUID unit) {
        return patch(tenant.path("/academic-units/" + unit), tenant.adminToken(), Map.of("status", "ARCHIVED"));
    }

    @Test
    void uneEcoleInscritSurUnNiveauEtLEvenementPorteLUnite() {
        Tenant tenant = newTenant();
        UUID level = newLevel(tenant, "Terminale D");
        UUID student = newStudent(tenant);

        Response enrolled = enroll(tenant, student, openYear(tenant), level);

        assertThat(enrolled.status()).as(enrolled.raw()).isEqualTo(201);
        assertThat(enrolled.id("academicUnitId")).isEqualTo(level);
        assertThat(enrolled.text("level")).isEqualTo("Terminale D");
        assertThat(events.published)
                .filteredOn(StudentEnrolled.class::isInstance)
                .map(StudentEnrolled.class::cast)
                .filteredOn(event -> event.studentId().equals(student))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.academicUnitId()).isEqualTo(level);
                    assertThat(event.level()).isEqualTo("Terminale D");
                });
    }

    @Test
    void uneUniversiteRegroupeSesNiveauxSousUnProgrammeOuOnNeSInscritPasDirectement() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID program = program(tenant);
        Response track = createUnit(tenant, program, "PROGRAM", "Parcours Réseaux");
        assertThat(track.status()).as(track.raw()).isEqualTo(201);
        Response level = createUnit(tenant, track.id("id"), "LEVEL", "L1");
        assertThat(level.status()).as(level.raw()).isEqualTo(201);
        assertThat(level.id("parentId")).isEqualTo(track.id("id"));

        Response refused = enroll(tenant, newStudent(tenant), year, program);
        assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("ACADEMIC_UNIT_NOT_ENROLLABLE");

        assertThat(enroll(tenant, newStudent(tenant), year, level.id("id")).status()).isEqualTo(201);
    }

    @Test
    void unNiveauNePeutRienRegrouper() {
        Tenant tenant = newTenant();

        Response refused = createUnit(tenant, newLevel(tenant), "LEVEL", "Sous-niveau");

        assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("PARENT_NOT_PROGRAM");
    }

    @Test
    void unCodeEstUniqueDansUnEtablissementMaisPasEntreEtablissements() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        Map<String, Object> body = Map.of("kind", "LEVEL", "code", "SIXIEME", "name", "Sixième");

        assertThat(post(a.path("/academic-units"), a.adminToken(), body).status()).isEqualTo(201);

        Response duplicate = post(a.path("/academic-units"), a.adminToken(), body);
        assertThat(duplicate.status()).as(duplicate.raw()).isEqualTo(409);
        assertThat(post(b.path("/academic-units"), b.adminToken(), body).status()).isEqualTo(201);
    }

    @Test
    void uneNatureOuUnCodeInvalideEstRefuse() {
        Tenant tenant = newTenant();
        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("kind", "CLASSE", "code", "OK", "name", "Nature inconnue"),
                Map.of("kind", "LEVEL", "code", "minuscule", "name", "Code invalide"),
                Map.of("kind", "LEVEL", "code", "OK", "name", " "))) {
            assertThat(post(tenant.path("/academic-units"), tenant.adminToken(), body).status())
                    .as(body.toString())
                    .isEqualTo(400);
        }
    }

    @Test
    void uneUniteArchiveeNAccepteNiInscriptionNiEnfantEtLesInscriptionsExistantesRestent() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);
        UUID enrolledStudent = newStudent(tenant);
        assertThat(enroll(tenant, enrolledStudent, year, level).status()).isEqualTo(201);
        UUID emptyProgram = program(tenant);

        assertThat(archive(tenant, level).status()).isEqualTo(200);
        assertThat(archive(tenant, emptyProgram).status()).isEqualTo(200);

        Response refused = enroll(tenant, newStudent(tenant), year, level);
        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("ACADEMIC_UNIT_ARCHIVED");

        Response orphan = createUnit(tenant, emptyProgram, "LEVEL", "L1");
        assertThat(orphan.status()).as(orphan.raw()).isEqualTo(409);
        assertThat(orphan.code()).isEqualTo("ACADEMIC_UNIT_ARCHIVED");

        Response existing = get(tenant.path("/students/" + enrolledStudent + "/enrollments"), tenant.adminToken());
        assertThat(existing.body().get(0).path("status").asString()).isEqualTo("ACTIVE");
    }

    @Test
    void unProgrammeNeSArchivePasAvantSesEnfantsEtUnEnfantNeRevitPasSansLui() {
        Tenant tenant = newTenant();
        UUID program = program(tenant);
        UUID level = createUnit(tenant, program, "LEVEL", "L1").id("id");

        Response refused = archive(tenant, program);
        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("ACADEMIC_UNIT_HAS_ACTIVE_CHILDREN");

        assertThat(archive(tenant, level).status()).isEqualTo(200);
        assertThat(archive(tenant, program).status()).isEqualTo(200);

        Response reactivate = patch(tenant.path("/academic-units/" + level), tenant.adminToken(), Map.of("status", "ACTIVE"));
        assertThat(reactivate.status()).as(reactivate.raw()).isEqualTo(409);
        assertThat(reactivate.code()).isEqualTo("ACADEMIC_UNIT_ARCHIVED");

        assertThat(patch(tenant.path("/academic-units/" + program), tenant.adminToken(), Map.of("status", "ACTIVE")).status()).isEqualTo(200);
        assertThat(patch(tenant.path("/academic-units/" + level), tenant.adminToken(), Map.of("status", "ACTIVE")).status()).isEqualTo(200);
    }

    @Test
    void renommerUneUniteNeReecritPasLHistoriqueDesInscriptions() {
        Tenant tenant = newTenant();
        UUID level = newLevel(tenant, "Terminale D");
        UUID student = newStudent(tenant);
        assertThat(enroll(tenant, student, openYear(tenant), level).status()).isEqualTo(201);

        Response renamed = patch(tenant.path("/academic-units/" + level), tenant.adminToken(), Map.of("name", "Terminale Sciences"));

        assertThat(renamed.status()).as(renamed.raw()).isEqualTo(200);
        assertThat(renamed.text("name")).isEqualTo("Terminale Sciences");
        Response enrollments = get(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken());
        assertThat(enrollments.body().get(0).path("level").asString()).isEqualTo("Terminale D");
    }

    @Test
    void leCodeNeSeModifiePasParLApi() {
        Tenant tenant = newTenant();

        Response refused = patch(tenant.path("/academic-units/" + newLevel(tenant)), tenant.adminToken(), Map.of("code", "AUTRE"));

        assertThat(refused.status()).as(refused.raw()).isEqualTo(400);
    }

    @Test
    void uneModificationSansContenuEstRefusee() {
        Tenant tenant = newTenant();

        Response refused = patch(tenant.path("/academic-units/" + newLevel(tenant)), tenant.adminToken(), Map.of());

        assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("NOTHING_TO_UPDATE");
    }

    @Test
    void lesRolesDeControleLisentLeCatalogueSansLeModifier() {
        Tenant tenant = newTenant();
        UUID level = newLevel(tenant);
        String cashier = newStaff(tenant, "CASHIER");
        String registrar = newStaff(tenant, "REGISTRAR");

        assertThat(get(tenant.path("/academic-units"), cashier).status()).isEqualTo(200);
        assertThat(post(tenant.path("/academic-units"), cashier, Map.of("kind", "LEVEL", "code", uniqueUnitCode(), "name", "Refusé")).status())
                .isEqualTo(403);
        assertThat(patch(tenant.path("/academic-units/" + level), cashier, Map.of("name", "Refusé")).status()).isEqualTo(403);

        assertThat(post(tenant.path("/academic-units"), registrar, Map.of("kind", "LEVEL", "code", uniqueUnitCode(), "name", "Accepté")).status())
                .isEqualTo(201);
    }

    @Test
    void lesModificationsDuCatalogueSontAuditees() {
        Tenant tenant = newTenant();
        UUID level = newLevel(tenant);
        patch(tenant.path("/academic-units/" + level), tenant.adminToken(), Map.of("name", "Renommé"));

        List<String> actions = jdbc.sql("SELECT action FROM audit_events WHERE establishment_id = :id AND target_id = :target ORDER BY occurred_at")
                .param("id", tenant.id())
                .param("target", level)
                .query(String.class)
                .list();

        assertThat(actions).containsExactly("schooling.academic_unit.created", "schooling.academic_unit.updated");
    }

    @Test
    void unEtablissementNUtiliseNiUnNiveauNiUnProgrammeDUnAutre() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID levelA = newLevel(a);
        UUID programA = program(a);

        Response foreignLevel = enroll(b, newStudent(b), openYear(b), levelA);
        assertThat(foreignLevel.status()).as(foreignLevel.raw()).isEqualTo(404);
        assertThat(foreignLevel.code()).isEqualTo("ACADEMIC_UNIT_NOT_FOUND");

        assertThat(createUnit(b, programA, "LEVEL", "L1").status()).isEqualTo(404);
        assertThat(patch(b.path("/academic-units/" + levelA), b.adminToken(), Map.of("name", "Volé")).status()).isEqualTo(404);
        assertThat(get(b.path("/academic-units"), b.adminToken()).body().size()).isZero();
    }

    @Test
    void laBaseRefuseUneInscriptionSurLUniteDUnAutreEtablissement() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID levelA = newLevel(a);
        UUID studentB = newStudent(b);
        UUID yearB = openYear(b);

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO enrollments (id, establishment_id, student_record_id, academic_year_id, academic_unit_id, level, enrolled_at)
                VALUES (:id, :establishmentId, :studentId, :yearId, :unitId, 'Sixième', now())
                """)
                .param("id", UUID.randomUUID())
                .param("establishmentId", b.id())
                .param("studentId", studentB)
                .param("yearId", yearB)
                .param("unitId", levelA)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("enrollments_establishment_id_academic_unit_id_fkey");
    }

    @Test
    void laBaseRefuseUnParentDUnAutreEtablissement() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID programA = program(a);

        assertThatThrownBy(() -> insertUnit(b.id(), programA, "LEVEL")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void laBaseRefuseUnNiveauCommeParent() {
        Tenant tenant = newTenant();
        UUID level = newLevel(tenant);

        assertThatThrownBy(() -> insertUnit(tenant.id(), level, "LEVEL"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Seul un programme");
    }

    @Test
    void laBaseFigeLIdentiteDUneUniteEtEnInterditLaSuppression() {
        Tenant tenant = newTenant();
        UUID level = newLevel(tenant);
        UUID program = program(tenant);

        for (String change : List.of("code = 'AUTRE'", "kind = 'PROGRAM'", "parent_id = '" + program + "'")) {
            assertThatThrownBy(() -> jdbc.sql("UPDATE academic_units SET " + change + " WHERE id = :id").param("id", level).update())
                    .as(change)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("immuable");
        }
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM academic_units WHERE id = :id").param("id", level).update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void archiverUnProgrammePendantQuOnYAjouteUnNiveauNeLaissePasDOrphelinActif() throws Exception {
        Tenant tenant = newTenant();
        for (int round = 0; round < 10; round++) {
            UUID program = program(tenant);
            List<Response> responses = concurrently(2, new java.util.concurrent.Callable<>() {
                private final java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);

                @Override
                public Response call() {
                    return first.getAndSet(false) ? archive(tenant, program) : createUnit(tenant, program, "LEVEL", "L1");
                }
            });
            assertThat(responses).as(responses.toString()).allSatisfy(response -> assertThat(response.status()).isIn(200, 201, 409));
            Long orphans = jdbc.sql("""
                    SELECT count(*) FROM academic_units child
                    JOIN academic_units parent ON parent.id = child.parent_id
                    WHERE parent.id = :program AND parent.status = 'ARCHIVED' AND child.status = 'ACTIVE'
                    """)
                    .param("program", program)
                    .query(Long.class)
                    .single();
            assertThat(orphans).isZero();
        }
    }

    private void insertUnit(UUID establishmentId, UUID parentId, String kind) {
        jdbc.sql("""
                INSERT INTO academic_units (id, establishment_id, parent_id, kind, code, name, created_at, updated_at)
                VALUES (:id, :establishmentId, :parentId, :kind, :code, 'Direct', now(), now())
                """)
                .param("id", UUID.randomUUID())
                .param("establishmentId", establishmentId)
                .param("parentId", parentId)
                .param("kind", kind)
                .param("code", uniqueUnitCode())
                .update();
    }
}
