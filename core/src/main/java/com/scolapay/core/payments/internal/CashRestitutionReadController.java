package com.scolapay.core.payments.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/payments")
class CashRestitutionReadController {
    record BeneficiaryView(UUID id,String reference,UUID requestId,UUID receptionId,String currency,String amountMinor,
            String beneficiaryKind,String fullName,String verificationDetails,Instant verifiedAt,UUID verifiedByAccountId,
            boolean finalIdentityCheckRequired,boolean physicalHandoverAvailable){
        static BeneficiaryView of(CashRestitutionService.ReadBeneficiary r){var b=r.verification();return new BeneficiaryView(b.id(),b.reference(),b.requestId(),b.source(),
                b.currency(),Long.toString(b.amountMinor()),"REMITTER_ONLY",r.fullName(),r.verificationDetails(),b.verifiedAt(),b.verifiedBy(),true,false);}
    }
    record AuthorizationProof(UUID id,String reference,UUID requestId,UUID receptionId,UUID beneficiaryVerificationId,String currency,
            String amountMinor,String policy,String sourceReservationScope,Instant authorizedAt,UUID authorizedByAccountId,
            String restitutedMinor,boolean finalIdentityCheckRequired,boolean physicalHandoverAvailable){
        static AuthorizationProof of(CashRestitutionRepository.Authorization a){return new AuthorizationProof(a.id(),a.reference(),a.requestId(),a.source(),a.verificationId(),
                a.currency(),Long.toString(a.amountMinor()),a.policy(),"RESERVATION_ONLY_V1",a.authorizedAt(),a.authorizedBy(),"0",true,false);}
    }
    private final CashRestitutionService service;
    CashRestitutionReadController(CashRestitutionService service){this.service=service;}
    @GetMapping("/beneficiary-verifications/{id}")
    BeneficiaryView beneficiary(@PathVariable UUID establishmentId,@PathVariable UUID id){return BeneficiaryView.of(service.readBeneficiary(establishmentId,id,false));}
    @GetMapping("/beneficiary-verifications/by-key/{key}")
    BeneficiaryView beneficiaryByKey(@PathVariable UUID establishmentId,@PathVariable UUID key){return BeneficiaryView.of(service.readBeneficiary(establishmentId,key,true));}
    @GetMapping("/restitution-authorizations/{id}")
    AuthorizationProof authorization(@PathVariable UUID establishmentId,@PathVariable UUID id){return AuthorizationProof.of(service.readAuthorization(establishmentId,id,false));}
    @GetMapping("/restitution-authorizations/by-key/{key}")
    AuthorizationProof authorizationByKey(@PathVariable UUID establishmentId,@PathVariable UUID key){return AuthorizationProof.of(service.readAuthorization(establishmentId,key,true));}
}
