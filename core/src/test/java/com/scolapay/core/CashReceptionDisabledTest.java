package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CashReceptionDisabledTest extends IntegrationTest {

    @Test
    void sansActivationExpliciteAucuneReceptionNePeutEtreCreee() {
        Tenant tenant = newTenant();
        Response refused = post(tenant.path("/payments/cash-receptions"), tenant.adminToken(), Map.of(
                "idempotencyKey", UUID.randomUUID(), "amountReceivedMinor", "25000", "changeGivenMinor", "0",
                "cashReceivedConfirmed", true));
        assertThat(refused.status()).as(refused.raw()).isEqualTo(404);
        assertThat(refused.code()).isEqualTo("CASH_RECEPTIONS_DISABLED");
        assertThat(jdbc.sql("SELECT count(*) FROM cash_receptions WHERE establishment_id=:id")
                .param("id", tenant.id()).query(Long.class).single()).isZero();
    }
}
