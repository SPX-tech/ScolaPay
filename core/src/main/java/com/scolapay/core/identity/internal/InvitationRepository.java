package com.scolapay.core.identity.internal;

import com.scolapay.core.identity.Identifier;
import com.scolapay.core.shared.Columns;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepository {

    public record Row(
            UUID id,
            Identifier contact,
            String purpose,
            UUID establishmentId,
            UUID subjectId,
            String attributes,
            UUID issuedBy,
            Instant expiresAt,
            Instant acceptedAt,
            Instant revokedAt) {}

    private final JdbcClient jdbc;

    InvitationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            UUID id,
            String tokenHash,
            Identifier contact,
            String purpose,
            UUID establishmentId,
            UUID subjectId,
            String attributes,
            UUID issuedBy,
            Instant expiresAt,
            Instant now) {
        jdbc.sql("""
                INSERT INTO invitations
                  (id, token_hash, contact_kind, contact_value, purpose, establishment_id, subject_id,
                   attributes, issued_by, expires_at, created_at)
                VALUES
                  (:id, :tokenHash, :kind, :value, :purpose, :establishmentId, :subjectId,
                   CAST(:attributes AS jsonb), :issuedBy, :expiresAt, :now)
                """)
                .param("id", id)
                .param("tokenHash", tokenHash)
                .param("kind", contact.kind().name())
                .param("value", contact.value())
                .param("purpose", purpose)
                .param("establishmentId", establishmentId)
                .param("subjectId", subjectId)
                .param("attributes", attributes)
                .param("issuedBy", issuedBy)
                .param("expiresAt", Columns.timestamp(expiresAt))
                .param("now", Columns.timestamp(now))
                .update();
    }

    /** Le verrou garantit qu'un jeton ne peut être consommé qu'une fois, même en concurrence. */
    public Optional<Row> lockByTokenHash(String tokenHash) {
        return jdbc.sql("""
                SELECT id, contact_kind, contact_value, purpose, establishment_id, subject_id,
                       attributes::text AS attributes, issued_by, expires_at, accepted_at, revoked_at
                FROM invitations
                WHERE token_hash = :tokenHash
                FOR UPDATE
                """)
                .param("tokenHash", tokenHash)
                .query((rs, rowNum) -> new Row(
                        Columns.uuid(rs, "id"),
                        new Identifier(Identifier.Kind.valueOf(rs.getString("contact_kind")), rs.getString("contact_value")),
                        rs.getString("purpose"),
                        Columns.uuid(rs, "establishment_id"),
                        Columns.uuid(rs, "subject_id"),
                        rs.getString("attributes"),
                        Columns.uuid(rs, "issued_by"),
                        Columns.instant(rs, "expires_at"),
                        Columns.instant(rs, "accepted_at"),
                        Columns.instant(rs, "revoked_at")))
                .optional();
    }

    public void markAccepted(UUID id, UUID accountId, Instant now) {
        jdbc.sql("UPDATE invitations SET accepted_at = :now, accepted_account_id = :accountId WHERE id = :id")
                .param("id", id)
                .param("accountId", accountId)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public int revoke(UUID establishmentId, UUID id, Instant now) {
        return jdbc.sql("""
                UPDATE invitations SET revoked_at = :now
                WHERE id = :id AND establishment_id = :establishmentId
                  AND accepted_at IS NULL AND revoked_at IS NULL
                """)
                .param("id", id)
                .param("establishmentId", establishmentId)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public Optional<String> deliverablePurpose(UUID id, Instant now) {
        return jdbc.sql("SELECT purpose FROM invitations WHERE id=:id AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > :now")
                .param("id", id).param("now", Columns.timestamp(now)).query(String.class).optional();
    }

    public boolean belongsTo(UUID establishmentId, UUID id) {
        return jdbc.sql("SELECT count(*) FROM invitations WHERE id=:id AND establishment_id=:school")
                .param("id", id).param("school", establishmentId).query(Long.class).single() == 1;
    }

    public boolean issuedByPlatformOperator(UUID establishmentId, UUID id, UUID operator) {
        return jdbc.sql("""
                SELECT count(*) FROM invitations WHERE id=:id AND establishment_id=:school
                  AND issued_by=:operator AND purpose='establishment.staff'
                  AND attributes->>'role'='ESTABLISHMENT_ADMIN'
                """).param("id", id).param("school", establishmentId).param("operator", operator)
                .query(Long.class).single() == 1;
    }
}
