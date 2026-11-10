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
        return startDatabase(TestDatabases.createEmpty(), extras);
    }

    ConfigurableApplicationContext startDatabase(String database, String... extras) {
        List<String> args = new ArrayList<>(TestDatabases.applicationArguments(database));
        args.add("--server.port=0");
        List<String> settings = new ArrayList<>(List.of(
                "--scolapay.auth-abuse.login.origin.capacity=180", "--scolapay.auth-abuse.login.global.capacity=1200",
                "--scolapay.auth-abuse.login.pair.capacity=4", "--scolapay.auth-abuse.login.target.capacity=20",
                "--scolapay.auth-abuse.login.concurrency=2", "--scolapay.auth-abuse.counter-concurrency=2",
                "--scolapay.auth-abuse.invitations.origin.capacity=60", "--scolapay.auth-abuse.invitations.global.capacity=300",
                "--scolapay.auth-abuse.invitations.pair.capacity=10", "--scolapay.auth-abuse.invitations.target.capacity=20",
                "--scolapay.auth-abuse.invitations.concurrency=1"));
        settings.addAll(List.of(extras));
        for (String argument : settings) {
            String prefix = argument.substring(0, argument.indexOf('=')+1);
            args.removeIf(previous -> previous.startsWith(prefix));
            args.add(argument);
        }
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

    static String account(ConfigurableApplicationContext context, String password) {
        var jdbc = context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
        var id = java.util.UUID.randomUUID();
        String contact = "auth-"+id+"@scolapay.test";
        String hash = context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class).encode(password);
        jdbc.sql("INSERT INTO accounts(id,full_name,password_hash,created_at,updated_at) VALUES(:id,'Compte de test',:hash,now(),now())")
                .param("id", id).param("hash", hash).update();
        jdbc.sql("INSERT INTO account_identifiers(kind,value,account_id,created_at) VALUES('EMAIL',:value,:id,now())")
                .param("id", id).param("value", contact).update();
        return contact;
    }

    @Test
    void ajouterUnSuffixeAuDelaDes72OctetsNeDoitPasAuthentifier() throws Exception {
        var context = start();
        String password = "a".repeat(72);
        String contact = account(context, password);
        assertThat(post(context, "/api/v2/auth/login", loginBody(contact, password), Map.of()).statusCode()).isEqualTo(200);
        assertThat(post(context, "/api/v2/auth/login", loginBody(contact, password+"suffixe"), Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void leQuotaParIdentifiantNormaliseNeBloquePasLesAutresUtilisateursDuNat() throws Exception {
        var context = start("--scolapay.auth-abuse.login.pair.capacity=2", "--scolapay.auth-abuse.login.pair.period=PT1H");
        String contact = account(context, IntegrationTest.PASSWORD);
        for (String variant : List.of(contact.toUpperCase(java.util.Locale.ROOT), "  "+contact+"  ")) {
            assertThat(post(context, "/api/v2/auth/login", loginBody(variant, "mauvais"), Map.of()).statusCode()).isEqualTo(401);
        }
        assertThat(post(context, "/api/v2/auth/login", loginBody(contact, IntegrationTest.PASSWORD), Map.of()).statusCode()).isEqualTo(429);
        String other = account(context, IntegrationTest.PASSWORD);
        assertThat(post(context, "/api/v2/auth/login", loginBody(other, IntegrationTest.PASSWORD), Map.of()).statusCode()).isEqualTo(200);
        var jdbc = context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
        assertThat(jdbc.sql("SELECT failed_login_attempts FROM accounts WHERE id=(SELECT account_id FROM account_identifiers WHERE value=:value)")
                .param("value", contact).query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void vingtEmployesDerriereUneMemeIpPeuventSeConnecter() throws Exception {
        var context = start();
        for (int index=0; index<20; index++) {
            String contact = account(context, IntegrationTest.PASSWORD);
            assertThat(post(context, "/api/v2/auth/login", loginBody(contact, IntegrationTest.PASSWORD), Map.of()).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void uneCibleAttaqueeDepuisPlusieursOriginesPartageLeMemeQuota() throws Exception {
        var context = start("--scolapay.auth-abuse.proxy.mode=trusted", "--scolapay.auth-abuse.proxy.trusted-cidrs=127.0.0.1/32",
                "--scolapay.auth-abuse.login.target.capacity=3", "--scolapay.auth-abuse.login.target.period=PT1H");
        for (int index=0; index<3; index++) {
            assertThat(post(context, "/api/v2/auth/login", loginBody("cible@scolapay.test", "mauvais"),
                    Map.of("X-Forwarded-For", "192.0.2."+(index+1))).statusCode()).isEqualTo(401);
        }
        assertThat(post(context, "/api/v2/auth/login", loginBody("cible@scolapay.test", "mauvais"),
                Map.of("X-Forwarded-For", "198.51.100.2")).statusCode()).isEqualTo(429);
    }

    @Test
    void deuxInstancesPartagentLeQuotaDeLaMemeOrigine() throws Exception {
        String database = TestDatabases.createEmpty();
        String[] policy = {"--scolapay.auth-abuse.login.origin.capacity=3", "--scolapay.auth-abuse.login.origin.period=PT1H"};
        var first = startDatabase(database, policy);
        var second = startDatabase(database, policy);
        for (int index=0; index<3; index++) {
            assertThat(post(index%2==0 ? first : second, "/api/v2/auth/login", loginBody("cible"+index+"@scolapay.test", "mauvais"), Map.of()).statusCode()).isEqualTo(401);
        }
        assertThat(post(second, "/api/v2/auth/login", loginBody("nouveau@scolapay.test", "mauvais"), Map.of()).statusCode()).isEqualTo(429);
    }

    @Test
    void lesTentativesSimultaneesNeDepassentPasLaRafalePartagee() throws Exception {
        String database = TestDatabases.createEmpty();
        String[] policy = {"--scolapay.auth-abuse.login.origin.capacity=3", "--scolapay.auth-abuse.login.origin.period=PT1H",
                "--scolapay.auth-abuse.login.concurrency=16", "--scolapay.auth-abuse.counter-concurrency=16"};
        var first = startDatabase(database, policy);
        var second = startDatabase(database, policy);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<Integer>> replies = new ArrayList<>();
            for (int index=0; index<8; index++) {
                int caller = index;
                replies.add(executor.submit(() -> { start.await(); return post(caller%2==0 ? first : second,
                        "/api/v2/auth/login", loginBody("course"+caller+"@scolapay.test", "mauvais"), Map.of()).statusCode(); }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var reply : replies) statuses.add(reply.get());
            assertThat(statuses.stream().filter(code -> code==401).count()).isEqualTo(3);
            assertThat(statuses).allMatch(code -> code==401 || code==429 || code==503);
        }
    }

    @Test
    void leRefusNeRevelePasSiLeCompteExiste() throws Exception {
        var context = start("--scolapay.auth-abuse.login.pair.capacity=1", "--scolapay.auth-abuse.login.pair.period=PT1H");
        String known = account(context, IntegrationTest.PASSWORD);
        String response = null;
        for (String contact : List.of(known, "inconnu@scolapay.test")) {
            assertThat(post(context, "/api/v2/auth/login", loginBody(contact, "mauvais"), Map.of()).statusCode()).isEqualTo(401);
            var refused = post(context, "/api/v2/auth/login", loginBody(contact, "mauvais"), Map.of());
            assertThat(refused.statusCode()).isEqualTo(429);
            assertThat(refused.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            if (response == null) response = refused.body(); else assertThat(refused.body()).isEqualTo(response);
        }
    }

    @Test
    void leRemplissageSeRetablitEtLesCompartimentsInactifsSontPurges() throws Exception {
        var context = start("--scolapay.auth-abuse.login.origin.capacity=1", "--scolapay.auth-abuse.login.origin.period=PT1H");
        post(context, "/api/v2/auth/login", loginBody("premier@scolapay.test", "mauvais"), Map.of());
        var limited = post(context, "/api/v2/auth/login", loginBody("second@scolapay.test", "mauvais"), Map.of());
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(Integer.parseInt(limited.headers().firstValue("Retry-After").orElseThrow())).isBetween(1,3600);
        var jdbc = context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
        jdbc.sql("UPDATE auth_abuse_buckets SET tat=clock_timestamp()-interval '1 hour'").update();
        context.getBean(com.scolapay.core.identity.internal.PublicAuthRateStore.class).prune();
        assertThat(jdbc.sql("SELECT count(*) FROM auth_abuse_buckets").query(Long.class).single()).isZero();
        assertThat(post(context, "/api/v2/auth/login", loginBody("second@scolapay.test", "mauvais"), Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void unePanneDuStockageRefuseLAuthentificationSansFuite() throws Exception {
        var context = start();
        var jdbc = context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
        jdbc.sql("ALTER TABLE auth_abuse_buckets RENAME TO unavailable_auth_buckets").update();
        try {
            var refused = post(context, "/api/v2/auth/login", loginBody(IntegrationTest.OPERATOR_CONTACT, IntegrationTest.OPERATOR_PASSWORD), Map.of());
            assertThat(refused.statusCode()).isEqualTo(503);
            assertThat(refused.body()).contains("AUTH_PROTECTION_UNAVAILABLE").doesNotContain("buckets", "SQL", IntegrationTest.OPERATOR_CONTACT);
        } finally { jdbc.sql("ALTER TABLE unavailable_auth_buckets RENAME TO auth_abuse_buckets").update(); }
    }

    @Test
    void lesCorpsAnnoncesEtChunkedSontBornesAvantLeParsing() throws Exception {
        var context = start();
        String huge = "{\"identifier\":\""+"x".repeat(10000)+"\",\"password\":\"secret\"}";
        assertThat(post(context, "/api/v2/auth/login", huge, Map.of()).statusCode()).isEqualTo(413);
        int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v2/auth/login"))
                .header("Content-Type", "application/json").timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(huge.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).build();
        var chunked = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(chunked.statusCode()).isEqualTo(413);
        assertThat(chunked.body()).doesNotContain("secret", "xxxxxxxx");
    }

    @Test
    void leCheminEncodeNeContournePasLaProtection() throws Exception {
        var context = start("--scolapay.auth-abuse.login.origin.capacity=1", "--scolapay.auth-abuse.login.origin.period=PT1H");
        assertThat(post(context, "/api/v2/auth/%6cogin", loginBody("a@scolapay.test", "mauvais"), Map.of()).statusCode()).isEqualTo(401);
        assertThat(post(context, "/api/v2/auth/login", loginBody("b@scolapay.test", "mauvais"), Map.of()).statusCode()).isEqualTo(429);
    }

    @Test
    void unForwardingAutomatiqueIncompatibleEmpecheLeDemarrage() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> start("--server.forward-headers-strategy=framework"))
                .hasStackTraceContaining("AUTH_FORWARDING_CONFIGURATION_CONFLICT");
    }
}
