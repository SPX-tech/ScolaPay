package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Les garanties portées par PostgreSQL lui-même : elles tiennent même si le code
 * applicatif se trompe, ce que ces tests simulent en écrivant directement en base.
 */
class DatabaseGuardsTest extends IntegrationTest {

    @Autowired
    Flyway flyway;

    @Test
    void uneInscriptionNePeutPasReferencerLeDossierDUnAutreEtablissement() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID studentA = newStudent(a);
        UUID yearB = openYear(b);
        UUID levelB = newLevel(b);

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO enrollments (id, establishment_id, student_record_id, academic_year_id, academic_unit_id, level, enrolled_at)
                VALUES (:id, :establishmentId, :studentId, :yearId, :unitId, 'Sixième', now())
                """)
                .param("id", UUID.randomUUID())
                .param("establishmentId", b.id())
                .param("studentId", studentA)
                .param("yearId", yearB)
                .param("unitId", levelB)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("enrollments_establishment_id_student_record_id_fkey");
    }

    @Test
    void unLienFamilialNePeutPasReferencerLeDossierDUnAutreEtablissement() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID studentA = newStudent(a);
        UUID account = get("/api/v2/me", b.adminToken()).id("id");

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO record_access_grants
                  (id, establishment_id, student_record_id, account_id, kind, relationship, academic_access, granted_at)
                VALUES (:id, :establishmentId, :studentId, :accountId, 'THIRD_PARTY', 'OTHER', true, now())
                """)
                .param("id", UUID.randomUUID())
                .param("establishmentId", b.id())
                .param("studentId", studentA)
                .param("accountId", account)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unLienSansAucunDroitEstRefuseParLaBase() {
        Tenant tenant = newTenant();
        UUID student = newStudent(tenant);
        UUID account = get("/api/v2/me", tenant.adminToken()).id("id");

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO record_access_grants
                  (id, establishment_id, student_record_id, account_id, kind, relationship, granted_at)
                VALUES (:id, :establishmentId, :studentId, :accountId, 'THIRD_PARTY', 'OTHER', now())
                """)
                .param("id", UUID.randomUUID())
                .param("establishmentId", tenant.id())
                .param("studentId", student)
                .param("accountId", account)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unEtablissementExigeUnPaysAyantUneRegleJuridique() {
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO establishments (id, code, name, kind, timezone, currency, country_code, created_at, updated_at)
                VALUES (:id, :code, 'Lycée', 'SCHOOL', 'UTC', 'XAF', 'ZZ', now(), now())
                """)
                .param("id", UUID.randomUUID())
                .param("code", uniqueCode())
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leJournalDAuditEstImmuable() {
        Tenant tenant = newTenant();
        Integer before = jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id = :id")
                .param("id", tenant.id()).query(Integer.class).single();
        assertThat(before).isGreaterThan(0);

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_events SET action = 'falsifie' WHERE establishment_id = :id")
                .param("id", tenant.id()).update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_events WHERE establishment_id = :id")
                .param("id", tenant.id()).update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE audit_events CASCADE").update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void lHistoriqueDesInscriptionsEstImmuable() {
        Tenant tenant = newTenant();
        UUID student = newStudent(tenant);
        UUID year = openYear(tenant);
        post(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "academicUnitId", newLevel(tenant).toString()));

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM enrollment_history WHERE establishment_id = :id")
                .param("id", tenant.id()).update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void unRoleInconnuEstRefuseParLaBase() {
        Tenant tenant = newTenant();
        UUID membership = jdbc.sql("SELECT id FROM memberships WHERE establishment_id = :id")
                .param("id", tenant.id()).query(UUID.class).single();
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO membership_roles (establishment_id, membership_id, role, granted_at)
                VALUES (:establishmentId, :membershipId, 'SUPER_ADMIN', now())
                """)
                .param("establishmentId", tenant.id())
                .param("membershipId", membership)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lesTestsTournentSurLaVersionDePostgreSQLRetenue() {
        String version = jdbc.sql("SHOW server_version").query(String.class).single();
        assertThat(version).startsWith("18.6");
    }

    @Test
    void lesMigrationsSontValidesEtRejouables() {
        flyway.validate();
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
