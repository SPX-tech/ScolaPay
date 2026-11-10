package com.scolapay.core.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.scolapay.core.notifications.NotificationTransport;
import com.scolapay.core.notifications.Notifications;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ResendEmailTransportTest {
    HttpServer server;
    ResendEmailTransport transport;
    final AtomicReference<String> reply = new AtomicReference<>();
    final AtomicReference<String> received = new AtomicReference<>();
    final AtomicReference<String> key = new AtomicReference<>();
    final AtomicInteger status = new AtomicInteger(200);
    final AtomicInteger calls = new AtomicInteger();
    final AtomicReference<CountDownLatch> blocked = new AtomicReference<>();
    final NotificationTransport.Message message = new NotificationTransport.Message("notification/" + UUID.randomUUID(),
            "invitations@scolapay.test", "destinataire@scolapay.test", "Votre invitation", "Jeton : secret-de-test");

    @BeforeEach
    void start() throws Exception {
        reply.set("{\"id\":\"" + UUID.randomUUID() + "\"}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            calls.incrementAndGet();
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            if (blocked.get() != null) {
                try { blocked.get().await(3, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            byte[] bytes = reply.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Location", "/must-not-follow");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        transport = new ResendEmailTransport(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"),
                "re_secret_key_for_test", Duration.ofSeconds(2), JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        if (blocked.get() != null) blocked.get().countDown();
        server.stop(0);
    }

    @Test
    void transmetLeDestinataireEtLaCleSansModifierLeCorpsEntreDeuxEssais() {
        assertThat(transport.send(message).outcome()).isEqualTo(NotificationTransport.Outcome.SUBMITTED);
        String first = received.get();
        assertThat(transport.send(message).outcome()).isEqualTo(NotificationTransport.Outcome.SUBMITTED);
        assertThat(received.get()).isEqualTo(first).contains("destinataire@scolapay.test", "secret-de-test");
        assertThat(key.get()).isEqualTo(message.idempotencyKey());
        assertThat(transport.supports(Notifications.Channel.SMS)).isFalse();
    }

    @Test
    void traiteLaPanneEtLaLimitationDeDebitCommeTemporaires() {
        for (int code : new int[] {408, 429, 500, 503}) {
            status.set(code);
            assertThat(transport.send(message).outcome()).isEqualTo(NotificationTransport.Outcome.RETRY);
        }
    }

    @Test
    void distingueLaConcurrenceDuConflitDIdempotence() {
        status.set(409);
        reply.set("{\"name\":\"concurrent_idempotent_requests\"}");
        assertThat(transport.send(message).outcome()).isEqualTo(NotificationTransport.Outcome.RETRY);
        reply.set("{\"name\":\"invalid_idempotent_request\"}");
        assertThat(transport.send(message).outcome()).isEqualTo(NotificationTransport.Outcome.FAILED);
    }

    @Test
    void unRefusOuUneRedirectionNeRenvoieJamaisLeCorpsFournisseur(CapturedOutput output) {
        reply.set("destinataire@scolapay.test secret-de-test re_secret_key_for_test");
        for (int code : new int[] {302, 400, 401, 403, 422}) {
            status.set(code);
            assertThat(transport.send(message).toString()).doesNotContain("destinataire", "secret-de-test", "re_secret");
        }
        assertThat(calls.get()).isEqualTo(5);
        assertThat(output.getAll()).doesNotContain("destinataire@scolapay.test", "secret-de-test", "re_secret_key_for_test");
    }

    @Test
    void refuseUneReponseTropGrandeOuUnRecuInvalide() {
        reply.set("x".repeat(9000));
        assertThat(transport.send(message).outcome()).isEqualTo(NotificationTransport.Outcome.RETRY);
        reply.set("{\"id\":\"invalide\"}");
        assertThat(transport.send(message).outcome()).isEqualTo(NotificationTransport.Outcome.RETRY);
        reply.set("pas du JSON");
        assertThat(transport.send(message).outcome()).isEqualTo(NotificationTransport.Outcome.RETRY);
    }

    @Test
    void borneLeTempsDeLectureDuCorps() {
        blocked.set(new CountDownLatch(1));
        long start = System.nanoTime();
        assertThat(transport.send(message).outcome()).isEqualTo(NotificationTransport.Outcome.RETRY);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }
}
