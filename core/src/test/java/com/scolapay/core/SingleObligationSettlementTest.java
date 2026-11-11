package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"scolapay.payments.cash-receptions-enabled=true", "scolapay.payments.cash-allocations-enabled=true"})
class SingleObligationSettlementTest extends CashSettlementScenario {
    @Test
    void uneReceptionExistanteReglePartiellementUneObligationSansRecreerDesEspeces() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        Response reception = post(tenant.path("/payments/cash-receptions"), tenant.adminToken(), Map.of(
                "idempotencyKey", UUID.randomUUID(), "amountReceivedMinor", "60000", "changeGivenMinor", "0", "cashReceivedConfirmed", true));
        assertThat(reception.status()).as(reception.raw()).isEqualTo(201);
        Response allocation = post(tenant.path("/payments/cash-receptions/" + reception.id("id") + "/allocations"),
                tenant.adminToken(), Map.of("idempotencyKey", UUID.randomUUID(), "obligationId", obligation,
                        "amountMinor", "40000", "expectedJournalSize", journalSize(obligation)));
        assertThat(allocation.status()).as(allocation.raw()).isEqualTo(201);
        assertThat(allocation.text("dueBeforeMinor")).isEqualTo("100000");
        assertThat(allocation.text("dueAfterMinor")).isEqualTo("60000");
        assertThat(allocation.text("netBilledMinor")).isEqualTo("100000");
        assertThat(allocation.text("settledAfterMinor")).isEqualTo("40000");
        assertThat(obligationView(tenant, obligation).path("dueMinor").asString()).isEqualTo("60000");
        Response remaining = get(tenant.path("/payments/cash-receptions/" + reception.id("id")), tenant.adminToken());
        assertThat(remaining.text("unallocatedMinor")).isEqualTo("20000");
        assertThat(jdbc.sql("SELECT count(*) FROM cash_receptions WHERE establishment_id=:id").param("id", tenant.id()).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void leRejeuRendLaPreuveHistoriqueMemeApresUneAutreEcriture() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        UUID source=receive(t,t.adminToken(),60000); UUID key=UUID.randomUUID();
        Response first=allocate(t,t.adminToken(),source,o,key,40000,1);
        assertThat(first.status()).as(first.raw()).isEqualTo(201);
        assertThat(discount(t,t.adminToken(),o,5000).status()).isEqualTo(201);
        Response replay=allocate(t,t.adminToken(),source,o,key,40000,1);
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(get(t.path("/payments/allocations/"+first.id("id")),t.adminToken()).body()).isEqualTo(first.body());
        assertThat(position(t,o).path("dueMinor").asString()).isEqualTo("55000");
        assertThat(count("cash_allocations",t)).isEqualTo(1);
        assertThat(allocate(t,t.adminToken(),source,o,key,39999,1).code()).isEqualTo("CASH_ALLOCATION_KEY_CONFLICT");
    }

