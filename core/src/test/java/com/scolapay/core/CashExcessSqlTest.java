package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.scolapay.core.shared.Columns;
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

class CashExcessSqlTest extends CashExcessScenario {
    @Autowired PlatformTransactionManager transactions;
    record Raw(Tenant tenant,UUID source,UUID actor,UUID id,UUID key,Instant at) {}
    Raw raw() {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000);
        return new Raw(t,source,get("/api/v2/me",t.adminToken()).id("id"),UUID.randomUUID(),UUID.randomUUID(),Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
    Map<String,Object> values(Raw r) {
        return new HashMap<>(Map.ofEntries(Map.entry("id",r.id()),Map.entry("tenant",r.tenant().id()),Map.entry("source",r.source()),
                Map.entry("actor",r.actor()),Map.entry("key",r.key()),Map.entry("at",Columns.timestamp(r.at())),Map.entry("currency","XAF"),
                Map.entry("amount",15000L),Map.entry("reason","Motif privé"),Map.entry("reference","XQ-"+r.id()),
                Map.entry("scope","RESERVATION_ONLY_V1"),Map.entry("retained",20000L),Map.entry("historical",0L),Map.entry("reserved",0L)));
    }
    void insert(Raw r,Map<String,Object> changes) {
        var p=values(r);p.putAll(changes);
        jdbc.sql("""
                INSERT INTO cash_excess_requests(id,establishment_id,reception_id,currency,amount_minor,reason,idempotency_key,
                  reference,authorization_scope,retained_minor,historical_allocated_before_minor,reserved_before_minor,created_at,created_by)
                VALUES (:id,:tenant,:source,:currency,:amount,:reason,:key,:reference,:scope,:retained,:historical,:reserved,:at,:actor)
                """).params(p).update();
    }
    void requestAudit(Raw r) {
        jdbc.sql("""
                INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                VALUES (:event,now(),:tenant,:actor,'payments.excess.requested','cash_excess_request',:id,
                   jsonb_build_object('reference',CAST(:reference AS text),'receptionId',CAST(:source AS text)))
                """).param("event",UUID.randomUUID()).param("tenant",r.tenant().id()).param("actor",r.actor()).param("id",r.id())
                .param("reference","XQ-"+r.id()).param("source",r.source().toString()).update();
    }
    void commit(Raw r,Map<String,Object> changes) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{insert(r,changes);requestAudit(r);});
    }
    void event(Raw r,UUID id,UUID actor,String kind,String note) {
        jdbc.sql("""
                INSERT INTO cash_excess_decisions(id,establishment_id,request_id,kind,note,idempotency_key,reference,decided_at,decided_by)
                VALUES (:id,:tenant,:request,:kind,:note,:key,:reference,now(),:actor)
                """).param("id",id).param("tenant",r.tenant().id()).param("request",r.id()).param("kind",kind).param("note",note)
                .param("key",UUID.randomUUID()).param("reference","XD-"+id).param("actor",actor).update();
    }
    void eventAudit(Raw r,UUID id,UUID actor,String kind) {
        jdbc.sql("""
                INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                VALUES (:event,now(),:tenant,:actor,:action,'cash_excess_decision',:id,
                  jsonb_build_object('reference',CAST(:reference AS text),'requestId',CAST(:request AS text)))
                """).param("event",UUID.randomUUID()).param("tenant",r.tenant().id()).param("actor",actor).param("id",id)
                .param("action","payments.excess."+kind.toLowerCase(java.util.Locale.ROOT)).param("reference","XD-"+id)
                .param("request",r.id().toString()).update();
    }
    void decide(Raw r,UUID actor,String kind,String note) {
        UUID id=UUID.randomUUID();new TransactionTemplate(transactions).executeWithoutResult(tx->{event(r,id,actor,kind,note);eventAudit(r,id,actor,kind);});
    }

    @Test void budgetEtValeursNullNeSontPasContournablesEtLeControlePositifCommit() {
        Raw r=raw();
        for(Map<String,Object> bad:List.of(Map.<String,Object>of("amount",0L),Map.<String,Object>of("amount",20001L),
                Map.<String,Object>of("reason"," \t "),Map.<String,Object>of("reason","a".repeat(501)),
                Map.<String,Object>of("retained",20001L),Map.<String,Object>of("historical",1L),Map.<String,Object>of("reserved",1L),
                Map.<String,Object>of("currency","EUR"),Map.<String,Object>of("reference","autre"),Map.<String,Object>of("scope","PHYSICAL_HANDOVER_V2"))) {
            assertThatThrownBy(()->commit(r,bad)).isInstanceOf(RuntimeException.class);
        }
        for(String field:List.of("source","actor","amount","reason","reference","scope","retained","historical","reserved","key","at","currency")) {
            Map<String,Object> bad=new HashMap<>();bad.put(field,null);
            assertThatThrownBy(()->commit(r,bad)).as(field).isInstanceOf(RuntimeException.class);
        }
        assertThat(count("cash_excess_requests",r.tenant())).isZero();
        commit(r,Map.of());assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("15000");
    }

    @Test void nouvelleReferenceEtNouveauSnapshotNeReconstituentPasLeBudget() {
        Raw r=raw();commit(r,Map.of());
        Raw next=new Raw(r.tenant(),r.source(),r.actor(),UUID.randomUUID(),UUID.randomUUID(),r.at());
        assertThatThrownBy(()->commit(next,Map.of())).isInstanceOf(RuntimeException.class).hasStackTraceContaining("Budget jamais affecte");
        commit(next,Map.of("amount",5000L,"reserved",15000L));
        assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("20000");
        assertThat(funds(r.tenant(),r.source()).text("unallocatedMinor")).isEqualTo("20000");
    }

    @Test void auditManquantAnnuleLaReservationEtLeRetraitNonAuditeNeLibereRien() {
        Raw r=raw();
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->insert(r,Map.of())))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("audit lie");
        assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("0");
        commit(r,Map.of());
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->event(r,UUID.randomUUID(),r.actor(),"WITHDRAWN","Retrait")))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("audit lie");
        assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("15000");
        decide(r,r.actor(),"WITHDRAWN","Retrait avant examen");
        assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("0");
    }

    @Test void leSqlExigeUnAutreComptePourApprouverEtRefuseTouteRemise() {
        Raw r=raw();commit(r,Map.of());
        assertThatThrownBy(()->decide(r,r.actor(),"APPROVED",null)).isInstanceOf(RuntimeException.class);
        UUID reviewer=get("/api/v2/me",newStaff(r.tenant(),"DIRECTOR")).id("id");
        for(String kind:List.of("COMPLETED","HANDOVER_CONFIRMED","REFUNDED")) {
            assertThatThrownBy(()->decide(r,reviewer,kind,null)).isInstanceOf(RuntimeException.class)
                    .hasStackTraceContaining("remise physique");
        }
        assertThat(count("cash_excess_decisions",r.tenant())).isZero();
        decide(r,reviewer,"APPROVED",null);
        assertThatThrownBy(()->decide(r,r.actor(),"WITHDRAWN","Après approbation")).isInstanceOf(RuntimeException.class);
        assertThat(excess(r.tenant(),r.id()).text("restitutedMinor")).isEqualTo("0");
        assertThat(funds(r.tenant(),r.source()).text("reservedMinor")).isEqualTo("15000");
    }

    @Test void etablissementEtAuteurNePeuventEtreReassociesNiLesFaitsEffaces() {
        Raw r=raw();Tenant other=newTenant();UUID foreign=receive(other,other.adminToken(),20000);
        UUID foreignActor=get("/api/v2/me",other.adminToken()).id("id");
        assertThatThrownBy(()->commit(r,Map.of("source",foreign,"actor",foreignActor))).isInstanceOf(RuntimeException.class);
        UUID coworker=get("/api/v2/me",newStaff(r.tenant(),"CASHIER")).id("id");
        assertThatThrownBy(()->commit(r,Map.of("actor",coworker))).isInstanceOf(RuntimeException.class);
        commit(r,Map.of());
        for(String statement:List.of("UPDATE cash_excess_requests SET amount_minor=1 WHERE id=:id",
                "UPDATE cash_excess_requests SET authorization_scope='PHYSICAL_HANDOVER_V2' WHERE id=:id",
                "UPDATE cash_excess_requests SET reception_id=gen_random_uuid() WHERE id=:id","DELETE FROM cash_excess_requests WHERE id=:id")) {
            assertThatThrownBy(()->jdbc.sql(statement).param("id",r.id()).update()).isInstanceOf(RuntimeException.class);
        }
        decide(r,get("/api/v2/me",newStaff(r.tenant(),"DIRECTOR")).id("id"),"APPROVED",null);
        assertThatThrownBy(()->jdbc.sql("UPDATE cash_excess_decisions SET kind='WITHDRAWN' WHERE request_id=:id").param("id",r.id()).update()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.sql("DELETE FROM cash_excess_decisions WHERE request_id=:id").param("id",r.id()).update()).isInstanceOf(RuntimeException.class);
        for(String table:List.of("cash_excess_requests","cash_excess_decisions")) {
            assertThatThrownBy(()->jdbc.sql("TRUNCATE "+table+" CASCADE").update()).isInstanceOf(RuntimeException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(ints={TransactionDefinition.ISOLATION_REPEATABLE_READ,TransactionDefinition.ISOLATION_SERIALIZABLE})
    void uneIsolationNonPriseEnChargeNePeutReserverNiDecider(int isolation) {
        Raw r=raw();var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(isolation);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{insert(r,Map.of());requestAudit(r);}))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("READ COMMITTED");
        commit(r,Map.of());
        UUID reviewer=get("/api/v2/me",newStaff(r.tenant(),"DIRECTOR")).id("id");
        UUID event=UUID.randomUUID();
        assertThatThrownBy(()->tx.executeWithoutResult(s->{event(r,event,reviewer,"APPROVED",null);eventAudit(r,event,reviewer,"APPROVED");}))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("READ COMMITTED");
        decide(r,reviewer,"APPROVED",null);
    }

    @Test void uneAffectationSqlNePeutConsommerUneReservationActive() throws Exception {
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s),source=receive(t,t.adminToken(),20000);
        UUID actor=get("/api/v2/me",t.adminToken()).id("id");
        Raw r=new Raw(t,source,actor,UUID.randomUUID(),UUID.randomUUID(),Instant.now().truncatedTo(ChronoUnit.MICROS));commit(r,Map.of());
        assertThatThrownBy(()->allocation(t,source,o,actor,20000)).isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("fonds reserves");
        assertThat(count("cash_allocations",t)).isZero();assertThat(journalSize(o)).isEqualTo(1);
        allocation(t,source,o,actor,5000);
        assertThat(funds(t,source).text("availableForAllocationMinor")).isEqualTo("0");
    }

    void allocation(Tenant t,UUID source,UUID obligation,UUID actor,long amount) {
        UUID id=UUID.randomUUID(),entry=UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            jdbc.sql("""
                    INSERT INTO cash_allocations(id,establishment_id,reception_id,obligation_id,settlement_entry_id,idempotency_key,
                      reference,currency,amount_minor,issued_minor,net_billed_minor,settled_before_minor,due_before_minor,journal_size_before,allocated_at,allocated_by)
                    VALUES (:id,:tenant,:source,:obligation,:entry,:key,:reference,'XAF',:amount,100000,100000,0,100000,1,:at,:actor)
                    """).param("id",id).param("tenant",t.id()).param("source",source).param("obligation",obligation)
                    .param("entry",entry).param("key",UUID.randomUUID()).param("reference","CA-"+id).param("amount",amount)
                    .param("at",Columns.timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))).param("actor",actor).update();
            jdbc.sql("""
                    INSERT INTO obligation_entries(id,establishment_id,obligation_id,kind,amount_minor,idempotency_key,recorded_at,recorded_by,cash_allocation_id)
                    SELECT :entry,establishment_id,obligation_id,'SETTLEMENT',-amount_minor,:key,allocated_at,allocated_by,id
                    FROM cash_allocations WHERE id=:id
                    """).param("entry",entry).param("key","cash:"+id).param("id",id).update();
            jdbc.sql("""
                    INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                    VALUES (:event,now(),:tenant,:actor,'payments.cash.allocated','cash_allocation',:id,
                      jsonb_build_object('reference',CAST(:ref AS text),'receptionId',CAST(:source AS text),'obligationId',CAST(:obligation AS text)))
                    """).param("event",UUID.randomUUID()).param("tenant",t.id()).param("actor",actor).param("id",id)
                    .param("ref","CA-"+id).param("source",source.toString()).param("obligation",obligation.toString()).update();
        });
    }
}
