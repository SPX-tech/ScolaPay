# 0015 : modèle du moteur de facturation

- **Statut** : Proposée. Les principes marqués « exigé » viennent du propriétaire, le reste attend sa validation.
- **Date** : 2026-11-06
- **Implémentation** : aucune. Voir le [cadrage de la Mission 003](../missions/mission-003-billing-engine.md).

## Contexte

Il faut définir des frais, générer des obligations, gérer échéances, remises et ajustements, et calculer ce qui est réellement dû. Le code NestJS porte une dette modifiable directement sur l'élève : ce modèle n'est pas à reproduire. `core/` ne contient encore aucun montant.

## Principes

1. **Facturer n'est pas encaisser** (exigé). Le moteur constate ce qui est dû. Il ne peut pas constater un règlement : aucune route, aucun ajustement ni aucun statut manuel ne rend une obligation réglée.
2. **Le dû se calcule, il ne se saisit pas.** Aucun solde modifiable : le montant dû découle d'écritures immuables.
3. **Une obligation émise ne se réécrit pas.** Un changement de tarif ne touche pas le passé, une correction passe par un ajustement motivé.
4. **Unité monétaire** (exigé) : [0012](0012-convention-monetaire.md), avec `amount_minor` en `BIGINT` et la devise de l'établissement.
5. **Isolation** (exigé) : `establishment_id`, clés composites et surface `/establishments/{id}/billing/**`, comme dans [0010](0010-identite-et-acces.md).

## Modèle proposé

Un module `billing`, au-dessus de `schooling`. Il ne lit ni n'écrit les tables des autres modules.

| Concept | Rôle | Écriture |
| --- | --- | --- |
| `fee_catalogs`, `fee_items` | Frais d'une année académique, par niveau ou pour tous, avec leur plan d'échéances | Modifiables tant qu'ils ne sont pas publiés, puis figés |
| `obligations` | Ce qu'un dossier doit pour une année : copie figée des frais applicables au moment de l'émission | Immuable après émission, hors statut |
| `installments` | Échéances datées d'une obligation. La somme égale le montant émis, l'arrondi va sur la dernière | Immuables, un rééchelonnement crée une nouvelle version |
| `obligation_entries` | Journal immuable : `ISSUED`, `DISCOUNT`, `ADJUSTMENT`, et `SETTLEMENT` réservé au futur module de paiement | Ajout seul, trigger d'immuabilité |
| `obligation_parties` | Personnes liées à une obligation et leur rôle | Ajout et révocation, jamais d'effacement |

**Montant dû** = somme des écritures de l'obligation. Une remise totale donne un dû nul et le statut `WAIVED`, **jamais** `SETTLED`. Le statut `SETTLED` n'est atteignable qu'avec des écritures `SETTLEMENT`, qu'aucun code de la Mission 003 ne peut créer.

Une écriture porte une clé d'idempotence unique. Une émission est unique par dossier, année et version de catalogue. Le verrou de ligne porte sur l'obligation.

### Personnes liées à une obligation

Trois rôles distincts (exigé), portés par `billing`. Ils ne se déduisent pas d'un lien familial.

| Rôle | Signification | Compte requis ? |
| --- | --- | --- |
| `CONTRACTUAL_DEBTOR` | Personne contractuellement redevable | Non : un parent sans compte ou une entreprise peut l'être |
| `FINANCIAL_RESPONSIBLE` | Responsable financier désigné, interlocuteur de l'établissement | Non |
| `VOLUNTARY_CONTRIBUTOR` | Peut régler, sans aucune obligation | Oui |

### Ce que chacun voit

Calculé côté serveur par une seule règle, comme [`AcademicAccess`](0013-majorite-et-autorisations-familiales.md).

| Information | Redevable | Responsable | Contributeur |
| --- | --- | --- | --- |
| Établissement, référence, devise | Oui | Oui | Oui |
| Montant restant dû et prochaine échéance | Oui | Oui | **Oui, strictement nécessaire au règlement** |
| Nom de l'élève | Oui | Oui | Oui, pour identifier ce qu'il règle |
| Détail des lignes de frais | Oui | Oui | Non |
| Remises, bourses et leurs motifs | Oui | À trancher | Non |
| Échéancier complet, historique | Oui | Oui | Non |
| Autres personnes liées et leurs versements | Non par défaut | Non par défaut | Non |

**Un rôle financier n'ouvre jamais l'accès académique** ([0013](0013-majorite-et-autorisations-familiales.md)). Un intitulé comme « Frais de Terminale D » révèle pourtant une information académique : pour un étudiant majeur sans consentement, les lignes montrées à un tiers utilisent un libellé neutre.

### Relation avec `financial_contributor`

Ce booléen de `record_access_grants` devient insuffisant. Il sera remplacé par une migration additive : un lien dit « ce compte est lié à ce dossier », `obligation_parties` dit quel rôle il tient sur quelle obligation. `StudentDirectory.financialContributorAccounts` sera retiré à ce moment.

### Événements

`StudentEnrolled` déclenche l'émission, via l'[outbox durable](0016-outbox-durable.md). `EnrollmentEnded` n'annule rien automatiquement : l'obligation reste due et passe en revue, voir les questions ouvertes.

## Conséquences

- Le module de paiement à venir n'aura qu'un seul point d'entrée vers `billing` : enregistrer un `SETTLEMENT` adossé à une preuve d'encaissement.
- Les rapports financiers se construiront sur le journal, sans colonne de solde à maintenir.

## Questions ouvertes

Listées dans le [cadrage](../missions/mission-003-billing-engine.md#décisions-attendues-du-propriétaire).
