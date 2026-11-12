package com.scolapay.core.payments.internal;

import com.scolapay.core.shared.Columns;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CashIdentityRepository {
    record Remitter(UUID id,String origin,String encryptedPayload,UUID key,String reference,String statementReference,
            Instant recordedAt,UUID recordedBy) {}
    record Review(UUID id,UUID source,UUID key,String reference,Instant reviewedAt,UUID reviewedBy) {}
    private final JdbcClient jdbc;
    CashIdentityRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    Optional<Remitter> find(UUID tenant,UUID source){
        return jdbc.sql("SELECT * FROM cash_remitter_identifications WHERE establishment_id=:tenant AND id=:id")
                .param("tenant",tenant).param("id",source).query(CashIdentityRepository::remitter).optional();
    }
    Optional<Remitter> byKey(UUID tenant,UUID key){return jdbc.sql("SELECT * FROM cash_remitter_identifications WHERE establishment_id=:tenant AND idempotency_key=:key")
            .param("tenant",tenant).param("key",key).query(CashIdentityRepository::remitter).optional();}
    boolean legacySource(UUID tenant,UUID source){return jdbc.sql("SELECT identification_policy='LEGACY_UNIDENTIFIED' FROM cash_receptions WHERE establishment_id=:tenant AND id=:id")
            .param("tenant",tenant).param("id",source).query(Boolean.class).single();}
    private static Remitter remitter(ResultSet rs,int row)throws SQLException{return new Remitter(Columns.uuid(rs,"id"),rs.getString("origin"),
            rs.getString("encrypted_payload"),Columns.uuid(rs,"idempotency_key"),rs.getString("reference"),rs.getString("statement_reference"),
            Columns.instant(rs,"recorded_at"),Columns.uuid(rs,"recorded_by"));}
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
    Optional<Review> reviewFor(UUID tenant,UUID source){return jdbc.sql("SELECT * FROM cash_remitter_reviews WHERE establishment_id=:tenant AND identification_id=:source")
            .param("tenant",tenant).param("source",source).query(CashIdentityRepository::review).optional();}
    Optional<Review> reviewByKey(UUID tenant,UUID key){return jdbc.sql("SELECT * FROM cash_remitter_reviews WHERE establishment_id=:tenant AND idempotency_key=:key")
            .param("tenant",tenant).param("key",key).query(CashIdentityRepository::review).optional();}
    private static Review review(ResultSet rs,int row)throws SQLException{return new Review(Columns.uuid(rs,"id"),Columns.uuid(rs,"identification_id"),
            Columns.uuid(rs,"idempotency_key"),rs.getString("reference"),Columns.instant(rs,"reviewed_at"),Columns.uuid(rs,"reviewed_by"));}
    boolean insert(UUID tenant,Review r){return jdbc.sql("""
            INSERT INTO cash_remitter_reviews(id,establishment_id,identification_id,original_receipt_matched_confirmed,
              signed_statement_checked_confirmed,identity_checked_confirmed,idempotency_key,reference,reviewed_at,reviewed_by)
            VALUES (:id,:tenant,:source,true,true,true,:key,:reference,:at,:actor)
            ON CONFLICT (establishment_id,idempotency_key) DO NOTHING
            """).param("id",r.id()).param("tenant",tenant).param("source",r.source()).param("key",r.key()).param("reference",r.reference())
            .param("at",Columns.timestamp(r.reviewedAt())).param("actor",r.reviewedBy()).update()==1;}
}
