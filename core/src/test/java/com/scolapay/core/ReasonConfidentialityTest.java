package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/** La restriction vit dans l'API : aucune réponse ne donne un motif à un caissier, quelle que soit l'interface. */
class ReasonConfidentialityTest extends BillingScenarioTest {

    private static final String SECRET = "MOTIF-CONFIDENTIEL";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    private record Scene(School school, UUID student, UUID obligation) {}

    /** Un motif à chaque endroit où il peut être stocké : écriture, demande, refus, retrait et réexamen. */
    private Scene scene() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID student = enrolledAndBilled(school);
        UUID obligation = obligationOf(student);
        String path = tenant.path("/billing/obligations/" + obligation);
        String admin = tenant.adminToken();
        String accountant = newStaff(tenant, "ACCOUNTANT");

        assertThat(post(path + "/discounts", admin, Map.of("amountMinor", "1000", "reason", "Bourse " + SECRET, "idempotencyKey", key())).status()).isEqualTo(201);
        assertThat(post(path + "/corrections", admin, Map.of("amountMinor", "-1000", "reason", "Correction " + SECRET, "idempotencyKey", key())).status()).isEqualTo(201);

        String rejected = requestIdOf(post(path + "/cancellation", admin, Map.of("reason", "Annulation " + SECRET, "idempotencyKey", key())));
        assertThat(reject(tenant, accountant, rejected, "Refus " + SECRET).status()).isEqualTo(204);

        String withdrawn = requestIdOf(post(path + "/cancellation", admin, Map.of("reason", "Annulation " + SECRET, "idempotencyKey", key())));
        assertThat(withdraw(tenant, admin, withdrawn, "Retrait " + SECRET).status()).isEqualTo(204);

        endEnrollment(tenant, student, "WITHDRAWN");
        JsonNode signal = get(tenant.path("/billing/reviews?academicYearId=" + school.year()), admin).body().path("signals").get(0);
        assertThat(post(tenant.path("/billing/reviews/decisions"), admin, Map.of(
                "obligationId", obligation.toString(),
                "signal", signal.path("signal").asString(),
                "fingerprint", signal.path("fingerprint").asString(),
                "decision", "NO_CHANGE",
                "reason", "Réexamen " + SECRET)).status()).isEqualTo(204);

