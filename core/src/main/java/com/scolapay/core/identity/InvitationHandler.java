package com.scolapay.core.identity;

import java.util.Map;
import java.util.UUID;

/**
 * Applique l'effet d'une invitation acceptée, dans la transaction de l'acceptation :
 * si le handler échoue, l'invitation reste utilisable et aucun compte n'est créé.
 * Le sens est volontairement inversé pour que ce module ignore les établissements.
 */
public interface InvitationHandler {

    record Accepted(
            UUID invitationId,
            UUID accountId,
            UUID establishmentId,
            UUID subjectId,
            Map<String, String> attributes,
            UUID issuedBy) {}

    String purpose();

    void onAccepted(Accepted invitation);
}
