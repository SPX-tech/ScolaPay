package com.scolapay.core.payments.internal;

import com.scolapay.core.payments.internal.CashReceptionRepository.Reception;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/payments/cash-receptions")
class CashReceptionController {

    record Remitter(@NotBlank @Size(max=160) String fullName,@NotBlank @Size(max=300) String identificationDetails,
            @NotNull Boolean identityRecordedConfirmed) {}
    record Declaration(
            @NotNull UUID idempotencyKey,
            @NotNull @Pattern(regexp = "^[1-9][0-9]{0,14}$") String amountReceivedMinor,
            @NotNull @Pattern(regexp = "^(0|[1-9][0-9]{0,14})$") String changeGivenMinor,
            @NotNull Boolean cashReceivedConfirmed,@Valid Remitter remitter) {}

    record View(
            UUID id, String reference, String currency, String amountReceivedMinor, String changeGivenMinor,
            String amountRetainedMinor, String unallocatedMinor, String settledMinor, String allocationState,
            String proofNature, Instant recordedAt, UUID recordedByAccountId, LocalDate businessDate, String timezone,
            String historicalAllocatedMinor, String reservedMinor, String availableForAllocationMinor) {

        static View of(Reception reception) {
            return new View(reception.id(), reception.reference(), reception.currency(),
                    Long.toString(reception.amountReceivedMinor()), Long.toString(reception.changeGivenMinor()),
                    Long.toString(reception.amountRetainedMinor()), Long.toString(reception.amountRetainedMinor() - reception.allocatedMinor()),
                    Long.toString(reception.allocatedMinor()), reception.allocatedMinor() == 0 ? "UNALLOCATED"
                            : reception.allocatedMinor() == reception.amountRetainedMinor() ? "ALLOCATED" : "PARTIALLY_ALLOCATED",
                    "INTERNAL_CASH_DECLARATION", reception.recordedAt(),
                    reception.recordedByAccountId(), reception.businessDate(), reception.timezone(),
                    Long.toString(reception.allocatedMinor()), Long.toString(reception.reservedMinor()), Long.toString(reception.availableForAllocationMinor()));
        }
    }

    private final CashReceptionService service;

    CashReceptionController(CashReceptionService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<View> record(@PathVariable UUID establishmentId, @Valid @RequestBody Declaration request) {
        var result = service.record(establishmentId, request.idempotencyKey(), Long.parseLong(request.amountReceivedMinor()),
                Long.parseLong(request.changeGivenMinor()), request.cashReceivedConfirmed(),request.remitter());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(View.of(result.reception()));
    }

    @GetMapping("/{id}")
    View find(@PathVariable UUID establishmentId, @PathVariable UUID id) {
        return View.of(service.find(establishmentId, id));
    }

    @GetMapping("/by-key/{key}")
    View findByKey(@PathVariable UUID establishmentId, @PathVariable UUID key) {
        return View.of(service.findByKey(establishmentId, key));
    }
}
