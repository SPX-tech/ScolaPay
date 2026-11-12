package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.scolapay.core.billing.BillingPaymentPreparation;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties="scolapay.payments.digital.intents-enabled=true")
class DigitalPaymentSqlTest extends BillingScenarioTest {
    @Autowired PlatformTransactionManager transactions;
    @Autowired BillingPaymentPreparation preparation;
    record Raw(Tenant tenant,UUID obligation,UUID actor,UUID id,UUID key){}
    Raw raw()throws Exception{School s=school();return new Raw(s.tenant(),issuedObligation(s),get("/api/v2/me",s.tenant().adminToken()).id("id"),UUID.randomUUID(),UUID.randomUUID());}
    Map<String,Object> values(Raw r){return new HashMap<>(Map.ofEntries(Map.entry("id",r.id()),Map.entry("tenant",r.tenant().id()),Map.entry("obligation",r.obligation()),
            Map.entry("actor",r.actor()),Map.entry("key",r.key()),Map.entry("origin","SCHOOL_OBLIGATION"),Map.entry("provider","KADRYZA"),Map.entry("purpose","PREPARATION_ONLY_V1"),
            Map.entry("state","PREPARED"),Map.entry("reference","DPI-"+r.id()),Map.entry("currency","XAF"),Map.entry("amount",40000L),Map.entry("issued",100000L),
            Map.entry("billed",100000L),Map.entry("paid",0L),Map.entry("due",100000L),Map.entry("journal",1L),Map.entry("at",Instant.now().toString())));}
    void insert(Raw r,Map<String,Object> changes){var p=values(r);p.putAll(changes);jdbc.sql("""
            INSERT INTO digital_payment_intents(id,establishment_id,obligation_id,origin_type,provider,purpose,state,reference,idempotency_key,currency,
              amount_minor,issued_minor,net_billed_minor,settled_before_minor,due_before_minor,journal_size_before,prepared_at,prepared_by)
            VALUES (:id,:tenant,:obligation,:origin,:provider,:purpose,:state,:reference,:key,:currency,:amount,:issued,:billed,:paid,:due,:journal,CAST(:at AS timestamptz),:actor)
            """).params(p).update();}
    void audit(Raw r){jdbc.sql("""
            INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
            VALUES (:event,now(),:tenant,:actor,'payments.digital.intent_prepared','digital_payment_intent',:id,
              jsonb_build_object('reference',CAST(:reference AS text),'obligationId',CAST(:obligation AS text)))
            """).param("event",UUID.randomUUID()).param("tenant",r.tenant().id()).param("actor",r.actor()).param("id",r.id()).param("reference","DPI-"+r.id()).param("obligation",r.obligation().toString()).update();}
    void commit(Raw r,Map<String,Object> changes){new TransactionTemplate(transactions).executeWithoutResult(tx->{insert(r,changes);audit(r);});}
    long count(Raw r){return jdbc.sql("SELECT count(*) FROM digital_payment_intents WHERE establishment_id=:tenant").param("tenant",r.tenant().id()).query(Long.class).single();}

