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
class DigitalPaymentIntentRepository {
    record Intent(UUID id,UUID obligationId,UUID key,String reference,String currency,long amountMinor,long issuedMinor,
            long netBilledMinor,long settledBeforeMinor,long dueBeforeMinor,long journalSizeBefore,Instant preparedAt,UUID preparedBy) {}
    private final JdbcClient jdbc;
    DigitalPaymentIntentRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    Optional<Intent> find(UUID tenant,UUID id){return jdbc.sql("SELECT * FROM digital_payment_intents WHERE establishment_id=:tenant AND id=:id")
            .param("tenant",tenant).param("id",id).query(DigitalPaymentIntentRepository::intent).optional();}
    Optional<Intent> byKey(UUID tenant,UUID key){return jdbc.sql("SELECT * FROM digital_payment_intents WHERE establishment_id=:tenant AND idempotency_key=:key")
            .param("tenant",tenant).param("key",key).query(DigitalPaymentIntentRepository::intent).optional();}
    private static Intent intent(ResultSet rs,int row)throws SQLException{return new Intent(Columns.uuid(rs,"id"),Columns.uuid(rs,"obligation_id"),Columns.uuid(rs,"idempotency_key"),
            rs.getString("reference"),rs.getString("currency"),rs.getLong("amount_minor"),rs.getLong("issued_minor"),rs.getLong("net_billed_minor"),rs.getLong("settled_before_minor"),
            rs.getLong("due_before_minor"),rs.getLong("journal_size_before"),Columns.instant(rs,"prepared_at"),Columns.uuid(rs,"prepared_by"));}
    boolean insert(UUID tenant,Intent i){return jdbc.sql("""
            INSERT INTO digital_payment_intents(id,establishment_id,obligation_id,origin_type,provider,purpose,state,reference,idempotency_key,currency,
              amount_minor,issued_minor,net_billed_minor,settled_before_minor,due_before_minor,journal_size_before,prepared_at,prepared_by)
            VALUES (:id,:tenant,:obligation,'SCHOOL_OBLIGATION','KADRYZA','PREPARATION_ONLY_V1','PREPARED',:reference,:key,:currency,
              :amount,:issued,:billed,:paid,:due,:journal,:at,:actor)
            ON CONFLICT (establishment_id,idempotency_key) DO NOTHING
            """).param("id",i.id()).param("tenant",tenant).param("obligation",i.obligationId()).param("reference",i.reference()).param("key",i.key()).param("currency",i.currency())
            .param("amount",i.amountMinor()).param("issued",i.issuedMinor()).param("billed",i.netBilledMinor()).param("paid",i.settledBeforeMinor()).param("due",i.dueBeforeMinor())
            .param("journal",i.journalSizeBefore()).param("at",Columns.timestamp(i.preparedAt())).param("actor",i.preparedBy()).update()==1;}
}
