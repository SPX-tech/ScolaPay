package com.scolapay.core.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ouverture du portail des familles. Fermé par défaut, voir {@link GoLiveGuard}. */
@ConfigurationProperties("scolapay.portal")
public record PortalProperties(boolean enabled) {

    public void requireOpen() {
        if (!enabled) {
            throw ApiException.conflict("PORTAL_CLOSED", "Le portail des familles n'est pas ouvert.");
        }
    }
}
