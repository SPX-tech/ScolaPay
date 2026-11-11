package com.scolapay.core.identity.internal;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class AuthAbuseKeys {
    private final byte[] key;
    AuthAbuseKeys(SecurityProperties security) {
        key = mac(security.jwtSecret().getBytes(StandardCharsets.UTF_8), "scolapay/auth-abuse/key/v1");
    }
    int slot(String dimension, String value) {
        byte[] digest = mac(key, dimension + "\0" + value);
        return ((digest[0] & 255) << 8) | (digest[1] & 255);
    }
    private static byte[] mac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException unavailable) {
            throw new IllegalStateException("AUTH_ABUSE_KEY_UNAVAILABLE");
        }
    }
}
