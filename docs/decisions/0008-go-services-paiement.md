# 0008 : Go pour de futurs services financiers spécialisés

- **Statut** : Orientation validée, introduction non prévue immédiatement
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Remplace** : aucune

## Contexte

Lors de cette décision du 6 novembre, les paiements numériques se limitaient au code NestJS derrière `PaymentProvider`. Aucun prestataire réel n'était actif et le connecteur Kadryza était un squelette. Aucun code Go ScolaPay n'existe dans ce dépôt.

## Décision

Go reste une technologie envisagée pour de futurs services financiers spécialisés, notamment les intégrations avec les prestataires de paiement. Son introduction n'est pas prévue immédiatement.

**Précision de Mission 005, 12 novembre :** intégrer l'API Kadryza existante dans le cœur Java, pas construire un nouveau backend ou réimplémenter ses opérateurs. Le Go déjà utilisé par Kadryza reste chez le fournisseur ; il ne justifie aucun service Go ScolaPay. Voir [0032](0032-intentions-numeriques-et-integration-kadryza.md).

## Conséquences

- Un service spécialisé ne devient pas une seconde source de vérité : il n'écrit pas directement les soldes, conformément à [0006](0006-backend-java-spring-boot.md).
- Créer un tel service demande une nouvelle décision, fondée sur un besoin mesuré ou un contrat de prestataire connu.

## Questions ouvertes

- Quels services, et selon quels critères les extraire.
- Contrat avec le cœur métier : API synchrone, événements ou autre.
- Hébergement, secrets et conformité des échanges avec les prestataires.
