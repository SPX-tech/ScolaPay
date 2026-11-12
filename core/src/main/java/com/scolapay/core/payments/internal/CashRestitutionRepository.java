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
class CashRestitutionRepository {
    record Beneficiary(UUID id,UUID requestId,UUID source,String currency,long amountMinor,UUID key,String reference,
            String encryptedPayload,Instant verifiedAt,UUID verifiedBy) {}
    record Authorization(UUID id,UUID requestId,UUID verificationId,UUID source,String currency,long amountMinor,UUID key,
            String reference,String policy,Instant authorizedAt,UUID authorizedBy) {}
    private static final String BENEFICIARY="""
            SELECT b.*,r.reception_id,r.currency,r.amount_minor FROM cash_beneficiary_verifications b
            JOIN cash_excess_requests r ON r.establishment_id=b.establishment_id AND r.id=b.request_id WHERE b.establishment_id=:tenant
            """;
    private static final String AUTHORIZATION="""
            SELECT a.*,r.reception_id,r.currency,r.amount_minor FROM cash_restitution_authorizations a
            JOIN cash_excess_requests r ON r.establishment_id=a.establishment_id AND r.id=a.request_id WHERE a.establishment_id=:tenant
            """;
    private final JdbcClient jdbc;
    CashRestitutionRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    Optional<Beneficiary> beneficiary(UUID tenant,UUID id){return jdbc.sql(BENEFICIARY+" AND b.id=:id").param("tenant",tenant).param("id",id).query(CashRestitutionRepository::beneficiary).optional();}
    Optional<Beneficiary> beneficiaryByKey(UUID tenant,UUID key){return jdbc.sql(BENEFICIARY+" AND b.idempotency_key=:key").param("tenant",tenant).param("key",key).query(CashRestitutionRepository::beneficiary).optional();}
    Optional<Beneficiary> beneficiaryFor(UUID tenant,UUID request){return jdbc.sql(BENEFICIARY+" AND b.request_id=:id").param("tenant",tenant).param("id",request).query(CashRestitutionRepository::beneficiary).optional();}
    Optional<Authorization> authorization(UUID tenant,UUID id){return jdbc.sql(AUTHORIZATION+" AND a.id=:id").param("tenant",tenant).param("id",id).query(CashRestitutionRepository::authorization).optional();}
    Optional<Authorization> authorizationByKey(UUID tenant,UUID key){return jdbc.sql(AUTHORIZATION+" AND a.idempotency_key=:key").param("tenant",tenant).param("key",key).query(CashRestitutionRepository::authorization).optional();}
    Optional<Authorization> authorizationFor(UUID tenant,UUID request){return jdbc.sql(AUTHORIZATION+" AND a.request_id=:id").param("tenant",tenant).param("id",request).query(CashRestitutionRepository::authorization).optional();}
    private static Beneficiary beneficiary(ResultSet rs,int row)throws SQLException{return new Beneficiary(Columns.uuid(rs,"id"),Columns.uuid(rs,"request_id"),
            Columns.uuid(rs,"reception_id"),rs.getString("currency"),rs.getLong("amount_minor"),Columns.uuid(rs,"idempotency_key"),rs.getString("reference"),
            rs.getString("encrypted_payload"),Columns.instant(rs,"verified_at"),Columns.uuid(rs,"verified_by"));}
    private static Authorization authorization(ResultSet rs,int row)throws SQLException{return new Authorization(Columns.uuid(rs,"id"),Columns.uuid(rs,"request_id"),
            Columns.uuid(rs,"beneficiary_verification_id"),Columns.uuid(rs,"reception_id"),rs.getString("currency"),rs.getLong("amount_minor"),
            Columns.uuid(rs,"idempotency_key"),rs.getString("reference"),rs.getString("policy"),Columns.instant(rs,"authorized_at"),Columns.uuid(rs,"authorized_by"));}
    boolean insert(UUID tenant,Beneficiary b){return jdbc.sql("""
            INSERT INTO cash_beneficiary_verifications(id,establishment_id,request_id,identification_id,beneficiary_kind,
              identity_checked_confirmed,encrypted_payload,idempotency_key,reference,verified_at,verified_by)
            VALUES (:id,:tenant,:request,:source,'REMITTER_ONLY',true,:payload,:key,:reference,:at,:actor)
            ON CONFLICT (establishment_id,idempotency_key) DO NOTHING
            """).param("id",b.id()).param("tenant",tenant).param("request",b.requestId()).param("source",b.source()).param("payload",b.encryptedPayload())
            .param("key",b.key()).param("reference",b.reference()).param("at",Columns.timestamp(b.verifiedAt())).param("actor",b.verifiedBy()).update()==1;}
    boolean insert(UUID tenant,Authorization a){return jdbc.sql("""
            INSERT INTO cash_restitution_authorizations(id,establishment_id,request_id,beneficiary_verification_id,policy,
              beneficiary_reviewed_confirmed,idempotency_key,reference,authorized_at,authorized_by)
            VALUES (:id,:tenant,:request,:verification,'NAMED_REMITTER_V1',true,:key,:reference,:at,:actor)
            ON CONFLICT (establishment_id,idempotency_key) DO NOTHING
            """).param("id",a.id()).param("tenant",tenant).param("request",a.requestId()).param("verification",a.verificationId())
            .param("key",a.key()).param("reference",a.reference()).param("at",Columns.timestamp(a.authorizedAt())).param("actor",a.authorizedBy()).update()==1;}
}
