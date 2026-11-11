package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.BillingSettlements;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.schooling.AcademicCatalog;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.Columns;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class BillingSettlementService implements BillingSettlements {
    private final ObligationRepository obligations;
    private final EstablishmentAccess access;
    private final AcademicCatalog academics;
    private final JdbcClient jdbc;

    BillingSettlementService(ObligationRepository obligations, EstablishmentAccess access, AcademicCatalog academics, JdbcClient jdbc) {
        this.obligations = obligations;
        this.access = access;
        this.academics = academics;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Position inspect(UUID establishmentId, UUID obligationId) {
        access.require(establishmentId, Permission.BILLING_READ);
        return position(establishmentId, obligationId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Position lock(UUID establishmentId, UUID obligationId) {
        access.require(establishmentId, Permission.CASH_ALLOCATE);
        if (jdbc.sql("SELECT id FROM obligations WHERE establishment_id=:tenant AND id=:id FOR UPDATE")
                .param("tenant", establishmentId).param("id", obligationId).query(UUID.class).optional().isEmpty()) {
            throw ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable.");
        }
        return position(establishmentId, obligationId);
    }

    private Position position(UUID establishmentId, UUID obligationId) {
        var obligation = obligations.find(establishmentId, obligationId)
                .orElseThrow(() -> ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable."));
        boolean supported = !obligation.corrected() && !obligation.cancelled();
        boolean open = academics.findYear(establishmentId, obligation.academicYearId()).map(year -> "OPEN".equals(year.status())).orElse(false);
        String blocked = !supported ? "ADJUSTMENT_POLICY_UNDEFINED" : !open ? "ACADEMIC_YEAR_NOT_OPEN"
                : obligation.dueMinor() == 0 ? "NOTHING_DUE" : null;
        return new Position(obligation.id(), obligation.currency(), obligation.issuedAmountMinor(), obligation.adjustmentsMinor(),
                Math.addExact(obligation.issuedAmountMinor(), obligation.adjustmentsMinor()), obligation.settledMinor(), obligation.dueMinor(),
                obligations.entries(establishmentId, obligationId).size(), ObligationService.ObligationStatus.of(obligation).name(), supported,
                blocked == null, blocked, supported ? InstallmentCoverage.calculate(obligations.installments(establishmentId, obligationId),
                        obligation.issuedAmountMinor() + obligation.adjustmentsMinor(), obligation.settledMinor()) : List.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(UUID establishmentId, UUID obligationId, UUID allocationId, UUID entryId, long amountMinor, Instant at) {
        UUID actor = access.require(establishmentId, Permission.CASH_ALLOCATE);
        if (amountMinor < 1 || amountMinor > 999_999_999_999_999L) throw ApiException.invalid("INVALID_AMOUNT", "Montant invalide.");
        jdbc.sql("""
                INSERT INTO obligation_entries(id,establishment_id,obligation_id,kind,amount_minor,reason,idempotency_key,
                    recorded_at,recorded_by,cash_allocation_id)
                VALUES (:entry,:tenant,:obligation,'SETTLEMENT',:amount,NULL,:key,:at,:actor,:allocation)
                """).param("entry", entryId).param("tenant", establishmentId).param("obligation", obligationId)
                .param("amount", -amountMinor).param("key", "cash:" + allocationId).param("at", Columns.timestamp(at))
                .param("actor", actor).param("allocation", allocationId).update();
    }
}
