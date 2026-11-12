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
class CashExcessRepository {
    record Request(UUID id,UUID receptionId,String currency,long amountMinor,String reason,UUID key,String reference,
            String authorizationScope,long retainedMinor,long historicalAllocatedBeforeMinor,long reservedBeforeMinor,
            Instant createdAt,UUID createdBy,String state) {}
    record Decision(UUID id,UUID requestId,UUID receptionId,String currency,long amountMinor,String authorizationScope,
            UUID key,String kind,String note,String reference,Instant decidedAt,UUID decidedBy) {}
    private static final String REQUEST_SELECT="""
            SELECT r.id,r.reception_id,r.currency,r.amount_minor,r.reason,r.idempotency_key,r.reference,r.authorization_scope,
                   r.retained_minor,r.historical_allocated_before_minor,r.reserved_before_minor,r.created_at,r.created_by,
                   CASE WHEN d.kind='APPROVED' THEN 'APPROVED_RESERVED' ELSE COALESCE(d.kind,'REQUESTED') END AS state
            FROM cash_excess_requests r LEFT JOIN cash_excess_decisions d ON d.establishment_id=r.establishment_id AND d.request_id=r.id
            WHERE r.establishment_id=:tenant
            """;
    private static final String DECISION_SELECT="""
            SELECT d.id,d.request_id,r.reception_id,r.currency,r.amount_minor,r.authorization_scope,
                   d.idempotency_key,d.kind,d.note,d.reference,d.decided_at,d.decided_by
            FROM cash_excess_decisions d JOIN cash_excess_requests r ON r.establishment_id=d.establishment_id AND r.id=d.request_id
            WHERE d.establishment_id=:tenant
            """;
    private final JdbcClient jdbc;
    CashExcessRepository(JdbcClient jdbc) {this.jdbc=jdbc;}

    Optional<Request> find(UUID tenant,UUID id) {
        return jdbc.sql(REQUEST_SELECT+" AND r.id=:id").param("tenant",tenant).param("id",id).query(CashExcessRepository::request).optional();
    }
    Optional<Request> byKey(UUID tenant,UUID key) {
        return jdbc.sql(REQUEST_SELECT+" AND r.idempotency_key=:key").param("tenant",tenant).param("key",key).query(CashExcessRepository::request).optional();
    }
    Optional<Decision> decisionByKey(UUID tenant,UUID key) {
        return jdbc.sql(DECISION_SELECT+" AND d.idempotency_key=:key").param("tenant",tenant).param("key",key).query(CashExcessRepository::decision).optional();
    }
    Optional<Decision> decisionFor(UUID tenant,UUID request) {
        return jdbc.sql(DECISION_SELECT+" AND d.request_id=:request").param("tenant",tenant).param("request",request).query(CashExcessRepository::decision).optional();
    }
    boolean insert(UUID tenant,Request r) {
        return jdbc.sql("""
                INSERT INTO cash_excess_requests(id,establishment_id,reception_id,currency,amount_minor,reason,idempotency_key,
                  reference,authorization_scope,retained_minor,historical_allocated_before_minor,reserved_before_minor,created_at,created_by)
                VALUES (:id,:tenant,:source,:currency,:amount,:reason,:key,:reference,:scope,:retained,:historical,:reserved,:at,:actor)
                ON CONFLICT (establishment_id,idempotency_key) DO NOTHING
                """).param("id",r.id()).param("tenant",tenant).param("source",r.receptionId()).param("currency",r.currency())
                .param("amount",r.amountMinor()).param("reason",r.reason()).param("key",r.key()).param("reference",r.reference())
                .param("scope",r.authorizationScope()).param("retained",r.retainedMinor()).param("historical",r.historicalAllocatedBeforeMinor())
                .param("reserved",r.reservedBeforeMinor()).param("at",Columns.timestamp(r.createdAt())).param("actor",r.createdBy()).update()==1;
    }
    boolean insert(UUID tenant,Decision d) {
        return jdbc.sql("""
                INSERT INTO cash_excess_decisions(id,establishment_id,request_id,kind,note,idempotency_key,reference,decided_at,decided_by)
                VALUES (:id,:tenant,:request,:kind,:note,:key,:reference,:at,:actor)
                ON CONFLICT (establishment_id,idempotency_key) DO NOTHING
                """).param("id",d.id()).param("tenant",tenant).param("request",d.requestId()).param("kind",d.kind()).param("note",d.note())
                .param("key",d.key()).param("reference",d.reference()).param("at",Columns.timestamp(d.decidedAt())).param("actor",d.decidedBy()).update()==1;
    }
    private static Request request(ResultSet rs,int row) throws SQLException {
        return new Request(Columns.uuid(rs,"id"),Columns.uuid(rs,"reception_id"),rs.getString("currency"),rs.getLong("amount_minor"),
                rs.getString("reason"),Columns.uuid(rs,"idempotency_key"),rs.getString("reference"),rs.getString("authorization_scope"),
                rs.getLong("retained_minor"),rs.getLong("historical_allocated_before_minor"),rs.getLong("reserved_before_minor"),
                Columns.instant(rs,"created_at"),Columns.uuid(rs,"created_by"),rs.getString("state"));
    }
    private static Decision decision(ResultSet rs,int row) throws SQLException {
        return new Decision(Columns.uuid(rs,"id"),Columns.uuid(rs,"request_id"),Columns.uuid(rs,"reception_id"),rs.getString("currency"),
                rs.getLong("amount_minor"),rs.getString("authorization_scope"),Columns.uuid(rs,"idempotency_key"),rs.getString("kind"),
                rs.getString("note"),rs.getString("reference"),Columns.instant(rs,"decided_at"),Columns.uuid(rs,"decided_by"));
    }
}
