package com.scolapay.core.identity;

import java.util.UUID;

/** Relecture de l'invitation avant chaque tentative. Aucune donnée personnelle n'est exportée. */
public interface InvitationDispatchState {
    boolean isDeliverable(UUID invitationId);
}