    @Test
    void huitRejeuxConcurrentsNeReglentQuUneFois() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        UUID source=receive(t,t.adminToken(),50000), key=UUID.randomUUID();
        var replies=concurrently(8,()->allocate(t,t.adminToken(),source,o,key,40000,1));
        assertThat(replies).allSatisfy(r->assertThat(r.status()).as(r.raw()).isIn(200,201));
        assertThat(replies.stream().filter(r->r.status()==201)).hasSize(1);
        assertThat(count("cash_allocations",t)).isEqualTo(1);
        assertThat(position(t,o).path("settledMinor").asString()).isEqualTo("40000");
    }

    @Test
    void deuxReceptionsConcurrentesNePeuventSurpayerEtLaReceptionRefuseeResteVisible() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        UUID a=receive(t,t.adminToken(),80000), b=receive(t,t.adminToken(),80000);
        CountDownLatch start=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var fa=executor.submit(()->{start.await();return allocate(t,t.adminToken(),a,o,UUID.randomUUID(),80000,1);});
            var fb=executor.submit(()->{start.await();return allocate(t,t.adminToken(),b,o,UUID.randomUUID(),80000,1);});
            start.countDown(); var ra=fa.get(15,TimeUnit.SECONDS); var rb=fb.get(15,TimeUnit.SECONDS);
            assertThat(List.of(ra.status(),rb.status())).containsExactlyInAnyOrder(201,409);
            UUID remaining=ra.status()==201?b:a;
            assertThat(get(t.path("/payments/cash-receptions/"+remaining),t.adminToken()).text("unallocatedMinor")).isEqualTo("80000");
            assertThat(allocate(t,t.adminToken(),remaining,o,UUID.randomUUID(),20000,2).status()).isEqualTo(201);
        }
        assertThat(count("cash_receptions",t)).isEqualTo(2);
        assertThat(position(t,o).path("dueMinor").asString()).isEqualTo("0");
        assertThat(position(t,o).path("status").asString()).isEqualTo("SETTLED");
    }

    @Test
    void lePaiementNeConsommePasLeSeuilDeRemiseEtUneRemiseFinaleExigeToujoursDeuxPersonnes() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        UUID source=receive(t,t.adminToken(),100000);
        assertThat(allocate(t,t.adminToken(),source,o,UUID.randomUUID(),80000,1).status()).isEqualTo(201);
        assertThat(discount(t,t.adminToken(),o,5000).status()).isEqualTo(201);
        Response finalDiscount=discount(t,t.adminToken(),o,15000);
        assertThat(finalDiscount.status()).as(finalDiscount.raw()).isEqualTo(202);
        assertThat(approve(t,t.adminToken(),finalDiscount.body().path("request").path("id").asString()).status()).isEqualTo(403);
        String accountant=newStaff(t,"ACCOUNTANT");
        assertThat(approve(t,accountant,finalDiscount.body().path("request").path("id").asString()).status()).isEqualTo(200);
        var p=position(t,o);
        assertThat(p.path("adjustmentsMinor").asString()).isEqualTo("-20000");
        assertThat(p.path("netBilledMinor").asString()).isEqualTo("80000");
        assertThat(p.path("settledMinor").asString()).isEqualTo("80000");
        assertThat(p.path("status").asString()).isEqualTo("SETTLED_WITH_ADJUSTMENTS");
    }

    @Test
    void uneDemandeAnterieureDevientPerimeeEtLesCheminsNonTranchesRestentFermes() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        Response pending=discount(t,t.adminToken(),o,30000);
        assertThat(pending.status()).isEqualTo(202);
        UUID source=receive(t,t.adminToken(),90000);
        assertThat(allocate(t,t.adminToken(),source,o,UUID.randomUUID(),80000,1).status()).isEqualTo(201);
        String accountant=newStaff(t,"ACCOUNTANT");
        assertThat(approve(t,accountant,pending.body().path("request").path("id").asString()).code()).isEqualTo("REQUEST_OUTDATED");
        assertThat(discount(t,t.adminToken(),o,30000).status()).isEqualTo(422);
        assertThat(correction(t,t.adminToken(),o,1).status()).isEqualTo(409);
        assertThat(cancellation(t,t.adminToken(),o).status()).isEqualTo(409);
        String entry=entries(t,o).get(1).path("id").asString();
        assertThat(reversal(t,t.adminToken(),o,entry).status()).isIn(409,422);
        assertThat(position(t,o).path("dueMinor").asString()).isEqualTo("20000");
    }

    @ParameterizedTest
    @ValueSource(strings={"CASHIER","ACCOUNTANT","ESTABLISHMENT_ADMIN"})
    void lesRolesHabilitesAffectentLeursPropresDeclarations(String role) throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school); String actor=newStaff(t,role);
        assertThat(allocate(t,actor,receive(t,actor,10000),o,UUID.randomUUID(),10000,1).status()).isEqualTo(201);
    }

    @ParameterizedTest
    @ValueSource(strings={"DIRECTOR","AUDITOR","REGISTRAR"})
    void lesRolesNonOperateursNeReglentPas(String role) throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        assertThat(allocate(t,newStaff(t,role),receive(t,t.adminToken(),10000),o,UUID.randomUUID(),10000,1).status()).isEqualTo(403);
    }

    @Test
    void isolationEtProprieteDeCaisseRestentAppliquees() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        UUID source=receive(t,t.adminToken(),10000);
        assertThat(allocate(t,newStaff(t,"CASHIER"),source,o,UUID.randomUUID(),10000,1).code()).isEqualTo("CASH_RECEPTION_AUTHOR_REQUIRED");
        assertThat(allocate(t,newTenant().adminToken(),source,o,UUID.randomUUID(),10000,1).status()).isEqualTo(404);
        UUID other=issuedObligation(school());
        assertThat(allocate(t,t.adminToken(),source,other,UUID.randomUUID(),10000,1).status()).isEqualTo(404);
        assertThat(count("cash_allocations",t)).isZero();
    }

    @Test
    void lesCorrectionsActivesEtAnneesFermeesNeRecoiventPasDeReglement() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        assertThat(correction(t,t.adminToken(),o,1000).status()).isEqualTo(201);
        assertThat(position(t,o).path("installmentsAvailable").asBoolean()).isFalse();
        UUID source=receive(t,t.adminToken(),10000);
        assertThat(allocate(t,t.adminToken(),source,o,UUID.randomUUID(),10000,2).code()).isEqualTo("ADJUSTMENT_POLICY_UNDEFINED");
        School other=school(); UUID target=issuedObligation(other); UUID cash=receive(other.tenant(),other.tenant().adminToken(),10000);
        assertThat(patch(other.tenant().path("/academic-years/"+other.year()),other.tenant().adminToken(),Map.of("status","CLOSED")).status()).isEqualTo(200);
        assertThat(allocate(other.tenant(),other.tenant().adminToken(),cash,target,UUID.randomUUID(),10000,1).code()).isEqualTo("ACADEMIC_YEAR_NOT_OPEN");
    }

    @Test
    void laReceptionNeDevientPasUnCreditTransferableEtLePlanPerimeNestPasAjusteSilencieusement() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID a=issuedObligation(school),b=issuedObligation(school);
        UUID source=receive(t,t.adminToken(),50000);
        assertThat(discount(t,t.adminToken(),a,1000).status()).isEqualTo(201);
        assertThat(allocate(t,t.adminToken(),source,a,UUID.randomUUID(),10000,1).code()).isEqualTo("ALLOCATION_OUTDATED");
        assertThat(allocate(t,t.adminToken(),source,a,UUID.randomUUID(),10000,2).status()).isEqualTo(201);
        assertThat(allocate(t,t.adminToken(),source,b,UUID.randomUUID(),10000,1).code()).isEqualTo("RECEPTION_ALREADY_BOUND");
        assertThat(allocate(t,t.adminToken(),source,a,UUID.randomUUID(),40001,3).code()).isEqualTo("AMOUNT_EXCEEDS_RECEPTION");
        assertThat(get(t.path("/payments/cash-receptions/"+source),t.adminToken()).text("unallocatedMinor")).isEqualTo("40000");
    }

    @Test
    void paiementsEtRemisesCouvrentLesEcheancesDansLesSensValidesSansLesModifier() throws Exception {
        School school=twoTermsSchool(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        assertThat(discount(t,t.adminToken(),o,20000).status()).isEqualTo(201);
        assertThat(allocate(t,t.adminToken(),receive(t,t.adminToken(),50000),o,UUID.randomUUID(),50000,2).status()).isEqualTo(201);
        var p=position(t,o);
        assertThat(p.path("installments").get(0).path("netMinor").asString()).isEqualTo("40000");
        assertThat(p.path("installments").get(0).path("settledMinor").asString()).isEqualTo("40000");
        assertThat(p.path("installments").get(1).path("originalMinor").asString()).isEqualTo("60000");
        assertThat(p.path("installments").get(1).path("netMinor").asString()).isEqualTo("40000");
        assertThat(p.path("installments").get(1).path("settledMinor").asString()).isEqualTo("10000");
        assertThat(p.path("installments").get(1).path("dueMinor").asString()).isEqualTo("30000");
        var original=get(t.path("/billing/obligations/"+o),t.adminToken()).body().path("installments");
        assertThat(original.get(0).path("amountMinor").asString()).isEqualTo("40000");
        assertThat(original.get(1).path("amountMinor").asString()).isEqualTo("60000");
        UUID student=UUID.fromString(obligationView(t,o).path("studentId").asString());
        var balance=get(t.path("/billing/students/"+student+"/balances"),t.adminToken());
        assertThat(balance.status()).as(balance.raw()).isEqualTo(200);
        assertThat(balance.body().get(0).path("nextDueOn").asString()).isEqualTo("2099-01-15");
        assertThat(balance.body().get(0).path("settledMinor").asString()).isEqualTo("50000");
        Response cumulative=discount(t,t.adminToken(),o,6000);
        assertThat(cumulative.status()).as(cumulative.raw()).isEqualTo(202);
        assertThat(position(t,o).path("dueMinor").asString()).isEqualTo("30000");
    }

    @Test
    void uneApprobationConcurrenteNePeutValiderUnAncienJournalApresUnPaiement() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        Response pending=discount(t,t.adminToken(),o,30000); assertThat(pending.status()).isEqualTo(202);
        String request=pending.body().path("request").path("id").asString(), approver=newStaff(t,"ACCOUNTANT");
        UUID source=receive(t,t.adminToken(),70000); CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var payment=executor.submit(()->{barrier.await();return allocate(t,t.adminToken(),source,o,UUID.randomUUID(),70000,1);});
            var approval=executor.submit(()->{barrier.await();return approve(t,approver,request);});
            barrier.countDown(); var a=payment.get(15,TimeUnit.SECONDS); var b=approval.get(15,TimeUnit.SECONDS);
            assertThat(a.status()).as(a.raw()).isIn(201,409);
            assertThat(b.status()).as(b.raw()).isIn(200,409);
            assertThat(a.status()==201 && b.status()==200).isFalse();
            assertThat(a.status()==201 || b.status()==200).isTrue();
        }
        assertThat(Long.parseLong(position(t,o).path("dueMinor").asString())).isGreaterThanOrEqualTo(0);
        assertThat(journalSize(o)).isEqualTo(2);
    }

    @Test
    void unEchecDAuditAnnuleAffectationEtReglementMaisConserveLesEspecesRecues() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        UUID source=receive(t,t.adminToken(),100000), key=UUID.randomUUID();
        String constraint="test_allocation_"+UUID.randomUUID().toString().replace("-","");
        jdbc.sql("ALTER TABLE audit_events ADD CONSTRAINT "+constraint+" CHECK (NOT (establishment_id='"+t.id()
                +"'::uuid AND action='payments.cash.allocated')) NOT VALID").update();
        try {
            Response failed=allocate(t,t.adminToken(),source,o,key,40000,1);
            assertThat(failed.status()).as(failed.raw()).isEqualTo(409);
            assertThat(failed.raw()).doesNotContain("INSERT","SQLException","cash_allocations","40000");
            assertThat(count("cash_allocations",t)).isZero();
            assertThat(journalSize(o)).isEqualTo(1);
            assertThat(count("cash_receptions",t)).isEqualTo(1);
            assertThat(get(t.path("/payments/cash-receptions/"+source),t.adminToken()).text("unallocatedMinor")).isEqualTo("100000");
        } finally { jdbc.sql("ALTER TABLE audit_events DROP CONSTRAINT "+constraint).update(); }
        assertThat(allocate(t,t.adminToken(),source,o,key,40000,1).status()).isEqualTo(201);
        assertThat(count("cash_receptions",t)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings={"CONTRACTUAL_DEBTOR","FINANCIAL_RESPONSIBLE","VOLUNTARY_CONTRIBUTOR"})
    void lesVuesFamilialesNeConfondentPasReglementEtRemiseEtNeRecoiventPasLesPreuvesIndividuelles(String role) throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        UUID student=UUID.fromString(obligationView(t,o).path("studentId").asString());
        Linked linked=linkedThirdParty(t,student,false);
        UUID party=partyId(createParty(t,"PERSON","Tiers désigné",null,linked.grant()));
        assertThat(designate(t,t.adminToken(),o,party,role).status()).isEqualTo(201);
        assertThat(discount(t,t.adminToken(),o,10000).status()).isEqualTo(201);
        UUID source=receive(t,t.adminToken(),30000);
        Response paid=allocate(t,t.adminToken(),source,o,UUID.randomUUID(),30000,2);
        assertThat(paid.status()).as(paid.raw()).isEqualTo(201);
        Response family=get("/api/v2/me/billing/obligations/"+o,linked.token());
        assertThat(family.status()).as(family.raw()).isEqualTo(200);
        assertThat(family.text("dueMinor")).isEqualTo("60000");
        assertThat(family.raw()).doesNotContain(paid.text("reference"),paid.text("settlementEntryId"),source.toString(),"SETTLEMENT");
        if ("FINANCIAL_RESPONSIBLE".equals(role)) assertThat(family.text("adjustmentsTotalMinor")).isEqualTo("-10000");
        if ("VOLUNTARY_CONTRIBUTOR".equals(role)) {
            assertThat(family.body().properties().stream().map(Map.Entry::getKey))
                    .containsExactlyInAnyOrder("view","id","establishmentName","reference","currency","dueMinor","nextDueOn","studentName");
        }
        assertThat(allocate(t,linked.token(),source,o,UUID.randomUUID(),1000,3).status()).isEqualTo(404);
    }

    @Test
    void affecterDesEspecesNeCreeAucuneDesignationFinanciere() throws Exception {
        School school=school(); Tenant t=school.tenant(); UUID o=issuedObligation(school);
        UUID student=UUID.fromString(obligationView(t,o).path("studentId").asString());
        Linked parent=linkedThirdParty(t,student,true);
        assertThat(allocate(t,t.adminToken(),receive(t,t.adminToken(),10000),o,UUID.randomUUID(),10000,1).status()).isEqualTo(201);
        assertThat(count("billing_parties",t)).isZero();
        assertThat(count("obligation_parties",t)).isZero();
        assertThat(get("/api/v2/me/billing/obligations/"+o,parent.token()).status()).isEqualTo(404);
    }
}
