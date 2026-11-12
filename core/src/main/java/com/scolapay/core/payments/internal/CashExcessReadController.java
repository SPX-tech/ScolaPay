package com.scolapay.core.payments.internal;

import com.scolapay.core.payments.internal.CashExcessRepository.Decision;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/payments")
class CashExcessReadController {
    record DecisionProof(UUID id,String reference,UUID requestId,UUID receptionId,String currency,String amountMinor,
            String authorizationScope,String kind,String note,boolean noteVisible,Instant decidedAt,UUID decidedByAccountId,
            String restitutedMinor,boolean physicalHandoverAvailable) {
        static DecisionProof of(Decision d,UUID actor,boolean reasons) {
            boolean visible=reasons||d.decidedBy().equals(actor);
            return new DecisionProof(d.id(),d.reference(),d.requestId(),d.receptionId(),d.currency(),Long.toString(d.amountMinor()),
                    d.authorizationScope(),d.kind(),visible?d.note():null,visible,d.decidedAt(),d.decidedBy(),"0",false);
        }
    }
    record RequestView(UUID id,String reference,UUID receptionId,String currency,String amountMinor,String reason,boolean reasonVisible,
            String authorizationScope,String retainedMinor,String historicalAllocatedBeforeMinor,String reservedBeforeMinor,
            String availableBeforeMinor,Instant createdAt,UUID createdByAccountId,String state,String reservedMinor,
            String restitutedMinor,boolean physicalHandoverAvailable,DecisionProof decision) {
        static RequestView of(CashExcessService.ReadRequest view) {
            var r=view.request();boolean reasons=view.privateReasons()||r.createdBy().equals(view.actor());
            boolean active="REQUESTED".equals(r.state())||"APPROVED_RESERVED".equals(r.state());
            return new RequestView(r.id(),r.reference(),r.receptionId(),r.currency(),Long.toString(r.amountMinor()),reasons?r.reason():null,reasons,
                    r.authorizationScope(),Long.toString(r.retainedMinor()),Long.toString(r.historicalAllocatedBeforeMinor()),Long.toString(r.reservedBeforeMinor()),
                    Long.toString(r.retainedMinor()-r.historicalAllocatedBeforeMinor()-r.reservedBeforeMinor()),r.createdAt(),r.createdBy(),r.state(),
                    active?Long.toString(r.amountMinor()):"0","0",false,view.decision()==null?null:DecisionProof.of(view.decision(),view.actor(),view.privateReasons()));
        }
    }
    private final CashExcessService service;
    CashExcessReadController(CashExcessService service) {this.service=service;}
    @GetMapping("/excess-requests/{id}")
    RequestView read(@PathVariable UUID establishmentId,@PathVariable UUID id){return RequestView.of(service.read(establishmentId,id,false));}
    @GetMapping("/excess-requests/by-key/{key}")
    RequestView byKey(@PathVariable UUID establishmentId,@PathVariable UUID key){return RequestView.of(service.read(establishmentId,key,true));}
    @GetMapping("/excess-decisions/by-key/{key}")
    DecisionProof decision(@PathVariable UUID establishmentId,@PathVariable UUID key){
        var r=service.readDecision(establishmentId,key);return DecisionProof.of(r.decision(),r.actor(),r.privateReasons());
    }
}
