package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.internal.BillingPartyRepository.HeldRole;
import java.util.List;
import java.util.Optional;

/**
 * Seule règle décidant ce qu'une personne voit d'une obligation, calculée à chaque lecture, comme
 * l'accès académique. Si elle tient plusieurs rôles, le plus large l'emporte. Un contributeur doit aussi
 * rester lié au dossier : révoquer ce lien ferme aussitôt sa vue.
 */
enum FinancialView {
    DEBTOR,
    RESPONSIBLE,
    CONTRIBUTOR;

    static Optional<FinancialView> of(List<HeldRole> roles, boolean linkedToStudent, long dueMinor) {
        if (roles.stream().anyMatch(role -> "CONTRACTUAL_DEBTOR".equals(role.role()))) {
            return Optional.of(DEBTOR);
        }
        if (roles.stream().anyMatch(role -> "FINANCIAL_RESPONSIBLE".equals(role.role()))) {
            return Optional.of(RESPONSIBLE);
        }
        boolean contributor = roles.stream().anyMatch(role -> "VOLUNTARY_CONTRIBUTOR".equals(role.role()));
        return contributor && linkedToStudent && dueMinor > 0 ? Optional.of(CONTRIBUTOR) : Optional.empty();
    }
}
