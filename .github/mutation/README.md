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
| `VERT`, `INSTABLE` | Résultat d'une exécution de contrôle du code intact |

Une campagne n'est concluante que si tous les défauts sont `DETECTE` et tous les contrôles `VERT`.

Pour ajouter une campagne, créer `<nom>.mjs` sur le modèle de `lot4.mjs`. Le plan vérifie que chaque défaut s'applique avant de lancer le moindre test.
