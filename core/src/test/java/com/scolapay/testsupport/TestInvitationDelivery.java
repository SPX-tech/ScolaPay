package com.scolapay.testsupport;

import com.scolapay.core.identity.InvitationDelivery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hors de {@code com.scolapay.core} : ainsi l'analyse des composants ne le trouve pas d'elle-même,
 * et un test qui veut prouver l'absence de canal n'en a réellement aucun.
 */
@Configuration(proxyBeanMethods = false)
public class TestInvitationDelivery {

    @Bean
    InvitationDelivery invitationDelivery() {
        return (contact, token, expiresAt) -> {};
    }
}
