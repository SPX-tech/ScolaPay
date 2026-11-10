package com.scolapay.core.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Remise durable au destinataire. deliver rejoint la transaction d'émission et n'appelle pas le réseau.
 */
public interface InvitationDelivery {

    record Report(String state, int attempts, Instant updatedAt, String failureCode) {}

    boolean supports(Identifier.Kind kind);

    void deliver(UUID invitationId, Identifier contact, String token, Instant expiresAt);

    Report status(UUID invitationId);
}
