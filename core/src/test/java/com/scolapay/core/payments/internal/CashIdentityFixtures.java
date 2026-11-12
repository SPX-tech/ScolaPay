package com.scolapay.core.payments.internal;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Identité valide pour les scénarios historiques dont le défaut exercé porte sur les montants ou l'audit. */
public final class CashIdentityFixtures {
    public static final String KEY="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    public static final Map<String,Object> REMITTER=Map.of("fullName","Remettant de test",
            "identificationDetails","Référence minimale de test","identityRecordedConfirmed",true);
    private CashIdentityFixtures() {}
    public static String encrypt(UUID tenant,UUID id,String domain,String clear){
        return new CashIdentityCipher(new CashProperties(true,false,false,false,KEY)).encrypt(tenant,id,domain,clear);
    }
    public static void identifyRaw(JdbcClient jdbc,UUID tenant,UUID source,UUID actor){
        identifyRaw(jdbc,tenant,source,actor,true);
    }
    public static void identifyRaw(JdbcClient jdbc,UUID tenant,UUID source,UUID actor,boolean audited){
        String payload=encrypt(tenant,source,"remitter","{\"fullName\":\"Remettant SQL\",\"identificationDetails\":\"Contrôle minimal\"}");
        jdbc.sql("""
                INSERT INTO cash_remitter_identifications(id,establishment_id,origin,encrypted_payload,idempotency_key,reference,
                  statement_reference,statement_signed_confirmed,recorded_at,recorded_by)
                SELECT id,establishment_id,'AT_RECEPTION',:payload,idempotency_key,'CI-'||id::text,NULL,false,recorded_at,recorded_by_account_id
                FROM cash_receptions WHERE establishment_id=:tenant AND id=:id
                """).param("payload",payload).param("tenant",tenant).param("id",source).update();
        if(!audited)return;
        jdbc.sql("""
                INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                VALUES (:event,now(),:tenant,:actor,'payments.remitter.recorded','cash_remitter_identification',:id,
                  jsonb_build_object('reference',CAST(:reference AS text),'receptionId',CAST(:source AS text)))
                """).param("event",UUID.randomUUID()).param("tenant",tenant).param("actor",actor).param("id",source)
                .param("reference","CI-"+source).param("source",source.toString()).update();
    }
}
