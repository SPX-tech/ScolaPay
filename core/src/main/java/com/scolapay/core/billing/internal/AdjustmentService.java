package com.scolapay.core.billing.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.billing.internal.AdjustmentRepository.NewEntry;
import com.scolapay.core.billing.internal.AdjustmentRepository.Request;
import com.scolapay.core.billing.internal.ObligationRepository.Entry;
import com.scolapay.core.billing.internal.ObligationRepository.Obligation;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.schooling.AcademicCatalog;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PageQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remises, corrections, annulations et contrepassées. Toute opération est une nouvelle écriture : rien
 * n'est modifié ni supprimé. <strong>Aucune n'est un encaissement.</strong>
 *
 * <p>Le contrôle est proportionné. Une opération courante s'applique aussitôt ; une opération sensible
 * attend la confirmation d'une seconde personne et, d'ici là, ne change pas le dû.
 */
@Service
public class AdjustmentService {

    /** Soit l'écriture appliquée, soit la demande en attente. {@code replay} : la même clé a déjà servi. */
    public record Outcome(UUID entryId, Request request, boolean replay) {

        boolean applied() {
            return entryId != null;
        }
    }

    private final AdjustmentRepository adjustments;
    private final ObligationRepository obligations;
    private final AcademicCatalog academics;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final Clock clock;
    private final int sensitivePercent;
    private final long sensitiveXaf;

    public AdjustmentService(
            AdjustmentRepository adjustments,
            ObligationRepository obligations,
            AcademicCatalog academics,
            EstablishmentAccess access,
            AuditTrail audit,
            Clock clock,
            @Value("${scolapay.billing.sensitive-adjustment-percent}") int sensitivePercent,
            @Value("${scolapay.billing.sensitive-adjustment-xaf}") long sensitiveXaf) {
        this.adjustments = adjustments;
        this.obligations = obligations;
        this.academics = academics;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.sensitivePercent = sensitivePercent;
        this.sensitiveXaf = sensitiveXaf;
    }

    /** Une remise commerciale : un avantage accordé, qui réduit le dû. */
    @Transactional
    public Outcome discount(UUID establishmentId, UUID obligationId, long amountMinor, String reason, String clientKey) {
        return submit(establishmentId, obligationId, "DISCOUNT", -amountMinor, null, reason, clientKey);
    }

    /** Une correction financière : le montant était faux, dans un sens ou dans l'autre. */
    @Transactional
    public Outcome correct(UUID establishmentId, UUID obligationId, long signedAmountMinor, String reason, String clientKey) {
        return submit(establishmentId, obligationId, "CORRECTION", signedAmountMinor, null, reason, clientKey);
    }

    /** Une annulation administrative : l'obligation n'aurait pas dû exister. Toujours sensible. */
    @Transactional
    public Outcome cancel(UUID establishmentId, UUID obligationId, String reason, String clientKey) {
        return submit(establishmentId, obligationId, "CANCELLATION", null, null, reason, clientKey);
    }

    /** Défait une écriture par son exact opposé, en gardant les deux dans l'historique. */
    @Transactional
    public Outcome reverse(UUID establishmentId, UUID obligationId, UUID entryId, String reason, String clientKey) {
        return submit(establishmentId, obligationId, "REVERSAL", null, entryId, reason, clientKey);
    }

