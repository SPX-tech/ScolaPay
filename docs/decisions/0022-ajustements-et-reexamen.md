# 0022 : remises, corrections, annulations et réexamen

- **Statut** : Acceptée par le propriétaire le 2026-11-07, après revue de la PR du lot 5 de la Mission 003. Le seuil est une politique initiale, à revoir après les retours terrain.
- **Date** : 2026-11-07
- **Implémentation** : en place dans `core/`. **Aucune de ces opérations n'est un encaissement, et aucune obligation ne peut être réglée.**
- **Précise** : [0015](0015-modele-de-facturation.md), [0021](0021-obligations-et-journal.md)

## Exigences

- Une obligation émise conserve son montant initial et son historique. Un nouveau tarif, un changement de niveau, un retrait ou une réinscription ne la modifient jamais automatiquement.
- Les situations à réexaminer sont identifiables, et l'établissement décide explicitement, sans qu'un supplément ou un remboursement soit présumé dû.
- Les opérations sont justifiées, autorisées et tracées dans le journal immuable. Une correction est une nouvelle écriture.
- Une protection renforcée vise les remises totales, les annulations et les corrections sensibles, sans double approbation pour chaque petite remise. Une opération sensible exige toujours deux personnes distinctes.
- Les caissiers n'accèdent pas aux motifs. La restriction vit dans l'API, et non dans une interface.
- Une demande en attente peut être refusée ou retirée, et ne se confirme jamais sur autre chose que ce qui a été présenté.
- La remise commerciale, la correction financière et l'annulation administrative restent distinctes, même si elles aboutissent au même solde.

## Des écritures qui gardent leur sens

| Écriture | Sens | Montant | Route |
| --- | --- | --- | --- |
| `DISCOUNT` | **Remise commerciale** : un avantage accordé | Négatif | `POST .../obligations/{id}/discounts` |
| `CORRECTION` | **Correction financière** : le montant était faux | Positif ou négatif | `POST .../obligations/{id}/corrections` |
| `CANCELLATION` | **Annulation administrative** : l'obligation n'aurait pas dû exister | Tout le reste dû | `POST .../obligations/{id}/cancellation` |
| `REVERSAL` | **Contrepassée** : défait une écriture par son exact opposé | Opposé de l'original | `POST .../obligations/{id}/entries/{entryId}/reversal` |

Le statut, toujours déduit du journal, garde cette distinction quand le dû tombe à zéro :

| Statut | Dû | Écritures actives |
| --- | --- | --- |
| `OPEN` | Positif | |
| `CANCELLED` | Zéro | Une annulation |
| `WAIVED` | Zéro | Des remises, sans correction ni annulation |
| `CORRECTED` | Zéro | Des corrections, sans remise ni annulation |
| `ADJUSTED_TO_ZERO` | Zéro | **Une combinaison de remises et de corrections** |

Une combinaison ne se présente pas comme une remise totale : `ADJUSTED_TO_ZERO` constate un dû nul sans prétendre à une cause unique. Les écritures gardent leur nature dans le journal, qui reste la source du détail.

Ce statut prépare l'arrivée des paiements. Il dit qu'aucun règlement n'a eu lieu, et le futur `SETTLED` s'appliquera dès qu'un règlement confirmé aura contribué à solder l'obligation, y compris avec une remise. Les statuts actuels ne deviennent donc jamais synonymes de règlement.

Une écriture contrepassée ne compte plus pour le statut. **`SETTLED` et `SETTLEMENT` n'existent toujours pas** : c'est prouvé en base et dans le code.

Une charge annulée reste émise : ni une réinscription ni un lot ne la refacturent. Seule une contrepassée, elle-même confirmée, la rétablit.

## Un contrôle proportionné

| Opération | Traitement |
| --- | --- |
| Remise ou correction dont le **cumul** sur l'obligation reste dans le seuil | **Directe**, avec motif |
| Cumul au-delà du seuil | Seconde personne |
| Dû amené à zéro | Seconde personne, **imposée par la base** |
| Annulation | Toujours une seconde personne, **imposée par la base** |
| Année clôturée | Seconde personne, même pour une petite opération |
| Contrepassée d'une écriture déjà confirmée | Seconde personne |

### Le seuil

**Politique initiale, décidée par le propriétaire : le cumul déclenche une seconde personne au-delà de 25 % du montant émis ou de 100 000 XAF, selon le premier atteint.** Exactement 25 % ou 100 000 XAF restent directs. Ces valeurs vivent dans la configuration (`scolapay.billing.sensitive-adjustment-percent` et `sensitive-adjustment-xaf`) : **aucun paramétrage par établissement n'existe**, volontairement à ce stade.

