package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.scolapay.core.schooling.EnrollmentEnded;
import com.scolapay.core.schooling.StudentEnrolled;
import com.scolapay.testsupport.TestInvitationDelivery;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Teste l'application réelle : vrai serveur HTTP, vraie chaîne de sécurité, vrai PostgreSQL.
 * Rien n'est simulé. Chaque test crée ses propres établissements, ce qui reproduit en plus
 * les conditions d'une base partagée entre plusieurs établissements.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({IntegrationTest.CapturedEvents.class, TestInvitationDelivery.class})
abstract class IntegrationTest {

    static final String JWT_SECRET = "secret-de-test-uniquement-0123456789-abcdef";
    static final String PASSWORD = "une-phrase-de-passe-solide";
    static final String OPERATOR_CONTACT = "operateur@scolapay.test";
    static final String OPERATOR_PASSWORD = "phrase-de-passe-operateur";

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabases.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", TestDatabases.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestDatabases.POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/test-migration");
        registry.add("scolapay.portal.enabled", () -> "true");
        registry.add("scolapay.security.jwt-secret", () -> JWT_SECRET);
        registry.add("scolapay.bootstrap.operator-contact", () -> OPERATOR_CONTACT);
        registry.add("scolapay.bootstrap.operator-password", () -> OPERATOR_PASSWORD);
    }

    @Autowired
    Environment environment;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CapturedEvents events;

    record Response(int status, JsonNode body, String raw) {

        String text(String field) {
            return body.path(field).asString();
        }

        UUID id(String field) {
            return UUID.fromString(text(field));
        }

        String code() {
            return text("code");
        }
    }

    record Tenant(UUID id, String adminToken, String adminContact) {

        String path(String suffix) {
            return "/api/v2/establishments/" + id + suffix;
        }
    }

    Response post(String path, String token, Object body) {
        return send("POST", path, token, JSON.writeValueAsString(body));
    }

    Response patch(String path, String token, Object body) {
        return send("PATCH", path, token, JSON.writeValueAsString(body));
    }

    Response put(String path, String token, Object body) {
        return send("PUT", path, token, JSON.writeValueAsString(body));
    }

    Response get(String path, String token) {
        return send("GET", path, token, null);
    }

    Response delete(String path, String token) {
        return send("DELETE", path, token, null);
    }

    Response send(String method, String path, String token, String body) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return exchange(method, path, token, publisher);
    }

    /** Sans longueur annoncée : envoyé en chunked, comme le ferait un client qui contourne le plafond. */
    Response sendChunked(String path, byte[] body) {
        return exchange("POST", path, null, HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));
    }

    private Response exchange(String method, String path, String token, HttpRequest.BodyPublisher publisher) {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .method(method, publisher);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            String raw = response.body();
            JsonNode parsed = parseOrNull(raw == null || raw.isBlank() ? "null" : raw);
            return new Response(response.statusCode(), parsed, raw);
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + path, e);
        }
    }

    private static JsonNode parseOrNull(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (RuntimeException notJson) {
            return JSON.readTree("null");
        }
    }

    String login(String identifier, String password) {
        Response response = post("/api/v2/auth/login", null, Map.of("identifier", identifier, "password", password));
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        return response.text("accessToken");
    }

    String operatorToken() {
        return login(OPERATOR_CONTACT, OPERATOR_PASSWORD);
    }

    static String uniqueEmail() {
        return "compte-" + UUID.randomUUID() + "@scolapay.test";
    }

    static String uniquePhone() {
        return "+2356" + String.format("%07d", (int) (Math.random() * 10_000_000)) + (int) (Math.random() * 10);
    }

    static String uniqueCode() {
        byte[] bytes = new byte[8];
        new java.security.SecureRandom().nextBytes(bytes);
        return "E-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }

    Response createEstablishment(String firstAdminContact) {
        return post("/api/v2/platform/establishments", operatorToken(), Map.of(
                "code", uniqueCode(),
                "name", "Lycée de test",
                "kind", "SCHOOL",
                "timezone", "Africa/Ndjamena",
                "currency", "XAF",
                "countryCode", "TD",
                "firstAdminContact", firstAdminContact));
    }

    Tenant newTenant() {
        String contact = uniqueEmail();
        Response created = createEstablishment(contact);
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        UUID id = UUID.fromString(created.body().path("establishment").path("id").asString());
        acceptAsNewAccount(created.body().path("firstAdminInvitation").path("token").asString(), "Admin Test");
        return new Tenant(id, login(contact, PASSWORD), contact);
    }

    Response acceptAsNewAccount(String invitationToken, String fullName) {
        Response response = post(
                "/api/v2/invitations/accept",
                null,
                Map.of("token", invitationToken, "fullName", fullName, "password", PASSWORD));
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        return response;
    }

    /** Crée un membre du personnel et renvoie son jeton. */
    String newStaff(Tenant tenant, String role) {
        String contact = uniqueEmail();
        Response invited = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", contact, "role", role));
        assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
        acceptAsNewAccount(invited.text("token"), "Personnel Test");
        return login(contact, PASSWORD);
    }

    UUID openYear(Tenant tenant) {
        Response created = post(tenant.path("/academic-years"), tenant.adminToken(), Map.of(
                "label", "Y-" + UUID.randomUUID().toString().substring(0, 8),
                "startsOn", "2026-09-01",
                "endsOn", "2027-07-15"));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        UUID id = created.id("id");
        Response opened = patch(tenant.path("/academic-years/" + id), tenant.adminToken(), Map.of("status", "OPEN"));
        assertThat(opened.status()).as(opened.raw()).isEqualTo(200);
        return id;
    }

    static String uniqueUnitCode() {
        return "U-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    UUID newLevel(Tenant tenant) {
        return newLevel(tenant, "Sixième");
    }

    UUID newLevel(Tenant tenant, String name) {
        Response created = post(tenant.path("/academic-units"), tenant.adminToken(), Map.of(
                "kind", "LEVEL", "code", uniqueUnitCode(), "name", name));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    UUID newStudent(Tenant tenant) {
        return newStudent(tenant, LocalDate.of(2012, 3, 14));
    }

    UUID newStudent(Tenant tenant, LocalDate birthDate) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("studentNumber", "ETU-" + UUID.randomUUID());
        body.put("fullName", "Élève Test");
        body.put("birthDate", birthDate == null ? null : birthDate.toString());
        Response created = post(tenant.path("/students"), tenant.adminToken(), body);
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return created.id("id");
    }

    Response inviteThirdParty(Tenant tenant, UUID studentId, String contact, boolean academic, boolean financial) {
        return post(tenant.path("/students/" + studentId + "/third-party-invitations"), tenant.adminToken(), Map.of(
                "contact", contact,
                "relationship", "MOTHER",
                "academicAccess", academic,
                "financialContributor", financial));
    }

    /** Invite un tuteur avec accès académique sur le dossier d'un mineur et renvoie le jeton d'invitation. */
    String inviteGuardian(Tenant tenant, UUID studentId, String contact, boolean financialContributor) {
        Response invited = inviteThirdParty(tenant, studentId, contact, true, financialContributor);
        assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
        return invited.text("token");
    }

    /** Crée un tuteur rattaché au dossier et renvoie son jeton. */
    String newGuardian(Tenant tenant, UUID studentId) {
        String contact = uniqueEmail();
        acceptAsNewAccount(inviteGuardian(tenant, studentId, contact, false), "Tuteur Test");
        return login(contact, PASSWORD);
    }

    static List<Response> concurrently(int count, Callable<Response> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Response>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get());
            }
            return responses;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CapturedEvents {

        final List<Object> published = new CopyOnWriteArrayList<>();

        @EventListener
        void onEnrolled(StudentEnrolled event) {
            published.add(event);
        }

        @EventListener
        void onEnded(EnrollmentEnded event) {
            published.add(event);
        }
    }
}
