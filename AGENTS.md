# Consignes pour les agents de développement

Lire [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md) avant de travailler : vision, état réel, décisions, priorités et risques y sont centralisés. Vérifier ce qu'il affirme dans le code quand un choix en dépend.

**Priorité actuelle : [Mission 005, paiements scolaires numériques et intégration de l'API Kadryza existante](docs/missions/mission-005-digital-school-payments.md), dans le cœur Java `core/`.** La Mission 003 reste clôturée. Les compléments cash de Mission 004 sont suspendus, leurs fondations intégrées restent protégées. Aucun nouveau backend de paiement ou service Go par défaut. Les interfaces web viendront ensuite ; Families reste fermé.

Hors de la feuille de route active, à ne pas proposer ni entreprendre sans demande explicite :

- **Android** : aucun développement, refonte Kotlin, test d'émulateur ou chantier APK. Le code est conservé comme référence ([0014](docs/decisions/0014-android-hors-feuille-de-route.md)).
- **NestJS** : `backend/` n'est plus modifié, il reste une référence ([0011](docs/decisions/0011-transition-depuis-nestjs.md)).
- **Déploiement** : rien n'est déployé sans autorisation explicite du propriétaire.

Règles propres à ce dépôt :

- **Le code prime.** Ne jamais présenter une fonctionnalité prévue comme terminée, dans la documentation comme dans une PR.
- **Mémoire du projet.** Une PR qui change une fonctionnalité, une priorité, un risque ou une décision met à jour `PROJECT_CONTEXT.md` dans cette même PR. Les décisions majeures vont dans [`docs/decisions/`](docs/decisions/README.md).
- **Dépôt public.** Pas de secret, d'adresse de serveur ni de détail d'exposition de la production dans le dépôt, les issues ou les PR.
- **Argent.** Respecter les [invariants financiers](docs/PROJECT_CONTEXT.md#invariants-financiers) et la [convention monétaire](docs/decisions/0012-convention-monetaire.md). Facturer n'est pas encaisser : aucun code ne doit permettre de considérer une obligation réglée sans encaissement réel. Ne pas supprimer un scénario de `backend/test/financial-integrity.e2e-spec.ts` pour obtenir une CI verte.
- **Nouveau backend.** Tout nouveau domaine métier va dans `core/`, jamais dans NestJS. Suivre les conventions de [`core/README.md`](core/README.md). Une frontière de module ne s'assouplit que par une décision explicite.
- **Suppressions irréversibles.** Toute opération susceptible de supprimer définitivement des données, des volumes ou des ressources importantes exige l'autorisation explicite du propriétaire, donnée après présentation de son impact et vérification d'une sauvegarde restaurable. Cela vaut même si la demande paraît claire, et pour les scripts distants, qui peuvent continuer après une coupure de connexion. Le travail courant n'est pas concerné : branches, artefacts de build, environnements de test jetables.
- **Migrations.** Les migrations existantes sont immuables. Jamais de `prisma migrate dev`, `db push` ou reset sur une base persistante.
- **Vérification.** Les compilations et suites de tests lourdes passent par la CI, pas par le poste de développement de 8 Go. Une campagne de défauts injectés s'exécute entièrement sur GitHub Actions : voir [`.github/mutation/README.md`](.github/mutation/README.md).
