package com.scolapay.core.notifications.internal;

import com.scolapay.core.identity.InvitationDispatchState;
import com.scolapay.core.notifications.NotificationTransport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Le bail protège la concurrence ordinaire, l'idempotence du transport protège les reprises ambiguës. */
@Component
public class NotificationWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
    private static final int MAX_ATTEMPTS = 8;
    private final NotificationRepository repository;
    private final NotificationTransports transports;
    private final PayloadCipher cipher;
    private final InvitationDispatchState invitations;
    private final JsonMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;

    NotificationWorker(NotificationRepository repository, NotificationTransports transports, PayloadCipher cipher,
                       InvitationDispatchState invitations, JsonMapper json, Clock clock, PlatformTransactionManager manager) {
        this.repository = repository;
        this.transports = transports;
        this.cipher = cipher;
        this.invitations = invitations;
        this.json = json;
        this.clock = clock;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void runOnce() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("NOTIFICATION_WORKER_REQUIRES_NO_TRANSACTION");
        }
        tx.executeWithoutResult(status -> repository.expire(clock.instant()));
        for (int count = 0; count < 25; count++) {
            Optional<NotificationRepository.Claim> claimed = tx.execute(status -> repository.claim(clock.instant()));
            if (claimed == null || claimed.isEmpty()) break;
            process(claimed.get());
        }
    }

    private void process(NotificationRepository.Claim claim) {
        Instant now = clock.instant();
        if (!claim.expiresAt().isAfter(now)
                || (claim.category().equals("INVITATION") && !invitations.isDeliverable(claim.id()))) {
            finish(claim, "CANCELLED", "INVITATION_UNUSABLE", null, now);
            return;
        }
        Optional<NotificationTransport> selected = transports.named(claim.transport());
        if (selected.isEmpty()) {
            finish(claim, "BLOCKED", "CHANNEL_UNAVAILABLE", null, now.plusSeconds(300));
            return;
        }
        // 60 secondes de marge : ne jamais commencer un appel à la fin de la fenêtre contractuelle.
        long window = Math.min(claim.retryWindowSeconds(), selected.get().idempotencyWindow().toSeconds());
        Instant lastSafeStart = claim.firstAttemptAt().plusSeconds(window).minusSeconds(60);
        if (claim.attempts() > MAX_ATTEMPTS || (window == 0 && claim.attempts() > 1)
                || (window > 0 && !lastSafeStart.isAfter(now))) {
            finish(claim, "UNKNOWN", "RETRY_SAFETY_LIMIT", null, now);
            return;
        }

        NotificationQueue.Payload payload;
        try {
            payload = json.readValue(cipher.decrypt(claim.id(), claim.encryptedPayload()), NotificationQueue.Payload.class);
        } catch (RuntimeException unreadable) {
            finish(claim, "BLOCKED", "PAYLOAD_UNREADABLE", null, now.plusSeconds(300));
            return;
        }
        NotificationTransport.Result result;
        try {
            result = selected.get().send(new NotificationTransport.Message("notification/" + claim.id(), payload.from(),
                    payload.recipient(), payload.subject(), payload.text()));
        } catch (RuntimeException failure) {
            // Le message ou la cause du fournisseur peut contenir le destinataire ou le jeton.
            result = NotificationTransport.Result.retry("TRANSPORT_UNAVAILABLE");
        }
        now = clock.instant();
        if (result.outcome() == NotificationTransport.Outcome.SUBMITTED) {
            try {
                finish(claim, "SUBMITTED", null, UUID.fromString(result.providerId()), now);
            } catch (IllegalArgumentException invalidReceipt) {
                finish(claim, "UNKNOWN", "PROVIDER_RECEIPT_INVALID", null, now);
            }
        } else if (result.outcome() == NotificationTransport.Outcome.FAILED) {
            finish(claim, "FAILED", safeCode(result.failureCode()), null, now);
        } else if (window == 0 || claim.attempts() >= MAX_ATTEMPTS) {
            finish(claim, "UNKNOWN", "RETRY_SAFETY_LIMIT", null, now);
        } else {
            Duration backoff = Duration.ofMinutes(Math.min(60, 1L << Math.min(claim.attempts() - 1, 6)));
            finish(claim, "RETRY", safeCode(result.failureCode()), null, now.plus(backoff));
        }
    }

    private static String safeCode(String code) {
        return java.util.Set.of("TRANSPORT_UNAVAILABLE", "TRANSPORT_REJECTED", "TRANSPORT_RATE_LIMITED",
                "TRANSPORT_PROTOCOL_ERROR", "IDEMPOTENCY_CONFLICT").contains(code == null ? "" : code)
                ? code : "TRANSPORT_UNAVAILABLE";
    }

    private void finish(NotificationRepository.Claim claim, String state, String code, UUID receipt, Instant next) {
        tx.executeWithoutResult(status -> repository.finish(claim, state, code, receipt, next, clock.instant()));
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void reportAttention() {
        long count = repository.attentionCount(clock.instant());
        if (count > 0) log.warn("Notifications : {} remise(s) à examiner.", count);
    }
}
