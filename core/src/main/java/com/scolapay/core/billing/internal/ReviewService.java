package com.scolapay.core.billing.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Item;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Version;
import com.scolapay.core.billing.internal.ObligationRepository.Entry;
import com.scolapay.core.billing.internal.ObligationRepository.Obligation;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.schooling.AcademicCatalog;
import com.scolapay.core.schooling.AcademicCatalog.EnrollmentSummary;
import com.scolapay.core.schooling.AcademicCatalog.UnitSummary;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PageQuery;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Situations à réexaminer. Elles se déduisent de la réalité à chaque lecture : aucun événement perdu ne
 * peut en faire disparaître une. <strong>Rien n'est modifié automatiquement</strong>, et un écart n'est
 * pas présenté comme un supplément ni un remboursement dû : la décision appartient à l'établissement.
 */
@Service
public class ReviewService {

    public record Signal(
            String signal,
            String fingerprint,
            String cause,
            Obligation obligation,
            UUID currentEnrollmentId,
            Long applicableAmountMinor,
            Long differenceMinor) {}

    public record Page(long total, List<Signal> signals) {}

    private static final String TARIFF_DIFFERENCE = "TARIFF_DIFFERENCE";
    private static final String WITHDRAWAL = "WITHDRAWAL";

    private final ObligationRepository obligations;
    private final AdjustmentRepository adjustments;
    private final FeeCatalogRepository catalogs;
    private final AcademicCatalog academics;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final Clock clock;
    private final int maxRows;

    public ReviewService(
            ObligationRepository obligations,
            AdjustmentRepository adjustments,
            FeeCatalogRepository catalogs,
            AcademicCatalog academics,
            EstablishmentAccess access,
            AuditTrail audit,
            Clock clock,
            @Value("${scolapay.billing.max-review-rows}") int maxRows) {
        this.obligations = obligations;
        this.adjustments = adjustments;
        this.catalogs = catalogs;
        this.academics = academics;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.maxRows = maxRows;
    }

    @Transactional(readOnly = true)
    public Page list(UUID establishmentId, UUID academicYearId, PageQuery page) {
        access.require(establishmentId, Permission.BILLING_READ);
        requireYear(establishmentId, academicYearId);
        List<Signal> signals = openSignals(establishmentId, academicYearId);
        return new Page(signals.size(), signals.stream().skip(page.offset()).limit(page.limit()).toList());
    }

