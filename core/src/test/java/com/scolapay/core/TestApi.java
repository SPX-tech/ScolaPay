package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Client HTTP des tests qui démarrent l'application eux-mêmes, hors du contexte partagé d'{@link IntegrationTest}. */
record TestApi(int port) {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Reply(int status, JsonNode body, String raw) {

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

    /** Un établissement de test, avec son administrateur et une année ouverte. */
    record School(TestApi api, UUID id, String adminToken, UUID yearId, UUID levelId) {

        String path(String suffix) {
            return "/api/v2/establishments/" + id + suffix;
        }

        UUID newStudent(LocalDate birthDate) {
            Reply created = api.post(path("/students"), adminToken, Map.of(
                    "studentNumber", "ETU-" + UUID.randomUUID(),
                    "fullName", "Élève Test",
                    "birthDate", birthDate.toString()));
            assertThat(created.status()).as(created.raw()).isEqualTo(201);
            return created.id("id");
        }

        UUID newStudent() {
            return newStudent(LocalDate.of(2012, 3, 14));
        }

        Reply enroll(UUID studentId) {
            return api.post(path("/students/" + studentId + "/enrollments"), adminToken, Map.of(
                    "academicYearId", yearId.toString(),
                    "academicUnitId", levelId.toString()));
        }
    }

    boolean healthy() {
        try {
            return get("/actuator/health", null).status() == 200;
        } catch (IllegalStateException notListeningYet) {
            return false;
        }
    }

    Reply post(String path, String token, Object body) {
        return exchange("POST", path, token, JSON.writeValueAsString(body));
    }

    Reply patch(String path, String token, Object body) {
        return exchange("PATCH", path, token, JSON.writeValueAsString(body));
    }

    Reply get(String path, String token) {
        return exchange("GET", path, token, null);
    }

    String login(String identifier, String password) {
        Reply reply = post("/api/v2/auth/login", null, Map.of("identifier", identifier, "password", password));
        assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
        return reply.text("accessToken");
    }

    Reply accept(String invitationToken) {
        return post("/api/v2/invitations/accept", null, Map.of(
                "token", invitationToken, "fullName", "Personne Test", "password", IntegrationTest.PASSWORD));
    }

    School newSchool() {
        String contact = IntegrationTest.uniqueEmail();
        String operator = login(IntegrationTest.OPERATOR_CONTACT, IntegrationTest.OPERATOR_PASSWORD);
        Reply created = post("/api/v2/platform/establishments", operator, Map.of(
                "code", IntegrationTest.uniqueCode(),
                "name", "Lycée de test",
                "kind", "SCHOOL",
                "timezone", "Africa/Ndjamena",
                "currency", "XAF",
                "countryCode", "TD",
                "firstAdminContact", contact));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        UUID id = UUID.fromString(created.body().path("establishment").path("id").asString());
        Reply accepted = accept(created.body().path("firstAdminInvitation").path("token").asString());
        assertThat(accepted.status()).as(accepted.raw()).isEqualTo(200);
        String admin = login(contact, IntegrationTest.PASSWORD);

        String years = "/api/v2/establishments/" + id + "/academic-years";
        Reply year = post(years, admin, Map.of(
                "label", "Y-" + UUID.randomUUID().toString().substring(0, 8),
                "startsOn", "2026-09-01",
                "endsOn", "2027-07-15"));
        assertThat(year.status()).as(year.raw()).isEqualTo(201);
        Reply opened = patch(years + "/" + year.id("id"), admin, Map.of("status", "OPEN"));
        assertThat(opened.status()).as(opened.raw()).isEqualTo(200);
        Reply level = post("/api/v2/establishments/" + id + "/academic-units", admin, Map.of(
                "kind", "LEVEL", "code", "SIXIEME", "name", "Sixième"));
        assertThat(level.status()).as(level.raw()).isEqualTo(201);
        return new School(this, id, admin, year.id("id"), level.id("id"));
    }

    private Reply exchange(String method, String path, String token, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            String raw = response.body();
            return new Reply(response.statusCode(), parse(raw), raw);
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException(method + " " + path, e);
        }
    }

    private static JsonNode parse(String raw) {
        try {
            return JSON.readTree(raw == null || raw.isBlank() ? "null" : raw);
        } catch (RuntimeException notJson) {
            return JSON.readTree("null");
        }
    }
}
