package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.internal.FeeCatalogRepository.Definition;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Installment;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Item;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Period;
import com.scolapay.core.billing.internal.FeeCatalogRepository.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Les montants circulent en chaîne décimale d'unités minimales, voir la décision 0012. */
@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/billing")
class FeeCatalogController {

    private static final String CODE = "^[A-Z0-9][A-Z0-9-]{0,30}$";
    private static final String AMOUNT = "^[1-9][0-9]{0,14}$";

    record CreatePeriodRequest(
            @NotNull UUID academicYearId,
            @NotNull @Pattern(regexp = CODE) String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn) {}

    record UpdatePeriodRequest(@Size(max = 100) String name, @Pattern(regexp = "^(ACTIVE|ARCHIVED)$") String status) {}

    record CreateDefinitionRequest(
            @NotNull UUID academicYearId, @NotNull UUID billingPeriodId, @NotBlank @Size(max = 200) String name) {}

    record CreateDraftRequest(@NotNull UUID academicYearId) {}

    record InstallmentRequest(@NotNull LocalDate dueOn, @NotNull @Pattern(regexp = AMOUNT) String amountMinor) {}

    record ItemRequest(
            @NotNull UUID feeDefinitionId,
            @NotNull @Pattern(regexp = CODE) String feeCode,
            @NotBlank @Size(max = 200) String label,
            @NotNull @Pattern(regexp = "^(REGISTRATION|TUITION|EXAM|TRANSPORT|CANTEEN|OTHER)$") String category,
            @NotNull @Pattern(regexp = "^(AUTOMATIC|ON_REQUEST)$") String application,
            @NotNull @Pattern(regexp = AMOUNT) String amountMinor,
            UUID academicUnitId,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull InstallmentRequest> installments) {

        Item toItem() {
            return new Item(
                    null, feeDefinitionId, feeCode, label, category, application, Long.parseLong(amountMinor), null, academicUnitId,
                    installments.stream()
                            .map(installment -> new Installment(installment.dueOn(), Long.parseLong(installment.amountMinor())))
                            .toList());
        }
    }

    record InstallmentView(LocalDate dueOn, String amountMinor) {}

    record ItemView(
            UUID id,
            UUID feeDefinitionId,
            String feeCode,
            String label,
            String category,
            String application,
            String amountMinor,
            UUID billingPeriodId,
            UUID academicUnitId,
            List<InstallmentView> installments) {

        static ItemView of(Item item) {
            return new ItemView(
                    item.id(), item.feeDefinitionId(), item.feeCode(), item.label(), item.category(), item.application(),
                    String.valueOf(item.amountMinor()), item.billingPeriodId(), item.academicUnitId(),
                    item.installments().stream()
                            .map(installment -> new InstallmentView(installment.dueOn(), String.valueOf(installment.amountMinor())))
                            .toList());
        }
    }

    record CatalogView(
            UUID id,
            UUID academicYearId,
            int version,
            String status,
            String currency,
            Instant createdAt,
            Instant publishedAt,
            Instant supersededAt,
            List<ItemView> items) {

        static CatalogView of(Version version, List<Item> items) {
            return new CatalogView(
                    version.id(), version.academicYearId(), version.version(), version.status(), version.currency(),
                    version.createdAt(), version.publishedAt(), version.supersededAt(),
                    items.stream().map(ItemView::of).toList());
        }
    }

    record ApplicableView(UUID catalogVersionId, Integer version, String currency, List<ItemView> fees) {}

    private final BillingPeriodService periods;
    private final FeeCatalogService catalogs;

    FeeCatalogController(BillingPeriodService periods, FeeCatalogService catalogs) {
        this.periods = periods;
        this.catalogs = catalogs;
    }

    // Périodes de facturation

    @PostMapping("/periods")
    @ResponseStatus(HttpStatus.CREATED)
    Period createPeriod(@PathVariable UUID establishmentId, @Valid @RequestBody CreatePeriodRequest request) {
        return periods.create(
                establishmentId, request.academicYearId(), request.code(), request.name(), request.startsOn(), request.endsOn());
    }

    @GetMapping("/periods")
    List<Period> listPeriods(@PathVariable UUID establishmentId, @RequestParam UUID academicYearId) {
        return periods.list(establishmentId, academicYearId);
    }

