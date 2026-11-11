# Campagnes de défauts injectés

Elles vérifient que les tests savent échouer : un défaut est introduit dans le code, un à la fois, et au moins un test doit le détecter.

**Tout s'exécute sur GitHub Actions.** Le poste de développement, limité à 8 Go de mémoire, ne fait que pousser une référence et lire un rapport.

```bash
git push origin HEAD:refs/heads/campaign/lot4/$(git rev-parse --short HEAD)
```

Le rapport est l'artefact `campaign-report` et le résumé de l'exécution. Il porte le commit testé. Supprimer ensuite la référence `campaign/...`.

| Résultat | Signification |
| --- | --- |
| `DETECTE` | Du code compilé, au moins un test échoue, et ceux de `mustFail` en font partie |
| `SURVIVANT` | Aucun test n'échoue : une garantie n'est pas prouvée |
| `MAUVAIS_TEST` | Un test échoue, mais pas celui qui doit prouver cette garantie |
| `INAPPLICABLE`, `NE_COMPILE_PAS`, `SANS_RESULTAT` | Le défaut n'a rien prouvé, et ne compte jamais comme détecté |
| `ERREUR_TEST` | Une erreur d'exécution sans échec d'assertion ne prouve pas la garantie |
| `VERT`, `INSTABLE` | Résultat d'une exécution de contrôle du code intact |

Une campagne n'est concluante que si tous les défauts sont `DETECTE` et tous les contrôles `VERT`. Le rapport vérifie aussi les identifiants attendus, les résultats manquants, inattendus ou en double. Un test désigné par `mustFail` doit avoir échoué par assertion. Les contrôles refusent les tests ignorés.

Les statuts sont vérifiés selon le rôle du résultat. Un mutant `VERT` ou un contrôle `DETECTE` interdit une conclusion positive. Le workflow exige à la fois la réussite de la génération et une ligne exacte `Conclusion : CONCLUANTE`. Un rapport vide, une conclusion absente ou un générateur en erreur fait échouer le job.

Le workflow `Mutation evidence` requalifie les artefacts JSON de la campagne finale existante avec l'outillage corrigé. Il vérifie le SHA source et publie un rapport mentionnant les deux SHA. Il exécute les 13 tests du rapport et des commandes réelles du workflow, dont un générateur qui imprime une conclusion positive avant de terminer en erreur.

Pour ajouter une campagne, créer `<nom>.mjs` sur le modèle de `lot4.mjs`. Le plan vérifie que chaque défaut s'applique avant de lancer le moindre test.

Une campagne peut préciser `testSelector` pour ses mutants et contrôles. Les campagnes historiques gardent par défaut toute la suite Java. La campagne `lot7` utilise les suites financières, de modularité et d'isolation sur le même code que la CI complète. Son rapport indique le nombre de tests de ces contrôles, distinct du nombre de tests de la CI complète.

`lot7` reprend 175 mutations de `lot6` et les 15 de `lot6-finalization`. Le doublon `db-party-mutable` est remplacé par `correction-without-trace`, qui exige une assertion précise. Cinq mutations du catalogue complètent les contrôles de priorité des unités, d'échéancier, d'identité des charges et d'immuabilité du tarif. Les 195 mutations et trois contrôles utilisent PostgreSQL jetable sur GitHub Actions.
