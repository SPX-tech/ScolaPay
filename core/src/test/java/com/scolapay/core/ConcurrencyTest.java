package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Les garanties qui ne se voient qu'en concurrence, vérifiées sur un vrai PostgreSQL. */
class ConcurrencyTest extends IntegrationTest {

    private static long countStatus(List<Response> responses, int status) {
        return responses.stream().filter(response -> response.status() == status).count();
    }

    @Test
    void desInscriptionsSimultaneesNeCreentQuUneInscriptionActive() throws Exception {
        Tenant tenant = newTenant();
        UUID student = newStudent(tenant);
        UUID year = openYear(tenant);
        UUID level = newLevel(tenant);

        List<Response> responses = concurrently(8, () -> post(
                tenant.path("/students/" + student + "/enrollments"),
                tenant.adminToken(),
                Map.of("academicYearId", year.toString(), "academicUnitId", level.toString())));

        assertThat(countStatus(responses, 201)).as(responses.toString()).isEqualTo(1);
        assertThat(countStatus(responses, 409)).as(responses.toString()).isEqualTo(7);
        Integer active = jdbc.sql("SELECT count(*) FROM enrollments WHERE student_record_id = :id AND status = 'ACTIVE'")
                .param("id", student).query(Integer.class).single();
        assertThat(active).isEqualTo(1);
        Integer history = jdbc.sql("""
                SELECT count(*) FROM enrollment_history h
                JOIN enrollments e ON e.id = h.enrollment_id
                WHERE e.student_record_id = :id
                """).param("id", student).query(Integer.class).single();
        assertThat(history).as("les perdants ne laissent aucune trace").isEqualTo(1);
    }

    @Test
    void uneInvitationAccepteeEnParalleleNeCreeQuUnCompte() throws Exception {
        Tenant tenant = newTenant();
        String contact = uniqueEmail();
        Response invited = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", contact, "role", "CASHIER"));

        List<Response> responses = concurrently(6, () -> post(
                "/api/v2/invitations/accept",
                null,
                Map.of("token", invited.text("token"), "fullName", "Course", "password", PASSWORD)));

        assertThat(countStatus(responses, 200)).as(responses.toString()).isEqualTo(1);
        Integer accounts = jdbc.sql("SELECT count(*) FROM account_identifiers WHERE value = :contact")
                .param("contact", contact).query(Integer.class).single();
        assertThat(accounts).isEqualTo(1);
    }

    @Test
    void deuxAdministrateursQuiSeRetrogradentNeLaissentPasLEtablissementSansAdministrateur() throws Exception {
        Tenant tenant = newTenant();
        String secondContact = uniqueEmail();
        Response invited = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", secondContact, "role", "ESTABLISHMENT_ADMIN"));
        acceptAsNewAccount(invited.text("token"), "Second Admin");
        String secondAdmin = login(secondContact, PASSWORD);

        Response members = get(tenant.path("/members"), tenant.adminToken());
        String first = members.body().get(0).path("membershipId").asString();
        String second = members.body().get(1).path("membershipId").asString();

        List<Response> responses = concurrently(2, new java.util.concurrent.Callable<>() {
            private final java.util.concurrent.atomic.AtomicInteger turn = new java.util.concurrent.atomic.AtomicInteger();

            @Override
            public Response call() {
                boolean firstCall = turn.getAndIncrement() == 0;
                return patch(
                        tenant.path("/members/" + (firstCall ? first : second)),
                        firstCall ? secondAdmin : tenant.adminToken(),
                        Map.of("roles", List.of("CASHIER")));
            }
        });

        Integer admins = jdbc.sql("""
                SELECT count(*) FROM memberships m
                JOIN membership_roles r ON r.membership_id = m.id
                WHERE m.establishment_id = :id AND m.status = 'ACTIVE' AND r.role = 'ESTABLISHMENT_ADMIN'
                """).param("id", tenant.id()).query(Integer.class).single();
        assertThat(admins).as(responses.toString()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void desEchecsDeConnexionSimultanesSontTousComptes() throws Exception {
        Tenant tenant = newTenant();

        concurrently(5, () -> post(
                "/api/v2/auth/login",
                null,
                Map.of("identifier", tenant.adminContact(), "password", "mauvais-mot-de-passe")));

        Response locked = post("/api/v2/auth/login", null, Map.of("identifier", tenant.adminContact(), "password", PASSWORD));
        assertThat(locked.status()).isEqualTo(401);
    }
}
