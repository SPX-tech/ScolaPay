package com.scolapay.core.payments.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/payments")
class CashIdentityReadController {
    record RemitterView(UUID receptionId,String state,String fullName,String identificationDetails,String statementReference,
            Instant recordedAt,UUID recordedByAccountId,boolean physicalHandoverAvailable,ReviewProof review) {
        static RemitterView of(UUID source,CashIdentityService.Read read){
            var r=read.remitter();var d=read.details();
            return new RemitterView(source,read.state(),d==null?null:d.fullName(),d==null?null:d.identificationDetails(),
                    r==null?null:r.statementReference(),r==null?null:r.recordedAt(),r==null?null:r.recordedBy(),false,
                    read.review()==null?null:ReviewProof.of(read.review()));
        }
    }
    record ReviewProof(UUID id,String reference,UUID receptionId,Instant reviewedAt,UUID reviewedByAccountId,boolean physicalHandoverAvailable){
        static ReviewProof of(CashIdentityRepository.Review r){return new ReviewProof(r.id(),r.reference(),r.source(),r.reviewedAt(),r.reviewedBy(),false);}
    }
    private final CashIdentityService service;
    CashIdentityReadController(CashIdentityService service){this.service=service;}
    @GetMapping("/cash-receptions/{source}/remitter")
    RemitterView read(@PathVariable UUID establishmentId,@PathVariable UUID source){return RemitterView.of(source,service.read(establishmentId,source));}
    @GetMapping("/remitter-declarations/by-key/{key}")
    RemitterView byKey(@PathVariable UUID establishmentId,@PathVariable UUID key){var r=service.readByKey(establishmentId,key);return RemitterView.of(r.remitter().id(),r);}
    @GetMapping("/remitter-reviews/by-key/{key}")
    ReviewProof review(@PathVariable UUID establishmentId,@PathVariable UUID key){return ReviewProof.of(service.readReviewByKey(establishmentId,key));}
}
