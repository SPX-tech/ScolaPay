# 0015 : modèle du moteur de facturation

- **Statut** : Acceptée. **Les règles juridiques restent à valider avant toute exploitation réelle.**
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Implémentation** : voir le [cadrage de la Mission 003](../missions/mission-003-billing-engine.md).

## Contexte

Il faut définir des frais, générer des obligations, gérer échéances, remises et ajustements, et calculer ce qui est réellement dû. Le code NestJS porte une dette modifiable directement sur l'élève : ce modèle n'est pas à reproduire. `core/` ne contient encore aucun montant.

## Principes

1. **Facturer n'est pas encaisser.** Le moteur constate ce qui est dû. Il ne peut pas constater un règlement : aucune route, aucun ajustement ni aucun statut manuel ne rend une obligation réglée sans opération financière confirmée.
2. **Le dû se calcule, il ne se saisit pas.** Aucun solde modifiable : le montant restant dû découle d'écritures immuables.
3. **Une obligation publiée conserve son historique.** Un changement de tarif ne touche pas le passé, une correction passe par un ajustement autorisé, motivé et tracé.
4. **Jamais de double facturation.** Ni à la publication d'une nouvelle version du catalogue, ni au rejeu d'un événement, ni lors d'une émission en lot.
5. **Unité monétaire** : [0012](0012-convention-monetaire.md), avec `amount_minor` en `BIGINT` et la devise de l'établissement.
6. **Isolation** : `establishment_id`, clés composites et surface `/establishments/{id}/billing/**`, comme dans [0010](0010-identite-et-acces.md).

## Modèle

Un module `billing`, au-dessus de `schooling`. Il ne lit ni n'écrit les tables des autres modules.

| Concept | Rôle | Écriture |
| --- | --- | --- |
| `fee_catalogs`, `fee_items` | Versions de frais d'une année, par [unité académique](0018-catalogue-academique.md) ou pour tous, avec leur plan d'échéances et leur [période de facturation](0020-catalogue-de-frais-et-periodes.md). Chaque frais renvoie à une identité stable d'une version à l'autre | Modifiables en brouillon, figés à la publication |
| `obligations` | Ce qu'un dossier doit pour une année. **Ce n'est pas une facture**, voir [0019](0019-nature-des-documents-et-numerotation.md). Porte une référence interne unique, stable et non réutilisable | Immuable après émission, hors statut |
| `obligation_lines` | Copie figée d'un frais facturé : identité de la charge, code et libellé du moment, montant et version de catalogue d'origine | Ajout seul, jamais modifiée |
| `installments` | Échéances datées d'une obligation. La somme égale le montant émis, l'arrondi va sur la dernière | Immuables, un rééchelonnement crée une nouvelle version |
| `obligation_entries` | Journal immuable : `ISSUED`, `DISCOUNT`, `ADJUSTMENT`, et `SETTLEMENT` réservé au futur module de paiement | Ajout seul, trigger d'immuabilité |
| `billing_parties` | Personnes physiques ou morales pouvant être liées à une obligation, avec ou sans compte | Ajout et correction tracée |
| `obligation_parties` | Personnes liées à une obligation et leur rôle | Ajout et révocation, jamais d'effacement |

**Montant dû** = somme des écritures de l'obligation. Une remise totale donne un dû nul et le statut `WAIVED`, **jamais** `SETTLED`. Le statut `SETTLED` n'est atteignable qu'avec des écritures `SETTLEMENT`, qu'aucun code de la Mission 003 ne peut créer.

Une écriture porte une clé d'idempotence unique. Le verrou de ligne porte sur l'obligation.

### Empêcher la double facturation

La garantie est une **contrainte PostgreSQL**, pas une vérification applicative : `UNIQUE (establishment_id, student_record_id, fee_definition_id)` sur `obligation_lines`, où `fee_definition_id` est l'[identité stable de la charge](0020-catalogue-de-frais-et-periodes.md#lidentité-dune-charge).

| Situation | Effet |
| --- | --- |
| Rejeu d'événement | La ligne existe déjà : rien n'est créé, le traitement réussit |
| Émission en lot rejouée ou concurrente | Idem, élève par élève |
| Nouvelle version du catalogue | La charge déjà facturée ne l'est pas une seconde fois, même si son code ou son tarif a changé. Un écart de tarif passe par un ajustement. Seule une charge créée explicitement crée une nouvelle ligne |
| Retrait puis réinscription la même année | Le frais déjà facturé ne l'est pas une seconde fois |

