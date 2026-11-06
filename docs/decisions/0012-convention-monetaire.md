# 0012 : convention monétaire

- **Statut** : Acceptée
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Remplace** : pour le nouveau backend, la convention historique décrite par [D-003](../architecture-scolapay-2.md#décision-003--conserver-les-unités-financières-historiques)

## Contexte

Le code NestJS stocke des colonnes `*Cents` et affiche le montant divisé par 100, y compris pour le XAF, alors que cette devise n'a pas de subdivision selon ISO 4217. **Aucun modèle financier n'existe encore dans `core/`** : c'est le moment de fixer la règle, sans rien à migrer.

## Décision

**Un montant est un entier exprimé dans l'unité minimale ISO 4217 de sa devise.**

| Devise | Exposant ISO | Stocké pour 25 000 XAF ou 25,00 EUR |
| --- | --- | --- |
| XAF | 0 | `25000` (francs entiers, sans multiplication par 100) |
| EUR | 2 | `2500` (centimes) |

Règles pour tout futur modèle financier :

1. **Type.** `BIGINT` en base, `long` en Java, chaîne décimale dans le JSON pour préserver la précision côté JavaScript. Jamais de flottant.
2. **Devise explicite.** Un montant est toujours accompagné de sa devise, ou appartient à un contexte qui la fixe sans ambiguïté. Aucune opération ne mélange deux devises.
3. **Nommage neutre.** `amountMinor`, `balanceMinor`. Le suffixe `Cents` est interdit : il suggère une subdivision qui n'existe pas toujours.
4. **Affichage.** La mise en forme lit l'exposant ISO de la devise : aucun facteur 100 en dur.
5. **Prestataires.** Une conversion vers le format d'un prestataire reste dans son connecteur, à la frontière. Elle n'entre ni dans le modèle ni dans les événements internes, et elle est testée avec le connecteur.

## Conséquences

- Le moteur de facturation (Mission 003) applique cette règle dès sa première migration.
- Le code NestJS n'est pas modifié : sa convention reste documentée comme historique.
- **Importer un montant historique demanderait une conversion explicite.** Les colonnes `*Cents` du XAF valent 100 fois le montant en francs. Elles ne se copient jamais telles quelles. Aucun import n'est prévu à ce jour.
- Les invariants financiers de [`PROJECT_CONTEXT.md`](../PROJECT_CONTEXT.md#invariants-financiers) intègrent cette règle.

## Questions ouvertes

- Forme exacte du type `Money` en Java, à définir par la Mission 003.
- Politique d'arrondi des frais, remises et échéanciers fractionnés, à définir par la Mission 003.
