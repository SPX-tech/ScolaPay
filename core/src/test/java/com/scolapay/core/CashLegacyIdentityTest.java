package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CashLegacyIdentityTest extends CashIdentityProbeScenario {
    @Test void uneAncienneReceptionConserveSesFaitsEtExigeUneVerificationIndependante() throws Exception{
        Legacy legacy=legacy();Instance current=start(null,"",true);var school=legacy.school();
        var before=jdbc.sql("SELECT * FROM cash_receptions WHERE id=:id").param("id",legacy.source()).query().singleRow();
        var pending=declare(current.api(),legacy,UUID.randomUUID());assertThat(pending.status()).as(pending.raw()).isEqualTo(201);
        String path=school.path("/payments/cash-receptions/"+legacy.source()+"/remitter-reviews");
        assertThat(current.api().post(path,school.adminToken(),review(UUID.randomUUID())).status()).isEqualTo(403);
        assertThat(current.api().get(school.path("/payments/cash-receptions/"+legacy.source()+"/remitter"),school.adminToken()).text("state"))
                .isEqualTo("LEGACY_PENDING_REVIEW");
        var checked=current.api().post(path,reviewer(current.api(),school),review(UUID.randomUUID()));
        assertThat(checked.status()).as(checked.raw()).isEqualTo(201);
        assertThat(current.api().get(school.path("/payments/cash-receptions/"+legacy.source()+"/remitter"),school.adminToken()).text("state"))
                .isEqualTo("LEGACY_VERIFIED");
        assertThat(jdbc.sql("SELECT * FROM cash_receptions WHERE id=:id").param("id",legacy.source()).query().singleRow()).isEqualTo(before);
        var replay=current.api().post(school.path("/payments/cash-receptions"),school.adminToken(),Map.of("idempotencyKey",legacy.originalKey(),
                "amountReceivedMinor","20000","changeGivenMinor","0","cashReceivedConfirmed",true));
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.text("unallocatedMinor")).isEqualTo("20000");
    }
}
