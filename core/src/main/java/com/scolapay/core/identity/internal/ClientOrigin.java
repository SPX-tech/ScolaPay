package com.scolapay.core.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Adresses littérales uniquement : aucune résolution DNS, aucune confiance implicite dans les en-têtes. */
@Component
public class ClientOrigin {
    private final AuthAbuseSettings settings;
    public ClientOrigin(AuthAbuseSettings settings) { this.settings = settings; }

    public String resolve(HttpServletRequest request) {
        byte[] peer = literal(request.getRemoteAddr());
        if (!settings.trustedProxy || !trusted(peer)) return network(peer);
        if (request.getHeader("Forwarded") != null) throw AuthAbuseRejection.origin();
        var values = Collections.list(request.getHeaders("X-Forwarded-For"));
        if (values.size() != 1 || values.getFirst().length() > 1024) throw AuthAbuseRejection.origin();
        String[] hops = values.getFirst().split(",", -1);
        if (hops.length > settings.maxHops) throw AuthAbuseRejection.origin();
        for (int index = hops.length - 1; index >= 0; index--) {
            byte[] address = literal(hops[index].trim());
            if (!trusted(address)) return network(address);
        }
        throw AuthAbuseRejection.origin();
    }

    private boolean trusted(byte[] address) { return settings.proxies.stream().anyMatch(n -> n.contains(address)); }

    private static String network(byte[] address) {
        if (address.length == 16) {
            // Un /64 empêche de multiplier les quotas par simple rotation des suffixes IPv6.
            java.util.Arrays.fill(address, 8, 16, (byte) 0);
        }
        return (address.length == 4 ? "v4:" : "v6/64:") + HexFormat.of().formatHex(address);
    }

    static byte[] literal(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 45 || raw.contains("%") || raw.contains("[") || raw.contains("]")) {
            throw AuthAbuseRejection.origin();
        }
        if (!raw.contains(":")) {
            if (!raw.matches("(0|[1-9][0-9]{0,2})(\\.(0|[1-9][0-9]{0,2})){3}")) throw AuthAbuseRejection.origin();
        }
        try { return InetAddress.ofLiteral(raw).getAddress(); }
        catch (IllegalArgumentException malformed) { throw AuthAbuseRejection.origin(); }
    }

    static final class Network {
        final byte[] address;
        final int bits;
        Network(byte[] address, int bits) { this.address = address; this.bits = bits; }
        static Network parse(String raw) {
            String[] parts = raw.split("/", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] address = literal(parts[0]);
            int bits = Integer.parseInt(parts[1]);
            if (bits <= 0 || bits > address.length * 8) throw new IllegalArgumentException();
            return new Network(address, bits);
        }
        boolean contains(byte[] candidate) {
            if (candidate.length != address.length) return false;
            for (int bit=0; bit<bits; bit++) {
                int mask = 1 << (7 - bit % 8);
                if ((candidate[bit/8] & mask) != (address[bit/8] & mask)) return false;
            }
            return true;
        }
    }
}
