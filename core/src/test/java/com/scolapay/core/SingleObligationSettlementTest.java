package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"scolapay.payments.cash-receptions-enabled=true", "scolapay.payments.cash-allocations-enabled=true"})
class SingleObligationSettlementTest extends BillingScenarioTest {
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
}
