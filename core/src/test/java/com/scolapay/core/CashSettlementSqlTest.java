package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.scolapay.core.shared.Columns;
import com.scolapay.core.billing.BillingSettlements;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class CashSettlementSqlTest extends CashSettlementScenario {
    @Autowired PlatformTransactionManager transactions;
    @Autowired BillingSettlements billing;
    record Raw(Tenant tenant,UUID reception,UUID obligation,UUID actor,UUID id,UUID entry,Instant at) {}
    @Test void lePortDeReglementNePeutOuvrirUneTransactionIndependante() {
        assertThatThrownBy(()->billing.post(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,Instant.now()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
    Raw raw() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        return new Raw(t,receive(t,t.adminToken(),100000),o,get("/api/v2/me",t.adminToken()).id("id"),
                UUID.randomUUID(),UUID.randomUUID(),Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
    void allocation(Raw r,Map<String,Object> overrides) {
        Map<String,Object> p=new HashMap<>(Map.ofEntries(Map.entry("id",r.id()),Map.entry("tenant",r.tenant().id()),
                Map.entry("reception",r.reception()),Map.entry("obligation",r.obligation()),Map.entry("entry",r.entry()),
                Map.entry("key",UUID.randomUUID()),Map.entry("reference","CA-"+r.id()),Map.entry("currency","XAF"),Map.entry("amount",20000L),
                Map.entry("issued",100000L),Map.entry("billed",100000L),Map.entry("paid",0L),Map.entry("due",100000L),
                Map.entry("journal",1L),Map.entry("at",Columns.timestamp(r.at())),Map.entry("actor",r.actor())));
        p.putAll(overrides);
        jdbc.sql("""
                INSERT INTO cash_allocations(id,establishment_id,reception_id,obligation_id,settlement_entry_id,idempotency_key,
                    reference,currency,amount_minor,issued_minor,net_billed_minor,settled_before_minor,due_before_minor,journal_size_before,allocated_at,allocated_by)
                VALUES (:id,:tenant,:reception,:obligation,:entry,:key,:reference,:currency,:amount,:issued,:billed,:paid,:due,:journal,:at,:actor)
                """).params(p).update();
    }
    void entry(Raw r,Map<String,Object> overrides) {
        Map<String,Object> p=new HashMap<>(Map.of("id",r.entry(),"tenant",r.tenant().id(),"obligation",r.obligation(),
                "amount",-20000L,"key","cash:"+r.id(),"at",Columns.timestamp(r.at()),"actor",r.actor(),"allocation",r.id()));
        p.putAll(overrides);
        jdbc.sql("""
                INSERT INTO obligation_entries(id,establishment_id,obligation_id,kind,amount_minor,idempotency_key,recorded_at,recorded_by,cash_allocation_id)
                VALUES (:id,:tenant,:obligation,'SETTLEMENT',:amount,:key,:at,:actor,:allocation)
                """).params(p).update();
    }
    void audit(Raw r) {
        jdbc.sql("""
                INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                VALUES (:event,now(),:tenant,:actor,'payments.cash.allocated','cash_allocation',:id,
                  jsonb_build_object('reference',CAST(:reference AS text),'receptionId',CAST(:reception AS text),'obligationId',CAST(:obligation AS text)))
                """).param("event",UUID.randomUUID()).param("tenant",r.tenant().id()).param("actor",r.actor()).param("id",r.id())
                .param("reference","CA-"+r.id()).param("reception",r.reception().toString()).param("obligation",r.obligation().toString()).update();
    }
    void pair(Raw r,Map<String,Object> a,Map<String,Object> e) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{allocation(r,a);entry(r,e);audit(r);});
    }
    void unchanged(Raw r) {
        assertThat(count("cash_allocations",r.tenant())).isZero();
        assertThat(journalSize(r.obligation())).isEqualTo(1);
        assertThat(position(r.tenant(),r.obligation()).path("dueMinor").asString()).isEqualTo("100000");
        assertThat(get(r.tenant().path("/payments/cash-receptions/"+r.reception()),r.tenant().adminToken()).text("unallocatedMinor")).isEqualTo("100000");
    }

    @Test void unReglementSansAffectationOuUneAffectationSansReglementNePeutExister() throws Exception {
        Raw r=raw();
        assertThatThrownBy(()->entry(r,Map.of())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{allocation(r,Map.of());audit(r);}))
                .isInstanceOf(RuntimeException.class);
        unchanged(r);
        pair(r,Map.of(),Map.of());
        assertThat(position(r.tenant(),r.obligation()).path("dueMinor").asString()).isEqualTo("80000");
    }

    @Test void unAuditManquantOuUnePreuveMismatcheeAnnuleAussiLeJournal() throws Exception {
        Raw r=raw();
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{allocation(r,Map.of());entry(r,Map.of());}))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("audit lie");
        for(Map<String,Object> e:List.of(Map.<String,Object>of("amount",-19999L),Map.<String,Object>of("id",UUID.randomUUID()),
                Map.<String,Object>of("at",Columns.timestamp(r.at().plusSeconds(1))))) {
            assertThatThrownBy(()->pair(r,Map.of(),e)).isInstanceOf(RuntimeException.class);
        }
        unchanged(r);
        pair(r,Map.of(),Map.of());
    }

    @Test void montantsDeviseVersionEtValeursNullNeContournentPasLesContraintes() throws Exception {
        Raw r=raw();
        for(Map<String,Object> a:List.of(Map.<String,Object>of("amount",0L),Map.<String,Object>of("amount",100001L),
                Map.<String,Object>of("currency","EUR"),Map.<String,Object>of("journal",2L),Map.<String,Object>of("due",99999L),
                Map.<String,Object>of("reference","preuve-fausse"),Map.<String,Object>of("billed",100001L))) {
            assertThatThrownBy(()->pair(r,a,Map.of())).isInstanceOf(RuntimeException.class);
        }
        for(String column:List.of("reference","amount","currency","entry","actor","journal","due","billed","paid","at")) {
            Map<String,Object> a=new HashMap<>(); a.put(column,null);
            assertThatThrownBy(()->pair(r,a,Map.of())).as(column).isInstanceOf(RuntimeException.class);
        }
        unchanged(r); pair(r,Map.of(),Map.of());
    }

    @Test void uneReceptionEtrangereOuUnAutreAuteurNePeutEtreUtiliseEnSql() throws Exception {
        Raw r=raw(); Tenant foreign=newTenant();
        UUID foreignCash=receive(foreign,foreign.adminToken(),100000);
        UUID otherActor=get("/api/v2/me",newStaff(r.tenant(),"CASHIER")).id("id");
        assertThatThrownBy(()->pair(r,Map.of("reception",foreignCash),Map.of())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->pair(r,Map.of("actor",otherActor),Map.of("actor",otherActor))).isInstanceOf(RuntimeException.class);
        unchanged(r); pair(r,Map.of(),Map.of());
    }

    @Test void leRegistreEtLesPreuvesSontImmuables() throws Exception {
        Raw r=raw(); pair(r,Map.of(),Map.of());
        for(String sql:List.of("UPDATE cash_allocations SET amount_minor=1 WHERE id=:id","DELETE FROM cash_allocations WHERE id=:id")) {
            assertThatThrownBy(()->jdbc.sql(sql).param("id",r.id()).update()).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(()->jdbc.sql("TRUNCATE cash_allocations CASCADE").update()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.sql("UPDATE obligation_entries SET amount_minor=-1 WHERE id=:id").param("id",r.entry()).update())
                .isInstanceOf(RuntimeException.class);
        assertThat(position(r.tenant(),r.obligation()).path("settledMinor").asString()).isEqualTo("20000");
    }

    @ParameterizedTest
    @ValueSource(ints={TransactionDefinition.ISOLATION_REPEATABLE_READ,TransactionDefinition.ISOLATION_SERIALIZABLE})
    void uneIsolationNonPriseEnChargeNePeutEcrireAvecUnAncienInstantane(int isolation) throws Exception {
        Raw r=raw(); var tx=new TransactionTemplate(transactions); tx.setIsolationLevel(isolation);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{allocation(r,Map.of());entry(r,Map.of());audit(r);}))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("READ COMMITTED");
        assertThatThrownBy(()->tx.executeWithoutResult(status->jdbc.sql("""
                INSERT INTO obligation_entries(id,establishment_id,obligation_id,kind,amount_minor,reason,idempotency_key,recorded_at,recorded_by)
                VALUES (:id,:tenant,:obligation,'DISCOUNT',-1,'Remise',:key,now(),:actor)
                """).param("id",UUID.randomUUID()).param("tenant",r.tenant().id()).param("obligation",r.obligation())
                .param("key",UUID.randomUUID().toString()).param("actor",r.actor()).update()))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("READ COMMITTED");
        unchanged(r); pair(r,Map.of(),Map.of());
    }

    @Test void lesCorrectionsEtContrepassationsDeReglementRestentRefuseesEnSql() throws Exception {
        Raw r=raw(); pair(r,Map.of(),Map.of());
        assertThatThrownBy(()->jdbc.sql("""
                INSERT INTO obligation_entries(id,establishment_id,obligation_id,kind,amount_minor,reason,idempotency_key,recorded_at,recorded_by)
                VALUES (:id,:tenant,:obligation,'CORRECTION',1,'Correction',:key,now(),:actor)
                """).param("id",UUID.randomUUID()).param("tenant",r.tenant().id()).param("obligation",r.obligation())
                .param("key",UUID.randomUUID().toString()).param("actor",r.actor()).update()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.sql("""
                INSERT INTO obligation_entries(id,establishment_id,obligation_id,kind,amount_minor,reason,idempotency_key,recorded_at,recorded_by,reverses_entry_id)
                VALUES (:id,:tenant,:obligation,'REVERSAL',20000,'Contrepassation',:key,now(),:actor,:entry)
                """).param("id",UUID.randomUUID()).param("tenant",r.tenant().id()).param("obligation",r.obligation())
                .param("key",UUID.randomUUID().toString()).param("actor",r.actor()).param("entry",r.entry()).update()).isInstanceOf(RuntimeException.class);
        assertThat(position(r.tenant(),r.obligation()).path("dueMinor").asString()).isEqualTo("80000");
    }

    @Test void leSqlNePeutApprouverUneDemandePerimeeMemeAvecUneEcritureConcordante() throws Exception {
        Raw r=raw(); Response pending=discount(r.tenant(),r.tenant().adminToken(),r.obligation(),30000);
        UUID request=UUID.fromString(pending.body().path("request").path("id").asString());
        pair(r,Map.of(),Map.of());
        UUID second=get("/api/v2/me",newStaff(r.tenant(),"ACCOUNTANT")).id("id");
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            UUID entry=UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO obligation_entries(id,establishment_id,obligation_id,kind,amount_minor,reason,idempotency_key,recorded_at,recorded_by,approved_by)
                    VALUES (:id,:tenant,:obligation,'DISCOUNT',-30000,'Remise accordée',:key,now(),:actor,:second)
                    """).param("id",entry).param("tenant",r.tenant().id()).param("obligation",r.obligation()).param("key","request:"+request)
                    .param("actor",r.actor()).param("second",second).update();
            jdbc.sql("UPDATE adjustment_requests SET status='APPROVED',entry_id=:entry,decided_at=now(),decided_by=:second WHERE id=:id")
                    .param("entry",entry).param("second",second).param("id",request).update();
        })).isInstanceOf(RuntimeException.class).hasStackTraceContaining("Le journal a changé");
        assertThat(position(r.tenant(),r.obligation()).path("dueMinor").asString()).isEqualTo("80000");
    }

    @Test void leSqlNePeutReglerPlusQueLesEspecesRecuesMemeSiLaDetteEstSuffisante() throws Exception {
        Raw original=raw();
        UUID small=receive(original.tenant(),original.tenant().adminToken(),10000);
        Raw r=new Raw(original.tenant(),small,original.obligation(),original.actor(),UUID.randomUUID(),UUID.randomUUID(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        assertThatThrownBy(()->pair(r,Map.of(),Map.of())).isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("especes disponibles");
        assertThat(count("cash_allocations",r.tenant())).isZero();
        assertThat(journalSize(r.obligation())).isEqualTo(1);
        pair(r,Map.of("amount",10000L),Map.of("amount",-10000L));
        assertThat(position(r.tenant(),r.obligation()).path("dueMinor").asString()).isEqualTo("90000");
        assertThat(get(r.tenant().path("/payments/cash-receptions/"+small),r.tenant().adminToken()).text("unallocatedMinor")).isEqualTo("0");
    }

    @Test void lesPolitiquesNonDefiniesEtAnneesFermeesRestentBloqueesMemeEnSql() throws Exception {
        Raw r=raw();
        assertThat(correction(r.tenant(),r.tenant().adminToken(),r.obligation(),1000).status()).isEqualTo(201);
        assertThatThrownBy(()->pair(r,Map.of("billed",101000L,"due",101000L,"journal",2L),Map.of()))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("ajustements non definie");
        assertThat(count("cash_allocations",r.tenant())).isZero();
        assertThat(reversal(r.tenant(),r.tenant().adminToken(),r.obligation(),entries(r.tenant(),r.obligation()).get(1).path("id").asString()).status()).isEqualTo(201);
        Raw restored=new Raw(r.tenant(),r.reception(),r.obligation(),r.actor(),UUID.randomUUID(),UUID.randomUUID(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        pair(restored,Map.of("journal",3L),Map.of());
        Raw closed=raw();
        UUID year=jdbc.sql("SELECT academic_year_id FROM obligations WHERE establishment_id=:tenant AND id=:id")
                .param("tenant",closed.tenant().id()).param("id",closed.obligation()).query(UUID.class).single();
        assertThat(patch(closed.tenant().path("/academic-years/"+year),closed.tenant().adminToken(),Map.of("status","CLOSED")).status()).isEqualTo(200);
        assertThatThrownBy(()->pair(closed,Map.of(),Map.of())).isInstanceOf(RuntimeException.class).hasStackTraceContaining("Annee non ouverte");
        unchanged(closed);
    }
}
