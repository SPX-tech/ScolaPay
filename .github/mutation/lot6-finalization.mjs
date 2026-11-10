// Mutations des garanties ajoutées à la finalisation, sur la suite Java complète.
const R='core/src/main/java/com/scolapay/core/billing/internal/BillingPartyRepository.java';
const F='core/src/main/java/com/scolapay/core/billing/internal/FamilyBillingService.java';
const O='core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java';
const S='core/src/main/java/com/scolapay/core/billing/internal/BillingPartyService.java';
const C='core/src/main/java/com/scolapay/core/billing/internal/PartyCorrectionService.java';
const M='core/src/main/resources/db/migration/V7__billing_parties.sql';
const snapshot=(file,signature) => [[file,
  '@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)\n    '+signature,
  '@Transactional(readOnly = true)\n    '+signature]];

export default {
  controls: 1,
  mutations: [
    { id:'pagination-without-link', steps:[[R,
      "AND (op.role <> 'VOLUNTARY_CONTRIBUTOR' OR o.student_record_id = ANY(:linkedStudents))",
      "AND (op.role <> 'VOLUNTARY_CONTRIBUTOR' OR o.student_record_id = ANY(:linkedStudents) OR TRUE)"]],
      mustFail:['Lot6FinalizationRegressionTest.uneObligationSansLienNeChangePasLaPaginationDesObligationsAccessibles'] },
    { id:'pagination-without-active-establishment', steps:[[R,
      'AND o.establishment_id = ANY(:activeEstablishments)',
      'AND (o.establishment_id = ANY(:activeEstablishments) OR TRUE)']],
      mustFail:['Lot6FinalizationRegressionTest.unEtablissementSuspenduNePrendPasUnePlaceDansLaPage'] },
    { id:'family-detail-without-snapshot', steps:snapshot(F,'public ObligationSummary myObligation(UUID obligationId) {'),
      mustFail:['Lot6FinalizationRegressionTest.leDetailFamilialNeMelangePasDeuxEtatsDuJournal'] },
    { id:'family-list-without-snapshot', steps:snapshot(F,'public List<ObligationSummary> myObligations(PageQuery page) {'),
      mustFail:['Lot6FinalizationRegressionTest.laListeFamilialeNeMelangePasDeuxEtatsDuJournal'] },
    { id:'staff-detail-without-snapshot', steps:snapshot(O,'public Detail read(UUID establishmentId, UUID obligationId) {'),
      mustFail:['Lot6FinalizationRegressionTest.leDetailDuPersonnelNeMelangePasDeuxEtatsDuJournal'] },
    { id:'debtor-count-without-snapshot', steps:snapshot(S,'public ByDebtorState byDebtorState(UUID establishmentId, UUID academicYearId, String state, PageQuery page) {'),
      mustFail:['Lot6FinalizationRegressionTest.leComptageDesRedevablesEtLaPagePartagentUnInstantane'] },
    { id:'confirmation-null-hole', steps:[[M,
      "(debtor_status IS NOT DISTINCT FROM 'CONFIRMED' AND commitment_reference IS NOT NULL",
      "(debtor_status = 'CONFIRMED' AND commitment_reference IS NOT NULL"]],
      mustFail:['Lot6FinalizationRegressionTest.unNonRedevableNePorteAucuneConfirmationMemeAvecUnStatutNull'] },
    { id:'correction-by-reader', steps:[[C,
      'UUID actorId=access.require(establishmentId,Permission.BILLING_PARTIES_MANAGE);',
      'UUID actorId=access.require(establishmentId,Permission.BILLING_READ);']],
      mustFail:['PartyCorrectionTest.uneCorrectionExigeAutorisationMotifEtVersionCourante'] },
    { id:'correction-ignores-revision', steps:[[C,
      'if (previous.revision()!=expectedRevision) {','if (false) {']],
      mustFail:['PartyCorrectionTest.deuxCorrectionsSimultaneesNePerdentPasUneVersion'] },
    { id:'correction-without-audit', steps:[[C,
      'audit.record(establishmentId,actorId,"billing.party.corrected","billing_party",partyId,\n                Map.of("correctionId",correction.id(),"revision",correction.revision()));','']],
      mustFail:['PartyCorrectionTest.uneDefaillanceDAuditAnnuleLaCorrectionEtSaTrace'] },
    { id:'correction-account-mutable', steps:[[M,
      'IF (NEW.id, NEW.establishment_id, NEW.kind, NEW.account_id, NEW.created_at, NEW.created_by)',
      'IF FALSE AND (NEW.id, NEW.establishment_id, NEW.kind, NEW.account_id, NEW.created_at, NEW.created_by)']],
      mustFail:['PartyCorrectionTest.laBaseRefuseLeTransfertDeCompteMemeAvecUneTraceValide'] },
    { id:'correction-log-without-update', steps:[[M,
      'CREATE CONSTRAINT TRIGGER billing_party_correction_applied AFTER INSERT ON billing_party_corrections\n  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION billing_party_correction_applied();\n','']],
      mustFail:['PartyCorrectionTest.uneTraceSeuleOuUnAncienEtatFalsifieNePeutPasEtreValide'] },
    { id:'correction-history-visible-to-cashier', steps:[[C,
      '        access.require(establishmentId,Permission.BILLING_REASONS_READ);\n','']],
      mustFail:['PartyCorrectionTest.leMotifEtLesAnciennesValeursRestentConfidentiels'] },
    { id:'correction-without-trace', steps:[[M,
      'CREATE TRIGGER billing_parties_update_guard BEFORE UPDATE ON billing_parties\n  FOR EACH ROW EXECUTE FUNCTION billing_parties_update_guard();\n','']],
      mustFail:['PartyCorrectionTest.leJournalDeCorrectionEstImmuableEtUneMiseAJourSansTraceEstRefusee'] },
    { id:'correction-log-mutable', steps:[[M,
      'CREATE TRIGGER billing_party_corrections_immutable BEFORE UPDATE OR DELETE ON billing_party_corrections\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n','']],
      mustFail:['PartyCorrectionTest.leJournalDeCorrectionEstImmuableEtUneMiseAJourSansTraceEstRefusee'] },
  ],
};
