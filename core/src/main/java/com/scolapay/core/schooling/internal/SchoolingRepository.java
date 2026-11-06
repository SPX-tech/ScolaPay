package com.scolapay.core.schooling.internal;

import com.scolapay.core.shared.Columns;
import com.scolapay.core.shared.PageQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Chaque méthode exige l'établissement : aucune lecture de dossier ne peut l'oublier. */
@Repository
public class SchoolingRepository {

    public record AcademicYear(UUID id, UUID establishmentId, String label, LocalDate startsOn, LocalDate endsOn, String status) {}

    public record Student(
            UUID id, UUID establishmentId, String studentNumber, String fullName, LocalDate birthDate, String status) {}

    public record Enrollment(
            UUID id,
            UUID studentId,
            UUID academicYearId,
            String academicYearLabel,
            UUID academicUnitId,
            String level,
            String status,
            Instant enrolledAt,
            Instant endedAt) {}

    public record HistoryEntry(String fromStatus, String toStatus, String reason, UUID actorAccountId, Instant occurredAt) {}

    public record Grant(
            UUID id,
            UUID establishmentId,
            UUID studentId,
            UUID accountId,
            String kind,
            String relationship,
            boolean academicAccess,
            boolean financialContributor,
            Instant academicConsentAt,
            Instant grantedAt,
            Instant revokedAt) {}

    private static final String GRANT_COLUMNS = """
            id, establishment_id, student_record_id, account_id, kind, relationship,
            academic_access, financial_contributor, academic_consent_at, granted_at, revoked_at
            """;

    private final JdbcClient jdbc;

    public SchoolingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Années académiques

    public void insertYear(AcademicYear year, Instant now) {
        jdbc.sql("""
                INSERT INTO academic_years (id, establishment_id, label, starts_on, ends_on, status, created_at, updated_at)
                VALUES (:id, :establishmentId, :label, :startsOn, :endsOn, :status, :now, :now)
                """)
                .param("id", year.id())
                .param("establishmentId", year.establishmentId())
                .param("label", year.label())
                .param("startsOn", year.startsOn())
                .param("endsOn", year.endsOn())
                .param("status", year.status())
                .param("now", Columns.timestamp(now))
                .update();
    }

