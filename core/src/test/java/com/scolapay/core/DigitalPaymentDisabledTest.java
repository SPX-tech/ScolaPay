package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DigitalPaymentDisabledTest extends IntegrationTest {
    @Test void sansConfigurationAucunePreparationOuIntegrationExterneNEstActive(){
        Tenant t=newTenant();assertThat(post(t.path("/payments/digital-intents"),t.adminToken(),Map.of()).status()).isEqualTo(404);
        assertThat(post(t.path("/payments/kadryza/webhooks"),t.adminToken(),Map.of("event","payment_session.succeeded")).status()).isEqualTo(404);
    }
}
