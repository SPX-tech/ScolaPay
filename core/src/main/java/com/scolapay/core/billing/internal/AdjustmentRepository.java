package com.scolapay.core.billing.internal;

import com.scolapay.core.shared.Columns;
import com.scolapay.core.shared.PageQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdjustmentRepository {

    public record Request(
            UUID id,
            UUID obligationId,
            String kind,
            long amountMinor,
            long dueAtRequestMinor,
            int journalSizeAtRequest,
            boolean outdated,
            UUID reversesEntryId,
            String reason,
            String status,
            Instant requestedAt,
            UUID requestedBy,
            Instant decidedAt,
            UUID decidedBy,
            String decisionNote,
            UUID entryId) {

        boolean pending() {
            return "PENDING".equals(status);
        }

        Request withoutConfidentialText() {
            return new Request(
                    id, obligationId, kind, amountMinor, dueAtRequestMinor, journalSizeAtRequest, outdated, reversesEntryId, null, status,
                    requestedAt, requestedBy, decidedAt, decidedBy, null, entryId);
        }
    }

    public record ReviewDecision(
            String signal, String fingerprint, String decision, String reason, UUID entryId, Instant decidedAt, UUID decidedBy) {

        ReviewDecision withoutConfidentialText() {
            return new ReviewDecision(signal, fingerprint, decision, null, entryId, decidedAt, decidedBy);
        }
    }

    public record NewEntry(
            UUID id, UUID obligationId, String kind, long amountMinor, String reason, UUID reversesEntryId, String idempotencyKey,
            Instant recordedAt, UUID recordedBy, UUID approvedBy) {}

    private static final String REQUEST_COLUMNS = """
            id, obligation_id, kind, amount_minor, due_at_request_minor, journal_size_at_request, reverses_entry_id, reason, status,
            requested_at, requested_by, decided_at, decided_by, decision_note, entry_id,
            status = 'PENDING' AND journal_size_at_request <> (
              SELECT count(*) FROM obligation_entries e WHERE e.obligation_id = adjustment_requests.obligation_id) AS outdated
            """;

    private final JdbcClient jdbc;

    public AdjustmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Sérialise les opérations d'une obligation : les contrôles qui suivent portent sur un dû à jour. */
    public boolean lockObligation(UUID establishmentId, UUID obligationId) {
        return jdbc.sql("SELECT 1 FROM obligations WHERE establishment_id = :establishmentId AND id = :id FOR UPDATE")
                .param("establishmentId", establishmentId)
                .param("id", obligationId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    public void insertEntry(UUID establishmentId, NewEntry entry) {
        jdbc.sql("""
                INSERT INTO obligation_entries
                  (id, establishment_id, obligation_id, kind, amount_minor, reason, reverses_entry_id, idempotency_key, recorded_at, recorded_by, approved_by)
                VALUES
                  (:id, :establishmentId, :obligationId, :kind, :amountMinor, :reason, :reverses, :key, :recordedAt, :recordedBy, :approvedBy)
                """)
                .param("id", entry.id())
                .param("establishmentId", establishmentId)
                .param("obligationId", entry.obligationId())
                .param("kind", entry.kind())
                .param("amountMinor", entry.amountMinor())
                .param("reason", entry.reason())
                .param("reverses", entry.reversesEntryId())
                .param("key", entry.idempotencyKey())
                .param("recordedAt", Columns.timestamp(entry.recordedAt()))
                .param("recordedBy", entry.recordedBy())
                .param("approvedBy", entry.approvedBy())
                .update();
    }

    public Optional<UUID> entryIdByKey(UUID establishmentId, UUID obligationId, String key) {
        return jdbc.sql("""
                SELECT id FROM obligation_entries
                WHERE establishment_id = :establishmentId AND obligation_id = :obligationId AND idempotency_key = :key
                """)
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .param("key", key)
                .query(UUID.class)
                .optional();
    }

    public boolean keyUsedElsewhere(UUID establishmentId, UUID obligationId, String key) {
        return jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM obligation_entries WHERE establishment_id = :establishmentId AND idempotency_key = :key AND obligation_id <> :obligationId
                  UNION ALL
                  SELECT 1 FROM adjustment_requests WHERE establishment_id = :establishmentId AND idempotency_key = :key AND obligation_id <> :obligationId)
                """)
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .param("key", key)
                .query(Boolean.class)
                .single();
    }

    public boolean alreadyReversed(UUID establishmentId, UUID entryId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM obligation_entries WHERE establishment_id = :establishmentId AND reverses_entry_id = :id)")
                .param("establishmentId", establishmentId)
                .param("id", entryId)
                .query(Boolean.class)
                .single();
    }

    public void insertRequest(UUID establishmentId, Request request, String key) {
        jdbc.sql("""
                INSERT INTO adjustment_requests
                  (id, establishment_id, obligation_id, kind, amount_minor, due_at_request_minor, journal_size_at_request,
                   reverses_entry_id, reason, idempotency_key, requested_at, requested_by)
                VALUES (:id, :establishmentId, :obligationId, :kind, :amountMinor, :dueAtRequest, :journalSize,
                   :reverses, :reason, :key, :requestedAt, :requestedBy)
                """)
                .param("id", request.id())
                .param("establishmentId", establishmentId)
                .param("obligationId", request.obligationId())
                .param("kind", request.kind())
                .param("amountMinor", request.amountMinor())
                .param("dueAtRequest", request.dueAtRequestMinor())
                .param("journalSize", request.journalSizeAtRequest())
                .param("reverses", request.reversesEntryId())
                .param("reason", request.reason())
                .param("key", key)
                .param("requestedAt", Columns.timestamp(request.requestedAt()))
                .param("requestedBy", request.requestedBy())
                .update();
    }

    public Optional<Request> findRequest(UUID establishmentId, UUID id) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + " FROM adjustment_requests WHERE establishment_id = :establishmentId AND id = :id")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(AdjustmentRepository::request)
                .optional();
    }

    public Optional<Request> requestByKey(UUID establishmentId, UUID obligationId, String key) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + """
                 FROM adjustment_requests
                WHERE establishment_id = :establishmentId AND obligation_id = :obligationId AND idempotency_key = :key
                """)
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .param("key", key)
                .query(AdjustmentRepository::request)
                .optional();
    }

    public boolean hasPendingRequest(UUID establishmentId, UUID obligationId) {
        return jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM adjustment_requests
                  WHERE establishment_id = :establishmentId AND obligation_id = :obligationId AND status = 'PENDING')
                """)
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .query(Boolean.class)
                .single();
    }

    public List<Request> listRequests(UUID establishmentId, String status, PageQuery page) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + """
                 FROM adjustment_requests
                WHERE establishment_id = :establishmentId AND status = :status
                ORDER BY requested_at, id
                LIMIT :limit OFFSET :offset
                """)
                .param("establishmentId", establishmentId)
                .param("status", status)
                .param("limit", page.limit())
                .param("offset", page.offset())
                .query(AdjustmentRepository::request)
                .list();
    }

    public void markApproved(UUID establishmentId, UUID id, UUID decidedBy, Instant now, UUID entryId) {
        jdbc.sql("""
                UPDATE adjustment_requests SET status = 'APPROVED', decided_at = :now, decided_by = :decidedBy, entry_id = :entryId
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("now", Columns.timestamp(now))
                .param("decidedBy", decidedBy)
                .param("entryId", entryId)
                .update();
    }

    public void markRejected(UUID establishmentId, UUID id, UUID decidedBy, Instant now, String note) {
        jdbc.sql("""
                UPDATE adjustment_requests SET status = 'REJECTED', decided_at = :now, decided_by = :decidedBy, decision_note = :note
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("now", Columns.timestamp(now))
                .param("decidedBy", decidedBy)
                .param("note", note)
                .update();
    }

    public void markWithdrawn(UUID establishmentId, UUID id, UUID author, Instant now, String note) {
        jdbc.sql("""
                UPDATE adjustment_requests SET status = 'WITHDRAWN', decided_at = :now, decided_by = :author, decision_note = :note
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("now", Columns.timestamp(now))
                .param("author", author)
                .param("note", note)
                .update();
    }

    // Décisions de réexamen

    public List<ReviewDecision> decisionsOf(UUID establishmentId, UUID obligationId) {
        return jdbc.sql("""
                SELECT signal, fingerprint, decision, reason, entry_id, decided_at, decided_by
                FROM obligation_review_decisions
                WHERE establishment_id = :establishmentId AND obligation_id = :obligationId
                ORDER BY decided_at, id
                """)
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .query((rs, rowNum) -> new ReviewDecision(
                        rs.getString("signal"),
                        rs.getString("fingerprint"),
                        rs.getString("decision"),
                        rs.getString("reason"),
                        Columns.uuid(rs, "entry_id"),
                        Columns.instant(rs, "decided_at"),
                        Columns.uuid(rs, "decided_by")))
                .list();
    }

    public void insertDecision(
            UUID establishmentId, UUID obligationId, String signal, String fingerprint, String decision, String reason, UUID entryId, Instant now, UUID decidedBy) {
        jdbc.sql("""
                INSERT INTO obligation_review_decisions
                  (id, establishment_id, obligation_id, signal, fingerprint, decision, reason, entry_id, decided_at, decided_by)
                VALUES (:id, :establishmentId, :obligationId, :signal, :fingerprint, :decision, :reason, :entryId, :now, :decidedBy)
                """)
                .param("id", UUID.randomUUID())
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .param("signal", signal)
                .param("fingerprint", fingerprint)
                .param("decision", decision)
                .param("reason", reason)
                .param("entryId", entryId)
                .param("now", Columns.timestamp(now))
                .param("decidedBy", decidedBy)
                .update();
    }

    /** Les situations déjà décidées, sous la forme obligation, signal, empreinte. */
    public Set<List<String>> decidedSignals(UUID establishmentId) {
        Set<List<String>> decided = new HashSet<>();
        jdbc.sql("SELECT obligation_id, signal, fingerprint FROM obligation_review_decisions WHERE establishment_id = :establishmentId")
                .param("establishmentId", establishmentId)
                .query(rs -> {
                    decided.add(List.of(Columns.uuid(rs, "obligation_id").toString(), rs.getString("signal"), rs.getString("fingerprint")));
                });
        return decided;
    }

    private static Request request(ResultSet rs, int rowNum) throws SQLException {
        return new Request(
                Columns.uuid(rs, "id"),
                Columns.uuid(rs, "obligation_id"),
                rs.getString("kind"),
                rs.getLong("amount_minor"),
                rs.getLong("due_at_request_minor"),
                rs.getInt("journal_size_at_request"),
                rs.getBoolean("outdated"),
                Columns.uuid(rs, "reverses_entry_id"),
                rs.getString("reason"),
                rs.getString("status"),
                Columns.instant(rs, "requested_at"),
                Columns.uuid(rs, "requested_by"),
                Columns.instant(rs, "decided_at"),
                Columns.uuid(rs, "decided_by"),
                rs.getString("decision_note"),
                Columns.uuid(rs, "entry_id"));
    }
}
