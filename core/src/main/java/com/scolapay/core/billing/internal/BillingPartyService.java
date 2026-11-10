package com.scolapay.core.billing.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.billing.internal.BillingPartyRepository.Designation;
import com.scolapay.core.billing.internal.BillingPartyRepository.Party;
import com.scolapay.core.billing.internal.ObligationRepository.Obligation;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.schooling.AcademicCatalog;
import com.scolapay.core.schooling.StudentDirectory;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PageQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * Qui tient quel rôle sur quelle obligation. <strong>Rien n'est jamais déduit</strong> d'un âge ou d'un
 * lien de parenté : aucun code ne désigne une personne sans une demande explicite du personnel. Une
 * obligation peut donc rester sans redevable, ou avec un redevable non confirmé, et cela reste visible.
 */
@Service
public class BillingPartyService {

    public record Designated(Designation designation, boolean created) {}

    public record StudentAssignment(int assigned, int alreadyHeld, int heldByAnother) {}

    public record ByDebtorState(long total, List<Obligation> obligations) {}

    private enum Outcome {
        CREATED,
        ALREADY_HELD,
        HELD_BY_ANOTHER
    }

    private static final Set<String> EXCLUSIVE_ROLES = Set.of("CONTRACTUAL_DEBTOR", "FINANCIAL_RESPONSIBLE");

    private final BillingPartyRepository parties;
    private final ObligationRepository obligations;
    private final AdjustmentRepository adjustments;
    private final StudentDirectory students;
    private final AcademicCatalog academics;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final Clock clock;

