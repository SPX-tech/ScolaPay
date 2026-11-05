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
| NestJS | 11.2.8 | Correctif de la branche existante ; la version 12 implique aussi une migration ESM et TypeScript 6 |
| Prisma | 7.10.1 | Version 7 stable, pilote PostgreSQL explicite, aucun saut vers Prisma 8 en préversion |
| PostgreSQL | 16.15 | Branche maintenue, format des volumes conservé |
| Next.js | 16.4.5 | Branche stable avec migrations CSS, lint et React vérifiées |
| React | 19.3.1 | Version stable compatible avec Next.js 16 |
| TypeScript | 5.9.3 | Compatible avec le backend conservé et Prisma 7 |
| Kotlin / compilateur Compose | 2.3.20 | Versions alignées |
| AGP / Gradle / JDK | 8.13.2 / 8.13 / 17 | Combinaison documentée, sans migration AGP 9 |
| Compose BOM | 2026.09.00 | BOM stable publiée dans le dépôt Google |

Les dépendances npm directes sont figées et les lockfiles produits ensemble sur un runner GitHub. Aucun `--force`, `--legacy-peer-deps` ou override de dépendance incompatible n'est utilisé.

## Résultats finaux

À compléter avec les liens des exécutions finales, les comptes de tests et les limitations effectivement observées avant livraison. Une exécution commencée ou une étape ignorée ne vaut pas validation.

## Sources des décisions de version

- [Cycle de support Node.js](https://github.com/nodejs/Release)
- [Migration Next.js 16](https://nextjs.org/docs/app/guides/upgrading/version-16)
- [Migration Prisma 7](https://www.prisma.io/docs/guides/upgrade-prisma-orm/v7)
- [Prisma avec NestJS](https://docs.nestjs.com/recipes/prisma)
- [Migration NestJS 12](https://docs.nestjs.com/migration-guide)
- [Support PostgreSQL](https://www.postgresql.org/support/versioning/)
- [Compatibilité AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Configuration Hilt](https://dagger.dev/hilt/gradle-setup)
