package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.scolapay.core.TestApi.School;
import com.scolapay.outboxprobe.ProbeApplication;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Conditions de sortie de la décision 0016. Chaque instance est une vraie JVM sur un vrai PostgreSQL,
 * et l'arrêt brutal est un SIGKILL : aucun code de fermeture ne s'exécute. L'événement est le vrai
 * {@code StudentEnrolled}, publié par la vraie route d'inscription.
 */
class OutboxReliabilityTest {

    /** Reprise en quelques centaines de millisecondes au lieu de plusieurs minutes. */
    private static final List<String> FAST_RECOVERY = List.of(
            "--spring.modulith.events.staleness.published=PT0.5S",
            "--spring.modulith.events.staleness.processing=PT0.5S",
            "--spring.modulith.events.staleness.resubmitted=PT0.5S",
            "--spring.modulith.events.staleness.check-interval=PT0.2S",
            "--scolapay.outbox.recovery-interval=PT0.2S",
            "--scolapay.outbox.retry-delay=PT0.1S",
            "--scolapay.outbox.max-retry-delay=PT0.5S",
            "--scolapay.outbox.overdue-after=PT1S");

    private static final long TIMEOUT_MILLIS = 90_000;

    private final List<Instance> instances = new ArrayList<>();
    private String url;
    private JdbcClient jdbc;

    @BeforeEach
    void newDatabase() {
        url = TestDatabases.createEmpty();
        jdbc = TestDatabases.client(url);
    }

    @AfterEach
    void killInstances() {
        instances.forEach(Instance::kill);
    }

    @Test
    void unArretBrutalEntreLaValidationEtLeTraitementNePerdPasLEvenement() {
        Instance first = start("avant-traitement", List.of("--probe.drop-dispatch=true"));
        School school = first.api().newSchool();
        UUID student = school.newStudent();

        assertThat(school.enroll(student).status()).isEqualTo(201);
        assertThat(statusOfTheOnlyPublication()).isEqualTo("PUBLISHED");
        first.kill();
        assertThat(attempts(student)).isZero();

        start("reprise", FAST_RECOVERY);

        await("l'effet après la reprise", () -> effects(student) == 1 && active() == 0 && archived() == 1);
        assertThat(attempts(student)).isEqualTo(1);
    }

    @Test
    void unArretBrutalPendantLeTraitementNePerdPasLEvenement() {
        Instance first = start("pendant-traitement", List.of());
        School school = first.api().newSchool();
        UUID student = school.newStudent();
        behave(student, "HANG_ONCE");

        assertThat(school.enroll(student).status())
                .as("L'inscription répond sans attendre le listener bloqué : il est bien asynchrone")
                .isEqualTo(201);
        await("le début du traitement", () -> attempts(student) == 1);
        assertThat(statusOfTheOnlyPublication()).isEqualTo("PROCESSING");
        first.kill();
        assertThat(effects(student)).isZero();

        start("reprise", FAST_RECOVERY);

        await("l'effet après la reprise", () -> effects(student) == 1 && active() == 0 && archived() == 1);
        assertThat(attempts(student)).isEqualTo(2);
    }

    @Test
    void unArretBrutalApresLEffetNeLeProduitPasDeuxFois() {
        Instance first = start("apres-effet", List.of());
        School school = first.api().newSchool();
        UUID student = school.newStudent();
        behave(student, "EFFECT_THEN_HANG_ONCE");

        assertThat(school.enroll(student).status()).isEqualTo(201);
        await("l'effet validé avant l'arrêt", () -> effects(student) == 1);
        first.kill();
        assertThat(active()).as("La publication n'est pas encore marquée comme traitée").isEqualTo(1);

        start("reprise", FAST_RECOVERY);

        await("la fin de la reprise", () -> active() == 0 && archived() == 1);
        assertThat(attempts(student)).as("Le listener a bien été rejoué").isEqualTo(2);
        assertThat(effects(student)).isEqualTo(1);
    }

