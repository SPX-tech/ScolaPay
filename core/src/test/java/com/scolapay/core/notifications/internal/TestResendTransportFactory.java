package com.scolapay.core.notifications.internal;

import com.scolapay.core.notifications.NotificationTransport;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

/** Point d'assemblage réservé au classpath de test. Aucune URL de transport modifiable en production. */
public final class TestResendTransportFactory {
    private TestResendTransportFactory() {}

    public static NotificationTransport create(URI endpoint) {
        return new ResendEmailTransport(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                endpoint, "re_isolated_probe_only", Duration.ofSeconds(5), JsonMapper.builder().build());
    }
}
