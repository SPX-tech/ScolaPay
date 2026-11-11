package com.scolapay.core.identity.internal;

import com.scolapay.core.identity.internal.AuthAbuseSettings.Endpoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/** Installé uniquement dans SecurityFilterChain, après le firewall et avant le traitement du JWT. */
class PublicAuthFilter extends OncePerRequestFilter {
    private final AuthAbuseSettings settings;
    private final ClientOrigin origins;
    private final PublicAuthRateStore counters;

    PublicAuthFilter(AuthAbuseSettings settings, ClientOrigin origins, PublicAuthRateStore counters) {
        this.settings = settings; this.origins = origins; this.counters = counters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        Endpoint endpoint = switch (path) {
            case "/api/v2/auth/login" -> Endpoint.LOGIN;
            case "/api/v2/invitations/accept" -> Endpoint.INVITATIONS;
            default -> null;
        };
        if (endpoint == null || !request.getMethod().equals("POST")) { chain.doFilter(request, response); return; }
        try {
            if (request.getContentLengthLong() > settings.maxBodyBytes) throw AuthAbuseRejection.tooLarge();
            String origin = origins.resolve(request);
            counters.ingress(endpoint, origin);
            request.setAttribute(PublicAuthGuard.CONTEXT_ATTRIBUTE, new PublicAuthGuard.Context(endpoint, origin));
            chain.doFilter(new BoundedRequest(request, settings.maxBodyBytes), response);
        } catch (AuthAbuseRejection rejected) { rejected.write(response); }
    }

    static final class BodyTooLarge extends IOException {
        BodyTooLarge() { super("AUTH_BODY_TOO_LARGE"); }
    }

    private static final class BoundedRequest extends HttpServletRequestWrapper {
        final int maxBytes;
        ServletInputStream bounded;
        BoundedRequest(HttpServletRequest request, int maxBytes) { super(request); this.maxBytes = maxBytes; }
        @Override public ServletInputStream getInputStream() throws IOException {
            if (bounded == null) bounded = new BoundedInput(super.getInputStream(), maxBytes);
            return bounded;
        }
        @Override public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    private static final class BoundedInput extends ServletInputStream {
        final ServletInputStream delegate;
        int remaining;
        BoundedInput(ServletInputStream delegate, int remaining) { this.delegate = delegate; this.remaining = remaining; }
        @Override public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0 && --remaining < 0) throw new BodyTooLarge();
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            int count = delegate.read(bytes, offset, Math.min(length, remaining+1));
            if (count > 0 && (remaining -= count) < 0) throw new BodyTooLarge();
            return count;
        }
        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }
    }
}
