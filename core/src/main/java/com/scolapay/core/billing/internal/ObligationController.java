package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.internal.FeeCatalogController.ItemView;
import com.scolapay.core.billing.internal.ObligationRepository.Balance;
import com.scolapay.core.billing.internal.ObligationRepository.Entry;
import com.scolapay.core.billing.internal.ObligationRepository.InstallmentRow;
import com.scolapay.core.billing.internal.ObligationRepository.Obligation;
import com.scolapay.core.billing.internal.ObligationRepository.Run;
import com.scolapay.core.shared.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aucune route ne modifie, ne solde ni ne règle une obligation, et aucune n'écrit de montant dû. Les
 * montants circulent en chaîne décimale d'unités minimales, voir la décision 0012.
 */
@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/billing")
class ObligationController {

    record ConfirmIssuanceRequest(
            @NotNull UUID academicYearId,
            UUID academicUnitId,
            @NotNull UUID catalogVersionId,
            @NotNull @Pattern(regexp = "^[0-9a-f]{64}$") String previewToken) {}

    record ManualIssueRequest(@NotNull UUID enrollmentId, @NotNull UUID feeDefinitionId) {}

    record EnrollmentFeeView(ItemView fee, boolean alreadyIssued) {}

    record EnrollmentPreviewView(UUID catalogVersionId, Integer version, String currency, String totalToIssueMinor, List<EnrollmentFeeView> fees) {}

    record FeeTotalView(UUID feeDefinitionId, String feeCode, String label, long obligations, String totalMinor) {}

    record IssuancePreviewView(
            UUID academicYearId,
            UUID academicUnitId,
            UUID catalogVersionId,
            Integer version,
            String currency,
            long activeEnrollments,
            long studentsConcerned,
            long obligationsToCreate,
            long alreadyIssued,
            String totalMinor,
            List<FeeTotalView> fees,
            String previewToken) {}

    record PendingView(
            UUID enrollmentId, UUID studentId, String studentNumber, String fullName, UUID academicUnitId, String totalMinor, List<ItemView> fees) {}

    record PendingIssuancesView(UUID catalogVersionId, Integer version, String currency, long pendingEnrollments, String totalMinor, List<PendingView> pending) {}

    record RunView(
            UUID id, UUID academicYearId, UUID academicUnitId, UUID catalogVersionId, int obligationsCreated, String totalMinor, Instant confirmedAt) {

        static RunView of(Run run) {
            return new RunView(
                    run.id(), run.academicYearId(), run.academicUnitId(), run.catalogVersionId(), run.obligationsCreated(),
                    String.valueOf(run.totalMinor()), run.confirmedAt());
        }
    }

    record ObligationView(
            UUID id,
            UUID academicYearId,
            UUID studentId,
            UUID enrollmentId,
            UUID feeDefinitionId,
            UUID billingPeriodId,
            UUID catalogVersionId,
            String reference,
            String feeCode,
            String label,
            String category,
            String currency,
            String issuedAmountMinor,
            String dueMinor,
            ObligationService.ObligationStatus status,
            String source,
            Instant issuedAt) {

        static ObligationView of(Obligation obligation) {
            return new ObligationView(
                    obligation.id(), obligation.academicYearId(), obligation.studentId(), obligation.enrollmentId(),
                    obligation.feeDefinitionId(), obligation.billingPeriodId(), obligation.catalogVersionId(),
                    obligation.reference(), obligation.feeCode(), obligation.label(), obligation.category(), obligation.currency(),
                    String.valueOf(obligation.issuedAmountMinor()), String.valueOf(obligation.dueMinor()),
                    ObligationService.ObligationStatus.of(obligation.dueMinor()), obligation.source(), obligation.issuedAt());
        }
    }

    record InstallmentView(int seq, LocalDate dueOn, String amountMinor) {

        static InstallmentView of(InstallmentRow row) {
            return new InstallmentView(row.seq(), row.dueOn(), String.valueOf(row.amountMinor()));
        }
    }

    record EntryView(UUID id, String kind, String amountMinor, String reason, Instant recordedAt, UUID recordedBy) {

        static EntryView of(Entry entry) {
            return new EntryView(
                    entry.id(), entry.kind(), String.valueOf(entry.amountMinor()), entry.reason(), entry.recordedAt(), entry.recordedBy());
        }
    }

    record DetailView(ObligationView obligation, List<InstallmentView> installments, List<EntryView> entries) {}

    record BalanceView(String currency, String issuedMinor, String dueMinor, LocalDate nextDueOn) {

        static BalanceView of(Balance balance) {
            return new BalanceView(
                    balance.currency(), String.valueOf(balance.issuedMinor()), String.valueOf(balance.dueMinor()), balance.nextDueOn());
        }
    }

    private final ObligationService obligations;

    ObligationController(ObligationService obligations) {
        this.obligations = obligations;
    }

