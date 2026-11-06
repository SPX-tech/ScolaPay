package com.scolapay.core.billing.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Installment;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Item;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Version;
import com.scolapay.core.billing.internal.ObligationRepository.Balance;
import com.scolapay.core.billing.internal.ObligationRepository.Entry;
import com.scolapay.core.billing.internal.ObligationRepository.InstallmentRow;
import com.scolapay.core.billing.internal.ObligationRepository.NewObligation;
import com.scolapay.core.billing.internal.ObligationRepository.Obligation;
import com.scolapay.core.billing.internal.ObligationRepository.Run;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Establishments;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.schooling.AcademicCatalog;
import com.scolapay.core.schooling.AcademicCatalog.EnrollmentSummary;
import com.scolapay.core.schooling.AcademicCatalog.UnitSummary;
import com.scolapay.core.schooling.StudentDirectory;
import com.scolapay.core.schooling.StudentEnrolled;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PageQuery;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
 * Émission des obligations. Trois voies seulement, toutes explicites : l'inscription dont le personnel
 * a confirmé les frais, le lot confirmé après aperçu, et l'émission unitaire. Toutes passent par la
 * même contrainte, qui rend l'émission idempotente quelle que soit la cause d'un rejeu.
 *
 * <p>Ce service n'écrit qu'une écriture d'émission. Il ne peut ni solder ni régler une obligation.
 */
@Service
public class ObligationService {

    /** Déduit du journal, jamais saisi. SETTLED n'existe pas : seul le futur module de paiement pourra l'introduire. */
    public enum ObligationStatus {
        OPEN,
        WAIVED;

        static ObligationStatus of(long dueMinor) {
            return dueMinor > 0 ? OPEN : WAIVED;
        }
    }

    public record EnrollmentFee(Item item, boolean alreadyIssued) {}

    public record EnrollmentPreview(Version version, List<EnrollmentFee> fees, long totalToIssueMinor) {}

    public record FeeTotal(UUID feeDefinitionId, String feeCode, String label, long obligations, long totalMinor) {}

    public record IssuancePreview(
            UUID academicYearId,
            UUID academicUnitId,
            Version version,
            long activeEnrollments,
            long studentsConcerned,
            long obligationsToCreate,
            long alreadyIssued,
            long totalMinor,
            List<FeeTotal> fees,
            String previewToken) {}

    public record Detail(Obligation obligation, ObligationStatus status, List<InstallmentRow> installments, List<Entry> entries) {}

    public record ManualIssue(boolean created, Obligation obligation) {}

    private record Planned(EnrollmentSummary enrollment, Item item) {}

    private record Plan(List<Planned> obligations, long activeEnrollments, long alreadyIssued) {}

    // Sans I, L, O ni U (Crockford) : une référence se lit et se recopie sans ambiguïté.
    private static final char[] REFERENCE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int REFERENCE_LENGTH = 10;

    private final ObligationRepository obligations;
    private final FeeCatalogRepository catalogs;
    private final AcademicCatalog academics;
    private final StudentDirectory students;
    private final Establishments establishments;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final Clock clock;
    private final int maxIssuanceSize;
    private final SecureRandom random = new SecureRandom();

    public ObligationService(
            ObligationRepository obligations,
            FeeCatalogRepository catalogs,
            AcademicCatalog academics,
            StudentDirectory students,
            Establishments establishments,
            EstablishmentAccess access,
            AuditTrail audit,
            Clock clock,
            @Value("${scolapay.billing.max-issuance-size}") int maxIssuanceSize) {
        this.obligations = obligations;
        this.catalogs = catalogs;
        this.academics = academics;
        this.students = students;
        this.establishments = establishments;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.maxIssuanceSize = maxIssuanceSize;
    }

    // Inscription

    /** Ce que confirmer une inscription générerait. Une lecture, qui ne crée rien. */
    @Transactional(readOnly = true)
    public EnrollmentPreview enrollmentPreview(UUID establishmentId, UUID academicYearId, UUID academicUnitId, UUID studentId) {
        access.require(establishmentId, Permission.ESTABLISHMENT_READ);
        Map<UUID, UnitSummary> units = unitsById(establishmentId);
        if (!units.containsKey(academicUnitId)) {
            throw ApiException.notFound("ACADEMIC_UNIT_NOT_FOUND", "Unité académique introuvable.");
        }
        if (studentId != null) {
            requireStudent(establishmentId, studentId);
        }
        Optional<Version> published = catalogs.publishedVersion(establishmentId, academicYearId);
        if (published.isEmpty()) {
            return new EnrollmentPreview(null, List.of(), 0);
        }
        Set<List<UUID>> issued = obligations.issuedCharges(establishmentId, academicYearId);
        List<EnrollmentFee> fees = new ArrayList<>();
        long total = 0;
        for (Item item : automaticFees(establishmentId, published.get(), academicUnitId, units)) {
            boolean alreadyIssued = studentId != null && issued.contains(List.of(studentId, item.feeDefinitionId()));
            fees.add(new EnrollmentFee(item, alreadyIssued));
            if (!alreadyIssued) {
                total = Math.addExact(total, item.amountMinor());
            }
        }
        return new EnrollmentPreview(published.get(), fees, total);
    }

