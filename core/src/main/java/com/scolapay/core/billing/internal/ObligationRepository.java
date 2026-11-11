package com.scolapay.core.billing.internal;

import com.scolapay.core.shared.Columns;
import com.scolapay.core.shared.PageQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ObligationRepository {

    /** Le dû vient du journal : aucune colonne ne le stocke, et rien ne peut donc le modifier directement. */
    public record Obligation(
            UUID id,
            UUID academicYearId,
            UUID studentId,
            UUID enrollmentId,
            UUID feeDefinitionId,
            UUID billingPeriodId,
            UUID catalogVersionId,
            String reference,
            String feeCode,
            String label,
            String category,
            String currency,
            long issuedAmountMinor,
            long dueMinor,
            long adjustmentsMinor,
            long settledMinor,
            boolean cancelled,
            boolean discounted,
            boolean corrected,
            String source,
            Instant issuedAt) {}

    public record NewObligation(
            UUID id,
            UUID academicYearId,
            UUID studentId,
            UUID enrollmentId,
            UUID feeDefinitionId,
            UUID catalogVersionId,
            UUID feeItemId,
            String reference,
            String feeCode,
            String label,
            String category,
            String currency,
            long amountMinor,
            String source,
            UUID issuanceRunId,
            Instant issuedAt,
            UUID issuedBy) {}

    public record InstallmentRow(int seq, LocalDate dueOn, long amountMinor) {}

    public record Entry(
            UUID id, String kind, long amountMinor, String reason, Instant recordedAt, UUID recordedBy, UUID reversesEntryId, UUID approvedBy) {

        Entry withoutConfidentialText() {
            return new Entry(id, kind, amountMinor, null, recordedAt, recordedBy, reversesEntryId, approvedBy);
        }
    }

    public record Balance(String currency, long issuedMinor, long dueMinor, LocalDate nextDueOn, long adjustmentsMinor, long settledMinor) {}

    public record Run(
            UUID id, UUID academicYearId, UUID academicUnitId, UUID catalogVersionId, int obligationsCreated, long totalMinor, Instant confirmedAt) {}

    private static final String SELECT = """
            SELECT o.id, o.academic_year_id, o.student_record_id, o.enrollment_id, o.fee_definition_id, d.billing_period_id,
                   o.catalog_version_id, o.reference, o.fee_code, o.label, o.category, o.currency, o.issued_amount_minor,
                   (SELECT COALESCE(sum(e.amount_minor), 0) FROM obligation_entries e WHERE e.obligation_id = o.id) AS due_minor,
                   (SELECT COALESCE(sum(e.amount_minor), 0) FROM obligation_entries e WHERE e.obligation_id = o.id AND e.kind NOT IN ('ISSUED','SETTLEMENT')) AS adjustments_minor,
                   (SELECT -COALESCE(sum(e.amount_minor), 0) FROM obligation_entries e WHERE e.obligation_id = o.id AND e.kind = 'SETTLEMENT') AS settled_minor,
                   EXISTS (
                     SELECT 1 FROM obligation_entries e WHERE e.obligation_id = o.id AND e.kind = 'CANCELLATION'
                       AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id = e.id)) AS cancelled,
                   EXISTS (
                     SELECT 1 FROM obligation_entries e WHERE e.obligation_id = o.id AND e.kind = 'DISCOUNT'
                       AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id = e.id)) AS discounted,
                   EXISTS (
                     SELECT 1 FROM obligation_entries e WHERE e.obligation_id = o.id AND e.kind = 'CORRECTION'
                       AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id = e.id)) AS corrected,
                   o.source, o.issued_at
            FROM obligations o
            JOIN fee_definitions d ON d.establishment_id = o.establishment_id AND d.id = o.fee_definition_id
            """;

    private final JdbcClient jdbc;

    public ObligationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** La contrainte tranche, y compris en concurrence : si la charge est déjà émise, rien n'est créé. */
    public boolean insertIfAbsent(UUID establishmentId, NewObligation obligation) {
        return jdbc.sql("""
                INSERT INTO obligations
                  (id, establishment_id, academic_year_id, student_record_id, enrollment_id, fee_definition_id, catalog_version_id,
                   fee_item_id, reference, fee_code, label, category, currency, issued_amount_minor, source, issuance_run_id,
                   issued_at, issued_by)
                VALUES
                  (:id, :establishmentId, :yearId, :studentId, :enrollmentId, :definitionId, :versionId,
                   :itemId, :reference, :feeCode, :label, :category, :currency, :amountMinor, :source, :runId,
                   :issuedAt, :issuedBy)
                ON CONFLICT ON CONSTRAINT obligations_one_per_charge DO NOTHING
                """)
                .param("id", obligation.id())
                .param("establishmentId", establishmentId)
                .param("yearId", obligation.academicYearId())
                .param("studentId", obligation.studentId())
                .param("enrollmentId", obligation.enrollmentId())
                .param("definitionId", obligation.feeDefinitionId())
                .param("versionId", obligation.catalogVersionId())
                .param("itemId", obligation.feeItemId())
                .param("reference", obligation.reference())
                .param("feeCode", obligation.feeCode())
                .param("label", obligation.label())
                .param("category", obligation.category())
                .param("currency", obligation.currency())
                .param("amountMinor", obligation.amountMinor())
                .param("source", obligation.source())
                .param("runId", obligation.issuanceRunId())
                .param("issuedAt", Columns.timestamp(obligation.issuedAt()))
                .param("issuedBy", obligation.issuedBy())
                .update() == 1;
    }

    public void insertInstallment(UUID establishmentId, UUID obligationId, int seq, LocalDate dueOn, long amountMinor) {
        jdbc.sql("""
                INSERT INTO obligation_installments (obligation_id, seq, establishment_id, due_on, amount_minor)
                VALUES (:obligationId, :seq, :establishmentId, :dueOn, :amountMinor)
                """)
                .param("obligationId", obligationId)
                .param("seq", seq)
                .param("establishmentId", establishmentId)
                .param("dueOn", dueOn)
                .param("amountMinor", amountMinor)
                .update();
    }

    public void insertIssuedEntry(UUID establishmentId, UUID obligationId, long amountMinor, Instant recordedAt, UUID recordedBy) {
        jdbc.sql("""
                INSERT INTO obligation_entries (id, establishment_id, obligation_id, kind, amount_minor, idempotency_key, recorded_at, recorded_by)
                VALUES (:id, :establishmentId, :obligationId, 'ISSUED', :amountMinor, :key, :recordedAt, :recordedBy)
                """)
                .param("id", UUID.randomUUID())
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .param("amountMinor", amountMinor)
                .param("key", "issued:" + obligationId)
                .param("recordedAt", Columns.timestamp(recordedAt))
                .param("recordedBy", recordedBy)
                .update();
    }

    public void insertRun(UUID establishmentId, Run run, UUID confirmedBy) {
        jdbc.sql("""
                INSERT INTO issuance_runs
                  (id, establishment_id, academic_year_id, academic_unit_id, catalog_version_id, obligations_created, total_minor, confirmed_at, confirmed_by)
                VALUES (:id, :establishmentId, :yearId, :unitId, :versionId, :created, :total, :confirmedAt, :confirmedBy)
                """)
                .param("id", run.id())
                .param("establishmentId", establishmentId)
                .param("yearId", run.academicYearId())
                .param("unitId", run.academicUnitId())
                .param("versionId", run.catalogVersionId())
                .param("created", run.obligationsCreated())
                .param("total", run.totalMinor())
                .param("confirmedAt", Columns.timestamp(run.confirmedAt()))
                .param("confirmedBy", confirmedBy)
                .update();
    }

    public List<Run> listRuns(UUID establishmentId, UUID academicYearId) {
        return jdbc.sql("""
                SELECT id, academic_year_id, academic_unit_id, catalog_version_id, obligations_created, total_minor, confirmed_at
                FROM issuance_runs
                WHERE establishment_id = :establishmentId AND academic_year_id = :yearId
                ORDER BY confirmed_at DESC, id
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .query((rs, rowNum) -> new Run(
                        Columns.uuid(rs, "id"),
                        Columns.uuid(rs, "academic_year_id"),
                        Columns.uuid(rs, "academic_unit_id"),
                        Columns.uuid(rs, "catalog_version_id"),
                        rs.getInt("obligations_created"),
                        rs.getLong("total_minor"),
                        Columns.instant(rs, "confirmed_at")))
                .list();
    }

    /** Les charges déjà émises de l'année, sous la forme dossier puis charge. */
    public Set<List<UUID>> issuedCharges(UUID establishmentId, UUID academicYearId) {
        Set<List<UUID>> issued = new HashSet<>();
        jdbc.sql("""
                SELECT student_record_id, fee_definition_id FROM obligations
                WHERE establishment_id = :establishmentId AND academic_year_id = :yearId
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .query(rs -> {
                    issued.add(List.of(Columns.uuid(rs, "student_record_id"), Columns.uuid(rs, "fee_definition_id")));
                });
        return issued;
    }

    public Optional<Obligation> find(UUID establishmentId, UUID id) {
        return jdbc.sql(SELECT + "WHERE o.establishment_id = :establishmentId AND o.id = :id")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(ObligationRepository::obligation)
                .optional();
    }

    public Optional<Obligation> findByCharge(UUID establishmentId, UUID studentId, UUID feeDefinitionId) {
        return jdbc.sql(SELECT + """
                WHERE o.establishment_id = :establishmentId AND o.student_record_id = :studentId AND o.fee_definition_id = :definitionId
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .param("definitionId", feeDefinitionId)
                .query(ObligationRepository::obligation)
                .optional();
    }

    public List<Obligation> list(UUID establishmentId, UUID academicYearId, UUID studentId, PageQuery page) {
        String filter = studentId == null ? "" : "AND o.student_record_id = :studentId\n";
        JdbcClient.StatementSpec statement = jdbc.sql(SELECT + """
                WHERE o.establishment_id = :establishmentId AND o.academic_year_id = :yearId
                """ + filter + """
                ORDER BY o.issued_at DESC, o.id
                LIMIT :limit OFFSET :offset
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .param("limit", page.limit())
                .param("offset", page.offset());
        if (studentId != null) {
            statement = statement.param("studentId", studentId);
        }
        return statement.query(ObligationRepository::obligation).list();
    }

    public List<Obligation> listForReview(UUID establishmentId, UUID academicYearId, int limit) {
        return jdbc.sql(SELECT + """
                WHERE o.establishment_id = :establishmentId AND o.academic_year_id = :yearId
                ORDER BY o.issued_at, o.id
                LIMIT :limit
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .param("limit", limit)
                .query(ObligationRepository::obligation)
                .list();
    }

    public List<InstallmentRow> installments(UUID establishmentId, UUID obligationId) {
        return jdbc.sql("""
                SELECT seq, due_on, amount_minor FROM obligation_installments
                WHERE establishment_id = :establishmentId AND obligation_id = :id
                ORDER BY seq
                """)
                .param("establishmentId", establishmentId)
                .param("id", obligationId)
                .query((rs, rowNum) -> new InstallmentRow(
                        rs.getInt("seq"), rs.getObject("due_on", LocalDate.class), rs.getLong("amount_minor")))
                .list();
    }

    public List<Entry> entries(UUID establishmentId, UUID obligationId) {
        return jdbc.sql("""
                SELECT id, kind, amount_minor, reason, recorded_at, recorded_by, reverses_entry_id, approved_by FROM obligation_entries
                WHERE establishment_id = :establishmentId AND obligation_id = :id
                ORDER BY recorded_at, id
                """)
                .param("establishmentId", establishmentId)
                .param("id", obligationId)
                .query((rs, rowNum) -> new Entry(
                        Columns.uuid(rs, "id"),
                        rs.getString("kind"),
                        rs.getLong("amount_minor"),
                        rs.getString("reason"),
                        Columns.instant(rs, "recorded_at"),
                        Columns.uuid(rs, "recorded_by"),
                        Columns.uuid(rs, "reverses_entry_id"),
                        Columns.uuid(rs, "approved_by")))
                .list();
    }

    public long journalSize(UUID establishmentId, UUID obligationId) {
        return jdbc.sql("SELECT count(*) FROM obligation_entries WHERE establishment_id=:tenant AND obligation_id=:id")
                .param("tenant", establishmentId).param("id", obligationId).query(Long.class).single();
    }

    /** Par devise : deux devises ne s'additionnent jamais. */
    public List<Balance> balances(UUID establishmentId, UUID studentId, LocalDate today) {
        return jdbc.sql("""
                SELECT o.currency,
                       CAST(sum(o.issued_amount_minor) AS bigint) AS issued_minor,
                       CAST(sum(b.due_minor) AS bigint) AS due_minor,
                       CAST(sum(b.adjustments_minor) AS bigint) AS adjustments_minor,
                       CAST(sum(b.settled_minor) AS bigint) AS settled_minor,
                       min(n.next_due) AS next_due_on
                FROM obligations o
                JOIN LATERAL (
                  SELECT COALESCE(sum(e.amount_minor), 0) AS due_minor,
                         COALESCE(sum(e.amount_minor) FILTER (WHERE e.kind NOT IN ('ISSUED','SETTLEMENT')),0) AS adjustments_minor,
                         -COALESCE(sum(e.amount_minor) FILTER (WHERE e.kind='SETTLEMENT'),0) AS settled_minor
                  FROM obligation_entries e WHERE e.obligation_id = o.id
                ) b ON true
                LEFT JOIN LATERAL (
                  SELECT min(i.due_on) AS next_due FROM (
                    SELECT due_on,amount_minor,sum(amount_minor) OVER (ORDER BY due_on,seq) AS cumulative
                    FROM obligation_installments WHERE obligation_id=o.id
                  ) i
                  WHERE i.due_on >= :today AND b.due_minor > 0 AND (
                    EXISTS (SELECT 1 FROM obligation_entries e WHERE e.obligation_id=o.id AND e.kind IN ('CORRECTION','CANCELLATION')
                      AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id=e.id))
                    OR LEAST(i.cumulative,o.issued_amount_minor+b.adjustments_minor) > GREATEST(i.cumulative-i.amount_minor,b.settled_minor))
                ) n ON true
                WHERE o.establishment_id = :establishmentId AND o.student_record_id = :studentId
                GROUP BY o.currency
                ORDER BY o.currency
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .param("today", today)
                .query((rs, rowNum) -> new Balance(
                        rs.getString("currency"),
                        rs.getLong("issued_minor"),
                        rs.getLong("due_minor"),
                        rs.getObject("next_due_on", LocalDate.class), rs.getLong("adjustments_minor"), rs.getLong("settled_minor")))
                .list();
    }

    private static Obligation obligation(ResultSet rs, int rowNum) throws SQLException {
        return new Obligation(
                Columns.uuid(rs, "id"),
                Columns.uuid(rs, "academic_year_id"),
                Columns.uuid(rs, "student_record_id"),
                Columns.uuid(rs, "enrollment_id"),
                Columns.uuid(rs, "fee_definition_id"),
                Columns.uuid(rs, "billing_period_id"),
                Columns.uuid(rs, "catalog_version_id"),
                rs.getString("reference"),
                rs.getString("fee_code"),
                rs.getString("label"),
                rs.getString("category"),
                rs.getString("currency"),
                rs.getLong("issued_amount_minor"),
                rs.getLong("due_minor"),
                rs.getLong("adjustments_minor"),
                rs.getLong("settled_minor"),
                rs.getBoolean("cancelled"),
                rs.getBoolean("discounted"),
                rs.getBoolean("corrected"),
                rs.getString("source"),
                Columns.instant(rs, "issued_at"));
    }
}