    @Test void lePortNeCreePasUneTransactionIndependante(){assertThatThrownBy(()->preparation.lockForPreparation(UUID.randomUUID(),UUID.randomUUID()))
            .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);}
    @Test void lesMontantsSnapshotsOriginesStatutsEtNullNeContournentPasLeContrat()throws Exception{
        Raw r=raw();
        for(var bad:List.of(Map.<String,Object>of("amount",0L),Map.<String,Object>of("amount",100001L),Map.<String,Object>of("due",99999L),Map.<String,Object>of("paid",1L),
                Map.<String,Object>of("billed",100001L),Map.<String,Object>of("issued",99999L),Map.<String,Object>of("journal",2L),Map.<String,Object>of("currency","EUR"),
                Map.<String,Object>of("origin","CASH_RECEPTION"),Map.<String,Object>of("provider","OTHER"),Map.<String,Object>of("purpose","LIVE"),
                Map.<String,Object>of("state","SUCCESS"),Map.<String,Object>of("reference","FAUX-RECU"),Map.<String,Object>of("at","infinity"))){
            assertThatThrownBy(()->commit(r,bad)).isInstanceOf(RuntimeException.class);
        }
        for(String field:List.of("obligation","actor","key","origin","provider","purpose","state","reference","currency","amount","issued","billed","paid","due","journal","at")){
            var bad=new HashMap<String,Object>();bad.put(field,null);assertThatThrownBy(()->commit(r,bad)).as(field).isInstanceOf(RuntimeException.class);
        }
        assertThat(count(r)).isZero();commit(r,Map.of());assertThat(count(r)).isEqualTo(1);assertThat(journalSize(r.obligation())).isEqualTo(1);
    }
    @Test void leSqlExigeLeBonEtablissementEtUnPreparateurHabilite()throws Exception{
        Raw r=raw();Tenant other=newTenant();UUID auditor=get("/api/v2/me",newStaff(r.tenant(),"AUDITOR")).id("id");
        assertThatThrownBy(()->commit(r,Map.of("tenant",other.id()))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->commit(r,Map.of("actor",auditor))).isInstanceOf(RuntimeException.class).hasStackTraceContaining("non habilite");
        commit(r,Map.of());assertThat(count(r)).isEqualTo(1);
    }
    @Test void unAuditManquantAnnuleLaPreparation()throws Exception{
        Raw r=raw();assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->insert(r,Map.of())))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("audit lie");
        assertThat(count(r)).isZero();commit(r,Map.of());
    }
    @Test void uneModificationDuJournalApresInsertionDansLaMemeTransactionEchoueAuCommit()throws Exception{
        Raw r=raw();
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            insert(r,Map.of());audit(r);
            jdbc.sql("""
                    INSERT INTO obligation_entries(id,establishment_id,obligation_id,kind,amount_minor,reason,idempotency_key,recorded_at,recorded_by)
                    VALUES (:id,:tenant,:obligation,'DISCOUNT',-1000,'Remise SQL',:key,now(),:actor)
                    """).param("id",UUID.randomUUID()).param("tenant",r.tenant().id()).param("obligation",r.obligation()).param("key",UUID.randomUUID().toString()).param("actor",r.actor()).update();
        })).isInstanceOf(RuntimeException.class).hasStackTraceContaining("journal presente");
        assertThat(count(r)).isZero();assertThat(journalSize(r.obligation())).isEqualTo(1);
        assertThat(discount(r.tenant(),r.tenant().adminToken(),r.obligation(),1000).status()).isEqualTo(201);
        commit(r,Map.of("billed",99000L,"due",99000L,"journal",2L));assertThat(count(r)).isEqualTo(1);
    }
    @Test void aucuneIntentionNePeutServirDePreuvePourUnOuDeuxReglements()throws Exception{
        Raw r=raw();commit(r,Map.of());
        for(int i=0;i<2;i++)assertThatThrownBy(()->jdbc.sql("""
                INSERT INTO obligation_entries(id,establishment_id,obligation_id,kind,amount_minor,idempotency_key,recorded_at,recorded_by,cash_allocation_id)
                VALUES (:id,:tenant,:obligation,'SETTLEMENT',-40000,:key,now(),:actor,:intent)
                """).param("id",UUID.randomUUID()).param("tenant",r.tenant().id()).param("obligation",r.obligation()).param("key","digital:"+r.id())
                .param("actor",r.actor()).param("intent",r.id()).update()).isInstanceOf(RuntimeException.class);
        assertThat(journalSize(r.obligation())).isEqualTo(1);assertThat(count(r)).isEqualTo(1);
        assertThat(get(r.tenant().path("/billing/obligations/"+r.obligation()+"/financial-position"),r.tenant().adminToken()).text("settledMinor")).isEqualTo("0");
    }
    @Test void leFaitNePeutEtreConfirmeReassocieSupprimeOuTronque()throws Exception{
        Raw r=raw();commit(r,Map.of());
        for(String sql:List.of("UPDATE digital_payment_intents SET state='SUCCESS' WHERE id=:id","UPDATE digital_payment_intents SET obligation_id=gen_random_uuid() WHERE id=:id",
                "DELETE FROM digital_payment_intents WHERE id=:id"))assertThatThrownBy(()->jdbc.sql(sql).param("id",r.id()).update()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.sql("TRUNCATE digital_payment_intents CASCADE").update()).isInstanceOf(RuntimeException.class);assertThat(count(r)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(ints={TransactionDefinition.ISOLATION_REPEATABLE_READ,TransactionDefinition.ISOLATION_SERIALIZABLE})
    void uneIsolationNonPriseEnChargeNePeutCapturerUnAncienDu(int isolation)throws Exception{
        Raw r=raw();var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(isolation);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{insert(r,Map.of());audit(r);})).isInstanceOf(RuntimeException.class).hasStackTraceContaining("READ COMMITTED");
        commit(r,Map.of());
    }
}
