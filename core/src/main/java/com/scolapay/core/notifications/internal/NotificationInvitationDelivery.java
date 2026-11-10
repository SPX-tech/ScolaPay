package com.scolapay.core.notifications.internal;

import com.scolapay.core.identity.Identifier;
import com.scolapay.core.identity.InvitationDelivery;
import com.scolapay.core.notifications.Notifications;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class NotificationInvitationDelivery implements InvitationDelivery {
    private final Notifications queue;
    private final NotificationTransports transports;
    private final PayloadCipher cipher;
    private final NotificationSettings settings;

    NotificationInvitationDelivery(Notifications queue, NotificationTransports transports, PayloadCipher cipher,
                                   NotificationSettings settings) {
        this.queue = queue;
        this.transports = transports;
        this.cipher = cipher;
        this.settings = settings;
    }

    @Override public boolean supports(Identifier.Kind kind) {
        return cipher.configured() && !settings.from().isBlank() && transports.forChannel(channel(kind)).isPresent();
    }

    @Override
    public void deliver(UUID invitationId, Identifier contact, String token, Instant expiresAt) {
        queue.enqueue(new Notifications.Request(invitationId, "INVITATION", channel(contact.kind()), contact.value(),
                "Votre invitation ScolaPay",
                "Vous avez reçu une invitation ScolaPay.\n\nJeton : " + token
                        + "\n\nValable jusqu'au " + expiresAt + ".\n"
                        + "Utilisez ce jeton dans le parcours d'acceptation ScolaPay. "
                        + "Si vous n'attendiez pas cette invitation, ignorez ce message.",
                expiresAt));
    }

    @Override public Report status(UUID id) {
        Notifications.Status status = queue.status(id);
        return new Report(status.state(), status.attempts(), status.updatedAt(), status.failureCode());
    }

    private static Notifications.Channel channel(Identifier.Kind kind) {
        return kind == Identifier.Kind.EMAIL ? Notifications.Channel.EMAIL : Notifications.Channel.SMS;
    }
}
