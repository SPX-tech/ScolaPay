package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties="scolapay.payments.cash-excess-reservations-enabled=true")
class CashExcessReservationTest extends CashSettlementScenario {
    @Test void uneReceptionPartiellementAffecteeConserveUnExcedentReservableSansSortieDEspeces() throws Exception {
        School school=school();Tenant t=school.tenant();UUID obligation=issuedObligation(school);
        UUID source=receive(t,t.adminToken(),120000);
        assertThat(allocate(t,t.adminToken(),source,obligation,UUID.randomUUID(),100000,1).status()).isEqualTo(201);
        Response requested=request(t,source,UUID.randomUUID(),20000,"Excédent jamais affecté");
        assertThat(requested.status()).as(requested.raw()).isEqualTo(201);
        assertThat(requested.text("state")).isEqualTo("REQUESTED");
        assertThat(requested.text("authorizationScope")).isEqualTo("RESERVATION_ONLY_V1");
        String director=newStaff(t,"DIRECTOR");
        Response approved=post(t.path("/payments/excess-requests/"+requested.id("id")+"/approval"),director,Map.of("idempotencyKey",UUID.randomUUID()));
        assertThat(approved.status()).as(approved.raw()).isEqualTo(201);
        assertThat(approved.text("restitutedMinor")).isEqualTo("0");
        Response reread=get(t.path("/payments/excess-requests/"+requested.id("id")),t.adminToken());
        assertThat(reread.text("state")).isEqualTo("APPROVED_RESERVED");
        assertThat(reread.body().path("physicalHandoverAvailable").asBoolean()).isFalse();
        Response funds=get(t.path("/payments/cash-receptions/"+source),t.adminToken());
        assertThat(funds.text("unallocatedMinor")).isEqualTo("20000");
        assertThat(funds.text("reservedMinor")).isEqualTo("20000");
        assertThat(funds.text("availableForAllocationMinor")).isEqualTo("0");
        assertThat(position(t,obligation).path("settledMinor").asString()).isEqualTo("100000");
    }
    @Test void unRejeuNeReservePasDeuxFoisEtNeChangePasLaDemande() {
        Tenant t=newTenant();UUID source=receive(t,t.adminToken(),20000),key=UUID.randomUUID();
        Response first=request(t,source,key,15000,"Excédent à examiner");
        assertThat(first.status()).as(first.raw()).isEqualTo(201);
        Response replay=request(t,source,key,15000,"Excédent à examiner");
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(request(t,source,key,15001,"Excédent à examiner").code()).isEqualTo("CASH_EXCESS_KEY_CONFLICT");
    }
    Response request(Tenant t,UUID source,UUID key,long amount,String reason) {
        return post(t.path("/payments/cash-receptions/"+source+"/excess-requests"),t.adminToken(),
                Map.of("idempotencyKey",key,"amountMinor",Long.toString(amount),"reason",reason));
    }
}
