package com.scolapay.core.notifications;

import java.time.Instant;
import java.util.UUID;

/** Intention durable. L'identifiant appartient à l'intention et reste stable à chaque reprise. */
public interface Notifications {
    enum Channel { EMAIL, SMS }

    record Request(UUID id, String category, Channel channel, String recipient,
                   String subject, String text, Instant expiresAt) {
        @Override public String toString() { return "NotificationRequest[id=" + id + "]"; }
    }

    record Status(String state, int attempts, Instant updatedAt, String failureCode) {}

    void enqueue(Request request);
    Status status(UUID id);
}
