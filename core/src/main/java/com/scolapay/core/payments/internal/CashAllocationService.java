package com.scolapay.core.payments.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.billing.BillingSettlements;
import com.scolapay.core.billing.BillingSettlements.Position;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.payments.internal.CashAllocationRepository.Allocation;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CashAllocationService {
    record Result(Allocation allocation, boolean created) {}
    private final CashAllocationRepository allocations;
    private final CashReceptionRepository receptions;
    private final BillingSettlements billing;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final CashProperties properties;
    private final Clock clock;
    CashAllocationService(CashAllocationRepository allocations, CashReceptionRepository receptions, BillingSettlements billing,
            EstablishmentAccess access, AuditTrail audit, CashProperties properties, Clock clock) {
        this.allocations=allocations; this.receptions=receptions; this.billing=billing; this.access=access;
        this.audit=audit; this.properties=properties; this.clock=clock;
    }

    @Transactional(timeout=10)
    public Result allocate(UUID tenant, UUID reception, UUID obligation, UUID key, long amount, long expectedJournal) {
        UUID actor=access.require(tenant, Permission.CASH_ALLOCATE);
        requireEnabled();
        if (amount<1 || amount>999_999_999_999_999L) throw ApiException.invalid("INVALID_AMOUNT", "Montant invalide.");
        var prior=allocations.byKey(tenant,key);
        if (prior.isPresent()) return replay(prior.get(),reception,obligation,actor,amount,expectedJournal);
        try {
            var cash=receptions.lock(tenant,reception).orElseThrow(() -> ApiException.notFound("CASH_RECEPTION_NOT_FOUND","Déclaration introuvable."));
            // Après l'attente du verrou, un autre appel identique peut avoir terminé.
            prior=allocations.byKey(tenant,key);
            if (prior.isPresent()) return replay(prior.get(),reception,obligation,actor,amount,expectedJournal);
            if (!cash.recordedByAccountId().equals(actor)) throw ApiException.forbidden("CASH_RECEPTION_AUTHOR_REQUIRED","Cette tranche reste réservée au déclarant des espèces.");
            if (allocations.boundToAnother(tenant,reception,obligation)) throw ApiException.conflict("RECEPTION_ALREADY_BOUND","Cette réception est déjà affectée à une autre obligation.");
            Position target=billing.lock(tenant,obligation);
            if (!target.allocationAllowed()) throw ApiException.conflict(target.blockedReason(),"Ce règlement n'est pas disponible pour cette obligation.");
            if (!cash.currency().equals(target.currency())) throw ApiException.invalid("CURRENCY_MISMATCH","Les devises sont incompatibles.");
            if (target.journalSize()!=expectedJournal) throw ApiException.conflict("ALLOCATION_OUTDATED","Le journal a changé. Relisez la situation avant de confirmer.");
            if (amount>target.dueMinor()) throw ApiException.invalid("AMOUNT_EXCEEDS_DUE","Le montant dépasse le reste à payer.");
            if (amount>cash.availableForAllocationMinor()) throw ApiException.invalid("AMOUNT_EXCEEDS_RECEPTION","Le montant dépasse les espèces disponibles non réservées.");
            UUID id=UUID.randomUUID();
            Allocation allocation=new Allocation(id,reception,obligation,UUID.randomUUID(),key,"CA-"+id,cash.currency(),amount,
                    target.issuedMinor(),target.netBilledMinor(),target.settledMinor(),target.dueMinor(),target.journalSize(),
                    clock.instant().truncatedTo(ChronoUnit.MICROS),actor);
            if (!allocations.insert(tenant,allocation)) return replay(allocations.byKey(tenant,key).orElseThrow(),reception,obligation,actor,amount,expectedJournal);
            billing.post(tenant,obligation,id,allocation.entryId(),amount,allocation.allocatedAt());
            audit.record(tenant,actor,"payments.cash.allocated","cash_allocation",id,
                    Map.of("reference",allocation.reference(),"receptionId",reception,"obligationId",obligation));
            return new Result(allocation,true);
        } catch (TransientDataAccessException busy) {
            throw busy();
        } catch (DataIntegrityViolationException conflict) {
            throw ApiException.conflict("CASH_ALLOCATION_CONFLICT","L'affectation n'a pas pu être confirmée.");
        } catch (UncategorizedSQLException failure) {
            if ("55P03".equals(failure.getSQLException().getSQLState())) throw busy();
            throw failure;
        }
    }

    private Result replay(Allocation a,UUID reception,UUID obligation,UUID actor,long amount,long journal) {
        if (!a.receptionId().equals(reception)||!a.obligationId().equals(obligation)||!a.allocatedBy().equals(actor)
                ||a.amountMinor()!=amount||a.journalSizeBefore()!=journal) {
            throw ApiException.conflict("CASH_ALLOCATION_KEY_CONFLICT","Cette clé désigne une autre affectation.");
        }
        return new Result(a,false);
    }
    private static ApiException busy() { return ApiException.conflict("CASH_ALLOCATION_BUSY","Traitement en cours. Rejouez la même intention."); }
    private void requireEnabled() {
        if (!properties.cashAllocationsEnabled()) throw ApiException.notFound("CASH_ALLOCATIONS_DISABLED","Les règlements espèces sont désactivés.");
    }
    @Transactional(readOnly=true)
    public Allocation find(UUID tenant,UUID id) {
        access.require(tenant,Permission.CASH_RECEPTIONS_READ);
        return allocations.find(tenant,id).orElseThrow(() -> ApiException.notFound("CASH_ALLOCATION_NOT_FOUND","Affectation introuvable."));
    }
    @Transactional(readOnly=true)
    public Allocation byKey(UUID tenant,UUID key) {
        access.require(tenant,Permission.CASH_RECEPTIONS_READ);
        return allocations.byKey(tenant,key).orElseThrow(() -> ApiException.notFound("CASH_ALLOCATION_NOT_FOUND","Affectation introuvable."));
    }
}
