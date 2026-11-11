package com.scolapay.core.billing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contrat de règlement local. Les écritures participent obligatoirement à la transaction appelante. */
public interface BillingSettlements {
    record Installment(int seq, LocalDate dueOn, long originalMinor, long netMinor, long settledMinor, long dueMinor) {}
    record Position(UUID obligationId, String currency, long issuedMinor, long adjustmentsMinor, long netBilledMinor,
            long settledMinor, long dueMinor, long journalSize, String status, boolean installmentsAvailable,
            boolean allocationAllowed, String blockedReason, List<Installment> installments) {}

    Position inspect(UUID establishmentId, UUID obligationId);
    Position lock(UUID establishmentId, UUID obligationId);
    void post(UUID establishmentId, UUID obligationId, UUID allocationId, UUID entryId, long amountMinor, Instant at);
}
