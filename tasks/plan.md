# Mission 001 : modernisation du socle

Le périmètre et les critères d'acceptation sont ceux de la mission fournie par le propriétaire. Aucune fonctionnalité métier nouvelle, aucun déploiement, aucune modification de secret. Les trois applications et PostgreSQL restent les fondations du produit.

## Ordre de réalisation

1. Établir une baseline à partir du commit `2f1dd15`, avec installations par lockfile, génération Prisma, migrations sur une base jetable, tests et builds distants.
2. Moderniser les dépendances compatibles, les images et les commandes. Ajouter une CI pour les trois applications. Livrer une première PR.
3. Corriger les défauts confirmés de sérialisation, validation financière, reporting, concurrence et isolation. Interdire les paiements simulés en production. Vérifier par tests de régression et livrer une PR dédiée.
4. Aligner le client Android sur les contrats HTTP existants et vérifier compilation, lint et contrats JSON. Livrer une PR dédiée.
5. Documenter les résultats exacts, limites de validation, décisions d'architecture et prochaines missions.

## Vérification et limites

Les tâches lourdes s'exécutent sur PC-02, sinon sur un répertoire et des conteneurs jetables du VPS. Aucun accès aux bases applicatives existantes. Les commandes de contrôle doivent être bloquantes en CI, sans `--force`, `--legacy-peer-deps` ni suppression de tests défaillants.

Les ajouts ciblés d'ESLint web, JUnit et de l'adaptateur PostgreSQL Prisma ont été autorisés pendant la mission. Une montée majeure PostgreSQL n'est pas nécessaire à cette stabilisation : conserver la branche 16 maintenue évite de changer le format du volume existant.

## Risques à contrôler

- Les tests E2E initiaux omettent les gardes de production.
- Le sérialiseur récursif perd les dates.
- Les colonnes SQL du reporting diffèrent du schéma généré.
- Les paiements cash lisent les soldes avant la transaction ; vérifier les courses avec une vraie base.
- Le fournisseur simulé est choisi par défaut en production.
- Android contient des modèles dupliqués et des routes incompatibles.

Les évolutions famille, facturation, échéanciers, réconciliation et administration SaaS seront documentées comme domaines futurs, sans implémentation dans cette mission.