**Le cumul est brut** : la somme des valeurs absolues des écritures actives, plus l'opération demandée. La première version de cette décision utilisait un cumul net. Une hausse et une baisse s'y compensaient : une correction de +40 % suivie d'une remise de -40 % ne déclenchait aucun contrôle. La revue du propriétaire a demandé de le vérifier, et ce contournement est fermé.

| Tentative | Résultat |
| --- | --- |
| Découper une remise en petites opérations | Chacune s'ajoute au cumul |
| Alterner hausses et baisses pour garder un solde stable | Chacune s'ajoute au cumul |
| Passer une hausse pour faire de la place à une baisse | Les deux s'ajoutent au cumul |
| Contrepasser puis recommencer | L'écriture et sa contrepassée sortent du cumul, et le dû n'a pas bougé |
| Lancer les opérations en parallèle | Le verrou sur l'obligation les sérialise |
| Contrepasser une opération confirmée | Exige elle-même une seconde personne |

**Sans seconde personne, le cumul actif ne peut donc jamais dépasser le seuil.** Une opération déjà confirmée compte aussi : après une remise confirmée de 30 %, les suivantes restent sensibles.

Le seuil se contrôle dans le service, sous verrou, puisqu'il dépend de la configuration. Une écriture SQL directe n'y est pas soumise, contrairement à la règle de la seconde personne pour un dû amené à zéro.

### Les demandes

Une opération sensible devient une **demande** (`adjustment_requests`). Tant qu'elle attend, elle n'existe pas dans le journal et ne change pas le dû. Une seule demande attend par obligation.

Une demande **fige ce qu'elle présente** : son montant, le dû à ce moment et la taille du journal. Une annulation porte donc sur un montant précis, et non sur ce qui resterait dû plus tard. L'API expose `amountMinor`, `dueAtRequestMinor`, `resultingDueMinor` et `outdated`.

**Toute nouvelle écriture rend la demande périmée, même si le solde redevient identique.** La confirmation est alors refusée (`REQUEST_OUTDATED`) sans rien écrire. La demande doit être refusée ou retirée, puis formulée de nouveau, ce qui exige une nouvelle confirmation. La base le vérifie aussi : à la confirmation, l'écriture doit correspondre à la demande et le journal ne doit pas avoir bougé.

Il n'y a pas d'expiration automatique. Rien ne bloque indéfiniment :

| Issue | Par qui | Motif | Effet |
| --- | --- | --- | --- |
| `APPROVED` | Une autre personne, `BILLING_APPROVE` | Celui de la demande | L'écriture entre au journal |
| `REJECTED` | Une autre personne, `BILLING_APPROVE` | Obligatoire | Rien au journal |
| `WITHDRAWN` | **Son auteur seulement** | Obligatoire | Rien au journal |

Chaque issue est définitive, auditée et conservée. Une demande en attente n'empêche pas une petite opération directe, qui la rendra simplement périmée.

Chaque opération porte une clé d'idempotence : la rejouer renvoie le résultat existant et son état réel.

### Qui fait quoi

| Permission | Rôles |
| --- | --- |
| `BILLING_ADJUST` : opérer et trancher un réexamen | Administrateur, comptable |
| `BILLING_APPROVE` : confirmer ou refuser une demande | Administrateur, comptable, **directeur** |
| `BILLING_REASONS_READ` : lire les motifs | Administrateur, comptable, directeur, auditeur |
| `BILLING_READ` : lire les montants et le journal | Les mêmes, plus le caissier |

**Le rôle `DIRECTOR`** confirme ce qu'un autre a préparé, sans pouvoir opérer : ni remise, ni correction, ni émission, ni catalogue, ni journal d'audit, ni gestion du personnel. Il lit les motifs, car confirmer sans les lire reviendrait à décider à l'aveugle. Dans une petite école, un administrateur et un directeur suffisent donc.

**Une opération sensible exige toujours deux personnes distinctes.** Son auteur ne peut pas la confirmer, ce que la base impose aussi.

**Il n'existe aucune auto-approbation.** Une école avec un seul utilisateur ne peut ni annuler, ni amener un dû à zéro, ni dépasser le seuil : il lui faut une seconde personne habilitée, et elle peut retirer sa demande en attendant. Ce sujet reste ouvert pour les pilotes.

### Les motifs

Les motifs de remises, de bourses et de corrections sont confidentiels. Le service les retire des réponses à qui n'a pas `BILLING_REASONS_READ`, sans dépendre d'aucune interface.

