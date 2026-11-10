package com.scolapay.core.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PayloadCipherTest {
    static final String KEY = "cXVldWUtdGVzdC1rZXktMzItYnl0ZXMtb25seS0wMDE=";

    @Test
    void leChiffrementEstAleatoireEtLieALIdentifiantDeLIntention() {
        PayloadCipher cipher = new PayloadCipher(new NotificationSettings(new MockEnvironment()
                .withProperty("scolapay.notifications.encryption-key", KEY)));
        UUID id = UUID.randomUUID();
        String clear = "destinataire@scolapay.test jeton-confidentiel";
        String encrypted = cipher.encrypt(id, clear);
        assertThat(encrypted).doesNotContain(clear);
        assertThat(cipher.encrypt(id, clear)).isNotEqualTo(encrypted);
        assertThat(cipher.decrypt(id, encrypted)).isEqualTo(clear);
        assertThatThrownBy(() -> cipher.decrypt(UUID.randomUUID(), encrypted))
                .hasMessage("NOTIFICATION_PAYLOAD_UNREADABLE").hasNoCause();
        String modified = encrypted.substring(0, encrypted.length() - 5) + "AAAAA";
        assertThatThrownBy(() -> cipher.decrypt(id, modified)).hasMessage("NOTIFICATION_PAYLOAD_UNREADABLE");
    }

    @Test
    void uneMauvaiseCleNeDetruitPasLaConfidentialiteDesErreurs() {
        assertThatThrownBy(() -> new NotificationSettings(new MockEnvironment()
                .withProperty("scolapay.notifications.encryption-key", "ma-valeur-secrete-invalide")))
                .hasMessage("NOTIFICATIONS_ENCRYPTION_KEY_INVALID").hasNoCause();
    }

    @Test
    void sansConfigurationAucunChiffrementNiTransportNestActive() {
        NotificationSettings settings = new NotificationSettings(new MockEnvironment());
        assertThat(settings.enabled()).isFalse();
        assertThat(new PayloadCipher(settings).configured()).isFalse();
    }

    @Test
    void unFournisseurInconnuOuIncompletBloqueLeDemarrage() {
        assertThatThrownBy(() -> new NotificationSettings(new MockEnvironment()
                .withProperty("scolapay.notifications.email.provider", "inconnu")))
                .hasMessage("NOTIFICATIONS_EMAIL_PROVIDER_INVALID");
        assertThatThrownBy(() -> new NotificationSettings(new MockEnvironment()
                .withProperty("scolapay.notifications.email.provider", "resend")))
                .hasMessage("NOTIFICATIONS_EMAIL_CONFIGURATION_INCOMPLETE");
    }

    @Test
    void leDelaiEtLExpediteurSontValidesSansCiterLeurValeur() {
        assertThatThrownBy(() -> new NotificationSettings(new MockEnvironment()
                .withProperty("scolapay.notifications.email.timeout", "PT60S")))
                .hasMessage("NOTIFICATIONS_TIMEOUT_INVALID");
        assertThatThrownBy(() -> new NotificationSettings(new MockEnvironment()
                .withProperty("scolapay.notifications.email.from", "a@scolapay.test\r\nBcc:autre@scolapay.test")))
                .hasMessage("NOTIFICATIONS_EMAIL_FROM_INVALID");
    }
}
