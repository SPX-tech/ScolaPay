# 0021 : obligations, échéanciers et journal

- **Statut** : Orientations validées par le propriétaire, résultats en revue dans la PR du lot 4 de la Mission 003
- **Date** : 2026-11-06
- **Implémentation** : en place dans `core/`. **Aucun encaissement n'existe, et aucune obligation ne peut être réglée.**
- **Précise** : [0015](0015-modele-de-facturation.md)

## Exigences

- Les élèves inscrits avant la publication d'un tarif sont facturés par une émission en lot, explicite et confirmée après un aperçu.
- Une inscription ne crée pas de dette inattendue pour le personnel qui la confirme, et une inscription sans confirmation reste clairement identifiable pour une émission ultérieure.
- L'émission est idempotente, y compris en concurrence, à la reprise d'un événement ou après une nouvelle publication.
- Aucune remise ni action administrative ne simule un encaissement. Une obligation ne devient réglée qu'à travers le futur module de paiement, sur preuve d'un règlement confirmé.

## Décisions

### Une obligation lie un dossier à une charge

**Ceci simplifie [0015](0015-modele-de-facturation.md), qui prévoyait une obligation avec des lignes.** Une obligation est une charge pour un dossier, sans table de lignes :

- la protection contre la double facturation est une contrainte directe : `UNIQUE (établissement, dossier, fee_definition_id)`. **Ni la version du catalogue ni l'inscription n'en font partie** : une réinscription après un nouveau tarif ne peut donc pas refacturer une charge déjà émise ;
- une remise, un ajustement ou un règlement vise une charge précise ;
- un semestre donne naturellement sa propre obligation ;
- un futur **appel de frais** pourra regrouper plusieurs obligations, sans changer ce modèle.

L'obligation copie le code, le libellé, le montant et l'échéancier du moment : une nouvelle version du catalogue ne la modifie pas.

### Le dû et le statut ne sont stockés nulle part

| Élément | Source |
| --- | --- |
| Montant dû | Somme de `obligation_entries` |
| Statut | Déduit du dû : `OPEN` s'il reste quelque chose, `WAIVED` sinon |

Il n'existe donc **aucune colonne de solde ni de statut à modifier**, et aucune route pour le faire. Les obligations, échéances et écritures sont immuables en base.

**`SETTLEMENT` ne figure volontairement pas dans le `CHECK` du journal, et `SETTLED` n'existe pas dans le code.** Écrire un règlement est aujourd'hui impossible, même en SQL direct. Le module de paiement l'introduira par migration, avec une preuve d'encaissement. Une remise totale donne `WAIVED`, jamais un règlement.

Le schéma accepte déjà `DISCOUNT` et `ADJUSTMENT`, avec un motif obligatoire, mais **aucune route ne les écrit** : c'est le lot 5, avec ses permissions et sa confirmation par une seconde personne. La base refuse un dû négatif, et sérialise les écritures d'une obligation.

### Référence interne

`OBL-` suivi de dix caractères aléatoires (alphabet de Crockford, sans I, L, O ni U). Unique par établissement, stable, et jamais réutilisée puisqu'une obligation ne se supprime pas.

**Aléatoire plutôt que séquentielle**, pour trois raisons : aucun verrou entre les émissions, aucun volume révélé, et **aucune confusion possible avec une numérotation fiscale continue**, conformément à [0019](0019-nature-des-documents-et-numerotation.md).

### Trois voies d'émission, toutes explicites

Toutes passent par le même code et la même contrainte.

| Voie | Déclencheur | Permission |
| --- | --- | --- |
| **Inscription** | Le personnel consulte `GET .../billing/enrollment-preview`, puis inscrit en citant `confirmedFeeCatalogVersionId` | Celle d'inscrire |
| **Lot** | `GET .../billing/issuance-preview`, puis `POST .../billing/issuance-runs` avec son jeton | `BILLING_ISSUE` |
| **Unitaire** | `POST .../billing/obligations`, pour un frais facultatif ou une charge précise | `BILLING_ISSUE` |

**Inscription.**

