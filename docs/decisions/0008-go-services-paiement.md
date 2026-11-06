# 0008 : Go pour de futurs services financiers spécialisés

- **Statut** : Orientation validée, introduction non prévue immédiatement
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Remplace** : aucune

## Contexte

Les paiements numériques sont aujourd'hui du code NestJS derrière l'interface `PaymentProvider` (`backend/src/modules/payment/`). Aucun prestataire réel n'est actif et le connecteur Kadryza est un squelette. **Aucun code Go n'existe dans le dépôt à cette date.**

## Décision

Go reste une technologie envisagée pour de futurs services financiers spécialisés, notamment les intégrations avec les prestataires de paiement. Son introduction n'est pas prévue immédiatement.

## Conséquences

- Un service spécialisé ne devient pas une seconde source de vérité : il n'écrit pas directement les soldes, conformément à [0006](0006-backend-java-spring-boot.md).
- Créer un tel service demande une nouvelle décision, fondée sur un besoin mesuré ou un contrat de prestataire connu.

## Questions ouvertes

- Quels services, et selon quels critères les extraire.
- Contrat avec le cœur métier : API synchrone, événements ou autre.
- Hébergement, secrets et conformité des échanges avec les prestataires.