    /**
     * Enregistre la décision motivée. {@code NO_CHANGE} : l'établissement conserve l'obligation telle
     * quelle. {@code ADJUSTED} : renvoie à l'écriture déjà passée. La situation décidée ne réapparaît
     * plus, mais une situation différente redeviendra visible.
     */
    @Transactional
    public void decide(UUID establishmentId, UUID obligationId, String signal, String fingerprint, String decision, UUID entryId, String reason) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_ADJUST);
        if (!adjustments.lockObligation(establishmentId, obligationId)) {
            throw ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable.");
        }
        Obligation obligation = obligations.find(establishmentId, obligationId).orElseThrow();
        boolean current = openSignals(establishmentId, obligation.academicYearId()).stream()
                .anyMatch(open -> open.obligation().id().equals(obligationId) && open.signal().equals(signal) && open.fingerprint().equals(fingerprint));
        if (!current) {
            throw ApiException.conflict("REVIEW_OUTDATED", "Cette situation a changé ou a déjà été décidée : consultez à nouveau la liste.");
        }
        if ("ADJUSTED".equals(decision) != (entryId != null)) {
            throw ApiException.invalid("ENTRY_REQUIRED_IF_ADJUSTED", "Une décision ADJUSTED, et elle seule, renvoie à une écriture.");
        }
        if (entryId != null) {
            boolean adjustment = obligations.entries(establishmentId, obligationId).stream()
                    .anyMatch(entry -> entry.id().equals(entryId) && !"ISSUED".equals(entry.kind()));
            if (!adjustment) {
                throw ApiException.invalid("ENTRY_NOT_AN_ADJUSTMENT", "Cette écriture n'est pas un ajustement de cette obligation.");
            }
        }
        adjustments.insertDecision(establishmentId, obligationId, signal, fingerprint, decision, reason, entryId, clock.instant(), actorId);
        audit.record(
                establishmentId, actorId, "billing.review.decided", "obligation", obligationId,
                Map.of("signal", signal, "decision", decision));
    }

    private List<Signal> openSignals(UUID establishmentId, UUID academicYearId) {
        List<Obligation> issued = obligations.listForReview(establishmentId, academicYearId, maxRows + 1);
        List<EnrollmentSummary> enrollments = academics.enrollments(establishmentId, academicYearId, maxRows + 1);
        if (issued.size() > maxRows || enrollments.size() > maxRows) {
            throw ApiException.invalid("REVIEW_TOO_LARGE", "Trop de lignes à examiner pour cette année.");
        }
        Map<UUID, EnrollmentSummary> byId = enrollments.stream().collect(Collectors.toMap(EnrollmentSummary::id, Function.identity()));
        Map<UUID, List<EnrollmentSummary>> byStudent = enrollments.stream().collect(Collectors.groupingBy(EnrollmentSummary::studentId));
        Map<UUID, UnitSummary> units = academics.units(establishmentId).stream().collect(Collectors.toMap(UnitSummary::id, Function.identity()));
        Optional<Version> published = catalogs.publishedVersion(establishmentId, academicYearId);
        List<Item> items = published.map(version -> catalogs.items(establishmentId, version.id())).orElse(List.of());
        Map<UUID, List<Item>> applicableByUnit = new HashMap<>();
        Set<List<String>> decided = adjustments.decidedSignals(establishmentId);

        List<Signal> signals = new ArrayList<>();
        for (Obligation obligation : issued) {
            // Plus rien n'est dû : il n'y a rien à trancher, quelle que soit la voie qui y a mené.
            if (obligation.dueMinor() == 0) {
                continue;
            }
            List<EnrollmentSummary> ofStudent = byStudent.getOrDefault(obligation.studentId(), List.of());
            Optional<EnrollmentSummary> active = ofStudent.stream().filter(EnrollmentSummary::active).findFirst();
            Signal signal = active.isPresent()
                    ? tariffSignal(obligation, active.get(), byId, published.isPresent(), units, items, applicableByUnit)
                    : withdrawalSignal(obligation, ofStudent);
            if (signal != null && !decided.contains(List.of(obligation.id().toString(), signal.signal(), signal.fingerprint()))) {
                signals.add(signal);
            }
        }
        return signals;
    }

    private static Signal tariffSignal(
            Obligation obligation,
            EnrollmentSummary active,
            Map<UUID, EnrollmentSummary> enrollments,
            boolean hasCatalog,
            Map<UUID, UnitSummary> units,
            List<Item> items,
            Map<UUID, List<Item>> applicableByUnit) {
        if (!hasCatalog) {
            return null;
        }
        List<Item> applicable = applicableByUnit.computeIfAbsent(
                active.academicUnitId(), unit -> FeeResolution.applicableTo(unit, items, units));
        Optional<Item> item = applicable.stream().filter(candidate -> candidate.feeDefinitionId().equals(obligation.feeDefinitionId())).findFirst();
        if (item.isPresent() && item.get().amountMinor() == obligation.issuedAmountMinor()) {
            return null;
        }
        EnrollmentSummary original = enrollments.get(obligation.enrollmentId());
        String cause = original != null && !original.academicUnitId().equals(active.academicUnitId()) ? "LEVEL_CHANGED" : "CATALOG_CHANGED";
        Long amount = item.map(Item::amountMinor).orElse(null);
        return new Signal(
                TARIFF_DIFFERENCE,
                active.id() + ":" + (amount == null ? "none" : amount.toString()),
                cause,
                obligation,
                active.id(),
                amount,
                amount == null ? null : amount - obligation.issuedAmountMinor());
    }

    /** Une scolarité menée à son terme ne se réexamine pas : seul un retrait le demande. */
    private static Signal withdrawalSignal(Obligation obligation, List<EnrollmentSummary> ofStudent) {
        return ofStudent.stream()
                .max(Comparator.comparing(EnrollmentSummary::enrolledAt))
                .filter(latest -> "WITHDRAWN".equals(latest.status()))
                .map(latest -> new Signal(WITHDRAWAL, latest.id().toString(), "WITHDRAWN", obligation, null, null, null))
                .orElse(null);
    }

    private void requireYear(UUID establishmentId, UUID academicYearId) {
        if (academics.findYear(establishmentId, academicYearId).isEmpty()) {
            throw ApiException.notFound("ACADEMIC_YEAR_NOT_FOUND", "Année académique introuvable.");
        }
    }
}
