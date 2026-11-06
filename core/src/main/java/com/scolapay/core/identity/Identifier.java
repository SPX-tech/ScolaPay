package com.scolapay.core.identity;

import com.scolapay.core.shared.ApiException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Identifiant de contact normalisé : un e-mail, ou un téléphone au format E.164. */
public record Identifier(Kind kind, String value) {

    public enum Kind {
        EMAIL,
        PHONE
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s.]+(\\.[^@\\s.]+)+$");
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final int MAX_EMAIL_LENGTH = 254;

    public static Identifier parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.contains("@")) {
            String email = trimmed.toLowerCase(Locale.ROOT);
            if (email.length() <= MAX_EMAIL_LENGTH && EMAIL.matcher(email).matches()) {
                return new Identifier(Kind.EMAIL, email);
            }
        } else {
            String phone = trimmed.replaceAll("[\\s.()-]", "");
            if (PHONE.matcher(phone).matches()) {
                return new Identifier(Kind.PHONE, phone);
            }
        }
        throw ApiException.invalid(
                "INVALID_IDENTIFIER", "L'identifiant doit être un e-mail ou un téléphone au format international.");
    }
}
