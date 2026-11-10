package com.scolapay.core.notifications.internal;

import com.scolapay.core.identity.Identifier;
import com.scolapay.core.notifications.NotificationQueued;
import com.scolapay.core.notifications.NotificationTransport;
import com.scolapay.core.notifications.Notifications;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class NotificationQueue implements Notifications {
    record Payload(String from, String recipient, String subject, String text) {
        @Override public String toString() { return "NotificationPayload[redacted]"; }
    }

    private final NotificationRepository repository;
    private final PayloadCipher cipher;
    private final NotificationTransports transports;
    private final NotificationSettings settings;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;

    NotificationQueue(NotificationRepository repository, PayloadCipher cipher, NotificationTransports transports,
                      NotificationSettings settings, ApplicationEventPublisher events, JsonMapper json, Clock clock) {
        this.repository = repository;
        this.cipher = cipher;
        this.transports = transports;
        this.settings = settings;
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void enqueue(Request request) {
        validate(request);
        NotificationTransport transport = transports.forChannel(request.channel())
                .orElseThrow(() -> new IllegalStateException("NOTIFICATION_CHANNEL_UNAVAILABLE"));
        String clear = json.writeValueAsString(new Payload(settings.from(), request.recipient(), request.subject(), request.text()));
        String fingerprint = cipher.fingerprint(request.category() + "\n" + request.channel() + "\n"
                + transport.name() + "\n" + request.expiresAt() + "\n" + clear);
        boolean inserted = repository.insert(request, transport.name(), fingerprint, cipher.encrypt(request.id(), clear),
                Math.toIntExact(transport.idempotencyWindow().toSeconds()), clock.instant());
        if (inserted) events.publishEvent(new NotificationQueued(request.id()));
    }

    private void validate(Request request) {
        if (request == null || request.id() == null || request.channel() == null || request.category() == null
                || !request.category().matches("[A-Z_]{1,48}") || request.subject() == null || request.subject().isBlank()
                || request.subject().length() > 200 || request.subject().contains("\r") || request.subject().contains("\n")
                || request.text() == null || request.text().isBlank() || request.text().length() > 8192
                || request.expiresAt() == null || !request.expiresAt().isAfter(clock.instant())
                || request.expiresAt().isAfter(clock.instant().plus(Duration.ofDays(7)))
                || settings.from().isBlank() || !cipher.configured()) {
            throw new IllegalArgumentException("NOTIFICATION_REQUEST_INVALID");
        }
        Identifier recipient = Identifier.parse(request.recipient());
        Identifier.Kind expected = request.channel() == Channel.EMAIL ? Identifier.Kind.EMAIL : Identifier.Kind.PHONE;
        if (recipient.kind() != expected || !Objects.equals(recipient.value(), request.recipient())) {
            throw new IllegalArgumentException("NOTIFICATION_RECIPIENT_INVALID");
        }
    }

    @Override public Status status(UUID id) { return repository.status(id); }
}
