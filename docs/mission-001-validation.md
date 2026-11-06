# Mission 001 : journal de validation

## Baseline

Point de départ : `2f1dd15`. Dépôt propre, sans workflow GitHub Actions. Les runners PC-02 n'apparaissaient pas dans l'inventaire de l'organisation. Les runners VPS disponibles étaient dans des groupes interdisant les dépôts publics. Le propriétaire a confirmé l'utilisation temporaire de la CI GitHub.

Une première baseline a été exécutée dans `/home/spencer/work/scolapay-m001/baseline`, avec une base PostgreSQL jetable et sans charger de configuration applicative existante. Les installations par lockfile, la génération Prisma, les migrations et la compilation backend ont réussi. Les tests E2E existants ont donné 13 succès et 3 échecs. Le premier lint backend GitHub a révélé 158 erreurs et 23 avertissements après ses corrections automatiques historiques.

Le test ajouté sur le sérialiseur a reproduit une date ISO transformée en objet vide. Les tests de validation monétaire ont reproduit les dépassements de BIGINT et l'acceptation d'entrées invalides : 8 échecs attendus et 7 succès sur les deux suites ciblées avant correction.

Première exécution CI exploitable : [42101514091](https://github.com/SPX-tech/ScolaPay/actions/runs/42101514091). L'installation du SDK Android demandait le paquet retiré `tools`. Après correction, la compilation a mis en évidence les ressources Android invalides. L'inspection a aussi confirmé les modèles Kotlin dupliqués, les identifiants incompatibles et les routes absentes.

## Matrice retenue

| Technologie | Cible | Motif |
| --- | --- | --- |
| Node.js | 24.22.1 | Branche LTS corrigée, commune aux images et à la CI |
| NestJS | 12.3.3 | La branche 11 conserve une version de Multer vulnérable ; migration avec TypeScript 6 |
| Prisma | 7.10.1 | Version 7 stable, pilote PostgreSQL explicite, aucun saut vers Prisma 8 en préversion |
| PostgreSQL | 16.15 | Branche maintenue, format des volumes conservé |
| Next.js | 16.4.5 | Branche stable avec migrations CSS, lint et React vérifiées |
| React | 19.3.1 | Version stable compatible avec Next.js 16 |
| TypeScript | 6.0.3 backend, 5.9.3 web | Contraintes vérifiées par application |
| Kotlin / compilateur Compose | 2.4.21 | Versions alignées |
| AGP / Gradle / JDK | 9.3.3 / 9.6.0 / 17 | SDK 37 requis par Compose, Kotlin intégré et Hilt 2.60.1 |
| Compose BOM | 2026.09.00 | BOM stable publiée dans le dépôt Google |

Les dépendances npm directes sont figées et les lockfiles produits ensemble sur un runner GitHub. Aucun `--force`, `--legacy-peer-deps` ou override de dépendance incompatible n'est utilisé.

AGP 9 et Hilt 2.60.1 sont retenus car Compose 2026.09.00 exige le SDK 37 et AGP 9.1 au minimum. Kotlin utilise le support intégré, sans option de compatibilité temporaire. Le SDK Africa's Talking a été retiré : ses versions publiées 0.7.9 et 0.8.4 figent des versions vulnérables d'Axios et Joi. Son seul usage est remplacé par un transport SMS Node borné et testé, en conservant le fournisseur et le mode simulé sans clé. La CI n'envoie aucun SMS.

ESLint 10 utilise les plugins officiels Next.js et React Hooks directement. Le paquet de configuration agrégé Next.js entraîne encore des plugins dont les contraintes déclarées excluent ESLint 10 ; aucune contrainte n'est forcée pour les installer. Les tests Jest exécutent les imports dynamiques de Prisma avec le mode VM ESM documenté par Jest.

## Résultats finaux

Commit `f50feec` de `mission001/financial-hardening`, qui intègre `mission001/ci-baseline`. [CI complète](https://github.com/SPX-tech/ScolaPay/actions/runs/42134244570) et [conteneurs](https://github.com/SPX-tech/ScolaPay/actions/runs/42134244633) verts.

| Application | Résultat observé |
| --- | --- |
| Backend | 190 tests unitaires (17 suites), 35 E2E sur PostgreSQL 16.15 jetable, migrations rejouées sans dérive, seed, lint sans erreur (44 avertissements), audit de production : 0 vulnérabilité |
| Web | Lint, build, types, 2 tests monétaires, 47 tests Playwright sur le rendu autonome, audit de production : 0 vulnérabilité |
| Android | Compilation, tests JVM, lint, puis sur émulateur API 35 : écran de connexion affiché, journal de crash vide |
| Conteneurs | Images API, migration et web, démarrage derrière Nginx avec `PAYMENT_PROVIDER=disabled` |

### Défauts financiers corrigés

Résultats avant correction : 2 tests unitaires et 6 E2E en échec. Après dix encaissements concurrents de 1000 sur une dette de 10000, il restait `8000` au lieu de `0`. Le cash concurrent avec un paiement mobile laissait `3000` au lieu de `0`. Le reporting échouait sur la colonne absente `recorded_at`. Ces scénarios passent désormais sans avoir été retirés.

- **Simulation en production** : fournisseur `disabled` par défaut ; `mock`, `kadryza` et les valeurs inconnues empêchent le démarrage selon l'environnement.
- **Concurrence** : verrou de ligne par élève partagé par l'encaissement, la confirmation mobile, l'annulation et la modification d'un élève, avec outbox et audit dans la transaction.
- **Annulations** : transitions relues sous verrou, cash uniquement.
- **Isolation** : classe vérifiée contre l'établissement à la création et à la modification d'un élève.
- **Reporting** : colonnes réelles, sommes et dates typées, période validée, journées calculées dans le fuseau de l'établissement, journée finale incluse. Les filtres de dates des transactions suivent les mêmes règles.
- **Webhook** : contenu validé strictement après la signature et avant toute mutation.
- **Cache web** : cache lié à la session et clés portant l'établissement.

La revue et les tests ont aussi révélé des défauts absents de la liste initiale, corrigés :

- Un paiement mobile gardait sa date d'initiation : annuler un cash enregistré entre l'initiation et la confirmation aurait effacé l'effet du paiement mobile. La date devient celle de l'application, et l'annulation refuse tout solde modifié depuis.
- L'empreinte du reçu était calculée sur une date différente de celle stockée, donc non recalculable.
- Le nettoyage des fixtures E2E se heurtait au trigger d'immuabilité du grand livre, ce qui bloquait le processus de test. L'application se ferme maintenant dans un `finally`.
- Le formulaire élève envoyait les valeurs `__GRADUATED__` et `__SUSPENDED__` comme identifiant de classe.
- Le tableau des transactions affichait un paiement mobile comme remboursement et un échec comme annulation.
- La page Étudiants débordait de 19 px à 360 px, et le rapport affichait une heure parasite sur les journées, avec un risque de décalage d'un jour selon le fuseau du navigateur.

### Limites de cette validation

Non vérifié :

- Aucun déploiement, et aucune donnée réelle n'a été utilisée.
- Aucun SMS réel ni appel à un prestataire de paiement.
- Les tests Playwright utilisent une API simulée : l'intégration web et API réelle n'est pas couverte de bout en bout. L'API est couverte séparément par les E2E backend.
- Sur Android, seuls le lancement et l'écran de connexion sont contrôlés, pas les parcours métier.
- La restauration d'une sauvegarde n'a pas été répétée.
- `npm ci` signale 30 vulnérabilités basses ou modérées dans les dépendances de développement du backend, hors du périmètre de l'audit de production.

Les runners PC-02 n'apparaissant toujours pas sur le dépôt, toute la validation a utilisé les runners hébergés de GitHub, comme confirmé par le propriétaire. Le lockfile web a été résolu avec `--package-lock-only`, puis contrôlé par `npm ci --strict-peer-deps` en CI.

Les priorités et les risques restants sont dans [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md).

## Sources des décisions de version

- [Cycle de support Node.js](https://github.com/nodejs/Release)
- [Migration Next.js 16](https://nextjs.org/docs/app/guides/upgrading/version-16)
- [Migration Prisma 7](https://www.prisma.io/docs/guides/upgrade-prisma-orm/v7)
- [Prisma avec NestJS](https://docs.nestjs.com/recipes/prisma)
- [Migration NestJS 12](https://docs.nestjs.com/migration-guide)
- [Support PostgreSQL](https://www.postgresql.org/support/versioning/)
- [Compatibilité AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Configuration Hilt](https://dagger.dev/hilt/gradle-setup)
