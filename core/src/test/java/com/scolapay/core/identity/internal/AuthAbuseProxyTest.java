package com.scolapay.core.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthAbuseProxyTest {
    static MockHttpServletRequest request(String peer, String forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
    static ClientOrigin resolver(String ranges) {
        return new ClientOrigin(new AuthAbuseSettings(new MockEnvironment()
                .withProperty("scolapay.auth-abuse.proxy.mode", "trusted")
                .withProperty("scolapay.auth-abuse.proxy.trusted-cidrs", ranges)));
    }

    @Test
    void uneConnexionDirecteIgnoreTousLesEnTetesDAutorite() {
        var origin = new ClientOrigin(new AuthAbuseSettings(new MockEnvironment()));
        var forged = request("192.0.2.4", "198.51.100.8");
        forged.addHeader("Forwarded", "for=203.0.113.9");
        forged.addHeader("X-Real-IP", "203.0.113.10");
        assertThat(origin.resolve(forged)).isEqualTo(origin.resolve(request("192.0.2.4", null)));
    }

    @Test
    void unPairNonApprouveNePeutActiverLeForwarding() {
        var origin = resolver("192.0.2.1/32");
        assertThat(origin.resolve(request("198.51.100.4", "203.0.113.9")))
                .isEqualTo(origin.resolve(request("198.51.100.4", null)));
    }

    @Test
    void laChaineEstLueADroiteEtLePrefixeClientNestPasUneAutorite() {
        var origin = resolver("192.0.2.1/32,192.0.2.2/32");
        assertThat(origin.resolve(request("192.0.2.1", "faux.invalid, 198.51.100.9, 192.0.2.2")))
                .isEqualTo(origin.resolve(request("198.51.100.9", null)));
    }

    @Test
    void unProxyApprouveDoitFournirUneOrigineNonAmbigue() {
        var origin = resolver("192.0.2.1/32");
        for (String value : new String[] {"", "localhost", "198.51.100.4:8080", "192.0.2.1", "198.51.100.4,", "x".repeat(1025)}) {
            assertThatThrownBy(() -> origin.resolve(request("192.0.2.1", value))).isInstanceOf(AuthAbuseRejection.class);
        }
        assertThatThrownBy(() -> origin.resolve(request("192.0.2.1", null))).isInstanceOf(AuthAbuseRejection.class);
        var duplicate = request("192.0.2.1", "198.51.100.4");
        duplicate.addHeader("X-Forwarded-For", "198.51.100.5");
        assertThatThrownBy(() -> origin.resolve(duplicate)).isInstanceOf(AuthAbuseRejection.class);
        var competing = request("192.0.2.1", "198.51.100.4");
        competing.addHeader("Forwarded", "for=198.51.100.4");
        assertThatThrownBy(() -> origin.resolve(competing)).isInstanceOf(AuthAbuseRejection.class);
    }

    @Test
    void uneChaineTropLongueEstRefusee() {
        var origin = resolver("192.0.2.1/32");
        assertThatThrownBy(() -> origin.resolve(request("192.0.2.1", "198.51.100.1,".repeat(8)+"198.51.100.2")))
                .isInstanceOf(AuthAbuseRejection.class);
    }

    @Test
    void lesVariantesIpv6EtIpv4MappeesPartagentLeurQuota() {
        var origin = new ClientOrigin(new AuthAbuseSettings(new MockEnvironment()));
        assertThat(origin.resolve(request("2001:db8:1234:5678::1", null)))
                .isEqualTo(origin.resolve(request("2001:db8:1234:5678:abcd:ffff::2", null)));
        assertThat(origin.resolve(request("2001:db8:1234:5679::1", null)))
                .isNotEqualTo(origin.resolve(request("2001:db8:1234:5678::1", null)));
        assertThat(origin.resolve(request("::ffff:192.0.2.4", null)))
                .isEqualTo(origin.resolve(request("192.0.2.4", null)));
    }

    @Test
    void laConfigurationRefuseLesAmbiguitesEtLesValeursDangereusesSansLesRecopier() {
        for (String range : new String[] {"0.0.0.0/0", "::/0", "localhost/32", "192.0.2.1/33", "::ffff:192.0.2.1/120"}) {
            assertThatThrownBy(() -> resolver(range)).hasMessage("AUTH_ABUSE_CONFIGURATION_INVALID").hasNoCause();
        }
        assertThatThrownBy(() -> new AuthAbuseSettings(new MockEnvironment().withProperty("scolapay.auth-abuse.proxy.mode", "trusted")))
                .hasMessage("AUTH_ABUSE_CONFIGURATION_INVALID");
        assertThatThrownBy(() -> new AuthAbuseSettings(new MockEnvironment().withProperty("scolapay.auth-abuse.proxy.trusted-cidrs", "192.0.2.1/32")))
                .hasMessage("AUTH_ABUSE_CONFIGURATION_INVALID");
        for (String value : new String[] {"0", "-1", "100001", "secret-invalide"}) {
            assertThatThrownBy(() -> new AuthAbuseSettings(new MockEnvironment().withProperty("scolapay.auth-abuse.login.origin.capacity", value)))
                    .hasMessage("AUTH_ABUSE_CONFIGURATION_INVALID");
        }
        for (String value : new String[] {"PT0S", "PT2H", "jamais"}) {
            assertThatThrownBy(() -> new AuthAbuseSettings(new MockEnvironment().withProperty("scolapay.auth-abuse.login.origin.period", value)))
                    .hasMessage("AUTH_ABUSE_CONFIGURATION_INVALID");
        }
        assertThatThrownBy(() -> new AuthAbuseSettings(new MockEnvironment().withProperty("server.tomcat.remoteip.remote-ip-header", "X-Forwarded-For")))
                .hasMessage("AUTH_FORWARDING_CONFIGURATION_CONFLICT");
    }
}
