package com.scolapay.core.billing.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Period;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.schooling.AcademicCatalog;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les dates d'une période ne bougent pas et elle ne se supprime pas : une erreur se corrige en
 * archivant la période et en en créant une autre, sans changer le sens d'un tarif déjà publié.
 */
@Service
public class BillingPeriodService {

    private final FeeCatalogRepository repository;
    private final AcademicCatalog academics;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final Clock clock;

    public BillingPeriodService(
            FeeCatalogRepository repository, AcademicCatalog academics, EstablishmentAccess access, AuditTrail audit, Clock clock) {
        this.repository = repository;
        this.academics = academics;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Period create(UUID establishmentId, UUID academicYearId, String code, String name, LocalDate startsOn, LocalDate endsOn) {
        UUID actorId = access.require(establishmentId, Permission.FEES_MANAGE);
        AcademicCatalog.YearSummary year = academics.findYear(establishmentId, academicYearId)
                .orElseThrow(() -> ApiException.notFound("ACADEMIC_YEAR_NOT_FOUND", "Année académique introuvable."));
        if (year.closed()) {
            throw ApiException.conflict("ACADEMIC_YEAR_CLOSED", "Cette année académique est clôturée.");
        }
        if (startsOn.isAfter(endsOn)) {
            throw ApiException.invalid("INVALID_PERIOD", "La date de début ne peut pas suivre la date de fin.");
        }
        Period period = new Period(UUID.randomUUID(), academicYearId, code, requireText(name), startsOn, endsOn, "ACTIVE");
        repository.insertPeriod(establishmentId, period, clock.instant());
        audit.record(
                establishmentId, actorId, "billing.period.created", "billing_period", period.id(),
                Map.of("code", code, "academicYearId", academicYearId));
        return period;
    }

    @Transactional(readOnly = true)
    public List<Period> list(UUID establishmentId, UUID academicYearId) {
        access.require(establishmentId, Permission.ESTABLISHMENT_READ);
        return repository.listPeriods(establishmentId, academicYearId);
    }

    @Transactional
    public Period update(UUID establishmentId, UUID periodId, String name, String status) {
        UUID actorId = access.require(establishmentId, Permission.FEES_MANAGE);
        if (name == null && status == null) {
            throw ApiException.invalid("NOTHING_TO_UPDATE", "Indiquez un nom ou un statut.");
        }
        Period period = repository.findPeriod(establishmentId, periodId)
                .orElseThrow(() -> ApiException.notFound("BILLING_PERIOD_NOT_FOUND", "Période de facturation introuvable."));
        String newName = name == null ? period.name() : requireText(name);
        String newStatus = status == null ? period.status() : status;
        repository.updatePeriod(establishmentId, periodId, newName, newStatus, clock.instant());
        audit.record(
                establishmentId, actorId, "billing.period.updated", "billing_period", periodId,
                Map.of("status", newStatus, "renamed", !newName.equals(period.name())));
        return new Period(periodId, period.academicYearId(), period.code(), newName, period.startsOn(), period.endsOn(), newStatus);
    }

    private static String requireText(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.invalid("INVALID_NAME", "Le nom ne peut pas être vide.");
        }
        return trimmed;
    }
}
