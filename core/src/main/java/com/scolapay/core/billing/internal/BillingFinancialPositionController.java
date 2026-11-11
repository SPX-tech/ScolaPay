package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.BillingSettlements;
import com.scolapay.core.billing.BillingSettlements.Position;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/billing/obligations")
class BillingFinancialPositionController {
    record Term(int seq,LocalDate dueOn,String originalMinor,String netMinor,String settledMinor,String dueMinor) {}
    record View(UUID obligationId,String currency,String issuedAmountMinor,String adjustmentsMinor,String netBilledMinor,
            String settledMinor,String dueMinor,long journalSize,String status,boolean installmentsAvailable,boolean allocationAllowed,
            String blockedReason,List<Term> installments) {
        static View of(Position p) {
            return new View(p.obligationId(),p.currency(),Long.toString(p.issuedMinor()),Long.toString(p.adjustmentsMinor()),
                    Long.toString(p.netBilledMinor()),Long.toString(p.settledMinor()),Long.toString(p.dueMinor()),p.journalSize(),p.status(),
                    p.installmentsAvailable(),p.allocationAllowed(),p.blockedReason(),p.installments().stream().map(t -> new Term(t.seq(),t.dueOn(),
                            Long.toString(t.originalMinor()),Long.toString(t.netMinor()),Long.toString(t.settledMinor()),Long.toString(t.dueMinor()))).toList());
        }
    }
    private final BillingSettlements billing;
    BillingFinancialPositionController(BillingSettlements billing) { this.billing=billing; }
    @GetMapping("/{obligationId}/financial-position")
    View position(@PathVariable UUID establishmentId,@PathVariable UUID obligationId) { return View.of(billing.inspect(establishmentId,obligationId)); }
}