    private Outcome submit(
            UUID establishmentId, UUID obligationId, String kind, Long signedAmount, UUID reversedEntryId, String reason, String clientKey) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_ADJUST);
        Obligation obligation = lockedObligation(establishmentId, obligationId);

        String key = "op:" + clientKey;
        if (adjustments.keyUsedElsewhere(establishmentId, obligationId, key)) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Cette clé a déjà servi pour une autre obligation.");
        }
        Optional<Request> replayedRequest = adjustments.requestByKey(establishmentId, obligationId, key);
        if (replayedRequest.isPresent()) {
            return new Outcome(null, replayedRequest.get(), true);
        }
        Optional<UUID> replayedEntry = adjustments.entryIdByKey(establishmentId, obligationId, key);
        if (replayedEntry.isPresent()) {
            return new Outcome(replayedEntry.get(), null, true);
        }

        List<Entry> entries = obligations.entries(establishmentId, obligationId);
        Entry reversed = reversedEntryId == null ? null : reversibleEntry(establishmentId, entries, reversedEntryId);
        if ("CANCELLATION".equals(kind)) {
            requireDue(obligation);
        }
        long amount = "CANCELLATION".equals(kind) ? -obligation.dueMinor() : reversed != null ? -reversed.amountMinor() : signedAmount;
        requireWithinDue(obligation, amount);

        boolean sensitive = "CANCELLATION".equals(kind)
                || sensitive(establishmentId, obligation, entries, amount, reversed);
        if (sensitive) {
            return requestApproval(establishmentId, obligation, entries.size(), kind, amount, reversedEntryId, reason, key, actorId);
        }
        UUID entryId = UUID.randomUUID();
        Instant now = clock.instant();
        adjustments.insertEntry(establishmentId, new NewEntry(entryId, obligationId, kind, amount, reason, reversedEntryId, key, now, actorId, null));
        audit.record(
                establishmentId, actorId, "billing.adjustment.applied", "obligation", obligationId,
                Map.of("kind", kind, "amountMinor", String.valueOf(amount), "entryId", entryId));
        return new Outcome(entryId, null, false);
    }

    private Outcome requestApproval(
            UUID establishmentId, Obligation obligation, int journalSize, String kind, long amount, UUID reversedEntryId, String reason, String key, UUID actorId) {
        UUID obligationId = obligation.id();
        if (adjustments.hasPendingRequest(establishmentId, obligationId)) {
            throw ApiException.conflict("PENDING_REQUEST_EXISTS", "Une demande attend déjà une décision pour cette obligation.");
        }
        Request request = new Request(
                UUID.randomUUID(), obligationId, kind, amount, obligation.dueMinor(), journalSize, false, reversedEntryId, reason, "PENDING",
                clock.instant(), actorId, null, null, null, null);
        adjustments.insertRequest(establishmentId, request, key);
        audit.record(
                establishmentId, actorId, "billing.adjustment.requested", "adjustment_request", request.id(),
                Map.of("kind", kind, "obligationId", obligationId));
        return new Outcome(null, request, false);
    }

    /**
     * Sensible : amener le dû à zéro, dépasser le seuil cumulé, ou toucher une année clôturée. Le seuil
     * porte sur le cumul brut des écritures actives : un cumul net laisserait une hausse et une baisse se
     * compenser, et un contrôle par opération laisserait découper. Sans seconde personne, ce cumul ne
     * peut donc jamais dépasser le seuil.
     */
    private boolean sensitive(UUID establishmentId, Obligation obligation, List<Entry> entries, long amount, Entry reversed) {
        boolean closedYear = academics.findYear(establishmentId, obligation.academicYearId())
                .map(AcademicCatalog.YearSummary::closed)
                .orElse(true);
        if (closedYear || obligation.dueMinor() + amount == 0) {
            return true;
        }
        if (reversed != null) {
            return reversed.approvedBy() != null;
        }
        long cumulated = activeAdjustmentsMinor(entries) + Math.abs(amount);
        return cumulated * 100 > sensitivePercent * obligation.issuedAmountMinor()
                || ("XAF".equals(obligation.currency()) && cumulated > sensitiveXaf);
    }

    /** Une écriture contrepassée et sa contrepassée s'annulent : elles ne consomment pas le seuil. */
    private static long activeAdjustmentsMinor(List<Entry> entries) {
        Set<UUID> reversed = entries.stream().map(Entry::reversesEntryId).filter(Objects::nonNull).collect(Collectors.toSet());
        return entries.stream()
                .filter(entry -> !"ISSUED".equals(entry.kind()) && !"REVERSAL".equals(entry.kind()) && !reversed.contains(entry.id()))
                .mapToLong(entry -> Math.abs(entry.amountMinor()))
                .sum();
    }

    @Transactional
    public Entry approve(UUID establishmentId, UUID requestId) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_APPROVE);
        Request request = lockedPendingRequest(establishmentId, requestId);
        if (request.requestedBy().equals(actorId)) {
            throw ApiException.forbidden("SELF_APPROVAL_FORBIDDEN", "Cette opération doit être confirmée par une autre personne.");
        }
        // Toute écriture passée depuis la demande change ce qui a été présenté, même à solde identique.
        if (obligations.entries(establishmentId, request.obligationId()).size() != request.journalSizeAtRequest()) {
            throw outdated();
        }
        long amount = request.amountMinor();
        UUID entryId = UUID.randomUUID();
        Instant now = clock.instant();
        adjustments.insertEntry(establishmentId, new NewEntry(
                entryId, request.obligationId(), request.kind(), amount, request.reason(), request.reversesEntryId(),
                "request:" + request.id(), now, request.requestedBy(), actorId));
        adjustments.markApproved(establishmentId, requestId, actorId, now, entryId);
        audit.record(
                establishmentId, actorId, "billing.adjustment.approved", "adjustment_request", requestId,
                Map.of("kind", request.kind(), "amountMinor", String.valueOf(amount), "entryId", entryId));
        return obligations.entries(establishmentId, request.obligationId()).stream()
                .filter(entry -> entry.id().equals(entryId))
                .findFirst()
                .orElseThrow();
    }

    /** Refuser n'écrit rien au journal : le dû ne bouge pas, et le motif reste dans la demande. */
    @Transactional
    public void reject(UUID establishmentId, UUID requestId, String note) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_APPROVE);
        Request request = lockedPendingRequest(establishmentId, requestId);
        if (request.requestedBy().equals(actorId)) {
            throw ApiException.forbidden("SELF_DECISION_FORBIDDEN", "Votre propre demande se retire : elle ne se refuse pas.");
        }
        adjustments.markRejected(establishmentId, requestId, actorId, clock.instant(), note);
        audit.record(establishmentId, actorId, "billing.adjustment.rejected", "adjustment_request", requestId, Map.of());
    }

    /** Son auteur renonce à sa demande : la trace reste, et l'obligation peut recevoir une autre demande. */
    @Transactional
    public void withdraw(UUID establishmentId, UUID requestId, String note) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_ADJUST);
        Request request = lockedPendingRequest(establishmentId, requestId);
        if (!request.requestedBy().equals(actorId)) {
            throw ApiException.forbidden("NOT_REQUEST_AUTHOR", "Seul l'auteur retire sa demande : une autre personne la refuse.");
        }
        adjustments.markWithdrawn(establishmentId, requestId, actorId, clock.instant(), note);
        audit.record(establishmentId, actorId, "billing.adjustment.withdrawn", "adjustment_request", requestId, Map.of());
    }

    @Transactional(readOnly = true)
    public List<Request> listRequests(UUID establishmentId, String status, PageQuery page) {
        access.require(establishmentId, Permission.BILLING_READ);
        List<Request> requests = adjustments.listRequests(establishmentId, status, page);
        return access.allows(establishmentId, Permission.BILLING_REASONS_READ)
                ? requests
                : requests.stream().map(Request::withoutConfidentialText).toList();
    }

    private Request lockedPendingRequest(UUID establishmentId, UUID requestId) {
        Request found = adjustments.findRequest(establishmentId, requestId)
                .orElseThrow(() -> ApiException.notFound("ADJUSTMENT_REQUEST_NOT_FOUND", "Demande introuvable."));
        adjustments.lockObligation(establishmentId, found.obligationId());
        Request request = adjustments.findRequest(establishmentId, requestId).orElseThrow();
        if (!request.pending()) {
            throw ApiException.conflict("REQUEST_ALREADY_DECIDED", "Cette demande a déjà été décidée.");
        }
        return request;
    }

    private Obligation lockedObligation(UUID establishmentId, UUID obligationId) {
        if (!adjustments.lockObligation(establishmentId, obligationId)) {
            throw ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable.");
        }
        return obligations.find(establishmentId, obligationId).orElseThrow();
    }

    private Entry reversibleEntry(UUID establishmentId, List<Entry> entries, UUID entryId) {
        Entry entry = entries.stream()
                .filter(candidate -> candidate.id().equals(entryId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("ENTRY_NOT_FOUND", "Écriture introuvable pour cette obligation."));
        if ("ISSUED".equals(entry.kind()) || "REVERSAL".equals(entry.kind())) {
            throw ApiException.invalid("ENTRY_NOT_REVERSIBLE", "Cette écriture ne se contrepasse pas.");
        }
        if (adjustments.alreadyReversed(establishmentId, entryId)) {
            throw ApiException.conflict("ENTRY_ALREADY_REVERSED", "Cette écriture a déjà été contrepassée.");
        }
        return entry;
    }

    private static void requireDue(Obligation obligation) {
        if (obligation.dueMinor() == 0) {
            throw ApiException.conflict("NOTHING_DUE", "Il ne reste rien à annuler sur cette obligation.");
        }
    }

    private static void requireWithinDue(Obligation obligation, long amount) {
        if (obligation.dueMinor() + amount < 0) {
            throw ApiException.invalid("AMOUNT_EXCEEDS_DUE", "Le montant dépasse ce qui reste dû.");
        }
    }

    private static ApiException outdated() {
        return ApiException.conflict("REQUEST_OUTDATED", "Le journal a changé depuis cette demande : elle doit être refusée ou retirée, puis formulée de nouveau pour une nouvelle confirmation.");
    }
}
