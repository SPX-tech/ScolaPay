package com.scolapay.core.identity;

import java.time.Instant;

/**
 * Canal de remise d'une invitation à son destinataire. Aucune implémentation n'existe encore : le
 * jeton est rendu au personnel qui l'a émis, ce qui interdit d'ouvrir le portail aux familles.
 */
public interface InvitationDelivery {

    void deliver(Identifier contact, String token, Instant expiresAt);
}
