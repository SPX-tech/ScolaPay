package com.scolapay.core;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CashAllocationDisabledTest extends IntegrationTest {
    @Test void uneRouteDeReglementFermeeNeParsePasDeCommandeEtNecritRien() {
        Tenant t=newTenant();
        Response response=post(t.path("/payments/cash-receptions/"+UUID.randomUUID()+"/allocations"),t.adminToken(),Map.of());
        assertThat(response.status()).as(response.raw()).isEqualTo(404);
        assertThat(jdbc.sql("SELECT count(*) FROM cash_allocations WHERE establishment_id=:id").param("id",t.id()).query(Long.class).single()).isZero();
    }
}
