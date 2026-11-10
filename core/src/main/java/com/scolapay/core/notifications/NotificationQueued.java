package com.scolapay.core.notifications;

import java.util.UUID;

/** Ne jamais ajouter de destinataire, de contenu ou de jeton à cet événement archivé. */
public record NotificationQueued(UUID notificationId) {}
