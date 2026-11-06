package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** L'isolation entre établissements, exigence fondamentale de la plateforme. */
class TenantIsolationTest extends IntegrationTest {

    private static final String SURFACE = "/api/v2/establishments/{establishmentId}";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    private record Route(String method, String template) {

        String url(UUID establishmentId) {
            return template.replace("{establishmentId}", establishmentId.toString())
                    .replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        }
    }

    /** Lues dans l'application : une route ajoutée demain est couverte sans modifier ce test. */
    private List<Route> routes(String prefix) {
        List<Route> routes = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                if (pattern.startsWith(prefix)) {
                    for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                        routes.add(new Route(method.name(), pattern));
                    }
                }
            }
        }
        return routes;
    }

    private Response call(Route route, UUID establishmentId, String token) {
        boolean hasBody = route.method().equals("POST") || route.method().equals("PATCH");
        return send(route.method(), route.url(establishmentId), token, hasBody ? "{}" : null);
    }

    private void assertEveryRouteIsHidden(UUID establishmentId, String token, String who) {
        for (Route route : routes(SURFACE)) {
            Response response = call(route, establishmentId, token);
            assertThat(response.status())
                    .as("%s sur %s %s : %s", who, route.method(), route.template(), response.raw())
                    .isEqualTo(404);
            assertThat(response.code()).as(route.template()).isEqualTo("ESTABLISHMENT_NOT_FOUND");
        }
    }

    @Test
    void laMatriceCouvreBienToutesLesRoutesDeLaSurface() {
        assertThat(routes(SURFACE)).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void chaqueRoutePubliqueReleveDUneSurfaceConnue() {
        List<String> surfaces = List.of(
                SURFACE, "/api/v2/me", "/api/v2/auth/login", "/api/v2/invitations/accept", "/api/v2/platform/establishments");
        for (Route route : routes("/api/v2")) {
            assertThat(surfaces)
                    .as("%s n'appartient à aucune surface : décider explicitement de sa protection", route.template())
                    .anyMatch(route.template()::startsWith);
        }
    }

    @Test
    void sansJetonToutEstRefuse() {
        Tenant tenant = newTenant();
        for (Route route : routes(SURFACE)) {
            assertThat(call(route, tenant.id(), null).status()).as(route.template()).isEqualTo(401);
        }
        assertThat(get("/api/v2/me/students", null).status()).isEqualTo(401);
        assertThat(get("/api/v2/me", null).status()).isEqualTo(401);
        assertThat(post("/api/v2/platform/establishments", null, Map.of()).status()).isEqualTo(401);
    }

    @Test
    void unAdministrateurDUnAutreEtablissementNeVoitRien() {
        Tenant cible = newTenant();
        Tenant autre = newTenant();
        assertEveryRouteIsHidden(cible.id(), autre.adminToken(), "administrateur d'un autre établissement");
    }

    @Test
    void unOperateurDeLaPlateformeNEntreDansAucunEtablissement() {
        Tenant cible = newTenant();
        assertEveryRouteIsHidden(cible.id(), operatorToken(), "opérateur");
    }

    @Test
    void unParentRattacheAUnDossierNOuvrePasLaSurfaceDuPersonnel() {
        Tenant cible = newTenant();
        String parent = newGuardian(cible, newStudent(cible));
        assertEveryRouteIsHidden(cible.id(), parent, "parent");
    }

    @Test
    void unMembreSuspenduOuRevoquePerdSonAccesImmediatement() {
        Tenant tenant = newTenant();
        String contact = uniqueEmail();
        Response invited = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", contact, "role", "REGISTRAR"));
        acceptAsNewAccount(invited.text("token"), "Secrétaire");
        String registrar = login(contact, PASSWORD);
        assertThat(get(tenant.path("/students"), registrar).status()).isEqualTo(200);

        String membershipId = null;
        for (var member : get(tenant.path("/members"), tenant.adminToken()).body()) {
            if (member.path("contacts").get(0).asString().equals(contact)) {
                membershipId = member.path("membershipId").asString();
            }
        }
        assertThat(membershipId).isNotNull();

        assertThat(patch(tenant.path("/members/" + membershipId), tenant.adminToken(), Map.of("status", "SUSPENDED")).status()).isEqualTo(200);
        assertEveryRouteIsHidden(tenant.id(), registrar, "membre suspendu");

        assertThat(patch(tenant.path("/members/" + membershipId), tenant.adminToken(), Map.of("status", "REVOKED")).status()).isEqualTo(200);
        assertThat(get(tenant.path("/students"), registrar).status()).isEqualTo(404);
    }

    @Test
    void unEtablissementSuspenduDisparaitPourSonPersonnelEtSesFamilles() {
        Tenant tenant = newTenant();
        UUID student = newStudent(tenant);
        String parent = newGuardian(tenant, student);

        Response suspended = post("/api/v2/platform/establishments/" + tenant.id() + "/status", operatorToken(), Map.of("status", "SUSPENDED"));
        assertThat(suspended.status()).as(suspended.raw()).isEqualTo(204);

        assertThat(get(tenant.path(""), tenant.adminToken()).status()).isEqualTo(404);
        assertThat(get("/api/v2/me/students", parent).body().size()).isZero();
        assertThat(get("/api/v2/me/students/" + student, parent).status()).isEqualTo(404);

        // Le dossier n'a pas été touché : tout revient à la réactivation.
        assertThat(post("/api/v2/platform/establishments/" + tenant.id() + "/status", operatorToken(), Map.of("status", "ACTIVE")).status()).isEqualTo(204);
        assertThat(get("/api/v2/me/students/" + student, parent).status()).isEqualTo(200);
    }

    @Test
    void unIdentifiantDeRessourceEtrangereEstIntrouvableDepuisSonPropreEtablissement() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID studentA = newStudent(a);
        UUID yearA = openYear(a);
        UUID studentB = newStudent(b);
        UUID yearB = openYear(b);
        Response enrollmentA = post(a.path("/students/" + studentA + "/enrollments"), a.adminToken(), Map.of(
                "academicYearId", yearA.toString(), "level", "Sixième"));
        String grantHolder = uniqueEmail();
        acceptAsNewAccount(inviteGuardian(a, studentA, grantHolder, false), "Tuteur");
        String grantA = get(a.path("/students/" + studentA + "/access-grants"), a.adminToken()).body().get(0).path("id").asString();
        String token = b.adminToken();

        // Lecture et écriture sur le dossier de A, en passant par l'établissement B.
        assertThat(get(b.path("/students/" + studentA), token).status()).isEqualTo(404);
        assertThat(get(b.path("/students/" + studentA + "/enrollments"), token).status()).isEqualTo(404);
        assertThat(get(b.path("/students/" + studentA + "/access-grants"), token).status()).isEqualTo(404);
        assertThat(post(b.path("/students/" + studentA + "/enrollments"), token, Map.of(
                "academicYearId", yearB.toString(), "level", "Sixième")).status()).isEqualTo(404);
        assertThat(post(b.path("/students/" + studentA + "/guardian-invitations"), token, Map.of(
                "contact", uniqueEmail(), "relationship", "OTHER", "financiallyResponsible", false)).status()).isEqualTo(404);

        // Mélanger un dossier de B avec une année de A.
        assertThat(post(b.path("/students/" + studentB + "/enrollments"), token, Map.of(
                "academicYearId", yearA.toString(), "level", "Sixième")).status()).isEqualTo(404);

        // Agir sur les objets de A avec un identifiant deviné.
        assertThat(post(b.path("/students/" + studentA + "/enrollments/" + enrollmentA.text("id") + "/end"), token, Map.of(
                "status", "WITHDRAWN")).status()).isEqualTo(404);
        assertThat(delete(b.path("/students/" + studentB + "/access-grants/" + grantA), token).status()).isEqualTo(404);
        assertThat(patch(b.path("/academic-years/" + yearA), token, Map.of("status", "CLOSED")).status()).isEqualTo(404);

        // Rien n'a bougé côté A.
        assertThat(get(a.path("/students/" + studentA + "/enrollments"), a.adminToken()).body().get(0).path("status").asString()).isEqualTo("ACTIVE");
        assertThat(get(a.path("/students/" + studentA + "/access-grants"), a.adminToken()).body().get(0).path("revokedAt").isNull()).isTrue();
    }

    @Test
    void uneInvitationDUnEtablissementNePeutEtreAnnuleeDepuisUnAutre() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        Response invitation = post(a.path("/invitations"), a.adminToken(), Map.of("contact", uniqueEmail(), "role", "CASHIER"));
        assertThat(delete(b.path("/invitations/" + invitation.text("id")), b.adminToken()).status()).isEqualTo(404);
        assertThat(delete(a.path("/invitations/" + invitation.text("id")), a.adminToken()).status()).isEqualTo(204);
    }

    @Test
    void lesDossiersNeSeMelangentPasDansLesListes() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID studentA = newStudent(a);
        var listB = get(b.path("/students?limit=200"), b.adminToken()).body();
        for (var student : listB) {
            assertThat(student.path("id").asString()).isNotEqualTo(studentA.toString());
            assertThat(student.path("establishmentId").asString()).isEqualTo(b.id().toString());
        }
        assertThat(get(a.path("/students?limit=200"), a.adminToken()).body().size()).isEqualTo(1);
    }

    @Test
    void unParentNeVoitQueSesEnfants() {
        Tenant tenant = newTenant();
        UUID sonEnfant = newStudent(tenant);
        UUID autreEnfant = newStudent(tenant);
        String parent = newGuardian(tenant, sonEnfant);
        assertThat(get("/api/v2/me/students/" + sonEnfant, parent).status()).isEqualTo(200);
        assertThat(get("/api/v2/me/students/" + autreEnfant, parent).status()).isEqualTo(404);
        assertThat(get("/api/v2/me/students", parent).body().size()).isEqualTo(1);
    }

    @Test
    void lesRoutesDesOperateursSontReserveesAuxOperateurs() {
        Tenant tenant = newTenant();
        String admin = tenant.adminToken();
        Response create = post("/api/v2/platform/establishments", admin, Map.of(
                "code", uniqueCode(), "name", "Pirate", "kind", "SCHOOL", "timezone", "Africa/Ndjamena",
                "currency", "XAF", "firstAdminContact", uniqueEmail()));
        assertThat(create.status()).as(create.raw()).isEqualTo(403);
        assertThat(create.code()).isEqualTo("OPERATOR_REQUIRED");
        assertThat(post("/api/v2/platform/establishments/" + tenant.id() + "/status", admin, Map.of("status", "SUSPENDED")).status()).isEqualTo(403);
        assertThat(post("/api/v2/platform/establishments/" + tenant.id() + "/admin-invitations", admin, Map.of("contact", uniqueEmail())).status()).isEqualTo(403);
    }
}