    public Optional<AcademicYear> findYear(UUID establishmentId, UUID id) {
        return jdbc.sql("""
                SELECT id, establishment_id, label, starts_on, ends_on, status
                FROM academic_years WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(SchoolingRepository::year)
                .optional();
    }

    public List<AcademicYear> listYears(UUID establishmentId) {
        return jdbc.sql("""
                SELECT id, establishment_id, label, starts_on, ends_on, status
                FROM academic_years WHERE establishment_id = :establishmentId
                ORDER BY starts_on DESC
                """)
                .param("establishmentId", establishmentId)
                .query(SchoolingRepository::year)
                .list();
    }

    /** La transition n'a lieu que depuis le statut attendu, même en concurrence. */
    public int transitionYear(UUID establishmentId, UUID id, String from, String to, Instant now) {
        return jdbc.sql("""
                UPDATE academic_years SET status = :to, updated_at = :now
                WHERE establishment_id = :establishmentId AND id = :id AND status = :from
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("from", from)
                .param("to", to)
                .param("now", Columns.timestamp(now))
                .update();
    }

    // Dossiers

    public void insertStudent(Student student, Instant now) {
        jdbc.sql("""
                INSERT INTO student_records
                  (id, establishment_id, student_number, full_name, birth_date, status, created_at, updated_at)
                VALUES (:id, :establishmentId, :studentNumber, :fullName, :birthDate, :status, :now, :now)
                """)
                .param("id", student.id())
                .param("establishmentId", student.establishmentId())
                .param("studentNumber", student.studentNumber())
                .param("fullName", student.fullName())
                .param("birthDate", student.birthDate())
                .param("status", student.status())
                .param("now", Columns.timestamp(now))
                .update();
    }

    public Optional<Student> findStudent(UUID establishmentId, UUID id) {
        return jdbc.sql("""
                SELECT id, establishment_id, student_number, full_name, birth_date, status
                FROM student_records WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(SchoolingRepository::student)
                .optional();
    }

    public List<Student> listStudents(UUID establishmentId, String search, PageQuery page) {
        String filter = search == null ? "" : """
                AND (full_name ILIKE :pattern ESCAPE '\\' OR student_number ILIKE :pattern ESCAPE '\\')
                """;
        JdbcClient.StatementSpec statement = jdbc.sql("""
                SELECT id, establishment_id, student_number, full_name, birth_date, status
                FROM student_records
                WHERE establishment_id = :establishmentId
                """ + filter + """
                ORDER BY full_name, id
                LIMIT :limit OFFSET :offset
                """)
                .param("establishmentId", establishmentId)
                .param("limit", page.limit())
                .param("offset", page.offset());
        if (search != null) {
            statement = statement.param("pattern", "%" + escapeLike(search) + "%");
        }
        return statement.query(SchoolingRepository::student).list();
    }

    // Inscriptions

    public void insertEnrollment(
            UUID id, UUID establishmentId, UUID studentId, UUID academicYearId, UUID academicUnitId, String level, Instant now) {
        jdbc.sql("""
                INSERT INTO enrollments (id, establishment_id, student_record_id, academic_year_id, academic_unit_id, level, status, enrolled_at)
                VALUES (:id, :establishmentId, :studentId, :academicYearId, :academicUnitId, :level, 'ACTIVE', :now)
                """)
                .param("id", id)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .param("academicYearId", academicYearId)
                .param("academicUnitId", academicUnitId)
                .param("level", level)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public int endEnrollment(UUID establishmentId, UUID studentId, UUID id, String status, Instant now) {
        return jdbc.sql("""
                UPDATE enrollments SET status = :status, ended_at = :now
                WHERE establishment_id = :establishmentId AND student_record_id = :studentId
                  AND id = :id AND status = 'ACTIVE'
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .param("id", id)
                .param("status", status)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public List<Enrollment> listEnrollments(UUID establishmentId, UUID studentId) {
        return jdbc.sql("""
                SELECT e.id, e.student_record_id, e.academic_year_id, y.label, e.academic_unit_id, e.level, e.status, e.enrolled_at, e.ended_at
                FROM enrollments e
                JOIN academic_years y ON y.establishment_id = e.establishment_id AND y.id = e.academic_year_id
                WHERE e.establishment_id = :establishmentId AND e.student_record_id = :studentId
                ORDER BY e.enrolled_at DESC, e.id
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new Enrollment(
                        Columns.uuid(rs, "id"),
                        Columns.uuid(rs, "student_record_id"),
                        Columns.uuid(rs, "academic_year_id"),
                        rs.getString("label"),
                        Columns.uuid(rs, "academic_unit_id"),
                        rs.getString("level"),
                        rs.getString("status"),
                        Columns.instant(rs, "enrolled_at"),
                        Columns.instant(rs, "ended_at")))
                .list();
    }

    public void appendHistory(
            UUID establishmentId,
            UUID enrollmentId,
            String fromStatus,
            String toStatus,
            String reason,
            UUID actorAccountId,
            Instant now) {
        jdbc.sql("""
                INSERT INTO enrollment_history
                  (id, establishment_id, enrollment_id, from_status, to_status, reason, actor_account_id, occurred_at)
                VALUES (:id, :establishmentId, :enrollmentId, :fromStatus, :toStatus, :reason, :actor, :now)
                """)
                .param("id", UUID.randomUUID())
                .param("establishmentId", establishmentId)
                .param("enrollmentId", enrollmentId)
                .param("fromStatus", fromStatus)
                .param("toStatus", toStatus)
                .param("reason", reason)
                .param("actor", actorAccountId)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public List<HistoryEntry> history(UUID establishmentId, UUID studentId, UUID enrollmentId) {
        return jdbc.sql("""
                SELECT h.from_status, h.to_status, h.reason, h.actor_account_id, h.occurred_at
                FROM enrollment_history h
                JOIN enrollments e ON e.establishment_id = h.establishment_id AND e.id = h.enrollment_id
                WHERE h.establishment_id = :establishmentId
                  AND e.student_record_id = :studentId
                  AND h.enrollment_id = :enrollmentId
                ORDER BY h.occurred_at, h.id
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .param("enrollmentId", enrollmentId)
                .query((rs, rowNum) -> new HistoryEntry(
                        rs.getString("from_status"),
                        rs.getString("to_status"),
                        rs.getString("reason"),
                        Columns.uuid(rs, "actor_account_id"),
                        Columns.instant(rs, "occurred_at")))
                .list();
    }

    // Accès des comptes aux dossiers

    public void insertGrant(Grant grant, UUID grantedBy) {
        jdbc.sql("""
                INSERT INTO record_access_grants
                  (id, establishment_id, student_record_id, account_id, kind, relationship,
                   academic_access, financial_contributor, granted_at, granted_by)
                VALUES
                  (:id, :establishmentId, :studentId, :accountId, :kind, :relationship,
                   :academicAccess, :financialContributor, :grantedAt, :grantedBy)
                """)
                .param("id", grant.id())
                .param("establishmentId", grant.establishmentId())
                .param("studentId", grant.studentId())
                .param("accountId", grant.accountId())
                .param("kind", grant.kind())
                .param("relationship", grant.relationship())
                .param("academicAccess", grant.academicAccess())
                .param("financialContributor", grant.financialContributor())
                .param("grantedAt", Columns.timestamp(grant.grantedAt()))
                .param("grantedBy", grantedBy)
                .update();
    }

    public int revokeGrant(UUID establishmentId, UUID studentId, UUID grantId, UUID actorAccountId, Instant now) {
        return jdbc.sql("""
                UPDATE record_access_grants SET revoked_at = :now, revoked_by = :actor
                WHERE establishment_id = :establishmentId AND student_record_id = :studentId
                  AND id = :id AND revoked_at IS NULL
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .param("id", grantId)
                .param("actor", actorAccountId)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public List<Grant> grantsOfStudent(UUID establishmentId, UUID studentId) {
        return jdbc.sql("SELECT " + GRANT_COLUMNS + """
                FROM record_access_grants
                WHERE establishment_id = :establishmentId AND student_record_id = :studentId
                ORDER BY granted_at, id
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .query(SchoolingRepository::grant)
                .list();
    }

    /** Point d'entrée de la surface familles : part du compte, jamais d'un établissement fourni par le client. */
    public List<Grant> activeGrantsOfAccount(UUID accountId) {
        return jdbc.sql("SELECT " + GRANT_COLUMNS + """
                FROM record_access_grants
                WHERE account_id = :accountId AND revoked_at IS NULL
                ORDER BY granted_at, id
                """)
                .param("accountId", accountId)
                .query(SchoolingRepository::grant)
                .list();
    }

    public Optional<Grant> activeGrant(UUID accountId, UUID studentId) {
        return jdbc.sql("SELECT " + GRANT_COLUMNS + """
                FROM record_access_grants
                WHERE account_id = :accountId AND student_record_id = :studentId AND revoked_at IS NULL
                """)
                .param("accountId", accountId)
                .param("studentId", studentId)
                .query(SchoolingRepository::grant)
                .optional();
    }

    /** Le consentement ne concerne que les tiers : le lien SELF n'en a pas besoin. */
    public int setAcademicConsent(
            UUID establishmentId, UUID studentId, UUID grantId, Instant consentAt, UUID consentBy) {
        return jdbc.sql("""
                UPDATE record_access_grants
                SET academic_consent_at = :consentAt, academic_consent_by = :consentBy
                WHERE establishment_id = :establishmentId AND student_record_id = :studentId
                  AND id = :id AND kind = 'THIRD_PARTY' AND revoked_at IS NULL
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .param("id", grantId)
                .param("consentAt", Columns.timestamp(consentAt))
                .param("consentBy", consentBy)
                .update();
    }

    public List<UUID> financialContributorAccounts(UUID establishmentId, UUID studentId) {
        return jdbc.sql("""
                SELECT account_id FROM record_access_grants
                WHERE establishment_id = :establishmentId AND student_record_id = :studentId
                  AND financial_contributor AND revoked_at IS NULL
                ORDER BY granted_at, id
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .query((rs, rowNum) -> Columns.uuid(rs, "account_id"))
                .list();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static AcademicYear year(ResultSet rs, int rowNum) throws SQLException {
        return new AcademicYear(
                Columns.uuid(rs, "id"),
                Columns.uuid(rs, "establishment_id"),
                rs.getString("label"),
                rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class),
                rs.getString("status"));
    }

    private static Student student(ResultSet rs, int rowNum) throws SQLException {
        return new Student(
                Columns.uuid(rs, "id"),
                Columns.uuid(rs, "establishment_id"),
                rs.getString("student_number"),
                rs.getString("full_name"),
                rs.getObject("birth_date", LocalDate.class),
                rs.getString("status"));
    }

    private static Grant grant(ResultSet rs, int rowNum) throws SQLException {
        return new Grant(
                Columns.uuid(rs, "id"),
                Columns.uuid(rs, "establishment_id"),
                Columns.uuid(rs, "student_record_id"),
                Columns.uuid(rs, "account_id"),
                rs.getString("kind"),
                rs.getString("relationship"),
                rs.getBoolean("academic_access"),
                rs.getBoolean("financial_contributor"),
                Columns.instant(rs, "academic_consent_at"),
                Columns.instant(rs, "granted_at"),
                Columns.instant(rs, "revoked_at"));
    }
}
