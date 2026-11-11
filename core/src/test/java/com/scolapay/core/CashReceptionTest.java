package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "scolapay.payments.cash-receptions-enabled=true")
class CashReceptionTest extends IntegrationTest {

    @Test
    void uneReceptionConserveLaPreuveDesEspecesSansDeclarerDeReglement() {
        Tenant tenant = newTenant();
        String cashier = newStaff(tenant, "CASHIER");
        Response created = record(tenant, cashier, UUID.randomUUID(), "12000", "2000");
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        assertThat(created.text("currency")).isEqualTo("XAF");
        assertThat(created.text("amountReceivedMinor")).isEqualTo("12000");
        assertThat(created.text("changeGivenMinor")).isEqualTo("2000");
        assertThat(created.text("amountRetainedMinor")).isEqualTo("10000");
        assertThat(created.text("unallocatedMinor")).isEqualTo("10000");
        assertThat(created.text("settledMinor")).isEqualTo("0");
        assertThat(created.text("allocationState")).isEqualTo("UNALLOCATED");
        assertThat(created.text("proofNature")).isEqualTo("INTERNAL_CASH_DECLARATION");
        assertThat(created.text("reference")).isNotBlank();
        assertThat(created.text("recordedByAccountId")).isEqualTo(get("/api/v2/me", cashier).text("id"));
        Response read = get(tenant.path("/payments/cash-receptions/" + created.id("id")), cashier);
        assertThat(read.status()).as(read.raw()).isEqualTo(200);
        assertThat(read.body()).isEqualTo(created.body());
    }

    @Test
    void unRejeuRetourneLaMemePreuveEtUnAutreMontantEstRefuse() {
        Tenant tenant = newTenant();
        UUID key = UUID.randomUUID();
        Response created = record(tenant, tenant.adminToken(), key, "12000", "2000");
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        Response replay = record(tenant, tenant.adminToken(), key, "12000", "2000");
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(created.body());
        Response mismatch = record(tenant, tenant.adminToken(), key, "12001", "2000");
        assertThat(mismatch.status()).as(mismatch.raw()).isEqualTo(409);
        assertThat(mismatch.code()).isEqualTo("CASH_RECEPTION_KEY_CONFLICT");
    }

    Response record(Tenant tenant, String token, UUID key, String received, String change) {
        return post(tenant.path("/payments/cash-receptions"), token, Map.of(
                "idempotencyKey", key, "amountReceivedMinor", received, "changeGivenMinor", change,
                "cashReceivedConfirmed", true));
    }
}