    @Test
    void unListenerEnEchecEstReprisSansDoublon() {
        Instance instance = start("echec", FAST_RECOVERY);
        School school = instance.api().newSchool();
        UUID student = school.newStudent();
        behave(student, "FAIL_ONCE");

        assertThat(school.enroll(student).status()).isEqualTo(201);

        await("la reprise de l'échec", () -> effects(student) == 1 && active() == 0 && archived() == 1);
        assertThat(attempts(student)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void uneTransactionAnnuleeNePublieRien() {
        Instance instance = start("annulation", List.of());
        School school = instance.api().newSchool();
        UUID refused = school.newStudent();
        UUID control = school.newStudent();
        behave(refused, "FAIL_AT_COMMIT");

        assertThat(school.enroll(refused).status()).isEqualTo(500);
        assertThat(jdbc.sql("SELECT count(*) FROM enrollments WHERE student_record_id = :student")
                        .param("student", refused)
                        .query(Long.class)
                        .single())
                .as("L'inscription elle-même est annulée")
                .isZero();
        assertThat(active() + archived()).isZero();

        assertThat(school.enroll(control).status()).isEqualTo(201);
        await("le témoin, preuve que les listeners tournent", () -> effects(control) == 1 && archived() == 1);
        assertThat(attempts(refused)).isZero();
        assertThat(effects(refused)).isZero();
        assertThat(active()).isZero();
    }

    /**
     * Le traitement dure plus longtemps que le délai au bout duquel une publication est jugée bloquée :
     * l'autre instance la reprend donc pendant qu'elle est encore en cours. C'est le pire cas, et le
     * registre ne l'empêche pas : seule la contrainte d'unicité du métier protège le résultat.
     */
    @Test
    void deuxInstancesSimultaneesNeProduisentAucunEffetEnDouble() {
        Instance first = start("instance-a", FAST_RECOVERY);
        start("instance-b", FAST_RECOVERY);
        School school = first.api().newSchool();
        List<UUID> students = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID student = school.newStudent();
            behave(student, "SLOW");
            students.add(student);
        }

        for (UUID student : students) {
            assertThat(school.enroll(student).status()).isEqualTo(201);
        }

        await("le traitement de toutes les publications", () -> active() == 0 && archived() == students.size());
        assertThat(jdbc.sql("SELECT count(*) FROM probe_effects").query(Long.class).single()).isEqualTo(students.size());
        assertThat(jdbc.sql("SELECT count(*) FROM probe_attempts").query(Long.class).single())
                .as("Des exécutions en double ont bien eu lieu, sinon ce test ne prouve rien")
                .isGreaterThan(students.size());
        assertThat(jdbc.sql("SELECT count(DISTINCT instance) FROM probe_attempts").query(Long.class).single())
                .as("Les deux instances ont bien travaillé")
                .isEqualTo(2);
    }

    @Test
    void unEvenementIrrecuperableResteVisibleEtNeBloquePasLesAutres() {
        Instance instance = start("irrecuperable", FAST_RECOVERY);
        School school = instance.api().newSchool();
        UUID poison = school.newStudent();
        UUID healthy = school.newStudent();
        behave(poison, "ALWAYS_FAIL");
        behave(healthy, "FAIL_ONCE");

        assertThat(school.enroll(poison).status()).isEqualTo(201);
        assertThat(school.enroll(healthy).status()).isEqualTo(201);

        await("la reprise de l'événement sain", () -> effects(healthy) == 1 && archived() == 1);
        await("plusieurs essais sur l'événement irrécupérable", () -> attempts(poison) >= 3);
        assertThat(effects(poison)).isZero();
        assertThat(active()).as("Il n'est jamais abandonné").isEqualTo(1);
        await("l'alerte", () -> instance.log().contains("Outbox : 1 publication(s) non traitée(s)"));
    }

    /**
     * Limite connue du registre, consignée dans la décision 0016 : la taille de lot s'applique avant le
     * filtre, donc des événements plus anciens qui attendent leur délai de reprise occupent tout le lot.
     * Rien n'est perdu et l'alerte se déclenche. Si ce comportement change, ce test le signalera.
     */
    @Test
    void auDelaDeLaTailleDeLotLaRepriseEstBloqueeMaisSignalee() throws InterruptedException {
        List<String> smallBatch = new ArrayList<>(FAST_RECOVERY);
        smallBatch.add("--scolapay.outbox.batch-size=2");
        Instance instance = start("lot-sature", smallBatch);
        School school = instance.api().newSchool();
        for (int i = 0; i < 2; i++) {
            jdbc.sql("""
                    INSERT INTO event_publication
                      (id, listener_id, event_type, serialized_event, publication_date, status, completion_attempts, last_resubmission_date)
                    VALUES
                      (:id, 'listener.en.attente', 'com.scolapay.core.schooling.StudentEnrolled', '{}', now() - interval '1 hour', 'FAILED', 9, now() + interval '1 day')
                    """)
                    .param("id", UUID.randomUUID())
                    .update();
        }
        UUID blocked = school.newStudent();
        behave(blocked, "FAIL_ONCE");
        assertThat(school.enroll(blocked).status()).isEqualTo(201);

        await("le premier essai de l'événement bloqué", () -> attempts(blocked) == 1);
        Thread.sleep(5_000);

        assertThat(attempts(blocked)).isEqualTo(1);
        assertThat(effects(blocked)).isZero();
        assertThat(active()).as("Rien n'est perdu").isEqualTo(3);
        await("l'alerte", () -> instance.log().contains("Outbox : 3 publication(s) non traitée(s)"));
    }

    /** Un service en panne ne doit pas être sollicité à chaque passage de la reprise. */
    @Test
    void leDelaiDeRepriseEspaceLesEssais() throws InterruptedException {
        // Une option répétée serait fusionnée par Spring en une valeur invalide : on la remplace.
        List<String> longDelay = new ArrayList<>(FAST_RECOVERY);
        longDelay.removeIf(argument -> argument.contains("retry-delay"));
        longDelay.add("--scolapay.outbox.retry-delay=PT1H");
        longDelay.add("--scolapay.outbox.max-retry-delay=PT1H");
        Instance instance = start("delai", longDelay);
        School school = instance.api().newSchool();
        UUID student = school.newStudent();
        behave(student, "FAIL_ONCE");

        assertThat(school.enroll(student).status()).isEqualTo(201);
        await("le premier essai", () -> attempts(student) == 1);
        Thread.sleep(3_000);

        assertThat(attempts(student)).isEqualTo(1);
        assertThat(statusOfTheOnlyPublication()).isEqualTo("FAILED");
    }

    /** L'API du registre ignore cette ligne : seule la supervision en SQL peut la signaler. */
    @Test
    void unTypeDEvenementDisparuEstSignaleSansBloquerLaReprise() {
        Instance instance = start("type-disparu", FAST_RECOVERY);
        jdbc.sql("""
                INSERT INTO event_publication
                  (id, listener_id, event_type, serialized_event, publication_date, status, completion_attempts, last_resubmission_date)
                VALUES
                  (:id, 'listener.disparu', 'com.scolapay.core.EvenementRenomme', '{}', now() - interval '1 hour', 'FAILED', 1, now() - interval '1 hour')
                """)
                .param("id", UUID.randomUUID())
                .update();
        School school = instance.api().newSchool();
        UUID student = school.newStudent();
        behave(student, "FAIL_ONCE");

        assertThat(school.enroll(student).status()).isEqualTo(201);

        await("la reprise de l'événement connu", () -> effects(student) == 1 && archived() == 1);
        assertThat(active()).as("La ligne reste en place").isEqualTo(1);
        await("l'alerte", () -> instance.log().contains("Outbox : 1 publication(s) non traitée(s)"));
    }

    private void behave(UUID student, String behavior) {
        jdbc.sql("INSERT INTO probe_behaviors (student_id, behavior) VALUES (:student, :behavior)")
                .param("student", student)
                .param("behavior", behavior)
                .update();
    }

    private String statusOfTheOnlyPublication() {
        return jdbc.sql("SELECT status FROM event_publication").query(String.class).single();
    }

    private long active() {
        return jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single();
    }

    private long archived() {
        return jdbc.sql("SELECT count(*) FROM event_publication_archive").query(Long.class).single();
    }

    private long attempts(UUID student) {
        return countFor("probe_attempts", student);
    }

    private long effects(UUID student) {
        return countFor("probe_effects", student);
    }

    private long countFor(String table, UUID student) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE student_id = :student")
                .param("student", student)
                .query(Long.class)
                .single();
    }

