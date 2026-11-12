package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scolapay.cashprobe.CashProbeApplication;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class CashReceptionRecoveryTest {
    final List<Process> processes = new ArrayList<>();
    String url;
    JdbcClient jdbc;

    @AfterEach
    void stop() throws Exception {
        for (Process process : processes) {
            process.destroyForcibly();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void apresCommitSansReponseLaPreuveSurvitEtLesMigrationsNeSontPasRejouees() throws Exception {
        url = TestDatabases.createEmpty();
        jdbc = TestDatabases.client(url);
        Instance first = start(true, true);
        List<Map<String, Object>> installed = history();
        assertThat(installed).extracting(row -> row.get("version"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "7.1", "7.2", "8", "9", "10");
        assertThat(installed.get(9)).containsEntry("script", "V8__cash_receptions.sql");
        assertThat(installed.get(10)).containsEntry("script", "V9__single_obligation_cash_settlement.sql");
        assertThat(installed.get(11)).containsEntry("script", "V10__cash_excess_reservations.sql");
        assertThat(installed).allSatisfy(row -> {
            assertThat(row.get("checksum")).isInstanceOf(Integer.class);
            assertThat(row).containsEntry("success", true);
        });
        TestApi.School school = first.api().newSchool();
        UUID key = UUID.randomUUID();
        Map<String, Object> body = Map.of("idempotencyKey", key, "amountReceivedMinor", "25000",
                "changeGivenMinor", "0", "cashReceivedConfirmed", true);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = executor.submit(() -> first.api().post(school.path("/payments/cash-receptions"), school.adminToken(), body));
            await(() -> count("cash_receptions", school.id()) == 1);
            first.kill();
            assertThatThrownBy(() -> pending.get(10, TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
        }
        Instance restarted = start(false, false);
        assertThat(restarted.api().get("/api/v2/me/students", school.adminToken()).status()).isEqualTo(404);
        TestApi.Reply recovered = restarted.api().get(school.path("/payments/cash-receptions/by-key/" + key), school.adminToken());
        assertThat(recovered.status()).as(recovered.raw()).isEqualTo(200);
        assertThat(recovered.text("unallocatedMinor")).isEqualTo("25000");
        assertThat(restarted.api().post(school.path("/payments/cash-receptions"), school.adminToken(), body).status()).isEqualTo(404);
        assertThat(history()).isEqualTo(installed);
        restarted.kill();
        Instance enabled = start(true, false);
        TestApi.Reply replay = enabled.api().post(school.path("/payments/cash-receptions"), school.adminToken(), body);
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(recovered.body());
        assertThat(count("cash_receptions", school.id())).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id=:id AND action='payments.cash.received'")
                .param("id", school.id()).query(Long.class).single()).isEqualTo(1);
        assertThat(history()).isEqualTo(installed);
        Flyway.configure().dataSource(url, TestDatabases.POSTGRES.getUsername(), TestDatabases.POSTGRES.getPassword())
                .locations("classpath:db/migration").load().validate();
    }

    @Test
    void deuxInstancesPartagentLaMemeUniciteIdempotentePostgresql() throws Exception {
        url = TestDatabases.createEmpty();
        jdbc = TestDatabases.client(url);
        Instance first = start(true, false);
        Instance second = start(true, false);
        TestApi.School school = first.api().newSchool();
        Map<String, Object> body = Map.of("idempotencyKey", UUID.randomUUID(), "amountReceivedMinor", "25000",
                "changeGivenMinor", "0", "cashReceivedConfirmed", true);
        CountDownLatch barrier = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { barrier.await(); return first.api().post(school.path("/payments/cash-receptions"), school.adminToken(), body); });
            var b = executor.submit(() -> { barrier.await(); return second.api().post(school.path("/payments/cash-receptions"), school.adminToken(), body); });
            barrier.countDown();
            var replyA = a.get(15, TimeUnit.SECONDS);
            var replyB = b.get(15, TimeUnit.SECONDS);
            assertThat(List.of(replyA.status(), replyB.status())).containsExactlyInAnyOrder(201, 200);
            assertThat(replyA.body()).isEqualTo(replyB.body());
        }
        assertThat(count("cash_receptions", school.id())).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id=:id AND action='payments.cash.received'")
                .param("id", school.id()).query(Long.class).single()).isEqualTo(1);
    }

    long count(String table, UUID tenant) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE establishment_id=:id")
                .param("id", tenant).query(Long.class).single();
    }

    List<Map<String, Object>> history() {
        return jdbc.sql("SELECT installed_rank,version,script,checksum,installed_on,success FROM flyway_schema_history ORDER BY installed_rank")
                .query().listOfRows();
    }

    Instance start(boolean enabled, boolean drop) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Path log = Path.of("target", "cash-probe", "instance-" + port + ".log");
        Files.createDirectories(log.getParent());
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m", "-XX:TieredStopAtLevel=1", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), CashProbeApplication.class.getName()));
        command.addAll(TestDatabases.applicationArguments(url));
        command.addAll(List.of("--server.port=" + port, "--scolapay.payments.cash-receptions-enabled=" + enabled,
                "--probe.drop-cash-response=" + drop));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        processes.add(process);
        TestApi api = new TestApi(port, jdbc);
        await(() -> {
            if (!process.isAlive()) throw new AssertionError("La sonde s'est arretee : " + log);
            return api.healthy();
        });
        return new Instance(process, api);
    }

    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(50);
        assertThat(condition.getAsBoolean()).as("Etat durable attendu").isTrue();
    }

    record Instance(Process process, TestApi api) {
        void kill() throws Exception {
            process.destroyForcibly();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
