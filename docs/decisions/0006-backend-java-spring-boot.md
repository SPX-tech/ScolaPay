# 0006 : nouveau backend ScolaPay 2.0 en Java/Spring Boot

- **Statut** : Acceptée
- **Date** : 2026-11-06, précisée le même jour après le retrait de l'ancienne instance
- **Décideur** : propriétaire du projet
- **Remplace** : la partie de [D-001](../architecture-scolapay-2.md#décision-001--conserver-le-monolithe-modulaire) qui prévoit d'écrire les futurs domaines en modules NestJS. Le principe du monolithe modulaire est conservé.

## Contexte

Le backend actuel est un monolithe NestJS 12, Prisma 7 et PostgreSQL 16, exposé sous `/api/v1` et consommé par le web et Android. La fondation Java a été livrée par la Mission 002 dans `core/` : identités, établissements, inscriptions. **La facturation, les paiements et le portage des fonctions historiques ne sont pas commencés.**

L'ancienne instance a été retirée le 2026-11-06. Elle ne contenait qu'un établissement de démonstration, sans activité depuis juillet 2026. **Aucune instance de ScolaPay n'est donc en service à cette date.**

## Décision

- Le futur cœur métier sera développé en **Java et Spring Boot**, en **monolithe modulaire**. Évaluation de Spring Modulith : retenu en vérification, voir [0009](0009-fondation-technique-core.md).
- Les nouveaux domaines métier, notamment les **identités, les établissements, les inscriptions et la facturation scolaire**, seront développés directement en Java.
- Le nouveau backend est **construit directement**, plutôt que de maintenir durablement deux backends en parallèle, à condition qu'aucune dépendance de production ni donnée nécessaire ne soit perdue.
- Le **code NestJS est conservé dans le dépôt**, ainsi que ses branches et sa documentation, comme référence. Il n'y a **jamais de duplication des sources de vérité financières**.
- **Next.js/TypeScript** reste le socle des interfaces web et **PostgreSQL** celui des données.

## Conséquences

- Aucun nouveau domaine métier n'est ajouté à NestJS.
- Si les deux backends devaient coexister à un moment, chaque donnée financière garde un seul propriétaire applicatif.
- Les invariants de [`PROJECT_CONTEXT.md`](../PROJECT_CONTEXT.md#invariants-financiers) s'imposent au nouveau code. Les scénarios de `backend/test/financial-integrity.e2e-spec.ts` décrivent le comportement à reproduire.
- Le contrat HTTP `/api/v1` est la référence de départ pour les clients web et Android existants.

## Questions ouvertes

La stratégie de transition, le schéma, le contrat HTTP et les clients sont traités dans [0011](0011-transition-depuis-nestjs.md), les choix techniques dans [0009](0009-fondation-technique-core.md). Restent ouverts : le calendrier, l'ordre de portage des fonctions historiques et la condition de retrait du code NestJS.
