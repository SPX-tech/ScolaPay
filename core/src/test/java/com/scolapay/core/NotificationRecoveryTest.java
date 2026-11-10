package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.scolapay.notificationprobe.NotificationProbeApplication;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class NotificationRecoveryTest {
    String url;
    JdbcClient jdbc;
    HttpServer provider;
    final List<Process> processes = new ArrayList<>();
    final Map<String, String> bodies = new ConcurrentHashMap<>();
    final Map<String, UUID> receipts = new ConcurrentHashMap<>();
    final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void prepare() throws Exception {
        url = TestDatabases.createEmpty();
        jdbc = TestDatabases.client(url);
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/emails", exchange -> {
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String previous = bodies.putIfAbsent(key, body);
            calls.incrementAndGet();
            int status = previous == null || previous.equals(body) ? 200 : 409;
            UUID receipt = receipts.computeIfAbsent(key, ignored -> UUID.randomUUID());
            byte[] response = ("{\"id\":\"" + receipt + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var stream = exchange.getResponseBody()) { stream.write(response); }
        });
        provider.start();
    }

    @AfterEach
    void stop() throws Exception {
        for (Process process : processes) {
            process.destroyForcibly();
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        provider.stop(0);
    }

    @Test
    void unArretEntreCommitEtListenerReprendLeVraiEvenementSansPerte() throws Exception {
        assertThat(jdbc.sql("SELECT to_regclass('public.flyway_schema_history') IS NULL")
                .query(Boolean.class).single()).isTrue();
        Instance first = start("avant-listener", "--probe.drop-dispatch=true");
        List<Map<String, Object>> installed = migrationHistory();
        assertThat(installed).extracting(row -> row.get("version"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "7.1");
        assertThat(installed.get(6)).containsEntry("script", "V7__billing_parties.sql");
        assertThat(installed.get(7)).containsEntry("script", "V7_1__notification_delivery.sql");
        assertThat(installed).allSatisfy(row -> {
            assertThat(row).containsEntry("success", true);
            assertThat(row.get("checksum")).isInstanceOf(Integer.class);
        });
        UUID invitation = issue(first.api());
        assertThat(state(invitation)).isEqualTo("QUEUED");
        assertThat(jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single()).isEqualTo(1);
        first.kill();
        start("reprise-listener");
        await(() -> state(invitation).equals("SUBMITTED"));
        assertThat(migrationHistory()).isEqualTo(installed);
        Flyway.configure()
                .dataSource(url, TestDatabases.POSTGRES.getUsername(), TestDatabases.POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load().validate();
        assertThat(receipts).hasSize(1);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void unArretApresAcceptationFournisseurReprendLaMemeIntentionSansNouvelEffet() throws Exception {
        Instance first = start("apres-fournisseur", "--probe.block-after-submit=true");
        UUID invitation = issue(first.api());
        await(() -> receipts.size() == 1);
        assertThat(state(invitation)).isEqualTo("PROCESSING");
        first.kill();
        jdbc.sql("UPDATE notification_messages SET lease_until=now()-interval '1 second' WHERE id=:id")
                .param("id", invitation).update();
        start("reprise-fournisseur");
        await(() -> state(invitation).equals("SUBMITTED"));
        assertThat(calls.get()).isEqualTo(2);
        assertThat(receipts).hasSize(1);
        assertThat(bodies.keySet()).containsExactly("notification/" + invitation);
    }

    UUID issue(TestApi api) {
        TestApi.Reply created = api.post("/api/v2/platform/establishments",
                api.login(IntegrationTest.OPERATOR_CONTACT, IntegrationTest.OPERATOR_PASSWORD), Map.of(
                        "code", IntegrationTest.uniqueCode(), "name", "École reprise", "kind", "SCHOOL",
                        "timezone", "Africa/Ndjamena", "currency", "XAF", "countryCode", "TD",
                        "firstAdminContact", IntegrationTest.uniqueEmail()));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        return UUID.fromString(created.body().path("firstAdminInvitation").path("id").asString());
    }

    String state(UUID id) {
        return jdbc.sql("SELECT state FROM notification_messages WHERE id=:id").param("id", id).query(String.class).single();
    }

    List<Map<String, Object>> migrationHistory() {
        return jdbc.sql("""
                SELECT installed_rank, version, script, checksum, installed_on, success
                FROM flyway_schema_history
                ORDER BY installed_rank
                """).query().listOfRows();
    }

    void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plusSeconds(60);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            java.util.concurrent.locks.LockSupport.parkNanos(20_000_000);
        }
        assertThat(condition.getAsBoolean()).as("Transition durable attendue").isTrue();
    }

    Instance start(String name, String... extra) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Path log = Path.of("target", "notification-probe", name + "-" + port + ".log");
        Files.createDirectories(log.getParent());
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx256m", "-XX:TieredStopAtLevel=1",
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                NotificationProbeApplication.class.getName()));
        command.addAll(TestDatabases.applicationArguments(url));
        command.addAll(List.of("--server.port=" + port,
                "--scolapay.notifications.encryption-key=cXVldWUtdGVzdC1rZXktMzItYnl0ZXMtb25seS0wMDE=",
                "--scolapay.notifications.email.from=invitations@scolapay.test",
                "--scolapay.notifications.poll-interval=PT0.1S", "--scolapay.notifications.initial-delay=PT0.1S",
                "--spring.modulith.events.staleness.published=PT0.5S",
                "--spring.modulith.events.staleness.processing=PT0.5S",
                "--spring.modulith.events.staleness.check-interval=PT0.2S",
                "--scolapay.outbox.recovery-interval=PT0.2S", "--scolapay.outbox.retry-delay=PT0.1S",
                "--scolapay.outbox.max-retry-delay=PT0.5S",
                "--probe.email-endpoint=http://127.0.0.1:" + provider.getAddress().getPort() + "/emails"));
        command.addAll(List.of(extra));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        processes.add(process);
        TestApi api = new TestApi(port);
        await(() -> {
            if (!process.isAlive()) {
                try { throw new AssertionError(Files.readString(log)); }
                catch (java.io.IOException unreadable) { throw new IllegalStateException(unreadable); }
            }
            return api.healthy();
        });
        return new Instance(process, api);
    }

    record Instance(Process process, TestApi api) {
        void kill() throws Exception {
            process.destroyForcibly();
            assertThat(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }
}
