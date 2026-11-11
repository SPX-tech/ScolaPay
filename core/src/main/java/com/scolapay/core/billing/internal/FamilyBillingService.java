package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.internal.BillingPartyRepository.HeldRole;
import com.scolapay.core.billing.internal.BillingPartyRepository.ObligationRef;
import com.scolapay.core.billing.internal.ObligationRepository.Entry;
import com.scolapay.core.billing.internal.ObligationRepository.InstallmentRow;
import com.scolapay.core.billing.internal.ObligationRepository.Obligation;
import com.scolapay.core.establishment.Establishments;
import com.scolapay.core.identity.CurrentActor;
import com.scolapay.core.schooling.StudentDirectory;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PageQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * Ce qu'une personne liée voit de ses obligations. Chaque vue est un type distinct, qui ne contient que
 * ses champs : un champ absent ne peut pas fuiter par mégarde. Aucune écriture ici, et aucun rôle
 * financier n'ouvre l'accès académique.
 */
@Service
public class FamilyBillingService {

    public sealed interface ObligationSummary permits DebtorView, ResponsibleView, ContributorView {}

    public record InstallmentView(int seq, LocalDate dueOn, String amountMinor) {}

    public record DebtorEntryView(String kind, String amountMinor, String reason, Instant recordedAt) {}

    public record HistoryView(String kind, Instant recordedAt) {}

    public record DebtorView(
            String view,
            UUID id,
            String establishmentName,
            String reference,
            String currency,
            String studentName,
            String label,
            String issuedAmountMinor,
            String dueMinor,
            String status,
            LocalDate nextDueOn,
            String debtorStatus,
            List<InstallmentView> installments,
            List<DebtorEntryView> entries) implements ObligationSummary {}

    /** Les remises n'y figurent que dans le total, et l'historique ne dit pas de quelle nature fut un ajustement. */
    public record ResponsibleView(
            String view,
            UUID id,
            String establishmentName,
            String reference,
            String currency,
            String studentName,
            String label,
            String issuedAmountMinor,
            String adjustmentsTotalMinor,
            String dueMinor,
            String status,
            LocalDate nextDueOn,
            List<InstallmentView> installments,
            List<HistoryView> history) implements ObligationSummary {}

    /** Strictement ce qu'il faut pour régler : ni ligne, ni remise, ni autre personne, ni statut. */
    public record ContributorView(
            String view,
            UUID id,
            String establishmentName,
            String reference,
            String currency,
            String dueMinor,
            LocalDate nextDueOn,
            String studentName) implements ObligationSummary {}

    // Un intitulé comme « Frais de Terminale D » révèle le niveau : sans accès académique, il reste neutre.
    static final String NEUTRAL_LABEL = "Frais scolaires";

    private final BillingPartyRepository parties;
    private final ObligationRepository obligations;
    private final StudentDirectory students;
    private final Establishments establishments;
    private final CurrentActor actor;
    private final Clock clock;

