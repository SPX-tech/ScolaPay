// Défauts du lot 5 de la Mission 003 : remises, corrections, annulations et réexamen. La campagne rejoue
// aussi ceux du lot 4 : ces garanties doivent tenir après les ajouts.
import lot4 from './lot4.mjs';

const I = 'core/src/main/java/com/scolapay/core/billing/internal';
const O = `${I}/ObligationService.java`;
const R = `${I}/ObligationRepository.java`;
const A = `${I}/AdjustmentService.java`;
const AR = `${I}/AdjustmentRepository.java`;
const S = `${I}/ReviewService.java`;
const ROLE = 'core/src/main/java/com/scolapay/core/establishment/StaffRole.java';
const V = 'core/src/main/resources/db/migration/V6__adjustments_and_reviews.sql';

// Redéfinis par V6 : les modifier dans V5 n'aurait aucun effet. Ils sont recréés ci-dessous sur V6.
const REPLACED = new Set(['status-always-open', 'db-negative-open', 'db-entries-no-lock', 'db-settlement-allowed', 'db-reason-optional']);

const never = (file, condition) => [[file, `if (${condition}) {`, 'if ("__".isEmpty()) {']];

const added = {
  // Statuts : même solde, sens différent
  'status-cancelled-seen-as-other': never(O, 'obligation.cancelled()'),
  'status-always-corrected': [[O, 'return obligation.corrected() ? ADJUSTED_TO_ZERO : WAIVED;', 'return CORRECTED;']],
  'status-mix-shown-as-waiver': {
    steps: [[O, 'return obligation.corrected() ? ADJUSTED_TO_ZERO : WAIVED;', 'return WAIVED;']],
    mustFail: ['AdjustmentPolicyTest.uneCombinaisonDeRemisesEtDeCorrectionsResteNeutre'],
  },
  'status-mix-shown-as-correction': [[O, 'return obligation.corrected() ? ADJUSTED_TO_ZERO : WAIVED;', 'return obligation.corrected() ? CORRECTED : WAIVED;']],
  'status-always-neutral': [[O, 'return obligation.corrected() ? CORRECTED : ADJUSTED_TO_ZERO;', 'return ADJUSTED_TO_ZERO;']],
  'status-correction-ignores-reversal': {
    steps: [[R, 'AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id = e.id)) AS corrected,', ') AS corrected,']],
    mustFail: ['AdjustmentPolicyTest.uneCorrectionContrepasseeNeRendPasLeStatutNeutre'],
  },
  'status-cancel-ignores-reversal': {
    steps: [[R, 'AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id = e.id)) AS cancelled,', ') AS cancelled,']],
    mustFail: ['AdjustmentTest.uneAnnulationContrepasseeNeLaissePasLeStatutCancelled'],
  },
  'status-discount-ignores-reversal': [[R, 'AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id = e.id)) AS discounted,', ') AS discounted,']],

  // Protection renforcée
  'nothing-sensitive-except-cancel': [[A, `boolean sensitive = "CANCELLATION".equals(kind)\n                || sensitive(establishmentId, obligation, entries, amount, reversed);`, 'boolean sensitive = "CANCELLATION".equals(kind);']],
  'threshold-not-cumulative': [[A, 'long cumulated = activeAdjustmentsMinor(entries) + Math.abs(amount);', 'long cumulated = Math.abs(amount);']],
  'threshold-net-instead-of-gross': {
    steps: [[A, '.mapToLong(entry -> Math.abs(entry.amountMinor()))', '.mapToLong(entry -> entry.amountMinor())']],
    mustFail: ['AdjustmentPolicyTest.uneAlternanceDeHaussesEtDeBaissesConsommeLeSeuil'],
  },
  'threshold-ignores-percent': {
    steps: [[A, `return cumulated * 100 > sensitivePercent * obligation.issuedAmountMinor()
                || ("XAF".equals(obligation.currency()) && cumulated > sensitiveXaf);`, 'return "XAF".equals(obligation.currency()) && cumulated > sensitiveXaf;']],
    mustFail: ['AdjustmentPolicyTest.surUnePetiteObligationLePourcentageArriveLePremier'],
  },
  'threshold-ignores-absolute': {
    steps: [[A, `
                || ("XAF".equals(obligation.currency()) && cumulated > sensitiveXaf);`, ';']],
    mustFail: ['AdjustmentPolicyTest.leMontantAbsoluSAppliqueSurUneGrosseObligation'],
  },
  'threshold-counts-reversed': {
    steps: [[A, ' && !reversed.contains(entry.id()))', ')']],
    mustFail: ['AdjustmentPolicyTest.contrepasserPuisRecommencerNeFaitJamaisDepasserLeSeuil'],
  },
  'threshold-percent-raised': [['core/src/main/resources/application.yaml', 'sensitive-adjustment-percent: 25', 'sensitive-adjustment-percent: 26']],
  'threshold-amount-raised': [['core/src/main/resources/application.yaml', 'sensitive-adjustment-xaf: 100000', 'sensitive-adjustment-xaf: 100001']],
  'closed-year-not-sensitive': [[A, 'if (closedYear || obligation.dueMinor() + amount == 0) {', 'if (obligation.dueMinor() + amount == 0) {']],
  'reversal-of-approved-not-sensitive': [[A, 'return reversed.approvedBy() != null;', 'return false;']],
  'self-approval-open': [[A, `if (request.requestedBy().equals(actorId)) {\n            throw ApiException.forbidden("SELF_APPROVAL_FORBIDDEN"`, `if ("__".isEmpty()) {\n            throw ApiException.forbidden("SELF_APPROVAL_FORBIDDEN"`]],
  'approve-by-reader': [[A, `public Entry approve(UUID establishmentId, UUID requestId) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_APPROVE);`, `public Entry approve(UUID establishmentId, UUID requestId) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_READ);`]],
  'reject-by-reader': [[A, `public void reject(UUID establishmentId, UUID requestId, String note) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_APPROVE);`, `public void reject(UUID establishmentId, UUID requestId, String note) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_READ);`]],
  'adjust-by-reader': [[A, `UUID actorId = access.require(establishmentId, Permission.BILLING_ADJUST);\n        Obligation obligation = lockedObligation`, `UUID actorId = access.require(establishmentId, Permission.BILLING_READ);\n        Obligation obligation = lockedObligation`]],
  'requests-by-reader': [[A, `access.require(establishmentId, Permission.BILLING_READ);\n        List<Request> requests = adjustments.listRequests`, `access.require(establishmentId, Permission.ESTABLISHMENT_READ);\n        List<Request> requests = adjustments.listRequests`]],

  // Concurrence, idempotence, cohérence
  'no-obligation-lock': [[AR, 'WHERE establishment_id = :establishmentId AND id = :id FOR UPDATE")', 'WHERE establishment_id = :establishmentId AND id = :id")']],
  'decided-request-reusable': never(A, '!request.pending()'),
  'entry-replay-gone': never(A, 'replayedEntry.isPresent()'),
  'request-replay-gone': never(A, 'replayedRequest.isPresent()'),
  'key-reuse-open': never(A, 'adjustments.keyUsedElsewhere(establishmentId, obligationId, key)'),
  'amount-over-due-open': [[A, `if (obligation.dueMinor() + amount < 0) {\n            throw ApiException.invalid("AMOUNT_EXCEEDS_DUE"`, `if ("__".isEmpty()) {\n            throw ApiException.invalid("AMOUNT_EXCEEDS_DUE"`]],
  'outdated-approval-open': {
    steps: never(A, 'obligations.entries(establishmentId, request.obligationId()).size() != request.journalSizeAtRequest()'),
    mustFail: ['AdjustmentPolicyTest.uneAnnulationDontLeDuAChangeExigeUneNouvelleConfirmation'],
  },
  'outdated-flag-always-false': [[AR, `status = 'PENDING' AND journal_size_at_request <> (`, `status = 'NEVER' AND journal_size_at_request <> (`]],
  'request-without-due-snapshot': [[A, 'kind, amount, obligation.dueMinor(), journalSize, false,', 'kind, amount, 0L, journalSize, false,']],
  'reject-own-request-open': [[A, `if (request.requestedBy().equals(actorId)) {\n            throw ApiException.forbidden("SELF_DECISION_FORBIDDEN"`, `if ("__".isEmpty()) {\n            throw ApiException.forbidden("SELF_DECISION_FORBIDDEN"`]],
  'withdraw-by-anyone': never(A, '!request.requestedBy().equals(actorId)'),
  'withdraw-by-reader': [[A, `public void withdraw(UUID establishmentId, UUID requestId, String note) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_ADJUST);`, `public void withdraw(UUID establishmentId, UUID requestId, String note) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_READ);`]],

  // Confidentialité : la règle vit dans l'API
  'reasons-detail-entries-open': {
    steps: [[O, 'reasonsVisible ? entries : entries.stream().map(Entry::withoutConfidentialText).toList(),', 'entries,']],
    mustFail: ['ReasonConfidentialityTest.leCaissierVoitLesMontantsSansAucunMotif', 'ReasonConfidentialityTest.aucuneRouteDeLectureDeLaFacturationNeDonneUnMotifAuCaissier'],
  },
  'reasons-detail-decisions-open': {
    steps: [[O, 'reasonsVisible ? decisions : decisions.stream().map(AdjustmentRepository.ReviewDecision::withoutConfidentialText).toList(),', 'decisions,']],
    mustFail: ['ReasonConfidentialityTest.leCaissierVoitLesMontantsSansAucunMotif'],
  },
  'reasons-requests-open': {
    steps: [[A, `return access.allows(establishmentId, Permission.BILLING_REASONS_READ)
                ? requests
                : requests.stream().map(Request::withoutConfidentialText).toList();`, 'return requests;']],
    mustFail: ['ReasonConfidentialityTest.leCaissierVoitLesDemandesSansMotifNiNoteDeDecision'],
  },
  'reasons-note-kept-for-cashier': [[AR, `requestedAt, requestedBy, decidedAt, decidedBy, null, entryId);`, `requestedAt, requestedBy, decidedAt, decidedBy, decisionNote, entryId);`]],
  'reasons-hidden-from-everyone': [[O, 'boolean reasonsVisible = access.allows(establishmentId, Permission.BILLING_REASONS_READ);', 'boolean reasonsVisible = false;']],
  'reasons-granted-to-cashier': [[ROLE, 'CASHIER(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, BILLING_READ)),', 'CASHIER(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, BILLING_READ, BILLING_REASONS_READ)),']],
  'reasons-denied-to-auditor': [[ROLE, 'AUDITOR(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, AUDIT_READ, BILLING_READ, BILLING_REASONS_READ)),', 'AUDITOR(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, AUDIT_READ, BILLING_READ)),']],

  // Directeur
  'director-cannot-approve': [[ROLE, 'DIRECTOR(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, BILLING_READ, BILLING_APPROVE, BILLING_REASONS_READ));', 'DIRECTOR(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, BILLING_READ, BILLING_REASONS_READ));']],
  'director-can-adjust': [[ROLE, 'DIRECTOR(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, BILLING_READ, BILLING_APPROVE, BILLING_REASONS_READ));', 'DIRECTOR(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, BILLING_READ, BILLING_APPROVE, BILLING_ADJUST, BILLING_REASONS_READ));']],
  'double-reversal-open': [[A, `if (adjustments.alreadyReversed(establishmentId, entryId)) {\n            throw ApiException.conflict("ENTRY_ALREADY_REVERSED"`, `if ("__".isEmpty()) {\n            throw ApiException.conflict("ENTRY_ALREADY_REVERSED"`]],
  'any-entry-reversible': never(A, '"ISSUED".equals(entry.kind()) || "REVERSAL".equals(entry.kind())'),
  'second-pending-open': never(A, 'adjustments.hasPendingRequest(establishmentId, obligationId)'),
  'cancel-with-nothing-due': never(A, 'obligation.dueMinor() == 0'),

  // Réexamen : signaler et laisser décider
  'review-ignores-decisions': [[S, 'if (signal != null && !decided.contains(List.of(obligation.id().toString(), signal.signal(), signal.fingerprint()))) {', 'if (signal != null) {']],
  'review-includes-nothing-due': never(S, 'obligation.dueMinor() == 0'),
  'review-flags-same-amount': never(S, 'item.isPresent() && item.get().amountMinor() == obligation.issuedAmountMinor()'),
  'review-cause-always-catalog': [[S, '"LEVEL_CHANGED" : "CATALOG_CHANGED"', '"CATALOG_CHANGED" : "CATALOG_CHANGED"']],
  'review-difference-unsigned': [[S, 'amount - obligation.issuedAmountMinor());', 'Math.abs(amount - obligation.issuedAmountMinor()));']],
  'review-flags-completed': [[S, `                .filter(latest -> "WITHDRAWN".equals(latest.status()))\n`, '']],
  'review-fingerprint-ignores-amount': {
    steps: [[S, 'active.id() + ":" + (amount == null ? "none" : amount.toString())', 'active.id().toString()']],
    mustFail: ['ObligationReviewTest.uneSituationNouvelleRedevientVisibleApresUneDecision'],
  },
  'review-decision-not-verified': never(S, '!current'),
  'review-any-entry-accepted': never(S, '!adjustment'),
  'review-entry-rule-open': never(S, '"ADJUSTED".equals(decision) != (entryId != null)'),
  'review-read-by-reader': [[S, `access.require(establishmentId, Permission.BILLING_READ);\n        requireYear`, `access.require(establishmentId, Permission.ESTABLISHMENT_READ);\n        requireYear`]],
  'review-decide-by-reader': [[S, 'UUID actorId = access.require(establishmentId, Permission.BILLING_ADJUST);', 'UUID actorId = access.require(establishmentId, Permission.BILLING_READ);']],

  // Garanties portées par la base
  'db-settlement-allowed': [
    [V, `CHECK (kind IN ('ISSUED', 'DISCOUNT', 'CORRECTION', 'CANCELLATION', 'REVERSAL')),`, `CHECK (kind IN ('ISSUED', 'DISCOUNT', 'CORRECTION', 'CANCELLATION', 'REVERSAL', 'SETTLEMENT')),`],
    [V, `reverses_entry_id IS NOT NULL)),\n  ADD CONSTRAINT obligation_entries_second_person`, `reverses_entry_id IS NOT NULL)\n    OR kind = 'SETTLEMENT'),\n  ADD CONSTRAINT obligation_entries_second_person`],
  ],
  'db-negative-open': [[V, 'IF due + NEW.amount_minor < 0 THEN', 'IF FALSE THEN']],
  'db-entries-no-lock': {
    steps: [[V, '  PERFORM 1 FROM obligations WHERE id = NEW.obligation_id FOR UPDATE;\n', '']],
    mustFail: ['ObligationTest.uneEcritureConcurrenteAttendPuisNeRendJamaisLeDuNegatif'],
  },
  'db-zero-without-second-person': [[V, `IF NEW.kind <> 'ISSUED' AND due + NEW.amount_minor = 0 AND NEW.approved_by IS NULL THEN`, 'IF FALSE THEN']],
  'db-partial-cancellation': [[V, `IF NEW.kind = 'CANCELLATION' AND due + NEW.amount_minor <> 0 THEN`, 'IF FALSE THEN']],
  'db-reversal-any-kind': {
    steps: [[V, `IF original_kind IN ('ISSUED', 'REVERSAL') THEN`, 'IF FALSE THEN']],
    mustFail: ['AdjustmentTest.laBaseRefuseDeContrepasserUneEmissionOuUneContrepassee'],
  },
  'db-reversal-any-amount': [[V, 'IF original_amount <> -NEW.amount_minor THEN', 'IF FALSE THEN']],
  'db-self-approved-entry': [[V, '  ADD CONSTRAINT obligation_entries_second_person CHECK (approved_by IS NULL OR approved_by <> recorded_by),\n', '']],
  'db-reversal-other-obligation': [[V, `ALTER TABLE obligation_entries ADD CONSTRAINT obligation_entries_reverses_fk
  FOREIGN KEY (establishment_id, obligation_id, reverses_entry_id)
  REFERENCES obligation_entries (establishment_id, obligation_id, id);`, `ALTER TABLE obligation_entries ADD CONSTRAINT obligation_entries_reverses_fk
  FOREIGN KEY (reverses_entry_id) REFERENCES obligation_entries (id);`]],
  'db-many-reversals': [[V, 'CREATE UNIQUE INDEX obligation_entries_one_reversal ON obligation_entries (reverses_entry_id) WHERE reverses_entry_id IS NOT NULL;\n', '']],
  'db-discount-without-reason': [[V, `(kind = 'DISCOUNT' AND amount_minor < 0 AND reason IS NOT NULL AND reverses_entry_id IS NULL)`, `(kind = 'DISCOUNT' AND amount_minor < 0 AND reverses_entry_id IS NULL)`]],
  'db-correction-without-reason': [[V, `(kind = 'CORRECTION' AND amount_minor <> 0 AND reason IS NOT NULL AND reverses_entry_id IS NULL)`, `(kind = 'CORRECTION' AND amount_minor <> 0 AND reverses_entry_id IS NULL)`]],
  'db-decided-request-mutable': [[V, `IF OLD.status <> 'PENDING' THEN`, 'IF FALSE THEN']],
  'db-request-content-mutable': [[V, `IF (NEW.id, NEW.establishment_id, NEW.obligation_id, NEW.kind, NEW.amount_minor, NEW.due_at_request_minor,`, `IF FALSE AND (NEW.id, NEW.establishment_id, NEW.obligation_id, NEW.kind, NEW.amount_minor, NEW.due_at_request_minor,`]],
  'db-request-deletable': [[V, `CREATE TRIGGER adjustment_requests_no_delete BEFORE DELETE ON adjustment_requests\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n`, '']],
  'db-self-decided-request': [[V, `  CONSTRAINT adjustment_requests_second_person CHECK (decided_by IS NULL OR status = 'WITHDRAWN' OR decided_by <> requested_by),\n`, '']],
  'db-withdrawn-by-other': [[V, `  CONSTRAINT adjustment_requests_withdrawn_by_author CHECK (status <> 'WITHDRAWN' OR decided_by = requested_by),\n`, '']],
  'db-note-optional': [[V, `  CONSTRAINT adjustment_requests_note_required CHECK (status NOT IN ('REJECTED', 'WITHDRAWN') OR decision_note IS NOT NULL),\n`, '']],
  'db-partial-cancellation-request': [[V, `  CONSTRAINT adjustment_requests_cancels_all CHECK (kind <> 'CANCELLATION' OR amount_minor = -due_at_request_minor),\n`, '']],
  'db-approved-other-entry': {
    steps: [[V, `IF NOT EXISTS (
      SELECT 1 FROM obligation_entries e
      WHERE e.id = NEW.entry_id AND e.kind = NEW.kind AND e.amount_minor = NEW.amount_minor`, `IF FALSE AND NOT EXISTS (
      SELECT 1 FROM obligation_entries e
      WHERE e.id = NEW.entry_id AND e.kind = NEW.kind AND e.amount_minor = NEW.amount_minor`]],
    mustFail: ['AdjustmentPolicyTest.laBaseRefuseDeConfirmerAvecUneEcritureDifferenteDeLaDemande'],
  },
  'db-approved-stale': {
    steps: [[V, 'IF journal_size <> NEW.journal_size_at_request + 1 THEN', 'IF FALSE THEN']],
    mustFail: ['AdjustmentPolicyTest.laBaseRefuseDeConfirmerUneDemandePerimee'],
  },
  'db-many-pending': [[V, `CREATE UNIQUE INDEX adjustment_requests_one_pending ON adjustment_requests (obligation_id) WHERE status = 'PENDING';\n`, '']],
  'db-review-decision-mutable': [[V, `CREATE TRIGGER obligation_review_decisions_immutable BEFORE UPDATE OR DELETE ON obligation_review_decisions\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n`, '']],
};

const mutations = Object.entries(added).map(([id, value]) => (Array.isArray(value) ? { id, steps: value } : { id, ...value }));

export default {
  controls: 3,
  mutations: [...mutations, ...lot4.mutations.filter((mutation) => !REPLACED.has(mutation.id))],
};
