package com.scolapay.testsupport;

import com.scolapay.core.identity.InvitationDelivery;
import com.scolapay.core.identity.Identifier;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Hors de {@code com.scolapay.core} : ainsi l'analyse des composants ne le trouve pas d'elle-même,
 * et un test qui veut prouver l'absence de canal n'en a réellement aucun.
 */
@Configuration(proxyBeanMethods = false)
public class TestInvitationDelivery {

    private static final Map<UUID, String> TOKENS = new ConcurrentHashMap<>();

    public static String token(UUID id) {
        String token = TOKENS.get(id);
        if (token == null) throw new IllegalStateException("Invitation absente de la boîte de test");
        return token;
    }

    @Bean
    @Primary
    InvitationDelivery invitationDelivery(JdbcClient jdbc) {
        // Exclusivement dans les bases jetables : permet au parent de lire la boîte d'une JVM de sonde.
        jdbc.sql("CREATE TABLE IF NOT EXISTS test_invitation_deliveries (id uuid PRIMARY KEY, token text NOT NULL)").update();
        return new InvitationDelivery() {
            @Override public boolean supports(Identifier.Kind kind) { return true; }
            @Override public void deliver(UUID id, Identifier contact, String token, Instant expiresAt) {
                jdbc.sql("INSERT INTO test_invitation_deliveries (id, token) VALUES (:id,:token)")
                        .param("id", id).param("token", token).update();
                TOKENS.put(id, token);
            }
            @Override public Report status(UUID id) { return new Report("SUBMITTED", 1, Instant.now(), null); }
        };
    }
}
