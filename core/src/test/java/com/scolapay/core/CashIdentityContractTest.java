package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties={"scolapay.payments.cash-identities-enabled=true",
        "scolapay.payments.identity-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class CashIdentityContractTest extends CashSettlementScenario {
    static final Map<String,Object> REMITTER=Map.of("fullName","Remettant confidentiel",
            "identificationDetails","Référence minimale examinée au guichet","identityRecordedConfirmed",true);

    @Test void uneNouvelleReceptionSansRemettantEstRefuseeSansCreerDeFait() {
        Tenant t=newTenant();
        Response refused=post(t.path("/payments/cash-receptions"),t.adminToken(),Map.of("idempotencyKey",UUID.randomUUID(),
                "amountReceivedMinor","20000","changeGivenMinor","0","cashReceivedConfirmed",true));
        assertThat(refused.status()).as(refused.raw()).isEqualTo(400);
        assertThat(count("cash_receptions",t)).isZero();
    }

    @Test void uneReceptionIdentifieeConserveLaPreuveSansExposerLeRemettantDansLaVueGenerale() {
        Tenant t=newTenant();
        Response created=post(t.path("/payments/cash-receptions"),t.adminToken(),Map.of("idempotencyKey",UUID.randomUUID(),
                "amountReceivedMinor","20000","changeGivenMinor","0","cashReceivedConfirmed",true,"remitter",REMITTER));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        assertThat(created.raw()).doesNotContain("Remettant confidentiel","Référence minimale");
        Response identity=get(t.path("/payments/cash-receptions/"+created.id("id")+"/remitter"),t.adminToken());
        assertThat(identity.status()).as(identity.raw()).isEqualTo(200);
        assertThat(identity.text("fullName")).isEqualTo("Remettant confidentiel");
        assertThat(identity.body().path("physicalHandoverAvailable").asBoolean()).isFalse();
    }
}
