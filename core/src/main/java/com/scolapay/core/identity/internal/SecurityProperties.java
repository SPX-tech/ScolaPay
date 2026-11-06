package com.scolapay.core.identity.internal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scolapay.security")
record SecurityProperties(String jwtSecret, String issuer, Duration accessTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    SecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "scolapay.security.jwt-secret doit contenir au moins " + MIN_SECRET_BYTES + " octets.");
        }
    }
}