    public FamilyBillingService(
            BillingPartyRepository parties,
            ObligationRepository obligations,
            StudentDirectory students,
            Establishments establishments,
            CurrentActor actor,
            Clock clock) {
        this.parties = parties;
        this.obligations = obligations;
        this.students = students;
        this.establishments = establishments;
        this.actor = actor;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ObligationSummary> myObligations(PageQuery page) {
        UUID accountId = actor.requireAccountId();
        List<UUID> activeEstablishments = parties.establishmentsOfAccount(accountId).stream()
                .filter(id -> establishments.find(id).filter(Establishments.Establishment::active).isPresent())
                .toList();
        if (activeEstablishments.isEmpty()) return List.of();
        List<UUID> linkedStudents = new ArrayList<>();
        for (UUID establishmentId : activeEstablishments) linkedStudents.addAll(students.linkedStudentIds(establishmentId, accountId));
        // Les droits filtrent le jeu SQL avant LIMIT/OFFSET ; les API de modules partagent le même instantané.
        return parties.refsOfAccount(accountId, activeEstablishments, linkedStudents, page).stream()
                .map(ref -> viewOf(accountId, ref))
                .flatMap(Optional::stream)
                .toList();
    }

    /** Un 404 identique à une obligation inexistante : rien ne révèle qu'une charge existe pour un autre. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ObligationSummary myObligation(UUID obligationId) {
        UUID accountId = actor.requireAccountId();
        return parties.refOfAccount(accountId, obligationId)
                .flatMap(ref -> viewOf(accountId, ref))
                .orElseThrow(() -> ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable."));
    }

    private Optional<ObligationSummary> viewOf(UUID accountId, ObligationRef ref) {
        UUID establishmentId = ref.establishmentId();
        Optional<Establishments.Establishment> establishment = establishments.find(establishmentId).filter(Establishments.Establishment::active);
        if (establishment.isEmpty()) {
            return Optional.empty();
        }
        Obligation obligation = obligations.find(establishmentId, ref.obligationId()).orElseThrow();
        List<HeldRole> roles = parties.rolesOfAccount(accountId, establishmentId, obligation.id());
        Optional<StudentDirectory.StudentLink> link = students.linkOf(establishmentId, obligation.studentId(), accountId);
        return FinancialView.of(roles, link.isPresent(), obligation.dueMinor()).map(view -> {
            String studentName = students.find(establishmentId, obligation.studentId()).orElseThrow().fullName();
            String label = link.filter(StudentDirectory.StudentLink::academicAccess).isPresent() ? obligation.label() : NEUTRAL_LABEL;
            String name = establishment.get().name();
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(establishment.get().timezone())));
            List<InstallmentRow> installments = obligations.installments(establishmentId, obligation.id());
            LocalDate nextDueOn = obligation.dueMinor() == 0
                    ? null
                    : installments.stream().map(InstallmentRow::dueOn).filter(dueOn -> !dueOn.isBefore(today)).min(LocalDate::compareTo).orElse(null);
            if (!obligation.corrected() && !obligation.cancelled()) {
                nextDueOn = InstallmentCoverage.calculate(installments, obligation.issuedAmountMinor() + obligation.adjustmentsMinor(), obligation.settledMinor())
                        .stream().filter(term -> term.dueMinor() > 0 && !term.dueOn().isBefore(today))
                        .map(com.scolapay.core.billing.BillingSettlements.Installment::dueOn).min(LocalDate::compareTo).orElse(null);
            }
            LocalDate projectedNextDue = nextDueOn;
            return switch (view) {
                case CONTRIBUTOR -> contributor(obligation, name, projectedNextDue, studentName);
                case RESPONSIBLE -> responsible(establishmentId, obligation, name, studentName, label, projectedNextDue, installments);
                case DEBTOR -> debtor(establishmentId, obligation, name, studentName, label, projectedNextDue, installments, roles);
            };
        });
    }

    private static ObligationSummary contributor(Obligation obligation, String name, LocalDate nextDueOn, String studentName) {
        return new ContributorView(
                "CONTRIBUTOR", obligation.id(), name, obligation.reference(), obligation.currency(), String.valueOf(obligation.dueMinor()), nextDueOn, studentName);
    }

    private ObligationSummary responsible(
            UUID establishmentId, Obligation obligation, String name, String studentName, String label, LocalDate nextDueOn, List<InstallmentRow> installments) {
        return new ResponsibleView(
                "RESPONSIBLE",
                obligation.id(),
                name,
                obligation.reference(),
                obligation.currency(),
                studentName,
                label,
                String.valueOf(obligation.issuedAmountMinor()),
                String.valueOf(obligation.adjustmentsMinor()),
                String.valueOf(obligation.dueMinor()),
                // Le statut réel dirait s'il y a eu une remise ou une annulation.
                obligation.dueMinor() > 0 ? "OPEN" : "NOTHING_DUE",
                nextDueOn,
                installmentViews(installments),
                obligations.entries(establishmentId, obligation.id()).stream()
                        .filter(entry -> !"SETTLEMENT".equals(entry.kind()))
                        .map(entry -> new HistoryView("ISSUED".equals(entry.kind()) ? "ISSUED" : "ADJUSTMENT", entry.recordedAt()))
                        .toList());
    }

    private ObligationSummary debtor(
            UUID establishmentId,
            Obligation obligation,
            String name,
            String studentName,
            String label,
            LocalDate nextDueOn,
            List<InstallmentRow> installments,
            List<HeldRole> roles) {
        String debtorStatus = roles.stream()
                .filter(role -> "CONTRACTUAL_DEBTOR".equals(role.role()))
                .map(HeldRole::debtorStatus)
                .findFirst()
                .orElseThrow();
        return new DebtorView(
                "DEBTOR",
                obligation.id(),
                name,
                obligation.reference(),
                obligation.currency(),
                studentName,
                label,
                String.valueOf(obligation.issuedAmountMinor()),
                String.valueOf(obligation.dueMinor()),
                ObligationService.ObligationStatus.of(obligation).name(),
                nextDueOn,
                debtorStatus,
                installmentViews(installments),
                obligations.entries(establishmentId, obligation.id()).stream().filter(entry -> !"SETTLEMENT".equals(entry.kind()))
                        .map(FamilyBillingService::debtorEntry).toList());
    }

    /** Le redevable lit le motif d'une bourse ou d'une remise, pas les notes internes d'une correction ou d'une annulation. */
    private static DebtorEntryView debtorEntry(Entry entry) {
        return new DebtorEntryView(
                entry.kind(), String.valueOf(entry.amountMinor()), "DISCOUNT".equals(entry.kind()) ? entry.reason() : null, entry.recordedAt());
    }

    private static List<InstallmentView> installmentViews(List<InstallmentRow> installments) {
        return installments.stream().map(row -> new InstallmentView(row.seq(), row.dueOn(), String.valueOf(row.amountMinor()))).toList();
    }
}