    /** À consulter avant de confirmer une inscription : rien n'est créé. */
    @GetMapping("/enrollment-preview")
    EnrollmentPreviewView enrollmentPreview(
            @PathVariable UUID establishmentId,
            @RequestParam UUID academicYearId,
            @RequestParam UUID academicUnitId,
            @RequestParam(required = false) UUID studentId) {
        ObligationService.EnrollmentPreview preview = obligations.enrollmentPreview(establishmentId, academicYearId, academicUnitId, studentId);
        return new EnrollmentPreviewView(
                preview.version() == null ? null : preview.version().id(),
                preview.version() == null ? null : preview.version().version(),
                preview.version() == null ? null : preview.version().currency(),
                String.valueOf(preview.totalToIssueMinor()),
                preview.fees().stream().map(fee -> new EnrollmentFeeView(ItemView.of(fee.item()), fee.alreadyIssued())).toList());
    }

    @GetMapping("/issuance-preview")
    IssuancePreviewView issuancePreview(
            @PathVariable UUID establishmentId, @RequestParam UUID academicYearId, @RequestParam(required = false) UUID academicUnitId) {
        ObligationService.IssuancePreview preview = obligations.issuancePreview(establishmentId, academicYearId, academicUnitId);
        return new IssuancePreviewView(
                preview.academicYearId(),
                preview.academicUnitId(),
                preview.version() == null ? null : preview.version().id(),
                preview.version() == null ? null : preview.version().version(),
                preview.version() == null ? null : preview.version().currency(),
                preview.activeEnrollments(),
                preview.studentsConcerned(),
                preview.obligationsToCreate(),
                preview.alreadyIssued(),
                String.valueOf(preview.totalMinor()),
                preview.fees().stream()
                        .map(fee -> new FeeTotalView(fee.feeDefinitionId(), fee.feeCode(), fee.label(), fee.obligations(), String.valueOf(fee.totalMinor())))
                        .toList(),
                preview.previewToken());
    }

    /** Qui reste à facturer, nominativement, pour éviter les omissions. */
    @GetMapping("/pending-issuances")
    PendingIssuancesView pendingIssuances(
            @PathVariable UUID establishmentId,
            @RequestParam UUID academicYearId,
            @RequestParam(required = false) UUID academicUnitId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        ObligationService.PendingIssuances pending =
                obligations.pendingIssuances(establishmentId, academicYearId, academicUnitId, PageQuery.of(limit, offset));
        return new PendingIssuancesView(
                pending.version() == null ? null : pending.version().id(),
                pending.version() == null ? null : pending.version().version(),
                pending.version() == null ? null : pending.version().currency(),
                pending.pendingEnrollments(),
                String.valueOf(pending.totalMinor()),
                pending.page().stream()
                        .map(item -> new PendingView(
                                item.enrollment().id(),
                                item.enrollment().studentId(),
                                item.studentNumber(),
                                item.fullName(),
                                item.enrollment().academicUnitId(),
                                String.valueOf(item.totalMinor()),
                                item.fees().stream().map(ItemView::of).toList()))
                        .toList());
    }

    @PostMapping("/issuance-runs")
    @ResponseStatus(HttpStatus.CREATED)
    RunView confirmIssuance(@PathVariable UUID establishmentId, @Valid @RequestBody ConfirmIssuanceRequest request) {
        return RunView.of(obligations.confirmIssuance(
                establishmentId, request.academicYearId(), request.academicUnitId(), request.catalogVersionId(), request.previewToken()));
    }

    @GetMapping("/issuance-runs")
    List<RunView> listRuns(@PathVariable UUID establishmentId, @RequestParam UUID academicYearId) {
        return obligations.listRuns(establishmentId, academicYearId).stream().map(RunView::of).toList();
    }

    /** Idempotent : 201 à la création, 200 avec l'obligation existante si la charge est déjà émise. */
    @PostMapping("/obligations")
    ResponseEntity<ObligationView> issueManually(@PathVariable UUID establishmentId, @Valid @RequestBody ManualIssueRequest request) {
        ObligationService.ManualIssue issued = obligations.issueManually(establishmentId, request.enrollmentId(), request.feeDefinitionId());
        return ResponseEntity.status(issued.created() ? HttpStatus.CREATED : HttpStatus.OK).body(ObligationView.of(issued.obligation()));
    }

    @GetMapping("/obligations")
    List<ObligationView> list(
            @PathVariable UUID establishmentId,
            @RequestParam UUID academicYearId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return obligations.list(establishmentId, academicYearId, studentId, PageQuery.of(limit, offset)).stream()
                .map(ObligationView::of)
                .toList();
    }

    @GetMapping("/obligations/{obligationId}")
    DetailView read(@PathVariable UUID establishmentId, @PathVariable UUID obligationId) {
        ObligationService.Detail detail = obligations.read(establishmentId, obligationId);
        return new DetailView(
                ObligationView.of(detail.obligation()),
                detail.installments().stream().map(InstallmentView::of).toList(),
                detail.entries().stream().map(EntryView::of).toList());
    }

    @GetMapping("/students/{studentId}/balances")
    List<BalanceView> balances(@PathVariable UUID establishmentId, @PathVariable UUID studentId) {
        return obligations.balances(establishmentId, studentId).stream().map(BalanceView::of).toList();
    }
}
