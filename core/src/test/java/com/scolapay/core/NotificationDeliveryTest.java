package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scolapay.core.identity.Identifier;
import com.scolapay.core.identity.InvitationDelivery;
import com.scolapay.core.identity.Invitations;
import com.scolapay.core.notifications.NotificationTransport;
import com.scolapay.core.notifications.Notifications;
import com.scolapay.core.notifications.internal.NotificationWorker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Vraie file PostgreSQL, vrai registre Modulith, transport contrôlé sans réseau sortant. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(NotificationDeliveryTest.TransportConfiguration.class)
class NotificationDeliveryTest {

    private static final String DATABASE_URL = TestDatabases.createEmpty();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
        registry.add("spring.datasource.username", TestDatabases.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestDatabases.POSTGRES::getPassword);
        registry.add("scolapay.security.jwt-secret", () -> IntegrationTest.JWT_SECRET);
        registry.add("scolapay.bootstrap.operator-contact", () -> IntegrationTest.OPERATOR_CONTACT);
        registry.add("scolapay.bootstrap.operator-password", () -> IntegrationTest.OPERATOR_PASSWORD);
        registry.add("scolapay.notifications.encryption-key", () -> "cXVldWUtdGVzdC1rZXktMzItYnl0ZXMtb25seS0wMDE=");
        registry.add("scolapay.notifications.email.from", () -> "invitations@scolapay.test");
        registry.add("scolapay.notifications.poll-interval", () -> "PT24H");
        registry.add("scolapay.notifications.initial-delay", () -> "PT24H");
    }

    @Autowired Environment environment;
    @Autowired JdbcClient jdbc;
    @Autowired NotificationWorker worker;
    @Autowired Notifications notifications;
    @Autowired InvitationDelivery delivery;
    @Autowired Invitations invitations;
    @Autowired ScriptedTransport transport;
    @Autowired PlatformTransactionManager transactions;
    TestApi api;

    @BeforeEach
    void prepare() {
        api = new TestApi(environment.getRequiredProperty("local.server.port", Integer.class));
        jdbc.sql("DELETE FROM notification_messages").update();
        transport.result.set(NotificationTransport.Result.submitted(UUID.randomUUID().toString()));
        transport.sent.clear();
        transport.script.set(null);
    }

    record Issued(UUID id, UUID school, String contact) {}

    Issued issue() {
        String contact = IntegrationTest.uniqueEmail();
        TestApi.Reply created = api.post("/api/v2/platform/establishments",
                api.login(IntegrationTest.OPERATOR_CONTACT, IntegrationTest.OPERATOR_PASSWORD), Map.of(
                        "code", IntegrationTest.uniqueCode(), "name", "École de notification", "kind", "SCHOOL",
                        "timezone", "Africa/Ndjamena", "currency", "XAF", "countryCode", "TD", "firstAdminContact", contact));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        UUID id = UUID.fromString(created.body().path("firstAdminInvitation").path("id").asString());
        awaitReady(id);
        return new Issued(id, UUID.fromString(created.body().path("establishment").path("id").asString()), contact);
    }

    @Test
    void transmetAuVraiDestinataireHorsTransactionEtPurgeLaCharge() {
        Issued invitation = issue();
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("SUBMITTED");
        assertThat(transport.sent).hasSize(1);
        assertThat(transport.sent.getFirst().recipient()).isEqualTo(invitation.contact());
        assertThat(transport.sent.getFirst().text()).contains("ScolaPay", "Jeton : ");
        assertThat(transport.sent.getFirst().idempotencyKey()).isEqualTo("notification/" + invitation.id());
        assertThat(payload(invitation.id())).isNull();
        String token = transport.sent.getFirst().text().split("Jeton : ")[1].split("\\s")[0];
        assertThat(api.accept(token).status()).isEqualTo(200);
        assertThat(api.login(invitation.contact(), IntegrationTest.PASSWORD)).isNotBlank();
    }

    @Test
    void unEchecTemporaireEstReprisAvecLeMemeCorpsEtLaMemeCle() {
        Issued invitation = issue();
        transport.result.set(NotificationTransport.Result.retry("TRANSPORT_UNAVAILABLE"));
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("RETRY");
        assertThat(payload(invitation.id())).isNotBlank();
        worker.runOnce();
        assertThat(transport.sent).hasSize(1);
        transport.result.set(NotificationTransport.Result.submitted(UUID.randomUUID().toString()));
        due(invitation.id());
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("SUBMITTED");
        assertThat(transport.sent).hasSize(2);
        assertThat(transport.sent.get(1)).isEqualTo(transport.sent.getFirst());
    }

    @Test
    void unBailAbandonneEstRepris() {
        Issued invitation = issue();
        jdbc.sql("UPDATE notification_messages SET state='PROCESSING', attempts=1, lease_token=:lease, lease_until=now()-interval '1 minute', first_attempt_at=now()-interval '2 minutes' WHERE id=:id")
                .param("lease", UUID.randomUUID()).param("id", invitation.id()).update();
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("SUBMITTED");
        assertThat(transport.sent).hasSize(1);
    }

    @Test
    void deuxWorkersNeReclamentPasLaMemeNotification() throws Exception {
        Issued invitation = issue();
        IntegrationTest.concurrently(2, () -> {
            worker.runOnce();
            return new IntegrationTest.Response(200, null, "");
        });
        assertThat(state(invitation.id())).isEqualTo("SUBMITTED");
        assertThat(transport.sent).hasSize(1);
        worker.runOnce();
        assertThat(transport.sent).hasSize(1);
    }

    @Test
    void unAncienProprietaireNePeutEcraserLeResultatDeLaReprise() throws Exception {
        Issued invitation = issue();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        transport.script.set(message -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                try { assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
                return NotificationTransport.Result.failed("TRANSPORT_REJECTED");
            }
            return NotificationTransport.Result.submitted(UUID.randomUUID().toString());
        });
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var first = executor.submit(worker::runOnce);
            try {
                assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                jdbc.sql("UPDATE notification_messages SET lease_until=now()-interval '1 second' WHERE id=:id")
                        .param("id", invitation.id()).update();
                worker.runOnce();
                assertThat(state(invitation.id())).isEqualTo("SUBMITTED");
            } finally { release.countDown(); }
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(state(invitation.id())).isEqualTo("SUBMITTED");
        assertThat(transport.sent).hasSize(2);
    }

    @Test
    void uneRepriseApresLaFenetreFournisseurResteIncertaineSansRenvoyer() {
        Issued invitation = issue();
        jdbc.sql("UPDATE notification_messages SET state='PROCESSING', attempts=1, lease_token=:lease, lease_until=now()-interval '1 hour', first_attempt_at=now()-interval '24 hours' WHERE id=:id")
                .param("lease", UUID.randomUUID()).param("id", invitation.id()).update();
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("UNKNOWN");
        assertThat(payload(invitation.id())).isNull();
        assertThat(transport.sent).isEmpty();
    }

    @Test
    void uneInvitationRevoqueeNestPasEnvoyee() {
        Issued invitation = issue();
        jdbc.sql("UPDATE invitations SET revoked_at=now() WHERE id=:id").param("id", invitation.id()).update();
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("CANCELLED");
        assertThat(payload(invitation.id())).isNull();
        assertThat(transport.sent).isEmpty();
    }

    @Test
    void uneInvitationExpireeNestPasEnvoyee() {
        Issued invitation = issue();
        jdbc.sql("UPDATE invitations SET expires_at=now()-interval '1 second' WHERE id=:id").param("id", invitation.id()).update();
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("CANCELLED");
        assertThat(transport.sent).isEmpty();
    }

    @Test
    void uneInvitationDejaAccepteeNestPasEnvoyee() {
        Issued invitation = issue();
        jdbc.sql("UPDATE invitations SET accepted_at=now() WHERE id=:id").param("id", invitation.id()).update();
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("CANCELLED");
        assertThat(transport.sent).isEmpty();
    }

    @Test
    void unEchecPermanentEstTerminalEtPurge() {
        Issued invitation = issue();
        transport.result.set(NotificationTransport.Result.failed("TRANSPORT_REJECTED"));
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("FAILED");
        assertThat(payload(invitation.id())).isNull();
        worker.runOnce();
        assertThat(transport.sent).hasSize(1);
    }

    @Test
    void unTelephoneSansSmsEstRefuseSansCreerDEtablissementNiInvitation() {
        String code = IntegrationTest.uniqueCode();
        TestApi.Reply refused = api.post("/api/v2/platform/establishments",
                api.login(IntegrationTest.OPERATOR_CONTACT, IntegrationTest.OPERATOR_PASSWORD), Map.of(
                        "code", code, "name", "École sans SMS", "kind", "SCHOOL", "timezone", "Africa/Ndjamena",
                        "currency", "XAF", "countryCode", "TD", "firstAdminContact", "+23566123456"));
        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("INVITATION_CHANNEL_UNAVAILABLE");
        assertThat(jdbc.sql("SELECT count(*) FROM establishments WHERE code=:code").param("code", code).query(Long.class).single()).isZero();
    }

    @Test
    void uneTransactionAnnuleeNeLaisseNiChargeNiEvenement() {
        UUID id = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            notifications.enqueue(new Notifications.Request(id, "GENERAL", Notifications.Channel.EMAIL,
                    "destinataire@scolapay.test", "Sujet", "Contenu", Instant.now().plusSeconds(300)));
            status.setRollbackOnly();
        });
        assertThat(jdbc.sql("SELECT count(*) FROM notification_messages WHERE id=:id").param("id", id).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM event_publication WHERE serialized_event LIKE :id").param("id", "%" + id + "%").query(Long.class).single()).isZero();
    }

    @Test
    void invitationAuditChargeEtPublicationSontAnnulesEnsemble() {
        Issued existing = issue();
        UUID operator = jdbc.sql("SELECT account_id FROM account_identifiers WHERE kind='EMAIL' AND value=:value")
                .param("value", IntegrationTest.OPERATOR_CONTACT).query(UUID.class).single();
        AtomicReference<UUID> id = new AtomicReference<>();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            id.set(invitations.issue(new Invitations.NewInvitation(Identifier.parse(IntegrationTest.uniqueEmail()),
                    "establishment.staff", existing.school(), null, Map.of("role", "CASHIER"), operator)).id());
            status.setRollbackOnly();
        });
        for (String table : List.of("invitations", "notification_messages")) {
            assertThat(jdbc.sql("SELECT count(*) FROM " + table + " WHERE id=:id").param("id", id.get())
                    .query(Long.class).single()).isZero();
        }
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE target_id=:id").param("id", id.get()).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM event_publication WHERE serialized_event LIKE :id")
                .param("id", "%" + id.get() + "%").query(Long.class).single()).isZero();
    }

    @Test
    void unCanalRetireConserveLaChargeEtReprendApresRestauration() {
        Issued invitation = issue();
        jdbc.sql("UPDATE notification_messages SET transport='absent' WHERE id=:id").param("id", invitation.id()).update();
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("BLOCKED");
        assertThat(payload(invitation.id())).isNotNull();
        assertThat(transport.sent).isEmpty();
        jdbc.sql("UPDATE notification_messages SET transport='scripted', next_attempt_at=now()-interval '1 second' WHERE id=:id")
                .param("id", invitation.id()).update();
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("SUBMITTED");
    }

    @Test
    void uneChargeAltereeNestJamaisTransmise() {
        Issued invitation = issue();
        jdbc.sql("UPDATE notification_messages SET encrypted_payload='v1:AAAA' WHERE id=:id").param("id", invitation.id()).update();
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("BLOCKED");
        assertThat(payload(invitation.id())).isNotNull();
        assertThat(transport.sent).isEmpty();
    }

    @Test
    void sansDeduplicationUnePanneAmbigueNestPasRejouee() {
        Issued invitation = issue();
        jdbc.sql("UPDATE notification_messages SET retry_window_seconds=0 WHERE id=:id").param("id", invitation.id()).update();
        transport.result.set(NotificationTransport.Result.retry("TRANSPORT_UNAVAILABLE"));
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("UNKNOWN");
        assertThat(transport.sent).hasSize(1);
        worker.runOnce();
        assertThat(transport.sent).hasSize(1);
    }

    @Test
    void huitReservationsEpuisentLesReprisesAutomatiques() {
        Issued invitation = issue();
        jdbc.sql("UPDATE notification_messages SET attempts=7, first_attempt_at=now()-interval '1 minute' WHERE id=:id")
                .param("id", invitation.id()).update();
        transport.result.set(NotificationTransport.Result.retry("TRANSPORT_UNAVAILABLE"));
        worker.runOnce();
        assertThat(state(invitation.id())).isEqualTo("UNKNOWN");
        assertThat(payload(invitation.id())).isNull();
        worker.runOnce();
        assertThat(transport.sent).hasSize(1);
    }

    @Test
    void laFileEtLesEvenementsNeContiennentPasDeJetonOuContactEnClair() {
        Issued invitation = issue();
        String encrypted = payload(invitation.id());
        assertThat(encrypted).doesNotContain(invitation.contact(), "Jeton", "ScolaPay");
        worker.runOnce();
        String token = transport.sent.getFirst().text().split("Jeton : ")[1].split("\\s")[0];
        assertThat(encrypted).doesNotContain(token);
        for (String table : List.of("event_publication", "event_publication_archive")) {
            assertThat(jdbc.sql("SELECT serialized_event FROM " + table).query(String.class).list())
                    .allSatisfy(event -> assertThat(event).doesNotContain(token, invitation.contact()));
        }
    }

    @Test
    void uneMemeIntentionNEstPasEmpileeDeuxFois() {
        UUID id = UUID.randomUUID();
        Notifications.Request request = new Notifications.Request(id, "GENERAL", Notifications.Channel.EMAIL,
                "destinataire@scolapay.test", "Sujet", "Contenu", Instant.now().plusSeconds(300));
        notifications.enqueue(request);
        notifications.enqueue(request);
        assertThat(jdbc.sql("SELECT count(*) FROM notification_messages WHERE id=:id").param("id", id).query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> notifications.enqueue(new Notifications.Request(id, "GENERAL", Notifications.Channel.EMAIL,
                "autre@scolapay.test", "Sujet", "Autre", request.expiresAt())))
                .isInstanceOf(IllegalStateException.class).hasMessage("NOTIFICATION_INTENT_CONFLICT");
    }

    void due(UUID id) {
        jdbc.sql("UPDATE notification_messages SET next_attempt_at=now()-interval '1 second' WHERE id=:id").param("id", id).update();
    }

    String state(UUID id) {
        return jdbc.sql("SELECT state FROM notification_messages WHERE id=:id").param("id", id).query(String.class).single();
    }

    String payload(UUID id) {
        return jdbc.sql("SELECT encrypted_payload FROM notification_messages WHERE id=:id").param("id", id).query((rs, row) -> rs.getString(1)).list().getFirst();
    }

    void awaitReady(UUID id) {
        Instant deadline = Instant.now().plusSeconds(10);
        while (!state(id).equals("READY") && Instant.now().isBefore(deadline)) {
            java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        assertThat(state(id)).isEqualTo("READY");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransportConfiguration {
        @Bean ScriptedTransport scriptedTransport() { return new ScriptedTransport(); }
    }

    static class ScriptedTransport implements NotificationTransport {
        final List<Message> sent = new CopyOnWriteArrayList<>();
        final AtomicReference<Result> result = new AtomicReference<>();
        final AtomicReference<java.util.function.Function<Message, Result>> script = new AtomicReference<>();
        @Override public String name() { return "scripted"; }
        @Override public boolean supports(Notifications.Channel channel) { return channel == Notifications.Channel.EMAIL; }
        @Override public Duration idempotencyWindow() { return Duration.ofHours(23); }
        @Override public Result send(Message message) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            sent.add(message);
            return script.get() == null ? result.get() : script.get().apply(message);
        }
    }
}
