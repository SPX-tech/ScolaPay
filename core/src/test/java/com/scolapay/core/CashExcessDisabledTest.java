package com.scolapay.core;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CashExcessDisabledTest extends IntegrationTest {
    @Test void sansActivationLesDemandesEtRemisesRestentFermeesAvantValidationDuCorps() {
        Tenant t=newTenant();
        assertThat(post(t.path("/payments/cash-receptions/"+UUID.randomUUID()+"/excess-requests"),t.adminToken(),Map.of()).status()).isEqualTo(404);
        assertThat(post(t.path("/payments/excess-requests/"+UUID.randomUUID()+"/approval"),t.adminToken(),Map.of()).status()).isEqualTo(404);
        assertThat(post(t.path("/payments/excess-requests/"+UUID.randomUUID()+"/handover-confirmations"),t.adminToken(),Map.of()).status()).isEqualTo(404);
        assertThat(jdbc.sql("SELECT count(*) FROM cash_excess_requests WHERE establishment_id=:id").param("id",t.id()).query(Long.class).single()).isZero();
    }
}
