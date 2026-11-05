# 0007 : portail web des familles avant une APK

- **Statut** : Orientation validée
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Remplace** : aucune

## Contexte

Le produit actuel sert uniquement le personnel des établissements. Le dashboard web et l'application Android (`android/`) demandent un code établissement et un compte de personnel. **Il n'existe ni compte parent ou étudiant, ni lien famille-élève, ni portail familles.** Le modèle `User` appartient à un seul établissement et ses rôles sont `TENANT_ADMIN`, `CASHIER`, `ACCOUNTANT` et `AUDITOR`.

## Décision

Les parents et les étudiants accéderont d'abord à ScolaPay par un portail web. Une APK destinée aux familles viendra plus tard.

## Conséquences

- L'application Android existante reste un outil du personnel. Ne pas la présenter comme l'APK des familles.
- Le portail s'appuie sur le socle web Next.js/TypeScript.
- Le portail exige une identité distincte : [D-002](../architecture-scolapay-2.md#décision-002--isolation-explicite-par-établissement) écarte la transformation directe de `User` en compte parent global et demande une migration additive. Une relation famille-élève n'accorde jamais d'accès à tout l'établissement.

## Questions ouvertes

- Application Next.js séparée ou espace du dashboard actuel.
- Mode d'authentification des familles et preuve du lien avec l'élève.
- Périmètre de la première version : consultation des soldes et reçus, ou paiement en ligne. Le paiement dépend d'un prestataire réel, voir [D-004](../architecture-scolapay-2.md#décision-004--prestataires-financiers-fermés-par-défaut).
- Critères de lancement de l'APK familles.