| Réponse | Caissier | Comptable, administrateur, directeur, auditeur |
| --- | --- | --- |
| Détail d'une obligation : nature et montant des écritures, dû | Oui | Oui |
| Détail d'une obligation : `reason` des écritures | **`null`** | Oui |
| Détail d'une obligation : `reason` des décisions de réexamen | **`null`** | Oui |
| Demandes : montant, état | Oui | Oui |
| Demandes : `reason` et `decisionNote` | **`null`** | Oui |

`reasonsVisible` distingue un motif masqué d'une écriture sans motif. Le journal d'audit ne recopie aucun motif. Un test parcourt toutes les routes de lecture réelles de la facturation avec un compte caissier.

Le motif reste obligatoire et libre : **les catégories structurées de remises sont reportées**.

## Réexamen

`GET .../billing/reviews` liste ce qui demande une décision. **La liste se déduit de la réalité à chaque lecture**, et non d'événements : aucun événement perdu ne peut en faire disparaître un cas. Elle n'écrit rien. Seules les obligations dont il reste un dû y figurent.

| Signal | Situation | Détail |
| --- | --- | --- |
| `TARIFF_DIFFERENCE`, `LEVEL_CHANGED` | Le tarif du nouveau niveau diffère du montant émis | Montant applicable et différence signée |
| `TARIFF_DIFFERENCE`, `CATALOG_CHANGED` | Un nouveau tarif diffère, sur le même niveau | Idem |
| `TARIFF_DIFFERENCE`, sans montant | La charge ne s'applique plus | Aucun montant |
| `WITHDRAWAL` | L'élève s'est retiré | Une scolarité terminée normalement n'est pas signalée |

La différence est un fait signé. **Elle n'est présentée ni comme un supplément ni comme un remboursement.**

`POST .../billing/reviews/decisions` enregistre une décision motivée et immuable : `NO_CHANGE`, ou `ADJUSTED` avec l'écriture déjà passée. La décision porte sur une situation précise : si les faits changent, par exemple avec un autre montant, le signal revient. Une décision sur une situation périmée est refusée (`REVIEW_OUTDATED`). Les décisions se relisent dans le détail de l'obligation (`reviewDecisions`).

## Preuves

`AdjustmentTest`, `AdjustmentPolicyTest`, `ReasonConfidentialityTest` et `ObligationReviewTest` passent par la vraie API et écrivent directement en base pour les garanties de PostgreSQL. Sur le commit `542efe7` : 330 tests, **124 défauts injectés sur 124 détectés**, dont 41 repris à l'identique du lot 4, 3 exécutions de contrôle vertes sur 3. Détails dans le [journal de validation](../mission-003-validation.md#lot-5-en-détail).

## Décisions du propriétaire

Prises le 2026-11-07, après revue de la PR :

1. **Seuil** : 25 % ou 100 000 XAF, le premier atteint, comme politique initiale. Pas d'interface de paramétrage par établissement.
2. **Deux personnes distinctes**, toujours. Une autorisation de confirmation adaptée au directeur. Aucune auto-approbation pour une école à un seul utilisateur.
3. **Motifs** : interdits aux caissiers, ouverts aux administrateurs, comptables et auditeurs. Le directeur les lit aussi, puisqu'il confirme.
4. **Demandes** : pas d'expiration automatique, refus ou retrait explicites et tracés, nouvelle confirmation si le montant ou la portée change.
5. **Statuts** : distinction maintenue, représentation neutre pour une combinaison de remises et de corrections.
6. **Catégories de remises** : reportées, le motif reste obligatoire.
7. **Performance du réexamen** : ne bloque pas ce lot, à mesurer avant tout lancement auprès d'établissements importants.

## Questions ouvertes

- **Les écoles à un seul utilisateur**, à observer pendant les pilotes.
- **Les valeurs du seuil**, à revoir avec les retours terrain.

## Limites

- **Mesure obligatoire avant les grands établissements.** Le réexamen se calcule sur toute l'année à chaque lecture et à chaque décision, avec un plafond de 50 000 lignes pour les obligations et pour les inscriptions. Les décisions déjà prises sont lues sans plafond. **Le volume n'a pas été mesuré.**
- **Le seuil porte sur une obligation**, et non sur un élève ou une famille : des remises réparties sur plusieurs charges ne s'additionnent pas.
- **Le montant absolu ne vaut que pour le XAF**, seule devise de la première offre. Une autre devise n'aurait que le pourcentage.
- **Deux comptes détenus par une même personne** ne se détectent pas : la séparation repose sur des comptes distincts.
- Aucun signal actif ne prévient qu'un réexamen ou une demande attend : les interfaces web devront l'afficher.
- Le remboursement d'un trop-perçu n'est pas traité : il suppose un encaissement, donc le futur module de paiement.
- Le dû ne devient jamais négatif : une correction ne peut pas créer un avoir.
