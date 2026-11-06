package com.scolapay.core.audit;

import com.scolapay.core.shared.Columns;
import com.scolapay.core.shared.PageQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditTrail {

    public record Event(
            UUID id,
            Instant occurredAt,
            UUID actorAccountId,
            String action,
            String targetType,
            UUID targetId,
            Map<String, Object> details) {}

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public AuditTrail(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** MANDATORY : un événement d'audit ne peut pas exister sans le changement qu'il décrit, ni l'inverse. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            UUID establishmentId,
            UUID actorAccountId,
            String action,
            String targetType,
            UUID targetId,
            Map<String, ?> details) {
        jdbc.sql("""
                INSERT INTO audit_events
                  (id, occurred_at, establishment_id, actor_account_id, action, target_type, target_id, details)
                VALUES
                  (:id, :occurredAt, :establishmentId, :actor, :action, :targetType, :targetId, CAST(:details AS jsonb))
                """)
                .param("id", UUID.randomUUID())
                .param("occurredAt", Columns.timestamp(clock.instant()))
                .param("establishmentId", establishmentId)
                .param("actor", actorAccountId)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("details", json.writeValueAsString(details))
                .update();
    }

    @Transactional(readOnly = true)
    public List<Event> forEstablishment(UUID establishmentId, PageQuery page) {
        return jdbc.sql("""
                SELECT id, occurred_at, actor_account_id, action, target_type, target_id, details::text AS details
                FROM audit_events
                WHERE establishment_id = :establishmentId
                ORDER BY occurred_at DESC, id
                LIMIT :limit OFFSET :offset
                """)
                .param("establishmentId", establishmentId)
                .param("limit", page.limit())
                .param("offset", page.offset())
                .query((rs, rowNum) -> new Event(
                        Columns.uuid(rs, "id"),
                        Columns.instant(rs, "occurred_at"),
                        Columns.uuid(rs, "actor_account_id"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        Columns.uuid(rs, "target_id"),
                        json.readValue(rs.getString("details"), new TypeReference<Map<String, Object>>() {})))
                .list();
    }
}
