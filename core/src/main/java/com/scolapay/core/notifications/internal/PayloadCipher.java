package com.scolapay.core.notifications.internal;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PayloadCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public PayloadCipher(NotificationSettings settings) { key = settings.encryptionKey(); }
    boolean configured() { return key != null; }

    public String encrypt(UUID id, String clear) {
        if (key == null) throw new IllegalStateException("NOTIFICATION_ENCRYPTION_UNAVAILABLE");
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(id.toString().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(clear.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, envelope, nonce.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(envelope);
        } catch (Exception failure) {
            throw new IllegalStateException("NOTIFICATION_ENCRYPTION_FAILED");
        }
    }

    public String decrypt(UUID id, String envelope) {
        try {
            if (key == null || envelope == null || !envelope.startsWith("v1:") || envelope.length() > 65536) {
                throw new IllegalArgumentException();
            }
            byte[] bytes = Base64.getDecoder().decode(envelope.substring(3));
            if (bytes.length < 29) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, bytes, 0, 12));
            cipher.updateAAD(id.toString().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new IllegalStateException("NOTIFICATION_PAYLOAD_UNREADABLE");
        }
    }

    String fingerprint(String clear) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(clear.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("NOTIFICATION_FINGERPRINT_FAILED");
        }
    }
}
