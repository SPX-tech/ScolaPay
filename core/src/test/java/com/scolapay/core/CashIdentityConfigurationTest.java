package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;

class CashIdentityConfigurationTest {
    @ParameterizedTest @ValueSource(strings={"","pas-base64","YWJj"})
    void aucuneActivationSansCleValide(String key){
        var arguments=new ArrayList<>(TestDatabases.applicationArguments(TestDatabases.createEmpty()));
        arguments.removeIf(value->value.startsWith("--scolapay.payments.identity-encryption-key="));
        arguments.add("--server.port=0");arguments.add("--scolapay.payments.cash-identities-enabled=true");
        arguments.add("--scolapay.payments.identity-encryption-key="+key);
        assertThatThrownBy(()->new SpringApplicationBuilder(ScolaPayCoreApplication.class).run(arguments.toArray(String[]::new)))
                .hasStackTraceContaining("CASH_IDENTITY_KEY_MUST_BE_BASE64_32_BYTES");
    }
}
