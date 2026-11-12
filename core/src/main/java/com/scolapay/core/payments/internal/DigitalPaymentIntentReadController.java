package com.scolapay.core.payments.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/payments/digital-intents")
class DigitalPaymentIntentReadController {
    record View(UUID id,UUID establishmentId,UUID obligationId,String reference,String providerReference,String originType,String provider,
            String purpose,String state,String currency,String amountMinor,String issuedAtPreparationMinor,String netBilledAtPreparationMinor,
            String settledAtPreparationMinor,String dueAtPreparationMinor,long journalSizeAtPreparation,Instant preparedAt,UUID preparedByAccountId,
            boolean paymentConfirmed,boolean settlementCreated,boolean providerDispatchAvailable,boolean requiresRevalidation){
        static View of(UUID tenant,DigitalPaymentIntentRepository.Intent i){return new View(i.id(),tenant,i.obligationId(),i.reference(),i.reference(),"SCHOOL_OBLIGATION","KADRYZA",
                "PREPARATION_ONLY_V1","PREPARED",i.currency(),Long.toString(i.amountMinor()),Long.toString(i.issuedMinor()),Long.toString(i.netBilledMinor()),Long.toString(i.settledBeforeMinor()),
                Long.toString(i.dueBeforeMinor()),i.journalSizeBefore(),i.preparedAt(),i.preparedBy(),false,false,false,true);}
    }
    private final DigitalPaymentIntentService service;
    DigitalPaymentIntentReadController(DigitalPaymentIntentService service){this.service=service;}
    @GetMapping("/{id}") View find(@PathVariable UUID establishmentId,@PathVariable UUID id){return View.of(establishmentId,service.read(establishmentId,id,false));}
    @GetMapping("/by-key/{key}") View byKey(@PathVariable UUID establishmentId,@PathVariable UUID key){return View.of(establishmentId,service.read(establishmentId,key,true));}
}
