package com.scolapay.core.identity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Émission d'invitations à usage unique. Le jeton n'est disponible qu'au moment de l'émission. */
public interface Invitations {

    record NewInvitation(
            Identifier contact,
            String purpose,
            UUID establishmentId,
            UUID subjectId,
            Map<String, String> attributes,
            UUID issuedBy) {}

    record Issued(UUID id, String token, Instant expiresAt) {}

    Issued issue(NewInvitation invitation);

    void revoke(UUID establishmentId, UUID invitationId, UUID actorAccountId);
}
