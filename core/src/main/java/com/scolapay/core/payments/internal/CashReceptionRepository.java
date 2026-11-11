package com.scolapay.core.payments.internal;

import com.scolapay.core.shared.Columns;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CashReceptionRepository {

    record Reception(
            UUID id, String reference, UUID idempotencyKey, String currency,
            long amountReceivedMinor, long changeGivenMinor, Instant recordedAt,
            UUID recordedByAccountId, LocalDate businessDate, String timezone, long allocatedMinor) {

        long amountRetainedMinor() {
            return amountReceivedMinor - changeGivenMinor;
        }
    }

    private static final String SELECT = """
            SELECT id, reference, idempotency_key, currency, amount_received_minor, change_given_minor,
                   recorded_at, recorded_by_account_id, business_date, timezone,
                   (SELECT COALESCE(sum(a.amount_minor),0) FROM cash_allocations a WHERE a.establishment_id=cash_receptions.establishment_id
                     AND a.reception_id=cash_receptions.id) AS allocated_minor
            FROM cash_receptions WHERE establishment_id = :establishmentId
            """;

    private final JdbcClient jdbc;

    CashReceptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean insert(UUID establishmentId, Reception reception) {
        return jdbc.sql("""
                INSERT INTO cash_receptions
                  (id, establishment_id, reference, idempotency_key, currency, amount_received_minor,
                   change_given_minor, cash_received_confirmed, recorded_at, recorded_by_account_id,
                   business_date, timezone)
                VALUES (:id, :establishmentId, :reference, :key, :currency, :received, :change, true,
                        :recordedAt, :actor, :businessDate, :timezone)
                ON CONFLICT (establishment_id, idempotency_key) DO NOTHING
                """)
                .param("id", reception.id()).param("establishmentId", establishmentId)
                .param("reference", reception.reference()).param("key", reception.idempotencyKey())
                .param("currency", reception.currency()).param("received", reception.amountReceivedMinor())
                .param("change", reception.changeGivenMinor()).param("recordedAt", Columns.timestamp(reception.recordedAt()))
                .param("actor", reception.recordedByAccountId()).param("businessDate", reception.businessDate())
                .param("timezone", reception.timezone()).update() == 1;
    }

    Optional<Reception> find(UUID establishmentId, UUID id) {
        return jdbc.sql(SELECT + " AND id = :id")
                .param("establishmentId", establishmentId).param("id", id)
                .query(CashReceptionRepository::reception).optional();
    }

    Optional<Reception> findByKey(UUID establishmentId, UUID key) {
        return jdbc.sql(SELECT + " AND idempotency_key = :key")
                .param("establishmentId", establishmentId).param("key", key)
                .query(CashReceptionRepository::reception).optional();
    }

    private static Reception reception(ResultSet rs, int row) throws SQLException {
        return new Reception(Columns.uuid(rs, "id"), rs.getString("reference"), Columns.uuid(rs, "idempotency_key"),
                rs.getString("currency"), rs.getLong("amount_received_minor"), rs.getLong("change_given_minor"),
                Columns.instant(rs, "recorded_at"), Columns.uuid(rs, "recorded_by_account_id"),
                rs.getObject("business_date", LocalDate.class), rs.getString("timezone"), rs.getLong("allocated_minor"));
    }

    Optional<Reception> lock(UUID establishmentId, UUID id) {
        return jdbc.sql(SELECT + " AND id=:id FOR UPDATE")
                .param("establishmentId", establishmentId).param("id", id).query(CashReceptionRepository::reception).optional();
    }
}
