package com.scolapay.core.notifications.internal;

import com.scolapay.core.notifications.NotificationTransport;
import com.scolapay.core.notifications.Notifications;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** API HTTPS fixe, sans redirection. Réponse plafonnée avant toute désérialisation. */
@Component
@ConditionalOnProperty(name = "scolapay.notifications.email.provider", havingValue = "resend")
public class ResendEmailTransport implements NotificationTransport {
    private static final int MAX_RESPONSE_BYTES = 8192;
    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final Duration timeout;
    private final JsonMapper json;

    @org.springframework.beans.factory.annotation.Autowired
    public ResendEmailTransport(NotificationSettings settings, JsonMapper json) {
        this(HttpClient.newBuilder().connectTimeout(settings.timeout()).followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("https://api.resend.com/emails"), settings.apiKey(), settings.timeout(), json);
    }

    // Constructeur de paquet : le serveur local des tests exerce le même client, sans option HTTP en production.
    ResendEmailTransport(HttpClient http, URI endpoint, String apiKey, Duration timeout, JsonMapper json) {
        this.http = http;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.json = json;
    }

    @Override public String name() { return "resend"; }
    @Override public boolean supports(Notifications.Channel channel) { return channel == Notifications.Channel.EMAIL; }
    @Override public Duration idempotencyWindow() { return Duration.ofHours(23); }

    @Override
    public Result send(Message message) {
        // Map.of ne garantit pas le même ordre entre deux JVM. Le fournisseur compare l'intention rejouée.
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("from", message.from());
        fields.put("to", List.of(message.recipient()));
        fields.put("subject", message.subject());
        fields.put("text", message.text());
        String body = json.writeValueAsString(fields);
        if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 49152) {
            return Result.failed("TRANSPORT_REJECTED");
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", message.idempotencyKey())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request, ignored -> new LimitedBody());
        try {
            HttpResponse<byte[]> response = pending.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
            int status = response.statusCode();
            if (status == 429) return Result.retry("TRANSPORT_RATE_LIMITED");
            if (status == 408 || status >= 500) return Result.retry("TRANSPORT_UNAVAILABLE");
            if (status == 409) {
                JsonNode error = json.readTree(response.body());
                return "concurrent_idempotent_requests".equals(error.path("name").asString())
                        ? Result.retry("TRANSPORT_UNAVAILABLE") : Result.failed("IDEMPOTENCY_CONFLICT");
            }
            if (status != 200 && status != 201) return Result.failed("TRANSPORT_REJECTED");
            JsonNode accepted = json.readTree(response.body());
            String id = accepted.path("id").asString();
            UUID.fromString(id);
            return Result.submitted(id);
        } catch (RuntimeException unavailable) {
            pending.cancel(true);
            // Ni corps de réponse, ni cause : certains fournisseurs y recopient le corps de la requête.
            return Result.retry("TRANSPORT_PROTOCOL_ERROR");
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalStateException("TRANSPORT_RESPONSE_TOO_LARGE"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) {
            result.completeExceptionally(new IllegalStateException("TRANSPORT_RESPONSE_UNAVAILABLE"));
        }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
