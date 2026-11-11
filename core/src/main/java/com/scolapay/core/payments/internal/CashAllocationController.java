package com.scolapay.core.payments.internal;

import com.scolapay.core.payments.internal.CashAllocationRepository.Allocation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="scolapay.payments",name="cash-allocations-enabled",havingValue="true")
@RequestMapping("/api/v2/establishments/{establishmentId}")
class CashAllocationController {
    record Command(@NotNull UUID idempotencyKey,@NotNull UUID obligationId,
            @NotNull @Pattern(regexp="^[1-9][0-9]{0,14}$") String amountMinor,@NotNull @Min(1) Long expectedJournalSize) {}
    record Proof(UUID id,String reference,UUID receptionId,UUID obligationId,UUID settlementEntryId,String currency,String amountMinor,
            String issuedAmountMinor,String adjustmentsMinor,String netBilledMinor,String settledBeforeMinor,String settledAfterMinor,
            String dueBeforeMinor,String dueAfterMinor,long journalSizeBefore,Instant allocatedAt,UUID allocatedByAccountId) {
        static Proof of(Allocation a) {
            return new Proof(a.id(),a.reference(),a.receptionId(),a.obligationId(),a.entryId(),a.currency(),Long.toString(a.amountMinor()),
                    Long.toString(a.issuedMinor()),Long.toString(a.netBilledMinor()-a.issuedMinor()),Long.toString(a.netBilledMinor()),
                    Long.toString(a.settledBeforeMinor()),Long.toString(a.settledBeforeMinor()+a.amountMinor()),Long.toString(a.dueBeforeMinor()),
                    Long.toString(a.dueBeforeMinor()-a.amountMinor()),a.journalSizeBefore(),a.allocatedAt(),a.allocatedBy());
        }
    }
    private final CashAllocationService service;
    CashAllocationController(CashAllocationService service) { this.service=service; }
    @PostMapping("/payments/cash-receptions/{receptionId}/allocations")
    ResponseEntity<Proof> allocate(@PathVariable UUID establishmentId,@PathVariable UUID receptionId,@Valid @RequestBody Command command) {
        var result=service.allocate(establishmentId,receptionId,command.obligationId(),command.idempotencyKey(),
                Long.parseLong(command.amountMinor()),command.expectedJournalSize());
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(Proof.of(result.allocation()));
    }
}
