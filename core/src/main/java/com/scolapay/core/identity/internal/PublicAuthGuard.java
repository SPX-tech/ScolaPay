package com.scolapay.core.identity.internal;

import com.scolapay.core.identity.Identifier;
import com.scolapay.core.identity.internal.AuthAbuseSettings.Endpoint;
import com.scolapay.core.shared.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class PublicAuthGuard {
    static final String CONTEXT_ATTRIBUTE = PublicAuthGuard.class.getName()+".context";
    record Context(Endpoint endpoint, String origin) {
        @Override public String toString() { return "AuthAdmission[redacted]"; }
    }
    private final PublicAuthRateStore counters;
    private final Map<Endpoint, Semaphore> permits = new EnumMap<>(Endpoint.class);

    PublicAuthGuard(PublicAuthRateStore counters, AuthAbuseSettings settings) {
        this.counters = counters;
        for (Endpoint endpoint : Endpoint.values()) permits.put(endpoint, new Semaphore(settings.limits(endpoint).concurrency()));
    }

    <T> T execute(HttpServletRequest request, Endpoint endpoint, String presented, Supplier<T> work) {
        if (!(request.getAttribute(CONTEXT_ATTRIBUTE) instanceof Context context) || context.endpoint() != endpoint) {
            throw AuthAbuseRejection.unavailable();
        }
        Semaphore permit = permits.get(endpoint);
        if (!permit.tryAcquire()) throw AuthAbuseRejection.busy();
        try {
            String target = presented;
            if (endpoint == Endpoint.LOGIN) {
                try {
                    Identifier identifier = Identifier.parse(presented);
                    target = identifier.kind().name() + ":" + identifier.value();
                } catch (ApiException invalid) { target = "invalid-identifier"; }
            }
            counters.identity(endpoint, context.origin(), target);
            return work.get();
        } finally { permit.release(); }
    }
}