    /**
     * Appelé par l'outbox, donc possiblement plusieurs fois, en même temps et dans le désordre. Sans
     * confirmation, l'inscription ne génère rien : l'élève reste visible dans l'aperçu de l'émission en lot.
     */
    @Transactional
    public void issueForEnrollment(StudentEnrolled event) {
        UUID confirmed = event.confirmedFeeCatalogVersionId();
        if (confirmed == null) {
            return;
        }
        UUID establishmentId = event.establishmentId();
        Version version = catalogs.findVersion(establishmentId, confirmed)
                .filter(found -> !found.draft())
                .orElseThrow(() -> new IllegalStateException("Version de catalogue confirmée introuvable : " + confirmed));
        EnrollmentSummary enrollment = academics.findEnrollment(establishmentId, event.enrollmentId())
                .orElseThrow(() -> new IllegalStateException("Inscription introuvable : " + event.enrollmentId()));
        // L'ordre des événements n'est pas garanti : on relit l'état plutôt que de se fier à l'arrivée.
        if (!enrollment.active()) {
            audit.record(
                    establishmentId, event.actorAccountId(), "billing.issuance.skipped", "enrollment", enrollment.id(),
                    Map.of("reason", "ENROLLMENT_NOT_ACTIVE"));
            return;
        }
        Map<UUID, UnitSummary> units = unitsById(establishmentId);
        for (Item item : automaticFees(establishmentId, version, enrollment.academicUnitId(), units)) {
            issue(establishmentId, enrollment, version, item, "ENROLLMENT", null, event.actorAccountId());
        }
    }

    // Émission en lot

    @Transactional(readOnly = true)
    public IssuancePreview issuancePreview(UUID establishmentId, UUID academicYearId, UUID academicUnitId) {
        access.require(establishmentId, Permission.BILLING_ISSUE);
        requireYear(establishmentId, academicYearId);
        Optional<Version> published = catalogs.publishedVersion(establishmentId, academicYearId);
        if (published.isEmpty()) {
            return new IssuancePreview(academicYearId, academicUnitId, null, 0, 0, 0, 0, 0, List.of(), null);
        }
        Plan plan = plan(establishmentId, academicYearId, academicUnitId, published.get());
        return new IssuancePreview(
                academicYearId,
                academicUnitId,
                published.get(),
                plan.activeEnrollments(),
                plan.obligations().stream().map(planned -> planned.enrollment().studentId()).distinct().count(),
                plan.obligations().size(),
                plan.alreadyIssued(),
                total(plan),
                feeTotals(plan),
                token(published.get(), plan));
    }

