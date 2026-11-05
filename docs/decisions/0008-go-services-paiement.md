# 0008 : Go pour des services de paiement spécialisés

- **Statut** : Orientation validée, usage éventuel
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Remplace** : aucune

## Contexte

Les paiements numériques sont aujourd'hui du code NestJS derrière l'interface `PaymentProvider` (`backend/src/modules/payment/`). Aucun prestataire réel n'est actif et le connecteur Kadryza est un squelette. **Aucun code Go n'existe dans le dépôt à cette date.**

## Décision

Go pourra être utilisé pour des services spécialisés liés aux paiements. Cette possibilité n'engage aucun service à ce stade.

## Conséquences

- Le backend principal reste la source de vérité des soldes. Un service spécialisé ne modifie pas directement ces tables, conformément à [D-001](../architecture-scolapay-2.md#décision-001--conserver-le-monolithe-modulaire).
- Créer un tel service demande une nouvelle décision, fondée sur un besoin mesuré ou un contrat de prestataire connu.

## Questions ouvertes

- Quels services, et selon quels critères les extraire.
- Contrat avec le backend principal : API synchrone, événements de l'outbox ou autre.
- Hébergement, secrets et conformité des échanges avec les prestataires.
