package com.scolapay.core.payments.internal;

import com.scolapay.core.shared.Columns;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CashIdentityRepository {
    record Remitter(UUID id,String origin,String encryptedPayload,UUID key,String reference,String statementReference,
            Instant recordedAt,UUID recordedBy) {}
    private final JdbcClient jdbc;
    CashIdentityRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    Optional<Remitter> find(UUID tenant,UUID source){
        return jdbc.sql("SELECT * FROM cash_remitter_identifications WHERE establishment_id=:tenant AND id=:id")
                .param("tenant",tenant).param("id",source).query((rs,row)->new Remitter(Columns.uuid(rs,"id"),rs.getString("origin"),
                        rs.getString("encrypted_payload"),Columns.uuid(rs,"idempotency_key"),rs.getString("reference"),rs.getString("statement_reference"),
                        Columns.instant(rs,"recorded_at"),Columns.uuid(rs,"recorded_by"))).optional();
    }
    boolean insert(UUID tenant,Remitter r){
        return jdbc.sql("""
                INSERT INTO cash_remitter_identifications(id,establishment_id,origin,encrypted_payload,idempotency_key,reference,
                  statement_reference,statement_signed_confirmed,recorded_at,recorded_by)
                VALUES (:id,:tenant,:origin,:payload,:key,:reference,:statement,:signed,:at,:actor)
                ON CONFLICT (establishment_id,idempotency_key) DO NOTHING
                """).param("id",r.id()).param("tenant",tenant).param("origin",r.origin()).param("payload",r.encryptedPayload())
                .param("key",r.key()).param("reference",r.reference()).param("statement",r.statementReference())
                .param("signed","LEGACY_DECLARATION".equals(r.origin())).param("at",Columns.timestamp(r.recordedAt())).param("actor",r.recordedBy()).update()==1;
    }
}
