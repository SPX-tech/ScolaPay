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
                Map.of("contact", uniqueEmail(), "role", "SECRETARY"));
        assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
        assertThat(invited.body().has("token")).isFalse();
    }
}
