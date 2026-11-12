package com.scolapay.core.billing;

import java.util.UUID;

/** Lecture verrouillée pour préparer un paiement ; aucun port d'écriture de règlement. */
public interface BillingPaymentPreparation {
    record Snapshot(UUID obligationId,String currency,long issuedMinor,long netBilledMinor,long settledMinor,long dueMinor,
            long journalSize,boolean allowed,String blockedReason) {}
    Snapshot lockForPreparation(UUID establishmentId,UUID obligationId);
}
