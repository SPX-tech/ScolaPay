package com.scolapay.core.payments.internal;

import com.scolapay.core.payments.internal.CashIdentityReadController.RemitterView;
import com.scolapay.core.payments.internal.CashIdentityReadController.ReviewProof;
import com.scolapay.core.payments.internal.CashRestitutionReadController.AuthorizationProof;
import com.scolapay.core.payments.internal.CashRestitutionReadController.BeneficiaryView;
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
@ConditionalOnProperty(prefix="scolapay.payments",name="cash-identities-enabled",havingValue="true")
@RequestMapping("/api/v2/establishments/{establishmentId}/payments")
class CashIdentityWriteController {
    record LegacyDeclaration(@NotNull UUID idempotencyKey,@NotNull @Valid CashReceptionController.Remitter remitter,
            @NotNull @Pattern(regexp="^[A-Za-z0-9][A-Za-z0-9._/-]{0,79}$") String statementReference,@NotNull Boolean signedStatementConfirmed) {}
    record LegacyReview(@NotNull UUID idempotencyKey,@NotNull Boolean originalReceiptMatchedConfirmed,
            @NotNull Boolean signedStatementCheckedConfirmed,@NotNull Boolean identityCheckedConfirmed) {}
    record BeneficiaryCheck(@NotNull UUID idempotencyKey,@NotNull Boolean beneficiaryIsRemitter,@NotNull Boolean identityCheckedConfirmed,
            @NotBlank @Size(max=500) String verificationDetails) {}
    record Authorization(@NotNull UUID idempotencyKey,@NotNull UUID beneficiaryVerificationId,@NotNull Boolean beneficiaryReviewedConfirmed) {}
    private final CashIdentityService identities;
    private final CashRestitutionService restitution;
    CashIdentityWriteController(CashIdentityService identities,CashRestitutionService restitution){this.identities=identities;this.restitution=restitution;}
    @PostMapping("/cash-receptions/{source}/remitter-declarations")
    ResponseEntity<RemitterView> declare(@PathVariable UUID establishmentId,@PathVariable UUID source,@Valid @RequestBody LegacyDeclaration body){
        var r=identities.declareLegacy(establishmentId,source,body.idempotencyKey(),body.remitter(),body.statementReference(),body.signedStatementConfirmed());
        return ResponseEntity.status(status(r.created())).body(RemitterView.of(source,identities.read(establishmentId,source)));
    }
    @PostMapping("/cash-receptions/{source}/remitter-reviews")
    ResponseEntity<ReviewProof> review(@PathVariable UUID establishmentId,@PathVariable UUID source,@Valid @RequestBody LegacyReview body){
        var r=identities.reviewLegacy(establishmentId,source,body.idempotencyKey(),body.originalReceiptMatchedConfirmed(),body.signedStatementCheckedConfirmed(),body.identityCheckedConfirmed());
        return ResponseEntity.status(status(r.created())).body(ReviewProof.of(r.value()));
    }
    @PostMapping("/excess-requests/{request}/beneficiary-verifications")
    ResponseEntity<BeneficiaryView> verify(@PathVariable UUID establishmentId,@PathVariable UUID request,@Valid @RequestBody BeneficiaryCheck body){
        var r=restitution.verify(establishmentId,request,body.idempotencyKey(),body.beneficiaryIsRemitter(),body.identityCheckedConfirmed(),body.verificationDetails());
        return ResponseEntity.status(status(r.created())).body(BeneficiaryView.of(restitution.readBeneficiary(establishmentId,r.value().id(),false)));
    }
    @PostMapping("/excess-requests/{request}/restitution-authorizations")
    ResponseEntity<AuthorizationProof> authorize(@PathVariable UUID establishmentId,@PathVariable UUID request,@Valid @RequestBody Authorization body){
        var r=restitution.authorize(establishmentId,request,body.beneficiaryVerificationId(),body.idempotencyKey(),body.beneficiaryReviewedConfirmed());
        return ResponseEntity.status(status(r.created())).body(AuthorizationProof.of(r.value()));
    }
    private static HttpStatus status(boolean created){return created?HttpStatus.CREATED:HttpStatus.OK;}
}