    public BillingPartyService(
            BillingPartyRepository parties,
            ObligationRepository obligations,
            AdjustmentRepository adjustments,
            StudentDirectory students,
            AcademicCatalog academics,
            EstablishmentAccess access,
            AuditTrail audit,
            Clock clock) {
        this.parties = parties;
        this.obligations = obligations;
        this.adjustments = adjustments;
        this.students = students;
        this.academics = academics;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    /** Un compte ne s'associe que par un lien existant : ce service ne permet pas de viser un compte quelconque. */
    @Transactional
    public Party createParty(UUID establishmentId, String kind, String displayName, String identifier, UUID grantId) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_PARTIES_MANAGE);
        UUID accountId = null;
        if (grantId != null) {
            accountId = students.accountOfLink(establishmentId, grantId)
                    .orElseThrow(() -> ApiException.notFound("LINK_NOT_FOUND", "Lien introuvable pour cet établissement."));
            if (parties.accountHasParty(establishmentId, accountId)) {
                throw ApiException.conflict("PARTY_ALREADY_EXISTS", "Ce compte correspond déjà à une personne de cet établissement.");
            }
        }
        Party party = new Party(UUID.randomUUID(), kind, displayName, identifier, accountId, clock.instant(), actorId, 0);
        parties.insertParty(establishmentId, party);
        audit.record(establishmentId, actorId, "billing.party.created", "billing_party", party.id(), Map.of("kind", kind));
        return party;
    }

    @Transactional(readOnly = true)
    public List<Party> listParties(UUID establishmentId, PageQuery page) {
        access.require(establishmentId, Permission.BILLING_READ);
        return parties.listParties(establishmentId, page);
    }

    /** Idempotent : rejouer la même désignation renvoie celle qui existe. */
    @Transactional
    public Designated designate(UUID establishmentId, UUID obligationId, UUID partyId, String role) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_PARTIES_MANAGE);
        Obligation obligation = lockedObligation(establishmentId, obligationId);
        Party party = requireParty(establishmentId, partyId);
        requireCanHold(establishmentId, party, role, obligation.studentId());
        Outcome outcome = assign(establishmentId, obligationId, party, role, actorId);
        if (outcome == Outcome.HELD_BY_ANOTHER) {
            throw ApiException.conflict("ROLE_ALREADY_ASSIGNED", "Une autre personne tient déjà ce rôle : terminez d'abord sa désignation.");
        }
        Designation designation = parties.designationsOf(establishmentId, obligationId).stream()
                .filter(candidate -> candidate.active() && candidate.partyId().equals(partyId) && candidate.role().equals(role))
                .findFirst()
                .orElseThrow();
        return new Designated(designation, outcome == Outcome.CREATED);
    }

    /** Pour toutes les charges de l'année d'un élève. Une charge déjà tenue par une autre personne est comptée, jamais écrasée. */
    @Transactional
    public StudentAssignment assignToStudent(UUID establishmentId, UUID studentId, UUID academicYearId, UUID partyId, String role) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_PARTIES_MANAGE);
        if (students.find(establishmentId, studentId).isEmpty()) {
            throw ApiException.notFound("STUDENT_NOT_FOUND", "Élève introuvable.");
        }
        requireYear(establishmentId, academicYearId);
        Party party = requireParty(establishmentId, partyId);
        requireCanHold(establishmentId, party, role, studentId);
        int assigned = 0;
        int alreadyHeld = 0;
        int heldByAnother = 0;
        for (UUID obligationId : parties.obligationIdsOfStudent(establishmentId, studentId, academicYearId)) {
            adjustments.lockObligation(establishmentId, obligationId);
            switch (assign(establishmentId, obligationId, party, role, actorId)) {
                case CREATED -> assigned++;
                case ALREADY_HELD -> alreadyHeld++;
                case HELD_BY_ANOTHER -> heldByAnother++;
            }
        }
        return new StudentAssignment(assigned, alreadyHeld, heldByAnother);
    }

    /** Ce qui constitue un engagement valable reste à valider juridiquement : le modèle n'en retient que la référence. */
    @Transactional
    public Designation confirmDebtor(UUID establishmentId, UUID designationId, String reference) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_PARTIES_MANAGE);
        Designation designation = lockedDesignation(establishmentId, designationId);
        if (!"CONTRACTUAL_DEBTOR".equals(designation.role())) {
            throw ApiException.invalid("NOT_A_DEBTOR", "Seul un redevable porte un engagement.");
        }
        if (!designation.active()) {
            throw ApiException.conflict("DESIGNATION_ENDED", "Cette désignation est terminée.");
        }
        if ("CONFIRMED".equals(designation.debtorStatus())) {
            if (designation.commitmentReference().equals(reference)) {
                return designation;
            }
            throw ApiException.conflict("DEBTOR_ALREADY_CONFIRMED", "Ce redevable est déjà confirmé par un autre engagement.");
        }
        parties.markConfirmed(establishmentId, designationId, reference, clock.instant(), actorId);
        audit.record(
                establishmentId, actorId, "billing.debtor.confirmed", "obligation", designation.obligationId(),
                Map.of("designationId", designationId));
        return parties.findDesignation(establishmentId, designationId).orElseThrow();
    }

    /** Termine sans effacer : l'historique reste, et l'accès de la personne se ferme aussitôt. */
    @Transactional
    public Designation endDesignation(UUID establishmentId, UUID designationId, String reason) {
        UUID actorId = access.require(establishmentId, Permission.BILLING_PARTIES_MANAGE);
        Designation designation = lockedDesignation(establishmentId, designationId);
        if (!designation.active()) {
            throw ApiException.conflict("DESIGNATION_ENDED", "Cette désignation est déjà terminée.");
        }
        parties.markEnded(establishmentId, designationId, reason, clock.instant(), actorId);
        audit.record(
                establishmentId, actorId, "billing.party.ended", "obligation", designation.obligationId(),
                Map.of("designationId", designationId, "role", designation.role()));
        return parties.findDesignation(establishmentId, designationId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Designation> designationsOf(UUID establishmentId, UUID obligationId) {
        access.require(establishmentId, Permission.BILLING_READ);
        if (obligations.find(establishmentId, obligationId).isEmpty()) {
            throw ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable.");
        }
        List<Designation> designations = parties.designationsOf(establishmentId, obligationId);
        return access.allows(establishmentId, Permission.BILLING_REASONS_READ)
                ? designations
                : designations.stream().map(Designation::withoutConfidentialText).toList();
    }

    /** Ce qu'il reste à régulariser : les obligations encore dues, sans redevable ou avec un redevable non confirmé. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ByDebtorState byDebtorState(UUID establishmentId, UUID academicYearId, String state, PageQuery page) {
        access.require(establishmentId, Permission.BILLING_READ);
        requireYear(establishmentId, academicYearId);
        return new ByDebtorState(
                parties.countByDebtorState(establishmentId, academicYearId, state),
                parties.listByDebtorState(establishmentId, academicYearId, state, page).stream()
                        .map(id -> obligations.find(establishmentId, id).orElseThrow())
                        .toList());
    }

    private Outcome assign(UUID establishmentId, UUID obligationId, Party party, String role, UUID actorId) {
        List<Designation> active = parties.designationsOf(establishmentId, obligationId).stream()
                .filter(designation -> designation.active() && designation.role().equals(role))
                .toList();
        if (active.stream().anyMatch(designation -> designation.partyId().equals(party.id()))) {
            return Outcome.ALREADY_HELD;
        }
        if (EXCLUSIVE_ROLES.contains(role) && !active.isEmpty()) {
            return Outcome.HELD_BY_ANOTHER;
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        parties.insertDesignation(establishmentId, id, obligationId, party.id(), role, now, actorId);
        audit.record(
                establishmentId, actorId, "billing.party.designated", "obligation", obligationId,
                Map.of("designationId", id, "partyId", party.id(), "role", role));
        return Outcome.CREATED;
    }

    private void requireCanHold(UUID establishmentId, Party party, String role, UUID studentId) {
        if (!"VOLUNTARY_CONTRIBUTOR".equals(role)) {
            return;
        }
        if (party.accountId() == null) {
            throw ApiException.invalid("CONTRIBUTOR_ACCOUNT_REQUIRED", "Un contributeur volontaire doit disposer d'un compte.");
        }
        if (students.linkOf(establishmentId, studentId, party.accountId()).isEmpty()) {
            throw ApiException.invalid("CONTRIBUTOR_LINK_REQUIRED", "Ce compte doit d'abord être lié au dossier de l'élève.");
        }
    }

    private Party requireParty(UUID establishmentId, UUID partyId) {
        return parties.findParty(establishmentId, partyId)
                .orElseThrow(() -> ApiException.notFound("PARTY_NOT_FOUND", "Personne introuvable."));
    }

    private Obligation lockedObligation(UUID establishmentId, UUID obligationId) {
        if (!adjustments.lockObligation(establishmentId, obligationId)) {
            throw ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable.");
        }
        return obligations.find(establishmentId, obligationId).orElseThrow();
    }

    private Designation lockedDesignation(UUID establishmentId, UUID designationId) {
        Designation found = parties.findDesignation(establishmentId, designationId)
                .orElseThrow(() -> ApiException.notFound("DESIGNATION_NOT_FOUND", "Désignation introuvable."));
        adjustments.lockObligation(establishmentId, found.obligationId());
        return parties.findDesignation(establishmentId, designationId).orElseThrow();
    }

    private void requireYear(UUID establishmentId, UUID academicYearId) {
        if (academics.findYear(establishmentId, academicYearId).isEmpty()) {
            throw ApiException.notFound("ACADEMIC_YEAR_NOT_FOUND", "Année académique introuvable.");
        }
    }
}
