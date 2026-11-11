# Lot 7 de la Mission 003

Base intégrée de référence `7eb7ddb6c268b8ca503c1b6518decaa5397fb88d`, 461 tests Java vérifiés après fusion de #28.

- [x] Inventorier les mutations historiques sur le code intégré. Les 176 motifs du lot 6 et les 15 de sa finalisation s'appliquent.
- [x] Préparer 195 mutations, avec un doublon retiré et cinq contrôles du catalogue ajoutés.
- [x] Renforcer le rapport contre les résultats manquants, en double et les erreurs sans assertion.
- [x] Diagnostiquer la première campagne non concluante. Ajouter `IssuanceLimitTest` omis dans la sélection et rendre son assertion obligatoire pour `size-limit-ignored`.
- [x] Campagne finale sur `1278ca7`, 195/195 détectées et trois contrôles sains de 276 tests. [Preuve](https://github.com/SPX-tech/ScolaPay/actions/runs/42988964544).
- [x] CI complète sur `1278ca7`, 461 tests Java verts et Containers vert. [CI](https://github.com/SPX-tech/ScolaPay/actions/runs/42988964481), [Containers](https://github.com/SPX-tech/ScolaPay/actions/runs/42988964500).
- [x] Actualiser le cadrage, les ADR et le contexte à partir des fusions et des résultats vérifiés.
- [x] Préparer le bilan pour la [PR #29](https://github.com/SPX-tech/ScolaPay/pull/29), en brouillon, avec les limites et évolutions à arbitrer.
- [x] Corriger la correspondance des statuts aux rôles et la propagation des échecs de génération. Six échecs observés avant correction, 13 tests d'outillage verts après correction.
- [x] Requalifier les 198 résultats JSON originaux avec l'outillage corrigé. 195 mutants `DETECTE` et trois contrôles `VERT` de 276 tests, [preuve](https://github.com/SPX-tech/ScolaPay/actions/runs/43026773053). La campagne financière n'a pas été relancée.

Le verdict propose la clôture technique du périmètre livré. Les contrôles du commit documentaire final et le statut de livraison sont consignés dans #29.

Les scénarios historiques redéfinis par V6 restent ceux de `lot5`. Les anciens motifs V5 devenus sans effet après V6 ne sont pas réintroduits. Les scripts de migration intégrés restent identiques dans la branche. Les mutations SQL ne s'appliquent qu'aux copies extraites sur les runners jetables.
