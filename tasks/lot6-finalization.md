# Finalisation du lot 6

Responsabilité reprise après arrêt de l'intervention de l'agent initial.
Base publiée : 253312a2f76904ab673b0de68f483fec0313dd49. Branche de préparation finalize/lot6-core.
Les sept documents locaux retrouvés ont été copiés ; les originaux restent dans leur worktree et une copie exacte est conservée sous core/target/handover-original/.

## Périmètre validé

- Autorisations avant pagination ; aucune obligation inaccessible ne modifie la structure des pages visibles.
- Instantané cohérent des vues familiales et des lectures financières existantes concernées.
- Contraintes de rôle, statut et confirmation totales même avec NULL.
- Correction explicite du nom et de l'identifiant d'une personne, motivée, autorisée et historisée, sans changer son identité, son type ou son compte.
- Réassociation de compte exclue du parcours de correction : elle serait un transfert sensible de droits, à cadrer séparément.

## Vérification préalable de V7

PR #26 non fusionnée ; main c28862c ne contient que V1 à V6 ; aucun déploiement GitHub recensé.
Le contexte du projet et la confirmation du propriétaire indiquent qu'aucune base persistante n'a reçu V7.
Aucune base persistante n'est contactée. La révision pré-service de V7 est explicitement autorisée dans ce périmètre ; aucun checksum d'une migration persistante ne doit être réparé.

## Tâches

- [x] Préserver le travail publié et les documents locaux.
- [x] Régressions observées en échec sur GitHub Actions avant les corrections : neuf échecs attendus sur `9118469`, [preuve](https://github.com/SPX-tech/ScolaPay/actions/runs/42897808109).
- [x] Autorisation avant pagination et instantanés de lecture, sans accès SQL aux tables d'autres modules.
- [x] V7 renforcée et corrections des personnes avec journal append-only, contrôle de version et compte immuable.
- [x] 19 régressions vertes, dont une matrice de 144 combinaisons SQL : [preuve](https://github.com/SPX-tech/ScolaPay/actions/runs/42900073926).
- [x] CI complète : 390 tests et JAR verts, [preuve](https://github.com/SPX-tech/ScolaPay/actions/runs/42901332810). Mutations ciblées : 15/15 détectées, un contrôle sain de 390 tests, [preuve](https://github.com/SPX-tech/ScolaPay/actions/runs/42901353861), commit `974e9c4`.
- [x] ADR 0023 et contexte finalisés ; les questions métier non validées restent explicites. La description de #26 porte le statut de livraison et la validation finale.
- Livraison pour revue finale dans #26 après les contrôles du commit publié. Aucune fusion ni aucun déploiement, PR #27 et #28 inchangées.

## Exécution

Aucun runner PC-02 disponible pour le dépôt au contrôle initial. Utilisation des runners GitHub Actions hébergés, sans VPS ni build local.
Suivi par une seule commande gh run watch --exit-status pour chaque exécution, sans boucle de consultation des statuts.
