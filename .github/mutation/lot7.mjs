import lot6 from './lot6.mjs';
import finalization from './lot6-finalization.mjs';

const billing = 'core/src/main/java/com/scolapay/core/billing/internal';
const catalog = billing + '/FeeCatalogService.java';
const migration = 'core/src/main/resources/db/migration/V4__fee_catalog.sql';

export default {
  controls: 3,
  // La CI complète contrôle aussi identité, notifications et anti-abus sur le même code.
  testSelector: 'FeeCatalogTest,ObligationTest,IssuanceLimitTest,AdjustmentTest,AdjustmentPolicyTest,ObligationReviewTest,ReasonConfidentialityTest,BillingPartyTest,FamilyBillingViewTest,Lot6FinalizationRegressionTest,PartyCorrectionTest,ModularityTest,TenantIsolationTest,DatabaseGuardsTest',
  mutations: [
    // Même garde que correction-without-trace, qui exige le test de régression dédié.
    ...lot6.mutations
      .filter((mutation) => mutation.id !== 'db-party-mutable')
      .map((mutation) => mutation.id === 'size-limit-ignored'
        ? { ...mutation, mustFail: ['IssuanceLimitTest.unLotTropGrandEstRefuseEtSeDecoupeParUnite'] }
        : mutation),
    ...finalization.mutations,
    {
      id: 'catalog-nearest-unit-loses-priority',
      steps: [[billing + '/FeeResolution.java', 'for (UUID unit : lineage) {', 'for (UUID unit : lineage.reversed()) {']],
      mustFail: ['FeeCatalogTest.laLigneLaPlusProcheDeLUniteLEmporteEtUneUniteRecoitUneSeuleLigneParCode'],
    },
    {
      id: 'catalog-installment-total-unchecked',
      steps: [[catalog, 'if (total != item.amountMinor()) {', 'if (false) {']],
      mustFail: ['FeeCatalogTest.lesEcheancesDoiventCouvrirExactementLeMontantDansLOrdre'],
    },
    {
      id: 'catalog-installment-order-unchecked',
      steps: [[catalog, 'if (i > 0 && !installments.get(i).dueOn().isAfter(installments.get(i - 1).dueOn())) {', 'if (false) {']],
      mustFail: ['FeeCatalogTest.lesEcheancesDoiventCouvrirExactementLeMontantDansLOrdre'],
    },
    {
      id: 'catalog-published-item-mutable',
      steps: [[migration, "IF current_status IS DISTINCT FROM 'DRAFT' THEN", 'IF FALSE THEN']],
      mustFail: ['FeeCatalogTest.laBaseRefuseToutEcritureSurUnTarifPublie'],
    },
    {
      id: 'catalog-charge-identity-mutable',
      steps: [[migration, 'CREATE TRIGGER fee_definitions_immutable BEFORE UPDATE OR DELETE ON fee_definitions\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n', '']],
      mustFail: ['FeeCatalogTest.laBaseFigeLIdentiteDUneCharge'],
    },
  ],
};
