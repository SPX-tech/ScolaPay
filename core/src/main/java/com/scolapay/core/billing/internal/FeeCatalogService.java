package com.scolapay.core.billing.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Installment;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Item;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Period;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Version;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Establishments;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.schooling.AcademicCatalog;
import com.scolapay.core.schooling.AcademicCatalog.UnitSummary;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue des frais d'une année. Un brouillon se modifie librement, une version publiée ne change
 * plus jamais. <strong>Rien ici ne crée de dette</strong>, pas même la publication : un tarif dit ce
 * qu'un frais coûterait, l'émission d'une obligation est un acte distinct et explicite.
 */
@Service
public class FeeCatalogService {

    public record Catalog(Version version, List<Item> items) {}

    public record Applicable(Version version, List<Item> items) {}

    public enum FeeChange { NEW, CHANGED, UNCHANGED, REMOVED }

    public record FeeImpact(String feeCode, FeeChange change, String application, long eligibleEnrollments) {}

    public record Impact(Version version, Integer previousVersion, long activeEnrollments, List<FeeImpact> fees) {}

    private static final int MAX_INSTALLMENTS = 24;

    private final FeeCatalogRepository repository;
    private final AcademicCatalog academics;
    private final Establishments establishments;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final Clock clock;

    public FeeCatalogService(
            FeeCatalogRepository repository,
            AcademicCatalog academics,
            Establishments establishments,
            EstablishmentAccess access,
            AuditTrail audit,
            Clock clock) {
        this.repository = repository;
        this.academics = academics;
        this.establishments = establishments;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    /** Un nouveau brouillon part du tarif en vigueur : on ne saisit que ce qui change. */
    @Transactional
    public Catalog createDraft(UUID establishmentId, UUID academicYearId) {
        UUID actorId = access.require(establishmentId, Permission.FEES_MANAGE);
        requireOpenYear(establishmentId, academicYearId);
        String currency = establishments.find(establishmentId).orElseThrow().currency();
        // L'index unique partiel tranche les brouillons concurrents : le perdant reçoit un 409.
        Version draft = new Version(
                UUID.randomUUID(),
                academicYearId,
                repository.nextVersionNumber(establishmentId, academicYearId),
                "DRAFT",
                currency,
                clock.instant(),
                null,
                null);
        repository.insertVersion(establishmentId, draft, actorId);
        repository.publishedVersion(establishmentId, academicYearId).ifPresent(published -> {
            for (Item item : repository.items(establishmentId, published.id())) {
                repository.insertItem(establishmentId, academicYearId, draft.id(), new Item(
                        UUID.randomUUID(), item.feeCode(), item.label(), item.category(), item.application(),
                        item.amountMinor(), item.billingPeriodId(), item.academicUnitId(), item.installments()));
            }
        });
        audit.record(
                establishmentId, actorId, "billing.catalog.draft.created", "fee_catalog_version", draft.id(),
                Map.of("academicYearId", academicYearId, "version", draft.version()));
        return new Catalog(draft, repository.items(establishmentId, draft.id()));
    }

    @Transactional(readOnly = true)
    public List<Version> list(UUID establishmentId, UUID academicYearId) {
        access.require(establishmentId, Permission.ESTABLISHMENT_READ);
        return repository.listVersions(establishmentId, academicYearId);
    }

    @Transactional(readOnly = true)
    public Catalog read(UUID establishmentId, UUID versionId) {
        access.require(establishmentId, Permission.ESTABLISHMENT_READ);
        Version version = requireVersion(establishmentId, versionId);
        return new Catalog(version, repository.items(establishmentId, versionId));
    }

    @Transactional
    public void discardDraft(UUID establishmentId, UUID versionId) {
        UUID actorId = access.require(establishmentId, Permission.FEES_MANAGE);
        lockDraft(establishmentId, versionId);
        repository.deleteDraft(establishmentId, versionId);
        audit.record(establishmentId, actorId, "billing.catalog.draft.discarded", "fee_catalog_version", versionId, Map.of());
    }

    @Transactional
    public Item addItem(UUID establishmentId, UUID versionId, Item input) {
        UUID actorId = access.require(establishmentId, Permission.FEES_MANAGE);
        Version draft = lockDraft(establishmentId, versionId);
        Item item = withId(input, UUID.randomUUID());
        validate(establishmentId, draft, item);
        repository.insertItem(establishmentId, draft.academicYearId(), versionId, item);
        audit.record(
                establishmentId, actorId, "billing.catalog.item.added", "fee_item", item.id(),
                Map.of("feeCode", item.feeCode(), "amountMinor", String.valueOf(item.amountMinor())));
        return item;
    }

    @Transactional
    public Item updateItem(UUID establishmentId, UUID versionId, UUID itemId, Item input) {
        UUID actorId = access.require(establishmentId, Permission.FEES_MANAGE);
        Version draft = lockDraft(establishmentId, versionId);
        Item item = withId(input, itemId);
        validate(establishmentId, draft, item);
        if (repository.updateItem(establishmentId, versionId, item) == 0) {
            throw ApiException.notFound("FEE_ITEM_NOT_FOUND", "Ligne de tarif introuvable.");
        }
        audit.record(
                establishmentId, actorId, "billing.catalog.item.updated", "fee_item", itemId,
                Map.of("feeCode", item.feeCode(), "amountMinor", String.valueOf(item.amountMinor())));
        return item;
    }

    @Transactional
    public void removeItem(UUID establishmentId, UUID versionId, UUID itemId) {
        UUID actorId = access.require(establishmentId, Permission.FEES_MANAGE);
        lockDraft(establishmentId, versionId);
        if (repository.deleteItem(establishmentId, versionId, itemId) == 0) {
            throw ApiException.notFound("FEE_ITEM_NOT_FOUND", "Ligne de tarif introuvable.");
        }
        audit.record(establishmentId, actorId, "billing.catalog.item.removed", "fee_item", itemId, Map.of());
    }

    /** Fige la version et remplace la précédente, qui reste consultable telle quelle. */
    @Transactional
    public Catalog publish(UUID establishmentId, UUID versionId) {
        UUID actorId = access.require(establishmentId, Permission.FEES_PUBLISH);
        Version draft = lockDraft(establishmentId, versionId);
        requireOpenYear(establishmentId, draft.academicYearId());
        List<Item> items = repository.items(establishmentId, versionId);
        if (items.isEmpty()) {
            throw ApiException.invalid("CATALOG_EMPTY", "Un catalogue vide ne se publie pas.");
        }
        requireActiveReferences(establishmentId, items);

        Instant now = clock.instant();
        Optional<Version> current = repository.publishedVersion(establishmentId, draft.academicYearId());
        current.ifPresent(previous -> repository.markSuperseded(establishmentId, previous.id(), now));
        repository.markPublished(establishmentId, versionId, actorId, now);
        audit.record(
                establishmentId, actorId, "billing.catalog.published", "fee_catalog_version", versionId,
                Map.of(
                        "version", draft.version(),
                        "items", items.size(),
                        "replacedVersion", current.map(Version::version).orElse(0)));
        return new Catalog(repository.findVersion(establishmentId, versionId).orElseThrow(), items);
    }

    /** Seule une version publiée s'applique : un brouillon n'a aucun effet, même visible. */
    @Transactional(readOnly = true)
    public Applicable applicableFees(UUID establishmentId, UUID academicYearId, UUID academicUnitId) {
        access.require(establishmentId, Permission.ESTABLISHMENT_READ);
        Map<UUID, UnitSummary> units = unitsById(establishmentId);
        if (!units.containsKey(academicUnitId)) {
            throw ApiException.notFound("ACADEMIC_UNIT_NOT_FOUND", "Unité académique introuvable.");
        }
        return repository.publishedVersion(establishmentId, academicYearId)
                .map(version -> new Applicable(
                        version,
                        FeeResolution.applicableTo(academicUnitId, repository.items(establishmentId, version.id()), units)))
                .orElseGet(() -> new Applicable(null, List.of()));
    }

    /**
     * Ce que cette version changerait pour les élèves déjà inscrits. Une simple lecture : elle sert à
     * décider, et n'écrit rien. Compare avec la dernière version publiée avant celle-ci.
     */
    @Transactional(readOnly = true)
    public Impact impact(UUID establishmentId, UUID versionId) {
        access.require(establishmentId, Permission.FEES_MANAGE);
        Version version = requireVersion(establishmentId, versionId);
        Optional<Version> previous = repository.previousPublished(establishmentId, version.academicYearId(), version.version());
        List<Item> items = repository.items(establishmentId, versionId);
        List<Item> previousItems = previous.map(found -> repository.items(establishmentId, found.id())).orElse(List.of());
        Map<UUID, Long> enrollments = academics.activeEnrollmentsByUnit(establishmentId, version.academicYearId());
        Map<UUID, UnitSummary> units = unitsById(establishmentId);

        Set<String> codes = new TreeSet<>();
        items.forEach(item -> codes.add(item.feeCode()));
        previousItems.forEach(item -> codes.add(item.feeCode()));
        List<FeeImpact> fees = new ArrayList<>();
        for (String code : codes) {
            List<Item> now = withCode(items, code);
            List<Item> before = withCode(previousItems, code);
            FeeChange change = before.isEmpty()
                    ? FeeChange.NEW
                    : now.isEmpty() ? FeeChange.REMOVED : signature(now).equals(signature(before)) ? FeeChange.UNCHANGED : FeeChange.CHANGED;
            List<Item> reference = now.isEmpty() ? before : now;
            fees.add(new FeeImpact(code, change, reference.getFirst().application(), eligible(reference, enrollments, units)));
        }
        long active = enrollments.values().stream().mapToLong(Long::longValue).sum();
        return new Impact(version, previous.map(Version::version).orElse(null), active, fees);
    }

    private static List<Item> withCode(List<Item> items, String code) {
        return items.stream().filter(item -> item.feeCode().equals(code)).toList();
    }

    private static long eligible(List<Item> itemsOfCode, Map<UUID, Long> enrollments, Map<UUID, UnitSummary> units) {
        return enrollments.entrySet().stream()
                .filter(entry -> !FeeResolution.applicableTo(entry.getKey(), itemsOfCode, units).isEmpty())
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private static Set<List<Object>> signature(List<Item> items) {
        Set<List<Object>> signature = new HashSet<>();
        for (Item item : items) {
            signature.add(java.util.Arrays.asList(
                    item.academicUnitId(), item.amountMinor(), item.billingPeriodId(), item.label(), item.category(),
                    item.application(), item.installments()));
        }
        return signature;
    }

    private void validate(UUID establishmentId, Version draft, Item item) {
        Period period = repository.findPeriod(establishmentId, item.billingPeriodId())
                .filter(found -> found.academicYearId().equals(draft.academicYearId()))
                .orElseThrow(() -> ApiException.notFound("BILLING_PERIOD_NOT_FOUND", "Période de facturation introuvable pour cette année."));
        if (!period.active()) {
            throw ApiException.conflict("BILLING_PERIOD_ARCHIVED", "Cette période de facturation est archivée.");
        }
        if (item.academicUnitId() != null) {
            UnitSummary unit = Optional.ofNullable(unitsById(establishmentId).get(item.academicUnitId()))
                    .orElseThrow(() -> ApiException.notFound("ACADEMIC_UNIT_NOT_FOUND", "Unité académique introuvable."));
            if (!unit.active()) {
                throw ApiException.conflict("ACADEMIC_UNIT_ARCHIVED", "Cette unité académique est archivée.");
            }
        }
        validateInstallments(item);
        if (repository.feeCodeBoundToAnotherPeriod(
                establishmentId, draft.academicYearId(), draft.id(), item.id(), item.feeCode(), item.billingPeriodId())) {
            throw ApiException.conflict(
                    "FEE_CODE_BOUND_TO_PERIOD",
                    "Ce code de frais est déjà attaché à une autre période. Utilisez un code distinct par période.");
        }
    }

    private static void validateInstallments(Item item) {
        List<Installment> installments = item.installments();
        if (installments.size() > MAX_INSTALLMENTS) {
            throw ApiException.invalid("TOO_MANY_INSTALLMENTS", "Un frais compte au plus " + MAX_INSTALLMENTS + " échéances.");
        }
        long total = 0;
        for (int i = 0; i < installments.size(); i++) {
            total += installments.get(i).amountMinor();
            if (i > 0 && !installments.get(i).dueOn().isAfter(installments.get(i - 1).dueOn())) {
                throw ApiException.invalid("INSTALLMENTS_NOT_ORDERED", "Les échéances doivent se suivre dans le temps.");
            }
        }
        if (total != item.amountMinor()) {
            throw ApiException.invalid("INSTALLMENTS_MISMATCH", "La somme des échéances doit égaler le montant du frais.");
        }
    }

    private void requireActiveReferences(UUID establishmentId, List<Item> items) {
        Map<UUID, UnitSummary> units = unitsById(establishmentId);
        for (Item item : items) {
            Period period = repository.findPeriod(establishmentId, item.billingPeriodId()).orElseThrow();
            UnitSummary unit = item.academicUnitId() == null ? null : units.get(item.academicUnitId());
            if (!period.active() || (unit != null && !unit.active())) {
                throw ApiException.conflict(
                        "CATALOG_REFERENCES_ARCHIVED",
                        "Le frais " + item.feeCode() + " vise une période ou une unité archivée.");
            }
        }
    }

    private void requireOpenYear(UUID establishmentId, UUID academicYearId) {
        AcademicCatalog.YearSummary year = academics.findYear(establishmentId, academicYearId)
                .orElseThrow(() -> ApiException.notFound("ACADEMIC_YEAR_NOT_FOUND", "Année académique introuvable."));
        if (year.closed()) {
            throw ApiException.conflict("ACADEMIC_YEAR_CLOSED", "Cette année académique est clôturée.");
        }
    }

    private Version requireVersion(UUID establishmentId, UUID versionId) {
        return repository.findVersion(establishmentId, versionId)
                .orElseThrow(() -> ApiException.notFound("FEE_CATALOG_NOT_FOUND", "Catalogue introuvable."));
    }

    private Version lockDraft(UUID establishmentId, UUID versionId) {
        Version version = repository.lockVersion(establishmentId, versionId)
                .orElseThrow(() -> ApiException.notFound("FEE_CATALOG_NOT_FOUND", "Catalogue introuvable."));
        if (!version.draft()) {
            throw ApiException.conflict("CATALOG_NOT_DRAFT", "Un catalogue publié ne se modifie plus : créez une nouvelle version.");
        }
        return version;
    }

    private Map<UUID, UnitSummary> unitsById(UUID establishmentId) {
        return academics.units(establishmentId).stream().collect(Collectors.toMap(UnitSummary::id, Function.identity()));
    }

    private static Item withId(Item input, UUID id) {
        return new Item(
                Objects.requireNonNull(id), input.feeCode(), input.label().trim(), input.category(), input.application(),
                input.amountMinor(), input.billingPeriodId(), input.academicUnitId(), input.installments());
    }
}
