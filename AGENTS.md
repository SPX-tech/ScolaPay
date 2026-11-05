# Consignes pour les agents de développement

Lire [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md) avant de travailler : vision, état réel, décisions, priorités et risques y sont centralisés. Vérifier ce qu'il affirme dans le code quand un choix en dépend.

Règles propres à ce dépôt :

- **Le code prime.** Ne jamais présenter une fonctionnalité prévue comme terminée, dans la documentation comme dans une PR.
- **Mémoire du projet.** Une PR qui change une fonctionnalité, une priorité, un risque ou une décision met à jour `PROJECT_CONTEXT.md` dans cette même PR. Les décisions majeures vont dans [`docs/decisions/`](docs/decisions/README.md).
- **Dépôt public.** Pas de secret, d'adresse de serveur ni de détail d'exposition de la production dans le dépôt, les issues ou les PR.
- **Argent.** Respecter les [invariants financiers](docs/PROJECT_CONTEXT.md#invariants-financiers). Ne pas supprimer un scénario de `backend/test/financial-integrity.e2e-spec.ts` pour obtenir une CI verte.
- **Migrations.** Les migrations existantes sont immuables. Jamais de `prisma migrate dev`, `db push` ou reset sur une base persistante.
- **Vérification.** Les compilations et suites de tests lourdes passent par la CI, pas par le poste de développement de 8 Go.
