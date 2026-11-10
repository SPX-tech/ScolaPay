package com.scolapay.core.identity.internal;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Réponse publique indépendante du compte, du contact et du jeton présenté. */
public final class AuthAbuseRejection extends RuntimeException {
    final int status;
    final String code;
    final String detail;
    final int retryAfter;

    private AuthAbuseRejection(int status, String code, String detail, int retryAfter) {
        super(code, null, false, false);
        this.status = status; this.code = code; this.detail = detail; this.retryAfter = retryAfter;
    }
    static AuthAbuseRejection limited(int seconds) {
        return new AuthAbuseRejection(429, "AUTH_RATE_LIMITED", "Trop de tentatives. Réessayez après le délai indiqué.", Math.max(1, seconds));
    }
    static AuthAbuseRejection busy() {
        return new AuthAbuseRejection(503, "AUTH_BUSY", "Le service d'authentification est occupé. Réessayez dans un instant.", 1);
    }
    static AuthAbuseRejection unavailable() {
        return new AuthAbuseRejection(503, "AUTH_PROTECTION_UNAVAILABLE", "L'authentification est temporairement indisponible.", 1);
    }
    static AuthAbuseRejection origin() {
        return new AuthAbuseRejection(400, "CLIENT_ORIGIN_INVALID", "L'origine de la requête ne peut pas être établie.", 0);
    }
    static AuthAbuseRejection invalid() {
        return new AuthAbuseRejection(400, "AUTH_REQUEST_INVALID", "La requête d'authentification n'est pas valide.", 0);
    }
    static AuthAbuseRejection tooLarge() {
        return new AuthAbuseRejection(413, "PAYLOAD_TOO_LARGE", "Le corps de la requête est trop volumineux.", 0);
    }

    void write(HttpServletResponse response) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        if (retryAfter > 0) response.setHeader("Retry-After", Integer.toString(retryAfter));
        // Toutes les chaînes sont des constantes internes, jamais des données de la requête.
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status
                + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }
}
