package com.scolapay.core.payments.internal;

import com.scolapay.core.payments.internal.CashExcessReadController.DecisionProof;
import com.scolapay.core.payments.internal.CashExcessReadController.RequestView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
@ConditionalOnProperty(prefix="scolapay.payments",name="cash-excess-reservations-enabled",havingValue="true")
@RequestMapping("/api/v2/establishments/{establishmentId}/payments")
class CashExcessWriteController {
    record Reservation(@NotNull UUID idempotencyKey,@NotNull @Pattern(regexp="^[1-9][0-9]{0,14}$") String amountMinor,
            @NotBlank @Size(max=500) String reason) {}
    record Approval(@NotNull UUID idempotencyKey) {}
    record Release(@NotNull UUID idempotencyKey,@NotBlank @Size(max=500) String note) {}
    private final CashExcessService service;
    CashExcessWriteController(CashExcessService service){this.service=service;}
    @PostMapping("/cash-receptions/{receptionId}/excess-requests")
    ResponseEntity<RequestView> request(@PathVariable UUID establishmentId,@PathVariable UUID receptionId,@Valid @RequestBody Reservation body){
        var result=service.request(establishmentId,receptionId,body.idempotencyKey(),Long.parseLong(body.amountMinor()),body.reason());
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK)
                .body(RequestView.of(service.read(establishmentId,result.request().id(),false)));
    }
    @PostMapping("/excess-requests/{requestId}/approval")
    ResponseEntity<DecisionProof> approve(@PathVariable UUID establishmentId,@PathVariable UUID requestId,@Valid @RequestBody Approval body){
        return decide(establishmentId,requestId,body.idempotencyKey(),"APPROVED",null);
    }
    @PostMapping("/excess-requests/{requestId}/rejection")
    ResponseEntity<DecisionProof> reject(@PathVariable UUID establishmentId,@PathVariable UUID requestId,@Valid @RequestBody Release body){
        return decide(establishmentId,requestId,body.idempotencyKey(),"REJECTED",body.note());
    }
    @PostMapping("/excess-requests/{requestId}/withdrawal")
    ResponseEntity<DecisionProof> withdraw(@PathVariable UUID establishmentId,@PathVariable UUID requestId,@Valid @RequestBody Release body){
        return decide(establishmentId,requestId,body.idempotencyKey(),"WITHDRAWN",body.note());
    }
    private ResponseEntity<DecisionProof> decide(UUID tenant,UUID request,UUID key,String kind,String note){
        var result=service.decide(tenant,request,key,kind,note);
        var read=service.readDecision(tenant,key);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK)
                .body(DecisionProof.of(read.decision(),read.actor(),read.privateReasons()));
    }
}