    /**
     * Le jeton de l'aperçu lie la confirmation à ce qui a été vu : si le tarif ou les inscrits ont changé,
     * rien n'est émis. Tout ou rien, dans une seule transaction.
     */
    @Transactional
    public Run confirmIssuance(UUID establishmentId, UUID academicYearId, UUID academicUnitId, UUID catalogVersionId, String previewToken) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_ISSUE);
        AcademicCatalog.YearSummary year = requireYear(establishmentId, academicYearId);
        if (year.closed()) {
            throw ApiException.conflict("ACADEMIC_YEAR_CLOSED", "Cette année académique est clôturée.");
        }
        Version version = catalogs.publishedVersion(establishmentId, academicYearId)
                .filter(found -> found.id().equals(catalogVersionId))
                .orElseThrow(ObligationService::previewOutdated);
        Plan plan = plan(establishmentId, academicYearId, academicUnitId, version);
        if (!token(version, plan).equals(previewToken)) {
            throw previewOutdated();
        }

        UUID runId = UUID.randomUUID();
        int created = 0;
        long totalCreated = 0;
        for (Planned planned : plan.obligations()) {
            if (issue(establishmentId, planned.enrollment(), version, planned.item(), "BATCH", runId, actorId)) {
                created++;
                totalCreated = Math.addExact(totalCreated, planned.item().amountMinor());
            }
        }
        Run run = new Run(runId, academicYearId, academicUnitId, version.id(), created, totalCreated, clock.instant());
        obligations.insertRun(establishmentId, run, actorId);
        audit.record(
                establishmentId, actorId, "billing.issuance.confirmed", "issuance_run", runId,
                Map.of("catalogVersionId", version.id(), "obligationsCreated", created, "totalMinor", String.valueOf(totalCreated)));
        return run;
    }

    @Transactional(readOnly = true)
    public List<Run> listRuns(UUID establishmentId, UUID academicYearId) {
        access.require(establishmentId, Permission.BILLING_READ);
        return obligations.listRuns(establishmentId, academicYearId);
    }

    // Émission unitaire : un frais facultatif, ou une charge ajoutée pour un élève précis

    @Transactional
    public ManualIssue issueManually(UUID establishmentId, UUID enrollmentId, UUID feeDefinitionId) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_ISSUE);
        EnrollmentSummary enrollment = academics.findEnrollment(establishmentId, enrollmentId)
                .orElseThrow(() -> ApiException.notFound("ENROLLMENT_NOT_FOUND", "Inscription introuvable."));
        if (!enrollment.active()) {
            throw ApiException.conflict("ENROLLMENT_NOT_ACTIVE", "Cette inscription n'est plus active.");
        }
        Version version = catalogs.publishedVersion(establishmentId, enrollment.academicYearId())
                .orElseThrow(() -> ApiException.conflict("NO_PUBLISHED_CATALOG", "Aucun tarif n'est publié pour cette année."));
        Item item = FeeResolution.applicableTo(
                        enrollment.academicUnitId(), catalogs.items(establishmentId, version.id()), unitsById(establishmentId))
                .stream()
                .filter(candidate -> candidate.feeDefinitionId().equals(feeDefinitionId))
                .findFirst()
                .orElseThrow(() -> ApiException.invalid("FEE_NOT_APPLICABLE", "Ce frais ne s'applique pas à cette inscription."));
        boolean created = issue(establishmentId, enrollment, version, item, "MANUAL", null, actorId);
        return new ManualIssue(
                created, obligations.findByCharge(establishmentId, enrollment.studentId(), feeDefinitionId).orElseThrow());
    }

    // Lecture

    @Transactional(readOnly = true)
    public List<Obligation> list(UUID establishmentId, UUID academicYearId, UUID studentId, PageQuery page) {
        access.require(establishmentId, Permission.BILLING_READ);
        return obligations.list(establishmentId, academicYearId, studentId, page);
    }

    @Transactional(readOnly = true)
    public Detail read(UUID establishmentId, UUID obligationId) {
        access.require(establishmentId, Permission.BILLING_READ);
        Obligation obligation = obligations.find(establishmentId, obligationId)
                .orElseThrow(() -> ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable."));
        return new Detail(
                obligation,
                ObligationStatus.of(obligation.dueMinor()),
                obligations.installments(establishmentId, obligationId),
                obligations.entries(establishmentId, obligationId));
    }

    @Transactional(readOnly = true)
    public List<Balance> balances(UUID establishmentId, UUID studentId) {
        access.require(establishmentId, Permission.BILLING_READ);
        requireStudent(establishmentId, studentId);
        ZoneId zone = ZoneId.of(establishments.find(establishmentId).orElseThrow().timezone());
        return obligations.balances(establishmentId, studentId, LocalDate.now(clock.withZone(zone)));
    }

    // Commun

    /** Le seul endroit qui crée une obligation. Renvoie faux si cette charge est déjà émise pour ce dossier. */
    private boolean issue(
            UUID establishmentId, EnrollmentSummary enrollment, Version version, Item item, String source, UUID runId, UUID actorId) {
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String reference = newReference();
        boolean created = obligations.insertIfAbsent(establishmentId, new NewObligation(
                id,
                enrollment.academicYearId(),
                enrollment.studentId(),
                enrollment.id(),
                item.feeDefinitionId(),
                version.id(),
                item.id(),
                reference,
                item.feeCode(),
                item.label(),
                item.category(),
                version.currency(),
                item.amountMinor(),
                source,
                runId,
                now,
                actorId));
        if (!created) {
            return false;
        }
        int seq = 1;
        for (Installment installment : item.installments()) {
            obligations.insertInstallment(establishmentId, id, seq++, installment.dueOn(), installment.amountMinor());
        }
        obligations.insertIssuedEntry(establishmentId, id, item.amountMinor(), now, actorId);
        audit.record(
                establishmentId, actorId, "billing.obligation.issued", "obligation", id,
                Map.of(
                        "reference", reference,
                        "studentId", enrollment.studentId(),
                        "feeDefinitionId", item.feeDefinitionId(),
                        "catalogVersionId", version.id(),
                        "amountMinor", String.valueOf(item.amountMinor()),
                        "source", source));
        return true;
    }

    private Plan plan(UUID establishmentId, UUID academicYearId, UUID academicUnitId, Version version) {
        Map<UUID, UnitSummary> units = unitsById(establishmentId);
        List<UUID> scope = null;
        if (academicUnitId != null) {
            if (!units.containsKey(academicUnitId)) {
                throw ApiException.notFound("ACADEMIC_UNIT_NOT_FOUND", "Unité académique introuvable.");
            }
            scope = units.keySet().stream().filter(unit -> isWithin(unit, academicUnitId, units)).toList();
        }
        List<EnrollmentSummary> enrollments = academics.activeEnrollments(establishmentId, academicYearId, scope, maxIssuanceSize + 1);
        if (enrollments.size() > maxIssuanceSize) {
            throw ApiException.invalid(
                    "ISSUANCE_TOO_LARGE", "Trop d'inscriptions pour un seul lot : limitez l'émission à une unité académique.");
        }
        List<Item> items = catalogs.items(establishmentId, version.id()).stream()
                .filter(item -> "AUTOMATIC".equals(item.application()))
                .toList();
        Set<List<UUID>> issued = obligations.issuedCharges(establishmentId, academicYearId);
        List<Planned> planned = new ArrayList<>();
        long alreadyIssued = 0;
        for (EnrollmentSummary enrollment : enrollments) {
            for (Item item : FeeResolution.applicableTo(enrollment.academicUnitId(), items, units)) {
                if (issued.contains(List.of(enrollment.studentId(), item.feeDefinitionId()))) {
                    alreadyIssued++;
                } else {
                    planned.add(new Planned(enrollment, item));
                }
            }
        }
        return new Plan(planned, enrollments.size(), alreadyIssued);
    }

    private List<Item> automaticFees(UUID establishmentId, Version version, UUID academicUnitId, Map<UUID, UnitSummary> units) {
        return FeeResolution.applicableTo(academicUnitId, catalogs.items(establishmentId, version.id()), units).stream()
                .filter(item -> "AUTOMATIC".equals(item.application()))
                .toList();
    }

    private static boolean isWithin(UUID unit, UUID ancestor, Map<UUID, UnitSummary> units) {
        for (UnitSummary current = units.get(unit); current != null; current = current.parentId() == null ? null : units.get(current.parentId())) {
            if (current.id().equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private static long total(Plan plan) {
        long total = 0;
        for (Planned planned : plan.obligations()) {
            total = Math.addExact(total, planned.item().amountMinor());
        }
        return total;
    }

    private static List<FeeTotal> feeTotals(Plan plan) {
        Map<UUID, FeeTotal> totals = new LinkedHashMap<>();
        for (Planned planned : plan.obligations()) {
            Item item = planned.item();
            totals.merge(
                    item.feeDefinitionId(),
                    new FeeTotal(item.feeDefinitionId(), item.feeCode(), item.label(), 1, item.amountMinor()),
                    (a, b) -> new FeeTotal(a.feeDefinitionId(), a.feeCode(), a.label(), a.obligations() + 1, Math.addExact(a.totalMinor(), b.totalMinor())));
        }
        return totals.values().stream().sorted(Comparator.comparing(FeeTotal::feeCode)).toList();
    }

    private static String token(Version version, Plan plan) {
        List<String> lines = plan.obligations().stream()
                .map(planned -> planned.enrollment().id() + "|" + planned.item().id() + "|" + planned.item().amountMinor())
                .sorted()
                .toList();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(version.id().toString().getBytes(StandardCharsets.UTF_8));
            for (String line : lines) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException previewOutdated() {
        return ApiException.conflict("ISSUANCE_PREVIEW_OUTDATED", "La situation a changé depuis l'aperçu : consultez-le à nouveau avant de confirmer.");
    }

    private String newReference() {
        StringBuilder reference = new StringBuilder("OBL-");
        for (int i = 0; i < REFERENCE_LENGTH; i++) {
            reference.append(REFERENCE_ALPHABET[random.nextInt(REFERENCE_ALPHABET.length)]);
        }
        return reference.toString();
    }

    private AcademicCatalog.YearSummary requireYear(UUID establishmentId, UUID academicYearId) {
        return academics.findYear(establishmentId, academicYearId)
                .orElseThrow(() -> ApiException.notFound("ACADEMIC_YEAR_NOT_FOUND", "Année académique introuvable."));
    }

    private void requireStudent(UUID establishmentId, UUID studentId) {
        if (students.find(establishmentId, studentId).isEmpty()) {
            throw ApiException.notFound("STUDENT_NOT_FOUND", "Dossier introuvable.");
        }
    }

    private Map<UUID, UnitSummary> unitsById(UUID establishmentId) {
        return academics.units(establishmentId).stream().collect(Collectors.toMap(UnitSummary::id, Function.identity()));
    }
}
