package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

class CashReceptionConfigurationTest {
    @Test
    void uneActivationInvalideEmpecheLeDemarrage() {
        var arguments = new ArrayList<>(TestDatabases.applicationArguments(TestDatabases.createEmpty()));
        arguments.add("--server.port=0");
        arguments.add("--scolapay.payments.cash-receptions-enabled=indefini");
        assertThatThrownBy(() -> new SpringApplicationBuilder(ScolaPayCoreApplication.class).run(arguments.toArray(String[]::new)))
                .hasStackTraceContaining("cash-receptions-enabled");
    }
}
