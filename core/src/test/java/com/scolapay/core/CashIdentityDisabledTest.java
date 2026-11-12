package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CashIdentityDisabledTest extends IntegrationTest {
    @Test void sansActivationLesNouvellesEcrituresEtTouteConfirmationPhysiqueRestentFermees(){
        Tenant t=newTenant();UUID source=UUID.randomUUID(),request=UUID.randomUUID();
        for(String suffix:new String[]{"/cash-receptions/"+source+"/remitter-declarations","/cash-receptions/"+source+"/remitter-reviews",
                "/excess-requests/"+request+"/beneficiary-verifications","/excess-requests/"+request+"/restitution-authorizations",
                "/excess-requests/"+request+"/handover-confirmations"}){
            assertThat(post(t.path("/payments"+suffix),t.adminToken(),Map.of()).status()).isEqualTo(404);
        }
    }
}
