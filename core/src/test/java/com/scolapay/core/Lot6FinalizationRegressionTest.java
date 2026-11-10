package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.scolapay.core.billing.internal.BillingPartyRepository;
import com.scolapay.core.billing.internal.ObligationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

/** Le spy ne remplace aucune lecture : il retient un vrai résultat pour ordonner deux transactions HTTP. */
class Lot6FinalizationRegressionTest extends BillingScenarioTest {
    @MockitoSpyBean ObligationRepository obligations;
    @MockitoSpyBean BillingPartyRepository parties;
    @Autowired PlatformTransactionManager transactions;

    record Billed(School school, UUID student, UUID obligation, Linked linked, UUID party) {}

    Billed billed(String role) throws Exception {
        School school = school();
        UUID student = newStudent(school.tenant(), LocalDate.now().minusYears(14));
        enrolledAndBilled(school, student);
        Linked linked = linkedThirdParty(school.tenant(), student, true);
        UUID party = partyId(createParty(school.tenant(), "PERSON", "Personne liée", null, linked.grant()));
        UUID obligation = obligationOf(student);
        assertThat(designate(school.tenant(), school.tenant().adminToken(), obligation, party, role).status()).isEqualTo(201);
        return new Billed(school, student, obligation, linked, party);
    }

    UUID secondStudent(Billed first) throws Exception {
        Tenant tenant = first.school().tenant();
        UUID student = newStudent(tenant, LocalDate.now().minusYears(14));
        enrolledAndBilled(first.school(), student);
        String contact = get("/api/v2/me", first.linked().token()).body().path("contacts").get(0).asString();
        Response invited = inviteThirdParty(tenant, student, contact, false);
        assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
        assertThat(post("/api/v2/invitations/accept", first.linked().token(), Map.of("token", invited.text("token"))).status()).isEqualTo(200);
        return student;
    }

    @Test
    void uneObligationSansLienNeChangePasLaPaginationDesObligationsAccessibles() throws Exception {
        Billed first = billed("VOLUNTARY_CONTRIBUTOR");
        Tenant tenant = first.school().tenant();
        UUID secondStudent = secondStudent(first);
        UUID visible = obligationOf(secondStudent);
        assertThat(designate(tenant, tenant.adminToken(), visible, first.party(), "VOLUNTARY_CONTRIBUTOR").status()).isEqualTo(201);
        assertThat(delete(tenant.path("/students/"+first.student()+"/access-grants/"+first.linked().grant()), tenant.adminToken()).status()).isEqualTo(204);

        Response before = get("/api/v2/me/billing/obligations?limit=1", first.linked().token());
        Response beforeOffset = get("/api/v2/me/billing/obligations?limit=1&offset=1", first.linked().token());
        approve(tenant, newStaff(tenant, "ACCOUNTANT"), requestIdOf(discount(tenant, tenant.adminToken(), first.obligation(), AMOUNT)));
        Response after = get("/api/v2/me/billing/obligations?limit=1", first.linked().token());
        Response afterOffset = get("/api/v2/me/billing/obligations?limit=1&offset=1", first.linked().token());

        assertThat(before.status()).isEqualTo(200);
        assertThat(before.raw()).as("Une évolution privée ne modifie pas une page autorisée").isEqualTo(after.raw());
        assertThat(beforeOffset.raw()).isEqualTo(afterOffset.raw());
        assertThat(before.body().size()).isEqualTo(1);
        assertThat(before.body().get(0).path("id").asString()).isEqualTo(visible.toString());
        assertThat(beforeOffset.body().size()).isZero();
    }

    @Test
    void unEtablissementSuspenduNePrendPasUnePlaceDansLaPage() throws Exception {
        Billed first = billed("CONTRACTUAL_DEBTOR");
        School second = school();
        UUID student = enrolledAndBilled(second);
        String contact = get("/api/v2/me", first.linked().token()).body().path("contacts").get(0).asString();
        Response invited = inviteThirdParty(second.tenant(), student, contact, false);
        assertThat(post("/api/v2/invitations/accept", first.linked().token(), Map.of("token", invited.text("token"))).status()).isEqualTo(200);
        UUID grant = null;
        JsonNode grants = get(second.tenant().path("/students/"+student+"/access-grants"), second.tenant().adminToken()).body();
        for (JsonNode candidate : grants) if (candidate.path("accountId").asString().equals(first.linked().account().toString())) grant=UUID.fromString(candidate.path("id").asString());
        UUID party = partyId(createParty(second.tenant(), "PERSON", "Personne du second établissement", null, grant));
        UUID visible = obligationOf(student);
        designate(second.tenant(), second.tenant().adminToken(), visible, party, "CONTRACTUAL_DEBTOR");
        assertThat(post("/api/v2/platform/establishments/"+first.school().tenant().id()+"/status", operatorToken(), Map.of("status","SUSPENDED")).status()).isEqualTo(204);
        Response page = get("/api/v2/me/billing/obligations?limit=1", first.linked().token());
        assertThat(page.status()).isEqualTo(200);
        assertThat(page.body().size()).isEqualTo(1);
        assertThat(page.body().get(0).path("id").asString()).isEqualTo(visible.toString());
    }

