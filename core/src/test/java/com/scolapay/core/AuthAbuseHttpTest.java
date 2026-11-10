package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/** Serveur réel, chaîne Spring Security réelle, PostgreSQL jetable. */
class AuthAbuseHttpTest {
    static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    static final JsonMapper JSON = JsonMapper.builder().build();
    final List<ConfigurableApplicationContext> contexts = new ArrayList<>();

    ConfigurableApplicationContext start(String... extras) {
        List<String> args = new ArrayList<>(TestDatabases.applicationArguments(TestDatabases.createEmpty()));
        args.add("--server.port=0");
        args.addAll(List.of(extras));
        var context = new SpringApplicationBuilder(ScolaPayCoreApplication.class).run(args.toArray(String[]::new));
        contexts.add(context);
        return context;
    }

    @AfterEach void stop() { contexts.forEach(ConfigurableApplicationContext::close); }

    static HttpResponse<String> post(ConfigurableApplicationContext context, String path, String body, Map<String,String> headers) throws Exception {
        int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String loginBody(String identifier, String password) {
        return JSON.writeValueAsString(Map.of("identifier", identifier, "password", password));
    }

    @Test
    void uneOrigineQuiChangeDIdentifiantEtFalsifieSesEntetesResteLimitee() throws Exception {
        var context = start("--scolapay.auth-abuse.login.origin.capacity=3",
                "--scolapay.auth-abuse.login.origin.period=PT1H");
        for (int i=0; i<3; i++) {
            assertThat(post(context, "/api/v2/auth/login", loginBody("inconnu"+i+"@scolapay.test", "mot-de-passe-faux"),
                    Map.of("X-Forwarded-For", "192.0.2."+i, "Forwarded", "for=198.51.100."+i)).statusCode()).isEqualTo(401);
        }
        var refused = post(context, "/api/v2/auth/login", loginBody("encore@scolapay.test", "mot-de-passe-faux"),
                Map.of("X-Forwarded-For", "203.0.113.15"));
        assertThat(refused.statusCode()).isEqualTo(429);
        assertThat(refused.headers().firstValue("Retry-After")).isPresent();
        assertThat(JSON.readTree(refused.body()).path("code").asString()).isEqualTo("AUTH_RATE_LIMITED");
        assertThat(refused.body()).doesNotContain("encore@", "203.0.113", "mot-de-passe-faux");
    }

    @Test
    void unMotDePasseDepassantLaLimiteBcryptResteUnRefusGenerique() throws Exception {
        var context = start();
        for (String contact : List.of(IntegrationTest.OPERATOR_CONTACT, "inconnu@scolapay.test")) {
            var refused = post(context, "/api/v2/auth/login", loginBody(contact, "é".repeat(50)), Map.of());
            assertThat(refused.statusCode()).isEqualTo(401);
            assertThat(JSON.readTree(refused.body()).path("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        }
    }
}
