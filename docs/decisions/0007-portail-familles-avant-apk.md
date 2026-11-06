# 0007 : portail web des familles avant une application Android

- **Statut** : Acceptée
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Remplace** : aucune

## Contexte

Le dashboard web et l'application Android (`android/`) servent uniquement le personnel, sur `/api/v1`. Depuis la Mission 002, `core/` modélise les comptes des familles et leurs liens aux dossiers ([0010](0010-identite-et-acces.md)) et expose `/api/v2/me`. **Le portail lui-même n'existe pas : aucune interface n'est destinée aux familles.**

## Décision

Le portail destiné aux parents et aux étudiants sera d'abord un véritable site web, en Next.js/TypeScript. L'application Android destinée aux familles viendra dans une phase ultérieure.

## Conséquences

- L'application Android existante reste un outil du personnel. Ne pas la présenter comme l'application des familles.
- Le portail consomme `/api/v2/me` et s'appuie sur la fondation Java, livrée.
- **Condition d'ouverture réelle :** les modalités juridiques de [0013](0013-majorite-et-autorisations-familiales.md) doivent être confirmées.
- [D-002](../architecture-scolapay-2.md#décision-002--isolation-explicite-par-établissement) reste applicable : pas de transformation directe de `User` en compte parent global, et une relation famille-élève n'accorde jamais d'accès à tout l'établissement.

## Questions ouvertes

- Application Next.js séparée ou espace du dashboard actuel.
- Mode de session du portail, et remise réelle des invitations, sans laquelle une famille ne peut pas ouvrir de compte.
- Périmètre de la première version : consultation des soldes et reçus, ou paiement en ligne. Le paiement dépend d'un prestataire réel, voir [D-004](../architecture-scolapay-2.md#décision-004--prestataires-financiers-fermés-par-défaut).
- Critères de lancement de l'application Android des familles.
