package com.scolapay.core.notifications.internal;

import com.scolapay.core.notifications.Notifications;
import com.scolapay.core.shared.Columns;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class NotificationRepository {
    record Claim(UUID id, String category, String channel, String transport, String encryptedPayload,
                 Instant expiresAt, int attempts, Instant firstAttemptAt, int retryWindowSeconds, UUID leaseToken) {
        @Override public String toString() { return "NotificationClaim[id=" + id + "]"; }
    }

    private final JdbcClient jdbc;
    NotificationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    boolean insert(Notifications.Request request, String transport, String fingerprint, String payload, int window, Instant now) {
        int inserted = jdbc.sql("""
                INSERT INTO notification_messages
                    (id, category, channel, transport, intent_fingerprint, encrypted_payload, expires_at,
                     retry_window_seconds, next_attempt_at, created_at, updated_at)
                VALUES (:id, :category, :channel, :transport, :fingerprint, :payload, :expires, :window, :now, :now, :now)
                ON CONFLICT (id) DO NOTHING
                """).param("id", request.id()).param("category", request.category()).param("channel", request.channel().name())
                .param("transport", transport).param("fingerprint", fingerprint).param("payload", payload)
                .param("expires", Columns.timestamp(request.expiresAt())).param("window", window)
                .param("now", Columns.timestamp(now)).update();
        if (inserted == 0) {
            String previous = jdbc.sql("SELECT intent_fingerprint FROM notification_messages WHERE id=:id")
                    .param("id", request.id()).query(String.class).single();
            if (!previous.equals(fingerprint)) throw new IllegalStateException("NOTIFICATION_INTENT_CONFLICT");
        }
        return inserted == 1;
    }

    void ready(UUID id, Instant now) {
        jdbc.sql("UPDATE notification_messages SET state='READY', updated_at=:now WHERE id=:id AND state='QUEUED'")
                .param("id", id).param("now", Columns.timestamp(now)).update();
    }

    Optional<Claim> claim(Instant now) {
        UUID lease = UUID.randomUUID();
        return jdbc.sql("""
                WITH candidate AS (
                    SELECT id FROM notification_messages
                    WHERE (state IN ('READY','RETRY','BLOCKED') AND next_attempt_at <= :now)
                       OR (state='PROCESSING' AND lease_until <= :now)
                    ORDER BY next_attempt_at, id
                    LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE notification_messages n SET
                    state='PROCESSING', attempts=n.attempts+1,
                    first_attempt_at=COALESCE(n.first_attempt_at,:now),
                    lease_token=:lease, lease_until=:until, updated_at=:now
                FROM candidate c WHERE n.id=c.id
                RETURNING n.id, n.category, n.channel, n.transport, n.encrypted_payload, n.expires_at,
                          n.attempts, n.first_attempt_at, n.retry_window_seconds, n.lease_token
                """).param("now", Columns.timestamp(now)).param("lease", lease)
                .param("until", Columns.timestamp(now.plusSeconds(120)))
                .query((rs, row) -> new Claim(Columns.uuid(rs, "id"), rs.getString("category"), rs.getString("channel"),
                        rs.getString("transport"), rs.getString("encrypted_payload"), Columns.instant(rs, "expires_at"),
                        rs.getInt("attempts"), Columns.instant(rs, "first_attempt_at"), rs.getInt("retry_window_seconds"),
                        Columns.uuid(rs, "lease_token"))).optional();
    }

    boolean finish(Claim claim, String state, String code, UUID providerId, Instant nextAttempt, Instant now) {
        return jdbc.sql("""
                UPDATE notification_messages SET state=:state, failure_code=:code, provider_id=:provider,
                    encrypted_payload=CASE WHEN :terminal THEN NULL ELSE encrypted_payload END,
                    lease_token=NULL, lease_until=NULL, next_attempt_at=:next, updated_at=:now
                WHERE id=:id AND state='PROCESSING' AND lease_token=:lease
                """).param("state", state).param("code", code).param("provider", providerId)
                .param("terminal", java.util.Set.of("SUBMITTED", "FAILED", "CANCELLED", "UNKNOWN").contains(state))
                .param("next", Columns.timestamp(nextAttempt)).param("now", Columns.timestamp(now))
                .param("id", claim.id()).param("lease", claim.leaseToken()).update() == 1;
    }

    void expire(Instant now) {
        // Ne pas modifier une tentative encore en vol. Son propriétaire confirmera ou son bail expirera.
        jdbc.sql("""
                UPDATE notification_messages SET state='CANCELLED', failure_code='EXPIRED',
                    encrypted_payload=NULL, lease_token=NULL, lease_until=NULL, updated_at=:now
                WHERE expires_at <= :now AND encrypted_payload IS NOT NULL
                  AND (state <> 'PROCESSING' OR lease_until <= :now)
                """).param("now", Columns.timestamp(now)).update();
    }

    Notifications.Status status(UUID id) {
        return jdbc.sql("SELECT state, attempts, updated_at, failure_code FROM notification_messages WHERE id=:id")
                .param("id", id).query((rs, row) -> new Notifications.Status(rs.getString("state"), rs.getInt("attempts"),
                        Columns.instant(rs, "updated_at"), rs.getString("failure_code"))).optional()
                .orElse(new Notifications.Status("UNAVAILABLE", 0, null, "NO_DELIVERY_RECORD"));
    }

    long attentionCount(Instant now) {
        return jdbc.sql("""
                SELECT count(*) FROM notification_messages
                WHERE state IN ('BLOCKED','FAILED','UNKNOWN')
                   OR (state IN ('QUEUED','READY','RETRY','PROCESSING') AND created_at < :overdue)
                """).param("overdue", Columns.timestamp(now.minusSeconds(900))).query(Long.class).single();
    }
}
