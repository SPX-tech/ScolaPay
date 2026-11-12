package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties="scolapay.payments.digital.intents-enabled=true")
class DigitalPaymentIntentTest extends BillingScenarioTest {
    Response prepare(Tenant t,String actor,UUID obligation,UUID key,long amount,long journal){
        return post(t.path("/payments/digital-intents"),actor,Map.of("obligationId",obligation,"idempotencyKey",key,
                "amountMinor",Long.toString(amount),"currency","XAF","expectedJournalSize",journal));
    }
    long rows(String table,Tenant t){return jdbc.sql("SELECT count(*) FROM "+table+" WHERE establishment_id=:tenant").param("tenant",t.id()).query(Long.class).single();}
    @Test void uneIntentionNumeriqueNeConstateAucunPaiementEtNeReduitPasLeDu() throws Exception{
        School s=school();Tenant t=s.tenant();UUID obligation=issuedObligation(s);
        Response prepared=prepare(t,t.adminToken(),obligation,UUID.randomUUID(),40000,1);
        assertThat(prepared.status()).as(prepared.raw()).isEqualTo(201);
        assertThat(prepared.text("state")).isEqualTo("PREPARED");assertThat(prepared.text("provider")).isEqualTo("KADRYZA");
        assertThat(prepared.text("purpose")).isEqualTo("PREPARATION_ONLY_V1");assertThat(prepared.text("originType")).isEqualTo("SCHOOL_OBLIGATION");
        assertThat(prepared.body().path("paymentConfirmed").asBoolean()).isFalse();assertThat(prepared.body().path("settlementCreated").asBoolean()).isFalse();
        assertThat(prepared.body().path("providerDispatchAvailable").asBoolean()).isFalse();
        Response position=get(t.path("/billing/obligations/"+obligation+"/financial-position"),t.adminToken());
        assertThat(position.text("dueMinor")).isEqualTo("100000");assertThat(position.text("settledMinor")).isEqualTo("0");
        assertThat(journalSize(obligation)).isEqualTo(1);assertThat(rows("cash_receptions",t)).isZero();assertThat(rows("cash_allocations",t)).isZero();
    }
    @Test void unRejeuRetrouveLaMemeIntentionEtSaReferenceSansNouvelleOperation() throws Exception{
        School s=school();Tenant t=s.tenant();UUID obligation=issuedObligation(s),key=UUID.randomUUID();
        Response first=prepare(t,t.adminToken(),obligation,key,40000,1);assertThat(first.status()).as(first.raw()).isEqualTo(201);
        Response replay=prepare(t,t.adminToken(),obligation,key,40000,1);assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(first.body());assertThat(first.text("providerReference")).startsWith("DPI-");
        assertThat(rows("digital_payment_intents",t)).isEqualTo(1);assertThat(journalSize(obligation)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"CASHIER","DIRECTOR","AUDITOR","REGISTRAR"})
    void unRoleDeLectureOuDeCaisseNePreparePasDePaiementNumerique(String role) throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s);
        assertThat(prepare(t,newStaff(t,role),o,UUID.randomUUID(),40000,1).status()).isEqualTo(403);
        assertThat(rows("digital_payment_intents",t)).isZero();
    }
    @Test void leComptablePeutPreparerEtLesLecteursFinanciersPeuventRelireSansDroitFamilial() throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s);String accountant=newStaff(t,"ACCOUNTANT");
        Response made=prepare(t,accountant,o,UUID.randomUUID(),40000,1);assertThat(made.status()).as(made.raw()).isEqualTo(201);
        String path=t.path("/payments/digital-intents/"+made.id("id"));
        for(String role:List.of("CASHIER","DIRECTOR","AUDITOR"))assertThat(get(path,newStaff(t,role)).status()).isEqualTo(200);
        assertThat(get(path,newStaff(t,"REGISTRAR")).status()).isEqualTo(403);
        for(String outsider:List.of(newTenant().adminToken(),newGuardian(t,newStudent(t)),operatorToken())){
            assertThat(get(path,outsider).status()).isEqualTo(404);assertThat(prepare(t,outsider,o,UUID.randomUUID(),40000,1).status()).isEqualTo(404);
        }
        assertThat(made.raw()).doesNotContain("studentName","reason","contact","apiKey","customer_phone");
    }
    @Test void lesMontantsFlottantsNulsEtLesChampsDeConfirmationSontRefuses() throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s);
        Map<String,Object> valid=Map.of("obligationId",o,"idempotencyKey",UUID.randomUUID(),"currency","XAF","amountMinor","40000","expectedJournalSize",1);
        for(var changes:List.of(Map.<String,Object>of("amountMinor","0"),Map.<String,Object>of("amountMinor","-1"),Map.<String,Object>of("amountMinor","1.5"),
                Map.<String,Object>of("amountMinor",40000.5),Map.<String,Object>of("amountMinor","1000000000000000"),Map.<String,Object>of("expectedJournalSize",0),
                Map.<String,Object>of("expectedJournalSize",1.5),Map.<String,Object>of("expectedJournalSize","9".repeat(19)),Map.<String,Object>of("paymentConfirmed",true),Map.<String,Object>of("state","SUCCESS"),
                Map.<String,Object>of("merchant_id",UUID.randomUUID()),Map.<String,Object>of("environment","live"))){
            var body=new HashMap<>(valid);body.putAll(changes);
            Response rejected=post(t.path("/payments/digital-intents"),t.adminToken(),body);assertThat(rejected.status()).as(changes+" "+rejected.raw()).isEqualTo(400);
        }
        assertThat(rows("digital_payment_intents",t)).isZero();
        assertThat(prepare(t,t.adminToken(),o,UUID.randomUUID(),100001,1).code()).isEqualTo("AMOUNT_EXCEEDS_DUE");
        var euro=new HashMap<>(valid);euro.put("currency","EUR");assertThat(post(t.path("/payments/digital-intents"),t.adminToken(),euro).status()).isEqualTo(422);
    }
    @Test void laCleLieSourceAuteurMontantEtVersionSansFuiteOuNouvelleOperation() throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s),key=UUID.randomUUID();
        Response first=prepare(t,t.adminToken(),o,key,40000,1);assertThat(first.status()).isEqualTo(201);
        assertThat(prepare(t,t.adminToken(),o,key,40001,1).code()).isEqualTo("DIGITAL_INTENT_KEY_CONFLICT");
        assertThat(prepare(t,t.adminToken(),o,key,40000,2).code()).isEqualTo("DIGITAL_INTENT_KEY_CONFLICT");
        assertThat(prepare(t,newStaff(t,"ACCOUNTANT"),o,key,40000,1).code()).isEqualTo("DIGITAL_INTENT_KEY_CONFLICT");
        assertThat(prepare(t,t.adminToken(),UUID.randomUUID(),key,40000,1).code()).isEqualTo("DIGITAL_INTENT_KEY_CONFLICT");
        assertThat(rows("digital_payment_intents",t)).isEqualTo(1);assertThat(journalSize(o)).isEqualTo(1);
    }
    @Test void huitRejeuxConcurrentesCreentUneIntentionEtUnAudit() throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s),key=UUID.randomUUID();
        var replies=concurrently(8,()->prepare(t,t.adminToken(),o,key,40000,1));
        assertThat(replies).allSatisfy(r->assertThat(r.status()).as(r.raw()).isIn(200,201));
        assertThat(replies.stream().filter(r->r.status()==201)).hasSize(1);assertThat(rows("digital_payment_intents",t)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id=:tenant AND action='payments.digital.intent_prepared'")
                .param("tenant",t.id()).query(Long.class).single()).isEqualTo(1);assertThat(journalSize(o)).isEqualTo(1);
    }
    @Test void uneModificationUlterieureDuDuNeReecritPasLaPreparationEtImposeUneNouvelleValidation() throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s),key=UUID.randomUUID();
        Response first=prepare(t,t.adminToken(),o,key,90000,1);assertThat(first.status()).isEqualTo(201);
        assertThat(discount(t,t.adminToken(),o,20000).status()).isEqualTo(201);
        assertThat(prepare(t,t.adminToken(),o,key,90000,1).body()).isEqualTo(first.body());
        assertThat(prepare(t,t.adminToken(),o,UUID.randomUUID(),40000,1).code()).isEqualTo("DIGITAL_INTENT_OUTDATED");
        Response read=get(t.path("/payments/digital-intents/"+first.id("id")),t.adminToken());
        assertThat(read.text("dueAtPreparationMinor")).isEqualTo("100000");assertThat(read.body().path("requiresRevalidation").asBoolean()).isTrue();
        assertThat(read.body().path("providerDispatchAvailable").asBoolean()).isFalse();assertThat(journalSize(o)).isEqualTo(2);
    }
    @Test void uneRemiseConcurrenteEstSerialiseeSansCreerUnPaiement() throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s);CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->{barrier.await();return prepare(t,t.adminToken(),o,UUID.randomUUID(),90000,1);});
            var b=executor.submit(()->{barrier.await();return discount(t,t.adminToken(),o,20000);});barrier.countDown();
            assertThat(b.get(15,TimeUnit.SECONDS).status()).isEqualTo(201);Response prepared=a.get(15,TimeUnit.SECONDS);
            assertThat(prepared.status()).as(prepared.raw()).isIn(201,409);
            if(prepared.status()==201)assertThat(prepared.text("dueAtPreparationMinor")).isEqualTo("100000");
        }
        assertThat(get(t.path("/billing/obligations/"+o+"/financial-position"),t.adminToken()).text("dueMinor")).isEqualTo("80000");
        assertThat(rows("cash_allocations",t)).isZero();assertThat(journalSize(o)).isEqualTo(2);
    }
    @Test void unAuditDefaillantAnnuleLaPreparationEtSonRejeuRestePossible() throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s),key=UUID.randomUUID();String constraint="digital_audit_"+UUID.randomUUID().toString().replace("-","");
        jdbc.sql("ALTER TABLE audit_events ADD CONSTRAINT "+constraint+" CHECK (NOT (establishment_id='"+t.id()+"'::uuid AND action='payments.digital.intent_prepared')) NOT VALID").update();
        try{
            Response rejected=prepare(t,t.adminToken(),o,key,40000,1);assertThat(rejected.status()).as(rejected.raw()).isEqualTo(409);
            assertThat(rejected.raw()).doesNotContain("INSERT","SQLException");assertThat(rows("digital_payment_intents",t)).isZero();assertThat(journalSize(o)).isEqualTo(1);
        }finally{jdbc.sql("ALTER TABLE audit_events DROP CONSTRAINT "+constraint).update();}
        assertThat(prepare(t,t.adminToken(),o,key,40000,1).status()).isEqualTo(201);
    }
    @Test void aucunCallbackOuSuccesDeclareNAutoriseUneComptabilisation() throws Exception{
        School s=school();Tenant t=s.tenant();UUID o=issuedObligation(s);Response i=prepare(t,t.adminToken(),o,UUID.randomUUID(),40000,1);assertThat(i.status()).isEqualTo(201);
        for(String suffix:List.of("/payments/kadryza/webhooks","/payments/digital-intents/"+i.id("id")+"/confirmation")){
            assertThat(post(t.path(suffix),t.adminToken(),Map.of("status","SUCCESS","environment","test","amount",40000)).status()).isEqualTo(404);
        }
        assertThat(journalSize(o)).isEqualTo(1);assertThat(rows("cash_receptions",t)).isZero();
    }
}
