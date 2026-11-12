package com.scolapay.core.payments.internal;

import com.scolapay.core.payments.internal.DigitalPaymentIntentReadController.View;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix="scolapay.payments.digital",name="intents-enabled",havingValue="true")
@RequestMapping("/api/v2/establishments/{establishmentId}/payments/digital-intents")
class DigitalPaymentIntentWriteController {
    // La forme bornée évite la coercition JSON d'une version fractionnaire vers un long tronqué.
    record Preparation(@NotNull UUID obligationId,@NotNull UUID idempotencyKey,@NotNull @Pattern(regexp="^[A-Z]{3}$") String currency,
            @NotNull @Pattern(regexp="^[1-9][0-9]{0,14}$") String amountMinor,
            @NotNull @Pattern(regexp="^[1-9][0-9]{0,17}$") String expectedJournalSize) {}
    private final DigitalPaymentIntentService service;
    DigitalPaymentIntentWriteController(DigitalPaymentIntentService service){this.service=service;}
    @PostMapping ResponseEntity<View> prepare(@PathVariable UUID establishmentId,@Valid @RequestBody Preparation body){
        var r=service.prepare(establishmentId,body.obligationId(),body.idempotencyKey(),body.currency(),Long.parseLong(body.amountMinor()),Long.parseLong(body.expectedJournalSize()));
        return ResponseEntity.status(r.created()?HttpStatus.CREATED:HttpStatus.OK).body(View.of(establishmentId,r.intent()));
    }
}