    private static final class ReadBarrier {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean once = new AtomicBoolean();
        void pause() throws InterruptedException {
            if (once.compareAndSet(false,true)) {
                entered.countDown();
                assertThat(release.await(30, TimeUnit.SECONDS)).as("Le rédacteur doit libérer la lecture").isTrue();
            }
        }
    }

    private void snapshot(String role, boolean family, boolean list) throws Exception {
        Billed billed = billed(role);
        Tenant tenant = billed.school().tenant();
        ReadBarrier barrier = new ReadBarrier();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            barrier.pause();
            return result;
        }).when(obligations).find(tenant.id(), billed.obligation());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var read = executor.submit(() -> get(family ? "/api/v2/me/billing/obligations"+(list ? "" : "/"+billed.obligation())
                    : tenant.path("/billing/obligations/"+billed.obligation()), family ? billed.linked().token() : tenant.adminToken()));
            try {
                assertThat(barrier.entered.await(30,TimeUnit.SECONDS)).isTrue();
                assertThat(post(tenant.path("/billing/obligations/"+billed.obligation()+"/discounts"), tenant.adminToken(),
                        Map.of("amountMinor","10000","reason","Remise concurrente","idempotencyKey",key())).status()).isEqualTo(201);
            } finally { barrier.release.countDown(); }
            Response reply = read.get(30,TimeUnit.SECONDS);
            assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
            JsonNode view = list ? reply.body().get(0) : reply.body();
            JsonNode amount = family ? view : view.path("obligation");
            assertThat(amount.path("dueMinor").asString()).isEqualTo("100000");
            assertThat(view.path(role.equals("FINANCIAL_RESPONSIBLE") ? "history" : "entries").size())
                    .as("Le journal et le dû viennent du même instantané").isEqualTo(1);
        }
    }

    @Test void leDetailFamilialNeMelangePasDeuxEtatsDuJournal() throws Exception { snapshot("CONTRACTUAL_DEBTOR",true,false); }
    @Test void laListeFamilialeNeMelangePasDeuxEtatsDuJournal() throws Exception { snapshot("CONTRACTUAL_DEBTOR",true,true); }
    @Test void laVueDuResponsableNeMelangePasHistoriqueEtDu() throws Exception { snapshot("FINANCIAL_RESPONSIBLE",true,false); }
    @Test void leDetailDuPersonnelNeMelangePasDeuxEtatsDuJournal() throws Exception { snapshot("CONTRACTUAL_DEBTOR",false,false); }

    @Test
    void leComptageDesRedevablesEtLaPagePartagentUnInstantane() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        ReadBarrier barrier = new ReadBarrier();
        doAnswer(call -> { Object result=call.callRealMethod(); barrier.pause(); return result; })
                .when(parties).countByDebtorState(tenant.id(),school.year(),"NONE");
        String approver = newStaff(tenant,"ACCOUNTANT");
        try (var executor=Executors.newSingleThreadExecutor()) {
            var read=executor.submit(() -> get(tenant.path("/billing/obligations-by-debtor-state?academicYearId="+school.year()+"&state=NONE"),tenant.adminToken()));
            try {
                assertThat(barrier.entered.await(30,TimeUnit.SECONDS)).isTrue();
                approve(tenant,approver,requestIdOf(discount(tenant,tenant.adminToken(),obligation,AMOUNT)));
            } finally { barrier.release.countDown(); }
            Response reply=read.get(30,TimeUnit.SECONDS);
            assertThat(reply.status()).isEqualTo(200);
            assertThat(reply.body().path("total").asLong()).isEqualTo(1);
            assertThat(reply.body().path("obligations").size()).isEqualTo(1);
            assertThat(reply.body().path("obligations").get(0).path("dueMinor").asString()).isEqualTo("100000");
        }
    }

    @Test
    void unNonRedevableNePorteAucuneConfirmationMemeAvecUnStatutNull() throws Exception {
        Billed billed=billed("CONTRACTUAL_DEBTOR");
        Tenant tenant=billed.school().tenant();
        for (String role : List.of("FINANCIAL_RESPONSIBLE","VOLUNTARY_CONTRIBUTOR")) {
            assertThatThrownBy(() -> jdbc.sql("""
                    INSERT INTO obligation_parties(id,establishment_id,obligation_id,party_id,role,debtor_status,
                        commitment_reference,confirmed_at,confirmed_by,designated_at,designated_by)
                    VALUES(gen_random_uuid(),:establishment,:obligation,:party,:role,NULL,'FAUX',now(),:actor,now(),:actor)
                    """).param("establishment",tenant.id()).param("obligation",billed.obligation()).param("party",billed.party())
                    .param("role",role).param("actor",get("/api/v2/me",tenant.adminToken()).id("id")).update())
                    .as("Un rôle sans statut ne peut porter d'engagement : "+role).isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void toutesLesCombinaisonsNullDeConfirmationSontControleesEnInsertionEtMiseAJour() throws Exception {
        School school=school();
        Tenant tenant=school.tenant();
        UUID student=enrolledAndBilled(school);
        Linked linked=linkedThirdParty(tenant,student,false);
        UUID party=partyId(createParty(tenant,"PERSON","Personne liée",null,linked.grant()));
        UUID obligation=obligationOf(student);
        UUID actor=get("/api/v2/me",tenant.adminToken()).id("id");
        var allowed=java.util.Set.of("CONTRACTUAL_DEBTOR:UNCONFIRMED:0","CONTRACTUAL_DEBTOR:CONFIRMED:7",
                "FINANCIAL_RESPONSIBLE:null:0","VOLUNTARY_CONTRIBUTOR:null:0");
        for(String role:List.of("CONTRACTUAL_DEBTOR","FINANCIAL_RESPONSIBLE","VOLUNTARY_CONTRIBUTOR")) {
            for(String state:new String[]{null,"UNCONFIRMED","CONFIRMED"}) {
                for(int mask=0;mask<8;mask++) {
                    for(boolean update:new boolean[]{false,true}) {
                        int fields=mask;
                        String caseName=role+":"+state+":"+fields;
                        Runnable attempt=() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                            UUID id=UUID.randomUUID();
                            if(update) jdbc.sql("""
                                    INSERT INTO obligation_parties(id,establishment_id,obligation_id,party_id,role,debtor_status,designated_at,designated_by)
                                    VALUES(:id,:establishment,:obligation,:party,:role,:state,now(),:actor)
                                    """).param("id",id).param("establishment",tenant.id()).param("obligation",obligation).param("party",party)
                                    .param("role",role).param("state",role.equals("CONTRACTUAL_DEBTOR")?"UNCONFIRMED":null).param("actor",actor).update();
                            String sql=update
                                    ? "UPDATE obligation_parties SET debtor_status=:state,commitment_reference=:reference,confirmed_at=:at,confirmed_by=:by WHERE id=:id"
                                    : "INSERT INTO obligation_parties(id,establishment_id,obligation_id,party_id,role,debtor_status,commitment_reference,confirmed_at,confirmed_by,designated_at,designated_by) VALUES(:id,:establishment,:obligation,:party,:role,:state,:reference,:at,:by,now(),:actor)";
                            var command=jdbc.sql(sql).param("id",id).param("state",state)
                                    .param("reference",(fields&1)!=0?"CONTRAT":null)
                                    .param("at",(fields&2)!=0?java.sql.Timestamp.from(java.time.Instant.now()):null)
                                    .param("by",(fields&4)!=0?actor:null);
                            if(!update) command.param("establishment",tenant.id()).param("obligation",obligation).param("party",party).param("role",role).param("actor",actor);
                            command.update();
                            tx.setRollbackOnly();
                        });
                        if(allowed.contains(caseName)) org.assertj.core.api.Assertions.assertThatCode(attempt::run).as(caseName+" update="+update).doesNotThrowAnyException();
                        else assertThatThrownBy(attempt::run).as(caseName+" update="+update).isInstanceOf(DataAccessException.class);
                    }
                }
            }
        }
    }
}
