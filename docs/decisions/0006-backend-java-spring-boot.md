# 0006 : cœur métier ScolaPay 2.0 en Java/Spring Boot

- **Statut** : Acceptée
- **Date** : 2026-11-06, confirmée le même jour après l'orientation initiale
- **Décideur** : propriétaire du projet
- **Remplace** : la partie de [D-001](../architecture-scolapay-2.md#décision-001--conserver-le-monolithe-modulaire) qui prévoit d'écrire les futurs domaines en modules NestJS. Le principe du monolithe modulaire est conservé.

## Contexte

Le backend actuel est un monolithe NestJS 12, Prisma 7 et PostgreSQL 16, exposé sous `/api/v1` et consommé par le web et Android. **Aucun code Java n'existe dans le dépôt à cette date, et la migration n'est pas commencée.** La fondation Java fera l'objet d'une mission distincte, avec ses propres objectifs.

## Décision

- Le futur cœur métier sera développé en **Java et Spring Boot**, en **monolithe modulaire**. Spring Modulith est une option à évaluer sérieusement.
- Les nouveaux domaines métier, notamment les **identités, les établissements, les inscriptions et la facturation scolaire**, seront développés directement en Java.
- Le **backend NestJS est conservé pendant la transition**, avec une migration progressive et **sans duplication des sources de vérité financières**.
- **Next.js/TypeScript** reste le socle des interfaces web et **PostgreSQL** celui des données.

## Conséquences

- Aucun nouveau domaine métier n'est ajouté à NestJS. NestJS reçoit les corrections et la maintenance de ce qu'il porte déjà, et reste le backend de référence tant qu'un domaine n'a pas été migré.
- À tout instant, chaque donnée financière a un seul propriétaire applicatif. Tant que les soldes des élèves et le compte virtuel n'ont pas migré, seul NestJS les écrit.
- Les invariants de [`PROJECT_CONTEXT.md`](../PROJECT_CONTEXT.md#invariants-financiers) s'imposent au nouveau code. Les scénarios de `backend/test/financial-integrity.e2e-spec.ts` décrivent le comportement à préserver lors de toute migration d'un domaine financier.
- Le contrat HTTP `/api/v1` reste la référence pour les clients existants.

## Questions ouvertes

À trancher dans la mission de fondation. Ne pas les supposer :

- **Propriétaire de l'existant pendant la transition.** Les établissements (`Tenant`) et les comptes du personnel (`User`, connexion, jeton signé) existent déjà dans NestJS, alors que ces domaines sont destinés à Java. Qui les écrit, à partir de quand, et comment les deux backends se font-ils confiance sur un même jeton ?
- **Frontière financière.** Comment la facturation en Java déclenche et observe les paiements et soldes portés par NestJS sans les écrire : API interne, événements de l'outbox ou autre.
- **Schéma PostgreSQL.** Base et schéma partagés ou séparés, et outil de migration propriétaire de chaque périmètre. Les migrations Prisma existantes restent immuables.
- **Routage.** Répartition des routes entre les deux backends derrière le proxy.
- **Choix techniques.** Évaluation de Spring Modulith, versions de Java et de Spring Boot, couche d'accès aux données.
- **Séquence et critères.** Calendrier, ordre de migration des domaines existants, et condition de retrait de NestJS.
