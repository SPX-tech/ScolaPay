package com.scolapay.core.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.scolapay.core.billing.BillingSettlements.Installment;
import com.scolapay.core.billing.internal.ObligationRepository.InstallmentRow;

class InstallmentCoverageTest {
    private static final List<InstallmentRow> TERMS=List.of(new InstallmentRow(1,LocalDate.of(2026,10,1),40000),
            new InstallmentRow(2,LocalDate.of(2027,1,1),60000));
    @Test void uneRemiseReduitLaFinEtUnPaiementCouvreLeDebut() {
        assertThat(InstallmentCoverage.calculate(TERMS,80000,50000)).extracting(Installment::netMinor,Installment::settledMinor,Installment::dueMinor)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(40000L,40000L,0L),org.assertj.core.groups.Tuple.tuple(40000L,10000L,30000L));
    }
    @Test void uneRemiseNeReduitPasLesSommesDejaCouvertes() {
        assertThat(InstallmentCoverage.calculate(TERMS,50000,50000)).extracting(Installment::netMinor,Installment::settledMinor,Installment::dueMinor)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(40000L,40000L,0L),org.assertj.core.groups.Tuple.tuple(10000L,10000L,0L));
    }
    @Test void uneRemiseTotaleNePresenteAucunMontantEncaisse() {
        assertThat(InstallmentCoverage.calculate(TERMS,0,0)).allSatisfy(t->{assertThat(t.netMinor()).isZero();assertThat(t.settledMinor()).isZero();assertThat(t.dueMinor()).isZero();});
    }
    @Test void laDatePrimeSurLeNumeroDeLigne() {
        var unordered=List.of(TERMS.get(1),TERMS.get(0));
        assertThat(InstallmentCoverage.calculate(unordered,100000,20000)).extracting(Installment::seq,Installment::settledMinor)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1,20000L),org.assertj.core.groups.Tuple.tuple(2,0L));
    }
}
