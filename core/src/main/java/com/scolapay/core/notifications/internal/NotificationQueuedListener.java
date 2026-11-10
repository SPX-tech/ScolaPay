package com.scolapay.core.notifications.internal;

import com.scolapay.core.notifications.NotificationQueued;
import java.time.Clock;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class NotificationQueuedListener {
    private final NotificationRepository repository;
    private final Clock clock;

    NotificationQueuedListener(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void on(NotificationQueued event) {
        repository.ready(event.notificationId(), clock.instant());
    }
}
