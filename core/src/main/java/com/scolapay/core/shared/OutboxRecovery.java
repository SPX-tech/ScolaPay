package com.scolapay.core.shared;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reprend les publications en échec. Une obligation financière ne peut pas être abandonnée : il n'y
 * a pas de nombre maximal d'essais, seulement un délai croissant et une alerte. {@code @EnableScheduling}
 * active aussi le moniteur de Spring Modulith, sans lequel une publication interrompue par un arrêt
 * brutal resterait en attente sans jamais être reprise.
 */
@Component
@EnableAsync
@EnableScheduling
class OutboxRecovery {

    private static final Logger log = LoggerFactory.getLogger(OutboxRecovery.class);
    private static final int MAX_DOUBLINGS = 20;

    private final FailedEventPublications failed;
    private final OutboxProperties properties;
    private final JdbcClient jdbc;
    private final Clock clock;

    OutboxRecovery(FailedEventPublications failed, OutboxProperties properties, JdbcClient jdbc, Clock clock) {
        this.failed = failed;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${scolapay.outbox.recovery-interval}")
    void recover() {
        failed.resubmit(ResubmissionOptions.defaults()
                .withBatchSize(properties.batchSize())
                .withFilter(this::dueForRetry));
        reportOverdue();
    }

    private boolean dueForRetry(EventPublication publication) {
        Instant lastAttempt = publication.getLastResubmissionDate();
        if (lastAttempt == null) {
            return true;
        }
        Duration delay = retryDelay(publication.getCompletionAttempts(), properties.retryDelay(), properties.maxRetryDelay());
        return !lastAttempt.plus(delay).isAfter(clock.instant());
    }

    static Duration retryDelay(int attempts, Duration base, Duration max) {
        int doublings = Math.clamp(attempts - 1, 0, MAX_DOUBLINGS);
        Duration delay = base.multipliedBy(1L << doublings);
        return delay.compareTo(max) > 0 ? max : delay;
    }

    /**
     * Compte en SQL plutôt que par l'API du registre : celle-ci ignore en silence une ligne dont le
     * type d'événement n'existe plus, or c'est précisément une ligne à signaler.
     */
    private void reportOverdue() {
        long overdue = jdbc.sql("SELECT count(*) FROM event_publication WHERE publication_date < :limit")
                .param("limit", Columns.timestamp(clock.instant().minus(properties.overdueAfter())))
                .query(Long.class)
                .single();
        if (overdue > 0) {
            log.warn("Outbox : {} publication(s) non traitée(s) depuis plus de {}.", overdue, properties.overdueAfter());
        }
    }
}
