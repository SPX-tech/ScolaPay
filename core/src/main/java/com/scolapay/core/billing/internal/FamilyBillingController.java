package com.scolapay.core.billing.internal;

import com.scolapay.core.shared.PageQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lecture seule, sans identifiant d'établissement. Portail fermé, ces routes n'existent pas. */
@RestController
@ConditionalOnProperty(name = "scolapay.portal.enabled", havingValue = "true")
@RequestMapping("/api/v2/me/billing/obligations")
class FamilyBillingController {

    private final FamilyBillingService billing;

    FamilyBillingController(FamilyBillingService billing) {
        this.billing = billing;
    }

    @GetMapping
    List<FamilyBillingService.ObligationSummary> myObligations(
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {
        return billing.myObligations(PageQuery.of(limit, offset));
    }

    @GetMapping("/{obligationId}")
    FamilyBillingService.ObligationSummary myObligation(@PathVariable UUID obligationId) {
        return billing.myObligation(obligationId);
    }
}
