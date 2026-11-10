package com.scolapay.core.notifications.internal;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Les appels réseau ne doivent pas bloquer le scheduler partagé qui reprend le registre Modulith. */
@Component
class NotificationScheduling {
    private static final Logger log = LoggerFactory.getLogger(NotificationScheduling.class);
    private final NotificationWorker worker;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("notification-delivery").factory());

    NotificationScheduling(NotificationWorker worker) { this.worker = worker; }

    @Scheduled(fixedDelayString = "${scolapay.notifications.poll-interval:PT10S}",
               initialDelayString = "${scolapay.notifications.initial-delay:PT10S}")
    void tick() {
        if (!running.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try { worker.runOnce(); }
            catch (RuntimeException failure) {
                log.error("Notifications : traitement interrompu, bail conservé pour reprise.");
            } finally { running.set(false); }
        });
    }

    @PreDestroy
    void stop() { executor.shutdownNow(); }
}
