package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
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
}