    @PatchMapping("/periods/{periodId}")
    Period updatePeriod(
            @PathVariable UUID establishmentId, @PathVariable UUID periodId, @Valid @RequestBody UpdatePeriodRequest request) {
        return periods.update(establishmentId, periodId, request.name(), request.status());
    }

    // Identité des charges : une nouvelle charge se crée ici, intentionnellement

    @PostMapping("/fee-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    Definition createDefinition(@PathVariable UUID establishmentId, @Valid @RequestBody CreateDefinitionRequest request) {
        return catalogs.createDefinition(establishmentId, request.academicYearId(), request.billingPeriodId(), request.name());
    }

    @GetMapping("/fee-definitions")
    List<Definition> listDefinitions(@PathVariable UUID establishmentId, @RequestParam UUID academicYearId) {
        return catalogs.listDefinitions(establishmentId, academicYearId);
    }

    // Catalogue

    @PostMapping("/fee-catalogs")
    @ResponseStatus(HttpStatus.CREATED)
    CatalogView createDraft(@PathVariable UUID establishmentId, @Valid @RequestBody CreateDraftRequest request) {
        FeeCatalogService.Catalog catalog = catalogs.createDraft(establishmentId, request.academicYearId());
        return CatalogView.of(catalog.version(), catalog.items());
    }

    @GetMapping("/fee-catalogs")
    List<CatalogView> listCatalogs(@PathVariable UUID establishmentId, @RequestParam UUID academicYearId) {
        return catalogs.list(establishmentId, academicYearId).stream()
                .map(version -> CatalogView.of(version, List.of()))
                .toList();
    }

    @GetMapping("/fee-catalogs/{versionId}")
    CatalogView readCatalog(@PathVariable UUID establishmentId, @PathVariable UUID versionId) {
        FeeCatalogService.Catalog catalog = catalogs.read(establishmentId, versionId);
        return CatalogView.of(catalog.version(), catalog.items());
    }

    @DeleteMapping("/fee-catalogs/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discardDraft(@PathVariable UUID establishmentId, @PathVariable UUID versionId) {
        catalogs.discardDraft(establishmentId, versionId);
    }

    @PostMapping("/fee-catalogs/{versionId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    ItemView addItem(
            @PathVariable UUID establishmentId, @PathVariable UUID versionId, @Valid @RequestBody ItemRequest request) {
        return ItemView.of(catalogs.addItem(establishmentId, versionId, request.toItem()));
    }

    @PutMapping("/fee-catalogs/{versionId}/items/{itemId}")
    ItemView updateItem(
            @PathVariable UUID establishmentId,
            @PathVariable UUID versionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemRequest request) {
        return ItemView.of(catalogs.updateItem(establishmentId, versionId, itemId, request.toItem()));
    }

    @DeleteMapping("/fee-catalogs/{versionId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeItem(@PathVariable UUID establishmentId, @PathVariable UUID versionId, @PathVariable UUID itemId) {
        catalogs.removeItem(establishmentId, versionId, itemId);
    }

    @GetMapping("/fee-catalogs/{versionId}/impact")
    FeeCatalogService.Impact impact(@PathVariable UUID establishmentId, @PathVariable UUID versionId) {
        return catalogs.impact(establishmentId, versionId);
    }

    @PostMapping("/fee-catalogs/{versionId}/publish")
    CatalogView publish(@PathVariable UUID establishmentId, @PathVariable UUID versionId) {
        FeeCatalogService.Catalog catalog = catalogs.publish(establishmentId, versionId);
        return CatalogView.of(catalog.version(), catalog.items());
    }

    // Frais applicables à une unité, d'après le seul tarif publié

    @GetMapping("/applicable-fees")
    ApplicableView applicableFees(
            @PathVariable UUID establishmentId, @RequestParam UUID academicYearId, @RequestParam UUID academicUnitId) {
        FeeCatalogService.Applicable applicable = catalogs.applicableFees(establishmentId, academicYearId, academicUnitId);
        Version version = applicable.version();
        return new ApplicableView(
                version == null ? null : version.id(),
                version == null ? null : version.version(),
                version == null ? null : version.currency(),
                applicable.items().stream().map(ItemView::of).toList());
    }
}
