// Défauts du lot 5 de la Mission 003 : remises, corrections, annulations et réexamen. La campagne rejoue
// aussi ceux du lot 4 : ces garanties doivent tenir après les ajouts.
import lot4 from './lot4.mjs';

const I = 'core/src/main/java/com/scolapay/core/billing/internal';
const O = `${I}/ObligationService.java`;
const R = `${I}/ObligationRepository.java`;
const A = `${I}/AdjustmentService.java`;
const AR = `${I}/AdjustmentRepository.java`;
const S = `${I}/ReviewService.java`;
const V = 'core/src/main/resources/db/migration/V6__adjustments_and_reviews.sql';

// Redéfinis par V6 : les modifier dans V5 n'aurait aucun effet. Ils sont recréés ci-dessous sur V6.
const REPLACED = new Set(['status-always-open', 'db-negative-open', 'db-entries-no-lock', 'db-settlement-allowed', 'db-reason-optional']);

const never = (file, condition) => [[file, `if (${condition}) {`, 'if ("__".isEmpty()) {']];

const added = {
  // Statuts : même solde, sens différent
  'status-cancelled-seen-as-other': never(O, 'obligation.cancelled()'),
  'status-always-corrected': [[O, 'return obligation.discounted() ? WAIVED : CORRECTED;', 'return CORRECTED;']],
  'status-always-waived': [[O, 'return obligation.discounted() ? WAIVED : CORRECTED;', 'return WAIVED;']],
  'status-cancel-ignores-reversal': [[R, 'AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id = e.id)) AS cancelled,', ') AS cancelled,']],
  'status-discount-ignores-reversal': [[R, 'AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id = e.id)) AS discounted,', ') AS discounted,']],

  // Protection renforcée
  'nothing-sensitive-except-cancel': [[A, `boolean sensitive = "CANCELLATION".equals(kind)\n                || sensitive(establishmentId, obligation, entries, amount, reversed);`, 'boolean sensitive = "CANCELLATION".equals(kind);']],
  'threshold-not-cumulative': [[A, 'return Math.abs(net + amount) * 100 >', 'return Math.abs(amount) * 100 >']],
  'threshold-ignored': [[A, 'return Math.abs(net + amount) * 100 > sensitivePercent * obligation.issuedAmountMinor();', 'return false;']],
  'closed-year-not-sensitive': [[A, 'if (closedYear || obligation.dueMinor() + amount == 0) {', 'if (obligation.dueMinor() + amount == 0) {']],
  'reversal-of-approved-not-sensitive': [[A, 'return reversed.approvedBy() != null;', 'return false;']],
  'self-approval-open': never(A, 'request.requestedBy().equals(actorId)'),
  'approve-by-reader': [[A, `UUID actorId = access.require(establishmentId, Permission.BILLING_APPROVE);\n        Request request`, `UUID actorId = access.require(establishmentId, Permission.BILLING_READ);\n        Request request`]],
  'reject-by-reader': [[A, `UUID actorId = access.require(establishmentId, Permission.BILLING_APPROVE);\n        lockedPendingRequest`, `UUID actorId = access.require(establishmentId, Permission.BILLING_READ);\n        lockedPendingRequest`]],
  'adjust-by-reader': [[A, `UUID actorId = access.require(establishmentId, Permission.BILLING_ADJUST);\n        Obligation obligation = lockedObligation`, `UUID actorId = access.require(establishmentId, Permission.BILLING_READ);\n        Obligation obligation = lockedObligation`]],
  'requests-by-reader': [[A, `access.require(establishmentId, Permission.BILLING_READ);\n        return adjustments.listRequests`, `access.require(establishmentId, Permission.ESTABLISHMENT_READ);\n        return adjustments.listRequests`]],

  // Concurrence, idempotence, cohérence
  'no-obligation-lock': [[AR, 'WHERE establishment_id = :establishmentId AND id = :id FOR UPDATE")', 'WHERE establishment_id = :establishmentId AND id = :id")']],
  'decided-request-reusable': never(A, '!request.pending()'),
  'entry-replay-gone': never(A, 'replayedEntry.isPresent()'),
  'request-replay-gone': never(A, 'replayedRequest.isPresent()'),
  'key-reuse-open': never(A, 'adjustments.keyUsedElsewhere(establishmentId, obligationId, key)'),
  'amount-over-due-open': [[A, `if (obligation.dueMinor() + amount < 0) {\n            throw ApiException.invalid("AMOUNT_EXCEEDS_DUE"`, `if ("__".isEmpty()) {\n            throw ApiException.invalid("AMOUNT_EXCEEDS_DUE"`]],
  'outdated-approval-open': [[A, 'if (amount == 0 || obligation.dueMinor() + amount < 0) {', 'if (amount == 0) {']],
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
  'db-reversal-any-kind': [[V, `IF original_kind IN ('ISSUED', 'REVERSAL') THEN`, 'IF FALSE THEN']],
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
  'db-request-content-mutable': [[V, `IF (NEW.id, NEW.establishment_id, NEW.obligation_id, NEW.kind, NEW.amount_minor,`, `IF FALSE AND (NEW.id, NEW.establishment_id, NEW.obligation_id, NEW.kind, NEW.amount_minor,`]],
  'db-request-deletable': [[V, `CREATE TRIGGER adjustment_requests_no_delete BEFORE DELETE ON adjustment_requests\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n`, '']],
  'db-self-decided-request': [[V, '  CONSTRAINT adjustment_requests_second_person CHECK (decided_by IS NULL OR decided_by <> requested_by),\n', '']],
  'db-many-pending': [[V, `CREATE UNIQUE INDEX adjustment_requests_one_pending ON adjustment_requests (obligation_id) WHERE status = 'PENDING';\n`, '']],
  'db-review-decision-mutable': [[V, `CREATE TRIGGER obligation_review_decisions_immutable BEFORE UPDATE OR DELETE ON obligation_review_decisions\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n`, '']],
};

const mutations = Object.entries(added).map(([id, value]) => (Array.isArray(value) ? { id, steps: value } : { id, ...value }));

export default {
  controls: 3,
  mutations: [...mutations, ...lot4.mutations.filter((mutation) => !REPLACED.has(mutation.id))],
};
