package com.scolapay.core.payments.internal;

import com.scolapay.core.shared.Columns;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CashAllocationRepository {
    record Allocation(UUID id, UUID receptionId, UUID obligationId, UUID entryId, UUID key, String reference,
            String currency, long amountMinor, long issuedMinor, long netBilledMinor, long settledBeforeMinor,
            long dueBeforeMinor, long journalSizeBefore, Instant allocatedAt, UUID allocatedBy) {}

    private static final String SELECT = "SELECT * FROM cash_allocations WHERE establishment_id=:tenant";
    private final JdbcClient jdbc;
    CashAllocationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    Optional<Allocation> find(UUID tenant, UUID id) {
        return jdbc.sql(SELECT + " AND id=:id").param("tenant", tenant).param("id", id).query(CashAllocationRepository::row).optional();
    }
    Optional<Allocation> byKey(UUID tenant, UUID key) {
        return jdbc.sql(SELECT + " AND idempotency_key=:key").param("tenant", tenant).param("key", key).query(CashAllocationRepository::row).optional();
    }
    boolean boundToAnother(UUID tenant, UUID reception, UUID obligation) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM cash_allocations WHERE establishment_id=:tenant AND reception_id=:reception AND obligation_id<>:obligation)")
                .param("tenant", tenant).param("reception", reception).param("obligation", obligation).query(Boolean.class).single();
    }
    boolean insert(UUID tenant, Allocation a) {
        return jdbc.sql("""
                INSERT INTO cash_allocations(id,establishment_id,reception_id,obligation_id,settlement_entry_id,idempotency_key,
                    reference,currency,amount_minor,issued_minor,net_billed_minor,settled_before_minor,due_before_minor,
                    journal_size_before,allocated_at,allocated_by)
                VALUES (:id,:tenant,:reception,:obligation,:entry,:key,:reference,:currency,:amount,:issued,:billed,:settled,:due,:journal,:at,:actor)
                ON CONFLICT (establishment_id,idempotency_key) DO NOTHING
                """).param("id", a.id()).param("tenant", tenant).param("reception", a.receptionId()).param("obligation", a.obligationId())
                .param("entry", a.entryId()).param("key", a.key()).param("reference", a.reference()).param("currency", a.currency())
                .param("amount", a.amountMinor()).param("issued", a.issuedMinor()).param("billed", a.netBilledMinor())
                .param("settled", a.settledBeforeMinor()).param("due", a.dueBeforeMinor()).param("journal", a.journalSizeBefore())
                .param("at", Columns.timestamp(a.allocatedAt())).param("actor", a.allocatedBy()).update() == 1;
    }
    private static Allocation row(ResultSet rs, int row) throws SQLException {
        return new Allocation(Columns.uuid(rs,"id"),Columns.uuid(rs,"reception_id"),Columns.uuid(rs,"obligation_id"),
                Columns.uuid(rs,"settlement_entry_id"),Columns.uuid(rs,"idempotency_key"),rs.getString("reference"),rs.getString("currency"),
                rs.getLong("amount_minor"),rs.getLong("issued_minor"),rs.getLong("net_billed_minor"),rs.getLong("settled_before_minor"),
                rs.getLong("due_before_minor"),rs.getLong("journal_size_before"),Columns.instant(rs,"allocated_at"),Columns.uuid(rs,"allocated_by"));
    }
}
