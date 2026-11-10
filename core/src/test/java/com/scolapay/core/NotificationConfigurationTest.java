package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class NotificationConfigurationTest {

    @Test
    void sansConfigurationUneInvitationEstRefuseeEtLePortailResteFerme() {
        String url = TestDatabases.createEmpty();
        try (ConfigurableApplicationContext context = start(url)) {
            TestApi api = new TestApi(context.getEnvironment().getRequiredProperty("local.server.port", Integer.class));
            TestApi.Reply refused = api.post("/api/v2/platform/establishments",
                    api.login(IntegrationTest.OPERATOR_CONTACT, IntegrationTest.OPERATOR_PASSWORD), Map.of(
                            "code", IntegrationTest.uniqueCode(), "name", "École fermée", "kind", "SCHOOL",
                            "timezone", "Africa/Ndjamena", "currency", "XAF", "countryCode", "TD",
                            "firstAdminContact", IntegrationTest.uniqueEmail()));
            assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
            assertThat(refused.code()).isEqualTo("INVITATION_CHANNEL_UNAVAILABLE");
            assertThat(api.get("/api/v2/me/students", api.login(IntegrationTest.OPERATOR_CONTACT, IntegrationTest.OPERATOR_PASSWORD)).status())
                    .isEqualTo(404);
        }
    }

    @Test
    void unAdaptateurEmailNeSuffitPasAOuvrirFamiliesMemeApresConfirmationJuridique() {
        String url = TestDatabases.createEmpty();
        try (ConfigurableApplicationContext ignored = start(url)) {
            TestDatabases.client(url).sql("UPDATE legal_majority_rules SET legally_confirmed=true").update();
        }
        assertThatThrownBy(() -> start(url, "--scolapay.portal.enabled=true",
                "--scolapay.notifications.email.provider=resend",
                "--scolapay.notifications.email.api-key=re_isolated_test_only",
                "--scolapay.notifications.email.from=invitations@scolapay.test",
                "--scolapay.notifications.encryption-key=cXVldWUtdGVzdC1rZXktMzItYnl0ZXMtb25seS0wMDE="))
                .hasStackTraceContaining("canal-de-remise-des-invitations");
    }

    ConfigurableApplicationContext start(String url, String... extras) {
        List<String> arguments = new ArrayList<>(TestDatabases.applicationArguments(url));
        arguments.add("--server.port=0");
        arguments.addAll(List.of(extras));
        return new SpringApplicationBuilder(ScolaPayCoreApplication.class).run(arguments.toArray(String[]::new));
    }
}
