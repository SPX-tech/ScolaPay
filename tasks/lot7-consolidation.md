# Lot 7 de la Mission 003

Base intégrée de référence `7eb7ddb6c268b8ca503c1b6518decaa5397fb88d`, 461 tests Java vérifiés après fusion de #28.

- [x] Inventorier les mutations historiques sur le code intégré. Les 176 motifs du lot 6 et les 15 de sa finalisation s'appliquent.
- [x] Préparer 195 mutations, avec un doublon retiré et cinq contrôles du catalogue ajoutés.
- [x] Renforcer le rapport contre les résultats manquants, en double et les erreurs sans assertion.
- Exécuter la campagne et la CI complète sur GitHub Actions, conserver leurs SHA et rapports.
- Actualiser le cadrage, les ADR et le contexte à partir des fusions et des résultats vérifiés.
- Livrer une PR en brouillon avec les défauts bloquants, limites acceptées et évolutions à arbitrer.

Les scénarios historiques redéfinis par V6 restent ceux de `lot5`. Les anciens motifs V5 devenus sans effet après V6 ne sont pas réintroduits. Les scripts de migration intégrés restent identiques dans la branche. Les mutations SQL ne s'appliquent qu'aux copies extraites sur les runners jetables.
