package com.scolapay.core.notifications.internal;

import com.scolapay.core.identity.Identifier;
import java.time.Duration;
import java.util.Base64;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Classe volontairement sans toString généré ni binding susceptible de citer la valeur d'un secret. */
@Component
public class NotificationSettings {
    private final String provider;
    private final String apiKey;
    private final String from;
    private final byte[] key;
    private final Duration timeout;

    public NotificationSettings(Environment environment) {
        provider = environment.getProperty("scolapay.notifications.email.provider", "disabled");
        apiKey = environment.getProperty("scolapay.notifications.email.api-key", "");
        from = environment.getProperty("scolapay.notifications.email.from", "");
        String encodedKey = environment.getProperty("scolapay.notifications.encryption-key", "");
        try {
            key = encodedKey.isBlank() ? null : Base64.getDecoder().decode(encodedKey);
            if (key != null && key.length != 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("NOTIFICATIONS_ENCRYPTION_KEY_INVALID");
        }
        if (!provider.equals("disabled") && !provider.equals("resend")) {
            throw new IllegalStateException("NOTIFICATIONS_EMAIL_PROVIDER_INVALID");
        }
        if (!from.isEmpty()) {
            try {
                if (Identifier.parse(from).kind() != Identifier.Kind.EMAIL || !from.equals(Identifier.parse(from).value())) {
                    throw new IllegalArgumentException();
                }
            } catch (RuntimeException invalid) {
                throw new IllegalStateException("NOTIFICATIONS_EMAIL_FROM_INVALID");
            }
        }
        try {
            timeout = Duration.parse(environment.getProperty("scolapay.notifications.email.timeout", "PT10S"));
            if (timeout.compareTo(Duration.ofSeconds(1)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("NOTIFICATIONS_TIMEOUT_INVALID");
        }
        if (provider.equals("resend")) {
            if (key == null || from.isBlank() || !apiKey.matches("re_[A-Za-z0-9_-]{8,200}")) {
                throw new IllegalStateException("NOTIFICATIONS_EMAIL_CONFIGURATION_INCOMPLETE");
            }
            if (!System.getProperty("jdk.httpclient.HttpClient.log", "").isBlank()) {
                throw new IllegalStateException("NOTIFICATIONS_HTTP_LOGGING_FORBIDDEN");
            }
        }
    }

    boolean enabled() { return provider.equals("resend"); }
    String apiKey() { return apiKey; }
    String from() { return from; }
    byte[] encryptionKey() { return key == null ? null : key.clone(); }
    Duration timeout() { return timeout; }
}
