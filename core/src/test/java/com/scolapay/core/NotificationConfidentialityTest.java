package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Les réponses sont examinées brutes, sans les raccourcis du canal de test. */
class NotificationConfidentialityTest extends IntegrationTest {

    @Test
    void leJetonDuPremierAdministrateurNeRevientPasALOperateur() {
        Response created = createEstablishment(uniqueEmail());
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        assertThat(created.body().path("firstAdminInvitation").has("token")).isFalse();
    }

    @Test
    void leJetonDuPersonnelNeRevientPasALInvitant() {
        Tenant school = newTenant();
        Response invited = post(school.path("/invitations"), school.adminToken(),
                Map.of("contact", uniqueEmail(), "role", "REGISTRAR"));
        assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
        assertThat(invited.body().has("token")).isFalse();
    }

    @Test
    void leSuiviEstAutoriseEtIsoleSansDestinataireNiJeton() {
        Tenant owner = newTenant();
        Tenant other = newTenant();
        String contact = uniqueEmail();
        Response invited = post(owner.path("/invitations"), owner.adminToken(),
                Map.of("contact", contact, "role", "REGISTRAR"));
        String suffix = "/invitations/" + invited.text("id") + "/delivery";
        Response status = get(owner.path(suffix), owner.adminToken());
        assertThat(status.status()).as(status.raw()).isEqualTo(200);
        assertThat(status.raw()).doesNotContain(contact, invited.text("token"), "recipient", "encryptedPayload");
        assertThat(get(other.path(suffix), other.adminToken()).status()).isEqualTo(404);
        assertThat(get(owner.path(suffix), other.adminToken()).status()).isEqualTo(404);
        assertThat(get(owner.path(suffix), newStaff(owner, "CASHIER")).status()).isEqualTo(403);
        assertThat(get(owner.path(suffix), null).status()).isEqualTo(401);
    }

    @Test
    void lOperateurSuitSeulementLesInvitationsAdministrativesQuIlAEmises() {
        Response created = createEstablishment(uniqueEmail());
        String school = created.body().path("establishment").path("id").asString();
        String invitation = created.body().path("firstAdminInvitation").path("id").asString();
        String path = "/api/v2/platform/establishments/" + school + "/admin-invitations/" + invitation + "/delivery";
        assertThat(get(path, operatorToken()).status()).isEqualTo(200);
        Tenant other = newTenant();
        assertThat(get(path, other.adminToken()).status()).isEqualTo(403);
        Response staff = post(other.path("/invitations"), other.adminToken(),
                Map.of("contact", uniqueEmail(), "role", "CASHIER"));
        assertThat(get("/api/v2/platform/establishments/" + other.id() + "/admin-invitations/" + staff.text("id") + "/delivery",
                operatorToken()).status()).isEqualTo(404);
    }
}
