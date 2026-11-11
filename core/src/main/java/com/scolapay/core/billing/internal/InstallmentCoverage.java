package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.BillingSettlements.Installment;
import com.scolapay.core.billing.internal.ObligationRepository.InstallmentRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A02, seulement lorsque les ajustements actifs sont des remises : réduction en fin, paiement au début. */
final class InstallmentCoverage {
    private InstallmentCoverage() {}

    static List<Installment> calculate(List<InstallmentRow> original, long netBilled, long settled) {
        List<Installment> result = new ArrayList<>();
        long prefix = 0;
        for (InstallmentRow row : original.stream().sorted(Comparator.comparing(InstallmentRow::dueOn).thenComparingInt(InstallmentRow::seq)).toList()) {
            long net = Math.min(row.amountMinor(), Math.max(0, netBilled - prefix));
            long paid = Math.min(net, Math.max(0, settled - prefix));
            result.add(new Installment(row.seq(), row.dueOn(), row.amountMinor(), net, paid, net - paid));
            prefix = Math.addExact(prefix, row.amountMinor());
        }
        return List.copyOf(result);
    }
}