    private void await(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        StringBuilder logs = new StringBuilder();
        instances.forEach(instance -> logs.append("\n--- ").append(instance.name).append(" ---\n").append(instance.logTail()));
        throw new AssertionError("Attendu sans succès : " + what + logs);
    }

    private Instance start(String name, List<String> extraArguments) {
        try {
            int port = freePort();
            Path log = Path.of("target", "outbox-probe", name + "-" + port + ".log");
            Files.createDirectories(log.getParent());

            List<String> command = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Xmx300m",
                    "-XX:TieredStopAtLevel=1",
                    "-cp",
                    System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    ProbeApplication.class.getName()));
            command.addAll(TestDatabases.applicationArguments(url));
            command.add("--server.port=" + port);
            command.add("--probe.instance=" + name);
            command.add("--spring.flyway.locations=classpath:db/migration,classpath:db/probe-migration");
            command.addAll(extraArguments);

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            Instance instance = new Instance(name, process, new TestApi(port), log);
            instances.add(instance);
            await(name + " prête", () -> {
                if (!process.isAlive()) {
                    throw new AssertionError(name + " s'est arrêtée au démarrage :\n" + instance.logTail());
                }
                return instance.api().healthy();
            });
            return instance;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record Instance(String name, Process process, TestApi api, Path logFile) {

        /** Sur Linux, {@code destroyForcibly} envoie SIGKILL. */
        void kill() {
            process.destroyForcibly();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        String log() {
            try {
                return Files.readString(logFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        String logTail() {
            String all = log();
            return all.substring(Math.max(0, all.length() - 4_000));
        }
    }
}
