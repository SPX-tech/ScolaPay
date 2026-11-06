package com.scolapay.core.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRecoveryTest {

    private static final Duration BASE = Duration.ofMinutes(1);
    private static final Duration MAX = Duration.ofHours(1);

    @Test
    void leDelaiDoubleAChaqueEssai() {
        assertThat(OutboxRecovery.retryDelay(1, BASE, MAX)).isEqualTo(Duration.ofMinutes(1));
        assertThat(OutboxRecovery.retryDelay(2, BASE, MAX)).isEqualTo(Duration.ofMinutes(2));
        assertThat(OutboxRecovery.retryDelay(3, BASE, MAX)).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void leDelaiEstPlafonne() {
        assertThat(OutboxRecovery.retryDelay(8, BASE, MAX)).isEqualTo(MAX);
        assertThat(OutboxRecovery.retryDelay(Integer.MAX_VALUE, BASE, MAX)).isEqualTo(MAX);
    }

    @Test
    void unNombreDEssaisNulOuAbsentReprendLeDelaiDeBase() {
        assertThat(OutboxRecovery.retryDelay(0, BASE, MAX)).isEqualTo(BASE);
    }
}
