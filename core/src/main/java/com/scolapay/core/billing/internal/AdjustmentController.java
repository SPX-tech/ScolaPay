package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.internal.AdjustmentRepository.Request;
import com.scolapay.core.billing.internal.ObligationController.EntryView;
import com.scolapay.core.billing.internal.ObligationController.ObligationView;
import com.scolapay.core.shared.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
 * Chaque opération ajoute une écriture ou une demande. Aucune route ne modifie une écriture, ni le
 * montant dû, et aucune ne constate un encaissement. Une réponse 201 indique une écriture appliquée, 202
 * une demande en attente d'une seconde personne, 200 un rejeu de la même clé.
 */
@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/billing")
class AdjustmentController {

    private static final String KEY = "^[A-Za-z0-9-]{8,100}$";

    record DiscountRequest(
            @NotNull @Pattern(regexp = "^[1-9][0-9]{0,14}$") String amountMinor,
            @NotBlank @Size(min = 3, max = 500) String reason,
            @NotNull @Pattern(regexp = KEY) String idempotencyKey) {}

    record CorrectionRequest(
            @NotNull @Pattern(regexp = "^-?[1-9][0-9]{0,14}$") String amountMinor,
            @NotBlank @Size(min = 3, max = 500) String reason,
            @NotNull @Pattern(regexp = KEY) String idempotencyKey) {}

    record ReasonedRequest(@NotBlank @Size(min = 3, max = 500) String reason, @NotNull @Pattern(regexp = KEY) String idempotencyKey) {}

    record RejectionRequest(@NotBlank @Size(min = 3, max = 500) String note) {}

    record DecisionRequest(
            @NotNull UUID obligationId,
            @NotNull @Pattern(regexp = "^(TARIFF_DIFFERENCE|WITHDRAWAL)$") String signal,
            @NotBlank @Size(max = 200) String fingerprint,
            @NotNull @Pattern(regexp = "^(NO_CHANGE|ADJUSTED)$") String decision,
            UUID entryId,
            @NotBlank @Size(min = 3, max = 500) String reason) {}

    record RequestView(
            UUID id,
            UUID obligationId,
            String kind,
            String amountMinor,
            UUID reversesEntryId,
            String reason,
            String status,
            Instant requestedAt,
            UUID requestedBy,
            Instant decidedAt,
            UUID decidedBy,
            String decisionNote,
            UUID entryId) {

        static RequestView of(Request request) {
            return new RequestView(
                    request.id(), request.obligationId(), request.kind(),
                    request.amountMinor() == null ? null : String.valueOf(request.amountMinor()),
                    request.reversesEntryId(), request.reason(), request.status(), request.requestedAt(), request.requestedBy(),
                    request.decidedAt(), request.decidedBy(), request.decisionNote(), request.entryId());
        }
    }

    record OutcomeView(String outcome, UUID entryId, RequestView request) {}

    record SignalView(
            String signal,
            String fingerprint,
            String cause,
            ObligationView obligation,
            UUID currentEnrollmentId,
            String applicableAmountMinor,
            String differenceMinor) {

        static SignalView of(ReviewService.Signal signal) {
            return new SignalView(
                    signal.signal(), signal.fingerprint(), signal.cause(), ObligationView.of(signal.obligation()), signal.currentEnrollmentId(),
                    signal.applicableAmountMinor() == null ? null : String.valueOf(signal.applicableAmountMinor()),
                    signal.differenceMinor() == null ? null : String.valueOf(signal.differenceMinor()));
        }
    }

    record ReviewsView(long total, List<SignalView> signals) {}

    private final AdjustmentService adjustments;
    private final ReviewService reviews;

    AdjustmentController(AdjustmentService adjustments, ReviewService reviews) {
        this.adjustments = adjustments;
        this.reviews = reviews;
    }

    @PostMapping("/obligations/{obligationId}/discounts")
    ResponseEntity<OutcomeView> discount(
            @PathVariable UUID establishmentId, @PathVariable UUID obligationId, @Valid @RequestBody DiscountRequest request) {
        return respond(adjustments.discount(
                establishmentId, obligationId, Long.parseLong(request.amountMinor()), request.reason().trim(), request.idempotencyKey()));
    }

    @PostMapping("/obligations/{obligationId}/corrections")
    ResponseEntity<OutcomeView> correct(
            @PathVariable UUID establishmentId, @PathVariable UUID obligationId, @Valid @RequestBody CorrectionRequest request) {
        return respond(adjustments.correct(
                establishmentId, obligationId, Long.parseLong(request.amountMinor()), request.reason().trim(), request.idempotencyKey()));
    }

    @PostMapping("/obligations/{obligationId}/cancellation")
    ResponseEntity<OutcomeView> cancel(
            @PathVariable UUID establishmentId, @PathVariable UUID obligationId, @Valid @RequestBody ReasonedRequest request) {
        return respond(adjustments.cancel(establishmentId, obligationId, request.reason().trim(), request.idempotencyKey()));
    }

    @PostMapping("/obligations/{obligationId}/entries/{entryId}/reversal")
    ResponseEntity<OutcomeView> reverse(
            @PathVariable UUID establishmentId,
            @PathVariable UUID obligationId,
            @PathVariable UUID entryId,
            @Valid @RequestBody ReasonedRequest request) {
        return respond(adjustments.reverse(establishmentId, obligationId, entryId, request.reason().trim(), request.idempotencyKey()));
    }

    @GetMapping("/adjustment-requests")
    List<RequestView> listRequests(
            @PathVariable UUID establishmentId,
            @RequestParam(defaultValue = "PENDING") @Pattern(regexp = "^(PENDING|APPROVED|REJECTED)$") String status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return adjustments.listRequests(establishmentId, status, PageQuery.of(limit, offset)).stream().map(RequestView::of).toList();
    }

    @PostMapping("/adjustment-requests/{requestId}/approval")
    EntryView approve(@PathVariable UUID establishmentId, @PathVariable UUID requestId) {
        return EntryView.of(adjustments.approve(establishmentId, requestId));
    }

    @PostMapping("/adjustment-requests/{requestId}/rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(@PathVariable UUID establishmentId, @PathVariable UUID requestId, @Valid @RequestBody RejectionRequest request) {
        adjustments.reject(establishmentId, requestId, request.note().trim());
    }

    /** Ce qui demande une décision de l'établissement. Une lecture : rien n'est modifié. */
    @GetMapping("/reviews")
    ReviewsView reviews(
            @PathVariable UUID establishmentId,
            @RequestParam UUID academicYearId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        ReviewService.Page page = reviews.list(establishmentId, academicYearId, PageQuery.of(limit, offset));
        return new ReviewsView(page.total(), page.signals().stream().map(SignalView::of).toList());
    }

    @PostMapping("/reviews/decisions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decide(@PathVariable UUID establishmentId, @Valid @RequestBody DecisionRequest request) {
        reviews.decide(
                establishmentId, request.obligationId(), request.signal(), request.fingerprint(), request.decision(), request.entryId(), request.reason().trim());
    }

    private static ResponseEntity<OutcomeView> respond(AdjustmentService.Outcome outcome) {
        HttpStatus status = outcome.replay() ? HttpStatus.OK : outcome.applied() ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        OutcomeView body = outcome.applied()
                ? new OutcomeView("APPLIED", outcome.entryId(), null)
                : new OutcomeView(outcome.request().pending() ? "PENDING_APPROVAL" : outcome.request().status(), null, RequestView.of(outcome.request()));
        return ResponseEntity.status(status).body(body);
    }
}
