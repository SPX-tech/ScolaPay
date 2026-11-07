// Défauts du lot 6 de la Mission 003 : personnes liées, rôles et vues. La campagne rejoue aussi ceux du
// lot 5, qui reprennent eux-mêmes ceux du lot 4 : ces garanties doivent tenir après les ajouts.
import lot5 from './lot5.mjs';

const I = 'core/src/main/java/com/scolapay/core/billing/internal';
const S = `${I}/BillingPartyService.java`;
const R = `${I}/BillingPartyRepository.java`;
const F = `${I}/FamilyBillingService.java`;
const V = `${I}/FinancialView.java`;
const SCH = 'core/src/main/java/com/scolapay/core/schooling/internal/SchoolingService.java';
const ROLE = 'core/src/main/java/com/scolapay/core/establishment/StaffRole.java';
const M = 'core/src/main/resources/db/migration/V7__billing_parties.sql';

const never = (file, condition) => [[file, `if (${condition}) {`, 'if ("__".isEmpty()) {']];
const reader = (signature, permission) => [[S, `${signature} {\n        UUID actorId = access.require(establishmentId, Permission.${permission});`, `${signature} {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_READ);`]];

const added = {
  // Rien n'est déduit, rien n'est écrasé
  'second-debtor-open': [[S, 'if (EXCLUSIVE_ROLES.contains(role) && !active.isEmpty()) {', 'if ("__".isEmpty()) {']],
  'designation-not-idempotent': never(S, 'active.stream().anyMatch(designation -> designation.partyId().equals(party.id()))'),
  'held-role-overwritten-silently': {
    steps: [[S, 'return Outcome.HELD_BY_ANOTHER;', 'return Outcome.ALREADY_HELD;']],
    mustFail: ['BillingPartyTest.affecterAUnElevePorteSurSesChargesSansEcraserNiPrevoirLesSuivantes'],
  },
  'no-lock-on-designation': {
    steps: [[S, `        if (!adjustments.lockObligation(establishmentId, obligationId)) {
            throw ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable.");
        }
        return obligations.find(establishmentId, obligationId).orElseThrow();`, `        return obligations.find(establishmentId, obligationId)
                .orElseThrow(() -> ApiException.notFound("OBLIGATION_NOT_FOUND", "Obligation introuvable."));`]],
    mustFail: ['BillingPartyTest.uneDesignationAttendLeVerrouDeLObligation'],
  },

  // Redevable
  'debtor-confirmed-on-designation': [[R, `"CONTRACTUAL_DEBTOR".equals(role) ? "UNCONFIRMED" : null`, `"CONTRACTUAL_DEBTOR".equals(role) ? "CONFIRMED" : null`]],
  'confirm-any-role': never(S, '!"CONTRACTUAL_DEBTOR".equals(designation.role())'),
  'confirm-ended': [[S, `if (!designation.active()) {\n            throw ApiException.conflict("DESIGNATION_ENDED", "Cette désignation est terminée.");`, `if ("__".isEmpty()) {\n            throw ApiException.conflict("DESIGNATION_ENDED", "Cette désignation est terminée.");`]],
  'reconfirm-other-reference': [[S, 'if (designation.commitmentReference().equals(reference)) {', 'if ("__".isEmpty() == false) {']],
  'end-twice': [[S, `if (!designation.active()) {\n            throw ApiException.conflict("DESIGNATION_ENDED", "Cette désignation est déjà terminée.");`, `if ("__".isEmpty()) {\n            throw ApiException.conflict("DESIGNATION_ENDED", "Cette désignation est déjà terminée.");`]],
  'debtor-state-none-includes-settled': [[R, `AND\n            """ + DUE + """\n             > 0\n`, `AND\n            """ + DUE + """\n             >= 0\n`]],
  'debtor-state-ignores-ended': {
    steps: [[R, `WHERE op.obligation_id = o.id AND op.role = 'CONTRACTUAL_DEBTOR' AND op.ended_at IS NULL))`, `WHERE op.obligation_id = o.id AND op.role = 'CONTRACTUAL_DEBTOR'))`]],
    mustFail: ['BillingPartyTest.terminerUneDesignationGardeLHistoriqueEtLaissePlaceAUneAutre'],
  },

  // Contributeur
  'contributor-without-account': never(S, 'party.accountId() == null'),
  'contributor-without-link': never(S, 'students.linkOf(establishmentId, studentId, party.accountId()).isEmpty()'),
  'contributor-view-ignores-link': {
    steps: [[V, 'return contributor && linkedToStudent && dueMinor > 0 ?', 'return contributor && dueMinor > 0 ?']],
    mustFail: ['FamilyBillingViewTest.revoquerLeLienFermeAussitotLaVueDuContributeur'],
  },
  'contributor-sees-nothing-due': {
    steps: [
      [V, 'return contributor && linkedToStudent && dueMinor > 0 ?', 'return contributor && linkedToStudent ?'],
      [R, `AND (op.role <> 'VOLUNTARY_CONTRIBUTOR' OR\n            """ + DUE + " > 0)\\n";`, `AND (op.role <> 'VOLUNTARY_CONTRIBUTOR' OR\n            """ + DUE + " >= 0)\\n";`],
    ],
    mustFail: ['FamilyBillingViewTest.unContributeurNeVoitPlusUneObligationDontRienNestDu'],
  },
  'account-reused-for-party': never(S, 'parties.accountHasParty(establishmentId, accountId)'),

  // La règle unique des vues
  'debtor-shown-as-responsible': {
    steps: [[V, `if (roles.stream().anyMatch(role -> "CONTRACTUAL_DEBTOR".equals(role.role()))) {\n            return Optional.of(DEBTOR);`, `if (roles.stream().anyMatch(role -> "CONTRACTUAL_DEBTOR".equals(role.role()))) {\n            return Optional.of(RESPONSIBLE);`]],
    mustFail: ['FamilyBillingViewTest.leRedevableVoitLeDetailEtLesMotifsDeRemiseSeulement'],
  },
  'responsible-shown-as-debtor': {
    steps: [[V, `if (roles.stream().anyMatch(role -> "FINANCIAL_RESPONSIBLE".equals(role.role()))) {\n            return Optional.of(RESPONSIBLE);`, `if (roles.stream().anyMatch(role -> "FINANCIAL_RESPONSIBLE".equals(role.role()))) {\n            return Optional.of(DEBTOR);`]],
    mustFail: ['FamilyBillingViewTest.leResponsableVoitLesMontantsSansMotifNiDetailDesRemises'],
  },
  'ended-role-keeps-view': {
    steps: [
      [R, `WHERE p.account_id = :accountId AND op.establishment_id = :establishmentId AND op.obligation_id = :obligationId AND op.ended_at IS NULL`, `WHERE p.account_id = :accountId AND op.establishment_id = :establishmentId AND op.obligation_id = :obligationId`],
      [R, `WHERE p.account_id = :accountId AND op.ended_at IS NULL\n`, `WHERE p.account_id = :accountId\n`],
    ],
    mustFail: ['FamilyBillingViewTest.terminerUneDesignationFermeLaVueAussitot'],
  },
  // Deux filtres successifs portent cette garantie : retirer l'un seul ne change rien, il faut retirer les deux.
  'view-any-account': {
    steps: [
      [R, `WHERE p.account_id = :accountId AND op.ended_at IS NULL\n`, `WHERE (p.account_id = :accountId OR p.account_id IS NOT NULL) AND op.ended_at IS NULL\n`],
      [R, `WHERE p.account_id = :accountId AND op.establishment_id = :establishmentId`, `WHERE (p.account_id = :accountId OR p.account_id IS NOT NULL) AND op.establishment_id = :establishmentId`],
    ],
    mustFail: ['FamilyBillingViewTest.uneFamilleNeVoitQueSesObligationsDansChaqueEtablissement'],
  },

  // Ce que chaque vue contient
  'contributor-gets-label': {
    steps: [[F, `"CONTRIBUTOR", obligation.id(), name, obligation.reference(), obligation.currency(), String.valueOf(obligation.dueMinor()), nextDueOn, studentName);`, `"CONTRIBUTOR", obligation.id(), name, obligation.reference() + obligation.label(), obligation.currency(), String.valueOf(obligation.dueMinor()), nextDueOn, studentName);`]],
    mustFail: ['FamilyBillingViewTest.leContributeurNeRecoitQueLesChampsAutorises'],
  },
  'responsible-sees-kinds': {
    steps: [[F, `new HistoryView("ISSUED".equals(entry.kind()) ? "ISSUED" : "ADJUSTMENT", entry.recordedAt())`, `new HistoryView(entry.kind(), entry.recordedAt())`]],
    mustFail: ['FamilyBillingViewTest.leResponsableVoitLesMontantsSansMotifNiDetailDesRemises'],
  },
  'responsible-sees-real-status': {
    steps: [[F, `obligation.dueMinor() > 0 ? "OPEN" : "NOTHING_DUE",`, `ObligationService.ObligationStatus.of(obligation).name(),`]],
    mustFail: ['FamilyBillingViewTest.leResponsableNeVoitPasPourquoiPlusRienNestDu'],
  },
  'debtor-sees-internal-notes': {
    steps: [[F, `"DISCOUNT".equals(entry.kind()) ? entry.reason() : null`, `entry.reason()`]],
    mustFail: ['FamilyBillingViewTest.leRedevableVoitLeDetailEtLesMotifsDeRemiseSeulement'],
  },
  'debtor-loses-discount-reason': [[F, `"DISCOUNT".equals(entry.kind()) ? entry.reason() : null`, `null`]],
  'debtor-status-hidden': [[F, `.map(HeldRole::debtorStatus)\n                .findFirst()\n                .orElseThrow();`, `.map(role -> "CONFIRMED")\n                .findFirst()\n                .orElseThrow();`]],
  'next-due-while-nothing-due': {
    steps: [[F, `LocalDate nextDueOn = obligation.dueMinor() == 0\n                    ? null\n                    : installments`, `LocalDate nextDueOn = installments`]],
    mustFail: ['FamilyBillingViewTest.laProchaineEcheanceEstCelleAVenirEtDisparaitQuandPlusRienNestDu'],
  },
  'next-due-includes-past': {
    steps: [[F, '.filter(dueOn -> !dueOn.isBefore(today))', '']],
    mustFail: ['FamilyBillingViewTest.laProchaineEcheanceEstCelleAVenirEtDisparaitQuandPlusRienNestDu'],
  },

  // Accès académique
  'label-always-real': {
    steps: [[F, `link.filter(StudentDirectory.StudentLink::academicAccess).isPresent() ? obligation.label() : NEUTRAL_LABEL`, `obligation.label()`]],
    mustFail: ['FamilyBillingViewTest.unMajeurSansConsentementNeVoitSonNiveauRevelePasParLeLibelle'],
  },
  'label-always-neutral': [[F, `link.filter(StudentDirectory.StudentLink::academicAccess).isPresent() ? obligation.label() : NEUTRAL_LABEL`, `NEUTRAL_LABEL`]],
  'label-ignores-majority': {
    steps: [[SCH, `return Optional.of(new StudentLink(AcademicAccess.allowed(grant.get(), student.get(), establishment, clock)));`, `return Optional.of(new StudentLink(grant.get().academicAccess()));`]],
    mustFail: ['FamilyBillingViewTest.lePassageALaMajoriteRendLeLibelleNeutreLeJourMeme'],
  },

  // Permissions et motifs
  'create-party-by-reader': reader('public Party createParty(UUID establishmentId, String kind, String displayName, String identifier, UUID grantId)', 'BILLING_PARTIES_MANAGE'),
  'designate-by-reader': reader('public Designated designate(UUID establishmentId, UUID obligationId, UUID partyId, String role)', 'BILLING_PARTIES_MANAGE'),
  'assign-by-reader': {
    steps: reader('public StudentAssignment assignToStudent(UUID establishmentId, UUID studentId, UUID academicYearId, UUID partyId, String role)', 'BILLING_PARTIES_MANAGE'),
    mustFail: ['BillingPartyTest.lesPermissionsSeparentLaLectureDeLaGestion'],
  },
  'confirm-by-reader': reader('public Designation confirmDebtor(UUID establishmentId, UUID designationId, String reference)', 'BILLING_PARTIES_MANAGE'),
  'end-by-reader': reader('public Designation endDesignation(UUID establishmentId, UUID designationId, String reason)', 'BILLING_PARTIES_MANAGE'),
  'parties-read-by-anyone': [[S, `access.require(establishmentId, Permission.BILLING_READ);\n        return parties.listParties`, `access.require(establishmentId, Permission.ESTABLISHMENT_READ);\n        return parties.listParties`]],
  'designations-read-by-anyone': [[S, `access.require(establishmentId, Permission.BILLING_READ);\n        if (obligations.find(establishmentId, obligationId).isEmpty()) {`, `access.require(establishmentId, Permission.ESTABLISHMENT_READ);\n        if (obligations.find(establishmentId, obligationId).isEmpty()) {`]],
  'end-reason-visible-to-cashier': [[S, `return access.allows(establishmentId, Permission.BILLING_REASONS_READ)\n                ? designations\n                : designations.stream().map(Designation::withoutConfidentialText).toList();`, `return designations;`]],
  'accountant-loses-management': [[ROLE, `BILLING_REASONS_READ, BILLING_PARTIES_MANAGE)),`, `BILLING_REASONS_READ)),`]],

  // Garanties portées par la base
  'db-many-debtors': [[M, `CREATE UNIQUE INDEX obligation_parties_one_debtor ON obligation_parties (obligation_id)\n  WHERE role = 'CONTRACTUAL_DEBTOR' AND ended_at IS NULL;\n`, '']],
  'db-many-responsibles': [[M, `CREATE UNIQUE INDEX obligation_parties_one_responsible ON obligation_parties (obligation_id)\n  WHERE role = 'FINANCIAL_RESPONSIBLE' AND ended_at IS NULL;\n`, '']],
  'db-contributor-without-account': [[M, `IF NEW.role = 'VOLUNTARY_CONTRIBUTOR'\n     AND NOT EXISTS`, `IF FALSE\n     AND NOT EXISTS`]],
  'db-debtor-without-status': [[M, `  CONSTRAINT obligation_parties_debtor_status CHECK ((role = 'CONTRACTUAL_DEBTOR') = (debtor_status IS NOT NULL)),\n`, '']],
  'db-confirmed-without-reference': [[M, `  CONSTRAINT obligation_parties_commitment CHECK (\n    (debtor_status = 'CONFIRMED' AND commitment_reference IS NOT NULL)\n    OR (debtor_status IS DISTINCT FROM 'CONFIRMED' AND commitment_reference IS NULL)),\n  CHECK ((confirmed_at IS NULL) = (confirmed_by IS NULL)),\n  CHECK ((commitment_reference IS NULL) = (confirmed_at IS NULL)),\n`, '']],
  'db-party-mutable': [[M, `CREATE TRIGGER billing_parties_immutable BEFORE UPDATE OR DELETE ON billing_parties\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n`, '']],
  'db-designation-deletable': [[M, `CREATE TRIGGER obligation_parties_no_delete BEFORE DELETE ON obligation_parties\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n`, '']],
  'db-ended-mutable': [[M, 'IF OLD.ended_at IS NOT NULL THEN', 'IF FALSE THEN']],
  'db-identity-mutable': [[M, `IF (NEW.id, NEW.establishment_id, NEW.obligation_id, NEW.party_id, NEW.role, NEW.designated_at, NEW.designated_by)`, `IF FALSE AND (NEW.id, NEW.establishment_id, NEW.obligation_id, NEW.party_id, NEW.role, NEW.designated_at, NEW.designated_by)`]],
  'db-confirmation-mutable': [[M, `IF OLD.debtor_status = 'CONFIRMED'\n     AND`, `IF FALSE\n     AND`]],
  'db-party-other-establishment': [[M, `  FOREIGN KEY (establishment_id, party_id) REFERENCES billing_parties (establishment_id, id)\n`, `  FOREIGN KEY (party_id) REFERENCES billing_parties (id)\n`]],
  'db-keeps-financial-column': {
    steps: [[M, 'ALTER TABLE record_access_grants DROP COLUMN financial_contributor;\n', '']],
    mustFail: ['BillingPartyTest.leLienNePorteDesormaisPlusLeDroitFinancier'],
  },
};

const mutations = Object.entries(added).map(([id, value]) => (Array.isArray(value) ? { id, steps: value } : { id, ...value }));

export default {
  controls: 3,
  mutations: [...mutations, ...lot5.mutations],
};