        requestIdOf(post(path + "/cancellation", admin, Map.of("reason", "Annulation " + SECRET, "idempotencyKey", key())));
        return new Scene(school, student, obligation);
    }

    private static String requestsPath(Tenant tenant, String status) {
        return tenant.path("/billing/adjustment-requests?status=" + status);
    }

    @Test
    void leCaissierVoitLesMontantsSansAucunMotif() throws Exception {
        Scene scene = scene();
        Tenant tenant = scene.school().tenant();
        String cashier = newStaff(tenant, "CASHIER");

        Response detail = get(tenant.path("/billing/obligations/" + scene.obligation()), cashier);

        assertThat(detail.status()).as(detail.raw()).isEqualTo(200);
        assertThat(detail.raw()).doesNotContain(SECRET);
        assertThat(detail.body().path("reasonsVisible").asBoolean()).isFalse();
        assertThat(detail.body().path("obligation").path("dueMinor").asString()).as("Le caissier garde ce qu'il lui faut pour travailler").isEqualTo("98000");
        JsonNode discount = detail.body().path("entries").get(1);
        assertThat(discount.path("kind").asString()).isEqualTo("DISCOUNT");
        assertThat(discount.path("amountMinor").asString()).isEqualTo("-1000");
        assertThat(discount.path("reason").isNull()).isTrue();
        assertThat(detail.body().path("reviewDecisions").get(0).path("decision").asString()).isEqualTo("NO_CHANGE");
        assertThat(detail.body().path("reviewDecisions").get(0).path("reason").isNull()).isTrue();
    }

    @Test
    void leCaissierVoitLesDemandesSansMotifNiNoteDeDecision() throws Exception {
        Scene scene = scene();
        Tenant tenant = scene.school().tenant();
        String cashier = newStaff(tenant, "CASHIER");

        for (String status : List.of("PENDING", "REJECTED", "WITHDRAWN")) {
            Response requests = get(requestsPath(tenant, status), cashier);
            assertThat(requests.status()).as(requests.raw()).isEqualTo(200);
            assertThat(requests.body().size()).as(status).isEqualTo(1);
            assertThat(requests.raw()).as(status).doesNotContain(SECRET);
            assertThat(requests.body().get(0).path("reason").isNull()).as(status).isTrue();
            assertThat(requests.body().get(0).path("decisionNote").isNull()).as(status).isTrue();
            assertThat(requests.body().get(0).path("amountMinor").asString()).as(status).isEqualTo("-98000");
        }
    }

    @Test
    void lesRolesDeControleLisentLesMotifs() throws Exception {
        Scene scene = scene();
        Tenant tenant = scene.school().tenant();

        for (String token : List.of(
                tenant.adminToken(), newStaff(tenant, "ACCOUNTANT"), newStaff(tenant, "AUDITOR"), newStaff(tenant, "DIRECTOR"))) {
            Response detail = get(tenant.path("/billing/obligations/" + scene.obligation()), token);
            assertThat(detail.body().path("reasonsVisible").asBoolean()).isTrue();
            assertThat(detail.body().path("entries").get(1).path("reason").asString()).isEqualTo("Bourse " + SECRET);
            assertThat(detail.body().path("reviewDecisions").get(0).path("reason").asString()).isEqualTo("Réexamen " + SECRET);
            assertThat(get(requestsPath(tenant, "PENDING"), token).body().get(0).path("reason").asString()).isEqualTo("Annulation " + SECRET);
            assertThat(get(requestsPath(tenant, "REJECTED"), token).body().get(0).path("decisionNote").asString()).isEqualTo("Refus " + SECRET);
            assertThat(get(requestsPath(tenant, "WITHDRAWN"), token).body().get(0).path("decisionNote").asString()).isEqualTo("Retrait " + SECRET);
        }
    }

    /** Parcourt les vraies routes : une nouvelle route de lecture est couverte sans qu'on pense à l'ajouter. */
    @Test
    void aucuneRouteDeLectureDeLaFacturationNeDonneUnMotifAuCaissier() throws Exception {
        Scene scene = scene();
        School school = scene.school();
        Tenant tenant = school.tenant();
        String cashier = newStaff(tenant, "CASHIER");
        Map<String, String> ids = new HashMap<>();
        ids.put("{establishmentId}", tenant.id().toString());
        ids.put("{obligationId}", scene.obligation().toString());
        ids.put("{studentId}", scene.student().toString());
        ids.put("{versionId}", school.catalog().toString());
        ids.put("{catalogVersionId}", school.catalog().toString());

        List<String> read = new ArrayList<>();
        List<String> answered = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (!info.getMethodsCondition().getMethods().contains(RequestMethod.GET)) {
                continue;
            }
            for (String pattern : info.getPatternValues()) {
                if (!pattern.contains("/billing/") && !pattern.contains("/audit")) {
                    continue;
                }
                String path = pattern;
                for (Map.Entry<String, String> id : ids.entrySet()) {
                    path = path.replace(id.getKey(), id.getValue());
                }
                path = path.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                for (String status : List.of("PENDING", "REJECTED", "WITHDRAWN")) {
                    Response response = get(path + "?academicYearId=" + school.year() + "&academicUnitId=" + school.level() + "&status=" + status, cashier);
                    assertThat(response.raw()).as(pattern).doesNotContain(SECRET);
                    if (response.status() == 200) {
                        answered.add(pattern);
                    }
                }
                read.add(pattern);
            }
        }

        assertThat(read).as("Le test parcourt bien de vraies routes").anyMatch(route -> route.endsWith("/billing/obligations/{obligationId}"));
        assertThat(answered).as("Et ces routes répondent vraiment").anyMatch(route -> route.endsWith("/billing/obligations/{obligationId}"))
                .anyMatch(route -> route.endsWith("/billing/adjustment-requests"))
                .anyMatch(route -> route.endsWith("/billing/reviews"));
    }

    @Test
    void leJournalDAuditNeRecopieAucunMotif() throws Exception {
        Scene scene = scene();

        long leaks = jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id = :id AND CAST(details AS text) LIKE :secret")
                .param("id", scene.school().tenant().id())
                .param("secret", "%" + SECRET + "%")
                .query(Long.class)
                .single();

        assertThat(leaks).isZero();
    }
}
