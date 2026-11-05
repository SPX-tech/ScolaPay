# 0006 : backend cible en Java/Spring Boot

- **Statut** : Orientation validée
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Remplace** : la partie de [D-001](../architecture-scolapay-2.md#décision-001--conserver-le-monolithe-modulaire) qui prévoit d'écrire les futurs domaines en modules NestJS

## Contexte

Le backend actuel est un monolithe NestJS 12, Prisma 7 et PostgreSQL 16, exposé sous `/api/v1` et consommé par le web et Android. **Aucun code Java n'existe dans le dépôt à cette date.**

## Décision

ScolaPay s'oriente vers une migration du backend vers Java/Spring Boot. Next.js/TypeScript reste le socle web et PostgreSQL reste la base de données.

## Conséquences

- PostgreSQL et ses données sont l'actif à préserver : la migration porte sur le code applicatif, pas sur les données.
- Le contrat HTTP `/api/v1` et les invariants financiers de [`PROJECT_CONTEXT.md`](../PROJECT_CONTEXT.md#invariants-financiers) deviennent la référence à reproduire. Les tests E2E de `backend/test/financial-integrity.e2e-spec.ts` décrivent ces comportements et devront rester vérifiables contre le nouveau backend.
- Tant que la migration n'a pas abouti, NestJS reste le backend de référence. Toute correction financière s'y applique.

## Questions ouvertes

Non tranchées, à cadrer avant de démarrer. Ne pas les supposer :

- Calendrier et motif de déclenchement ; les motivations détaillées ne sont pas consignées dans le dépôt.
- Stratégie : remplacement progressif par domaine derrière le proxy, ou bascule complète.
- Langage des nouveaux domaines (familles, facturation) pendant la transition.
- Versions de Java et de Spring Boot, couche d'accès aux données.
- Propriété du schéma : l'outil de migration devra rester unique à tout moment, les migrations Prisma existantes étant immuables.
- Exclusion de deux écrivains concurrents sur les soldes : le verrou `lockStudentBalance` n'est sûr que si toute écriture le respecte.