La clé ne contient volontairement ni la version du catalogue, ni l'inscription, ni le code d'affichage. L'année et la période de facturation n'y figurent pas non plus, car l'identité de la charge les porte.

### Remises et ajustements

| Opération | Qui | Exigences |
| --- | --- | --- |
| Remise ou ajustement courant | Comptable ou administrateur (`BILLING_ADJUST`) | Motif obligatoire, effet immédiat, audit immuable |
| Remise totale, correction exceptionnelle | Permission distincte `BILLING_ADJUST_EXCEPTIONAL` | Motif obligatoire, **confirmation par une seconde personne** avant toute écriture au journal |

La seconde personne protège uniquement ces opérations : les petites opérations courantes restent directes. Une demande en attente ne change pas le dû, et son auteur ne peut pas la confirmer lui-même.

Est exceptionnelle toute opération qui annule le dû restant, ou qui corrige une écriture d'une année académique clôturée. Tout seuil chiffré éventuel reste à fixer par le propriétaire.

### Personnes liées à une obligation

Trois rôles distincts, portés par `billing`. Ils ne se déduisent pas d'un lien familial.

| Rôle | Signification | Compte requis ? |
| --- | --- | --- |
| `CONTRACTUAL_DEBTOR` | Personne redevable selon l'engagement effectivement établi | Non |
| `FINANCIAL_RESPONSIBLE` | Responsable financier désigné, interlocuteur de l'établissement | Non |
| `VOLUNTARY_CONTRIBUTOR` | Peut régler, sans aucune obligation | Oui |

**Le redevable n'est jamais déduit.** Ni l'âge de l'étudiant ni un lien de parenté ne désignent un redevable : il est identifié selon l'engagement effectivement établi.

- Une personne redevable (`billing_parties`) est **physique ou morale**, avec ou sans compte.
- Le redevable d'une obligation porte un état : `UNCONFIRMED` ou `CONFIRMED`, ce dernier avec la référence de l'engagement. Une obligation peut être publiée avec un redevable non confirmé, et cet état reste visible.
- Les règles juridiques qui diront ce qu'est un engagement valable seront validées avant toute exploitation réelle. Le modèle ne les préjuge pas.

### Ce que chacun voit

Calculé côté serveur par une seule règle, comme [`AcademicAccess`](0013-majorite-et-autorisations-familiales.md).

| Information | Redevable | Responsable | Contributeur |
| --- | --- | --- | --- |
| Établissement, référence, devise | Oui | Oui | Oui |
| Montant restant dû et prochaine échéance | Oui | Oui | **Oui, strictement nécessaire au règlement** |
| Nom de l'élève | Oui | Oui | Oui, pour identifier ce qu'il règle |
| Détail des lignes de frais | Oui | Oui | Non |
| Montants de remises | Oui | Oui, dans le total | Non |
| Motifs de bourses et de remises | Oui | **Non** | Non |
| Échéancier complet, historique | Oui | Oui | Non |
| Autres personnes liées et leurs versements | Non par défaut | Non par défaut | Non |

**Un rôle financier n'ouvre jamais l'accès académique** ([0013](0013-majorite-et-autorisations-familiales.md)). Un intitulé comme « Frais de Terminale D » révèle pourtant une information académique : pour un étudiant majeur sans consentement, les lignes montrées à un tiers utilisent un libellé neutre.

### Relation avec `financial_contributor`

Ce booléen de `record_access_grants` devient insuffisant. Il sera remplacé par une migration additive : un lien dit « ce compte est lié à ce dossier », `obligation_parties` dit quel rôle il tient sur quelle obligation. `StudentDirectory.financialContributorAccounts` sera retiré à ce moment.

### Événements

`StudentEnrolled` déclenche l'émission, via l'[outbox durable](0016-outbox-durable.md). **`EnrollmentEnded` ne supprime ni n'annule aucune obligation** : elle passe en réexamen, et toute modification financière passe par un ajustement autorisé, justifié et tracé.

### Exclusions

Les pénalités de retard sont exclues de cette mission.

## Conséquences

- Le module de paiement à venir n'aura qu'un seul point d'entrée vers `billing` : enregistrer un `SETTLEMENT` adossé à une preuve d'encaissement.
- Les rapports financiers se construiront sur le journal, sans colonne de solde à maintenir.

## Questions ouvertes

Remontées au propriétaire, sans décision par supposition :

- Ce qui constitue un engagement valable, selon les règles juridiques à valider.
- Le traitement fiscal et la numérotation : voir [0019](0019-nature-des-documents-et-numerotation.md).