- Sans confirmation : l'inscription reste valable, **aucune dette n'est créée**, et l'élève apparaît nominativement dans `GET .../billing/pending-issuances` et dans l'aperçu du lot.
- Avec confirmation : la version citée doit être celle en vigueur, sinon l'inscription est refusée (`FEES_PREVIEW_OUTDATED`) plutôt que d'émettre un autre tarif que celui vu.
- L'émission passe par l'[outbox](0016-outbox-durable.md) et se fait d'après **la version confirmée**, même si une autre a été publiée entre-temps.
- Un frais `ON_REQUEST` n'est jamais émis par cette voie.

**Lot.**

- L'aperçu n'écrit rien. Il donne les inscrits concernés, les obligations à créer, ce qui est déjà émis, les totaux par charge et un jeton.
- La confirmation recalcule le plan : si le tarif ou les inscrits ont changé, le jeton ne correspond plus et **rien n'est émis** (`ISSUANCE_PREVIEW_OUTDATED`).
- Tout se fait dans une seule transaction, avec au plus 5 000 inscriptions par lot. Au-delà, on découpe par unité académique.

### Ce qui reste à facturer

`GET .../billing/pending-issuances` liste nominativement les inscriptions actives à qui il manque des frais automatiques du tarif en vigueur, avec les frais et le total. **Elle se déduit de ce qui manque réellement**, et non d'un indicateur posé à l'inscription. Y figurent donc aussi les élèves inscrits avant toute publication, ceux concernés par une nouvelle charge, et ceux dont l'émission aurait échoué. Un frais facultatif n'y figure pas, ni un élève retiré.

### Limites de l'outbox respectées

- **Double exécution** : `INSERT ... ON CONFLICT DO NOTHING` sur la contrainte ; un rejeu ne crée ni écriture ni audit.
- **Absence d'ordre** : le listener relit l'état de l'inscription. Une inscription déjà terminée n'émet rien.
- **Connexion occupée** : le listener ne fait aucun appel externe.

### Permissions

| Rôle | Aperçu d'inscription | Lire les obligations | Émettre |
| --- | --- | --- | --- |
| Administrateur, comptable | Oui | Oui | Oui |
| Caissier, auditeur | Oui | Oui | Non |
| Secrétariat | Oui | **Non** | Par la confirmation d'une inscription seulement |

La liste de ce qui reste à facturer suit la permission de lecture des obligations.

## Preuves

`ObligationTest` passe par la vraie API et écrit directement en base pour les garanties de PostgreSQL. `OutboxReliabilityTest` tue une vraie JVM avant l'émission : l'obligation confirmée est créée une seule fois à la reprise.

Sur le commit `f2b89a3` : 236 tests, **46 défauts injectés sur 46 détectés**, 3 exécutions de contrôle vertes sur 3. Le scénario critique, une réinscription après un nouveau tarif, est prouvé par quatre tests, dont la lecture de la clé dans le catalogue de PostgreSQL. Détails dans le [journal de validation](../mission-003-validation.md#lot-4-en-détail).

## Conséquences

- [0015](0015-modele-de-facturation.md) est précisée : plus de table `obligation_lines`.
- Au lot 6, les personnes liées pourront se rattacher au dossier et à l'année plutôt qu'à chaque obligation, à décider à ce moment.
- Le module de paiement ajoutera par migration `SETTLEMENT` et `SETTLED`, et la répartition d'un règlement sur les échéances.

## Limites

- Le retrait ne déclenche pas encore de réexamen : c'est le lot 5. Il ne supprime déjà rien.
- Un élève inscrit sans confirmation n'est facturé que si quelqu'un lance un lot. Il est identifiable nominativement, mais **aucun signal actif ne le rappelle** : les interfaces web devront afficher cette liste.
- **Un écart de tarif n'est pas repris automatiquement.** Un élève réinscrit sur un niveau plus cher garde le montant de son obligation. Corriger cet écart relève d'un ajustement autorisé, au lot 5.
- Le volume n'a pas été mesuré : le plafond de 5 000 inscriptions est testé, pas la durée d'une telle transaction.
