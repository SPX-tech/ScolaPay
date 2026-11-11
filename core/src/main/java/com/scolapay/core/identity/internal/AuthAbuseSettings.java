package com.scolapay.core.identity.internal;

import java.time.Duration;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.stereotype.Component;

@Component
public class AuthAbuseSettings {
    public enum Endpoint {
        LOGIN("login", 0), INVITATIONS("invitations", 4);
        final String key;
        final int scope;
        Endpoint(String key, int scope) { this.key = key; this.scope = scope; }
    }

    public record Budget(int capacity, long stepMicros) {
        long toleranceMicros() { return Math.multiplyExact(capacity - 1L, stepMicros); }
    }

    public record Limits(Budget origin, Budget global, Budget pair, Budget target, int concurrency) {}

    private final Limits login;
    private final Limits invitations;
    final boolean trustedProxy;
    final List<ClientOrigin.Network> proxies;
    final int maxHops;
    final int maxBodyBytes;
    final int counterConcurrency;

    public AuthAbuseSettings(Environment env) {
        if (!value(env, "server.forward-headers-strategy", "none").equalsIgnoreCase("none")) {
            throw new IllegalStateException("AUTH_FORWARDING_CONFIGURATION_CONFLICT");
        }
        for (String property : List.of("remote-ip-header", "protocol-header", "host-header", "port-header",
                "internal-proxies", "trusted-proxies")) {
            if (Binder.get(env).bind("server.tomcat.remoteip." + property, String.class).isBound()) {
                throw new IllegalStateException("AUTH_FORWARDING_CONFIGURATION_CONFLICT");
            }
        }
        String mode = value(env, "scolapay.auth-abuse.proxy.mode", "direct");
        if (!mode.equals("direct") && !mode.equals("trusted")) throw invalid();
        trustedProxy = mode.equals("trusted");
        String configured = value(env, "scolapay.auth-abuse.proxy.trusted-cidrs", "");
        try {
            if (configured.isBlank()) proxies = List.of();
            else {
                String[] parts = configured.split(",", -1);
                if (parts.length > 32) throw invalid();
                proxies = java.util.Arrays.stream(parts).map(String::trim).map(ClientOrigin.Network::parse).toList();
            }
        } catch (RuntimeException malformed) { throw invalid(); }
        if (trustedProxy == proxies.isEmpty()) throw invalid();
        maxHops = number(env, "proxy.max-hops", 8, 1, 16);
        maxBodyBytes = number(env, "max-body-bytes", 8192, 1024, 16384);
        counterConcurrency = number(env, "counter-concurrency", 2, 1, 16);
        login = limits(env, Endpoint.LOGIN, 180, 1200, 4, 20, 2);
        invitations = limits(env, Endpoint.INVITATIONS, 60, 300, 10, 20, 1);
    }

    public Limits limits(Endpoint endpoint) { return endpoint == Endpoint.LOGIN ? login : invitations; }

    private Limits limits(Environment env, Endpoint endpoint, int origin, int global, int pair, int target, int concurrency) {
        String base = endpoint.key + ".";
        return new Limits(budget(env, base+"origin", origin, "PT1M"),
                budget(env, base+"global", global, "PT1M"),
                budget(env, base+"pair", pair, "PT1M"),
                budget(env, base+"target", target, "PT15M"),
                number(env, base+"concurrency", concurrency, 1, 64));
    }

    private Budget budget(Environment env, String prefix, int capacity, String period) {
        int count = number(env, prefix+".capacity", capacity, 1, 100000);
        try {
            Duration duration = Duration.parse(value(env, "scolapay.auth-abuse."+prefix+".period", period));
            if (duration.compareTo(Duration.ofSeconds(1)) < 0 || duration.compareTo(Duration.ofHours(1)) > 0) throw invalid();
            long micros = duration.toNanos() / 1000;
            return new Budget(count, (micros + count - 1) / count);
        } catch (RuntimeException malformed) { throw invalid(); }
    }

    private static int number(Environment env, String suffix, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(value(env, "scolapay.auth-abuse."+suffix, Integer.toString(fallback)));
            if (value < min || value > max) throw invalid();
            return value;
        } catch (RuntimeException malformed) { throw invalid(); }
    }

    private static IllegalStateException invalid() { return new IllegalStateException("AUTH_ABUSE_CONFIGURATION_INVALID"); }

    private static String value(Environment env, String key, String fallback) {
        try { return Binder.get(env).bind(key, String.class).orElse(fallback); }
        catch (RuntimeException malformed) { throw invalid(); }
    }
}
