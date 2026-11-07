# 0022 : remises, corrections, annulations et réexamen

- **Statut** : Proposée. Les exigences viennent du propriétaire. La conception, les résultats et les décisions ouvertes sont en revue dans la PR du lot 5 de la Mission 003
- **Date** : 2026-11-07
- **Implémentation** : en place dans `core/`. **Aucune de ces opérations n'est un encaissement, et aucune obligation ne peut être réglée.**
- **Précise** : [0015](0015-modele-de-facturation.md), [0021](0021-obligations-et-journal.md)

## Exigences

- Une obligation émise conserve son montant initial et son historique. Un nouveau tarif, un changement de niveau, un retrait ou une réinscription ne la modifient jamais automatiquement.
- Les situations à réexaminer sont identifiables, et l'établissement décide explicitement, sans qu'un supplément ou un remboursement soit présumé dû.
- Les opérations sont justifiées, autorisées et tracées dans le journal immuable. Une correction est une nouvelle écriture.
- Une protection renforcée vise les remises totales, les annulations et les corrections sensibles, sans double approbation pour chaque petite remise.
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
| `WAIVED` | Zéro | Au moins une remise, sans annulation |
| `CORRECTED` | Zéro | Ni l'une ni l'autre |

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

**Le seuil porte sur le cumul**, sans quoi il suffirait de découper une grosse remise. Il vaut 50 % du montant émis (`scolapay.billing.sensitive-adjustment-percent`) : **cette valeur est à confirmer par le propriétaire**.

Une opération sensible devient une **demande** (`adjustment_requests`). Tant qu'elle attend, elle n'existe pas dans le journal et ne change pas le dû. Une seule demande attend par obligation. Son auteur ne peut pas la confirmer, ce que la base impose aussi. Une demande devenue impossible est refusée sans rien écrire (`REQUEST_OUTDATED`). Une annulation porte sur ce qui reste dû au moment de la confirmation.

Chaque opération porte une clé d'idempotence : la rejouer renvoie le résultat existant. Le verrou sur l'obligation rend le cumul juste en concurrence.

| Permission | Rôles |
| --- | --- |
| `BILLING_ADJUST` : opérer et trancher un réexamen | Administrateur, comptable |
| `BILLING_APPROVE` : confirmer ou refuser une demande | Administrateur, comptable |
| `BILLING_READ` : lire | Administrateur, comptable, caissier, auditeur |

## Réexamen

`GET .../billing/reviews` liste ce qui demande une décision. **La liste se déduit de la réalité à chaque lecture**, et non d'événements : aucun événement perdu ne peut en faire disparaître un cas. Elle n'écrit rien. Seules les obligations dont il reste un dû y figurent.

| Signal | Situation | Détail |
| --- | --- | --- |
| `TARIFF_DIFFERENCE`, `LEVEL_CHANGED` | Le tarif du nouveau niveau diffère du montant émis | Montant applicable et différence signée |
| `TARIFF_DIFFERENCE`, `CATALOG_CHANGED` | Un nouveau tarif diffère, sur le même niveau | Idem |
| `TARIFF_DIFFERENCE`, sans montant | La charge ne s'applique plus | Aucun montant |
| `WITHDRAWAL` | L'élève s'est retiré | Une scolarité terminée normalement n'est pas signalée |

La différence est un fait signé. **Elle n'est présentée ni comme un supplément ni comme un remboursement.**

`POST .../billing/reviews/decisions` enregistre une décision motivée et immuable : `NO_CHANGE`, ou `ADJUSTED` avec l'écriture déjà passée. La décision porte sur une situation précise : si les faits changent, par exemple avec un autre montant, le signal revient. Une décision sur une situation périmée est refusée (`REVIEW_OUTDATED`).

## Preuves

`AdjustmentTest` et `ObligationReviewTest` passent par la vraie API et écrivent directement en base pour les garanties de PostgreSQL. Sur le commit `94dec6e` : 294 tests, **97 défauts injectés sur 97 détectés**, dont 41 repris à l'identique du lot 4, 3 exécutions de contrôle vertes sur 3. Détails dans le [journal de validation](../mission-003-validation.md#lot-5-en-détail).

## Décisions ouvertes pour le propriétaire

1. **Le seuil de 50 %.** Valeur unique pour tous les établissements aujourd'hui. Faut-il une autre valeur, un montant, ou un réglage par établissement ?
2. **Qui confirme.** Le comptable et l'administrateur se confirment mutuellement. **Un établissement avec une seule personne habilitée ne peut donc ni annuler ni amener un dû à zéro.** Faut-il réserver la confirmation à l'administrateur, ou prévoir un cas pour les très petites structures ?
3. **Les motifs.** Tout le personnel qui lit les obligations, caissier compris, voit les motifs de bourses et de remises. Faut-il les restreindre ?
4. **Les demandes sans échéance.** Une demande attend indéfiniment et bloque toute autre demande sur cette obligation. Faut-il une expiration ?
5. **Les catégories de remise.** Le motif est un texte libre. Une catégorie (fratrie, bourse, personnel) sera utile aux rapports : à décider avec eux.

## Limites

- Le réexamen se calcule sur toute l'année à chaque lecture et à chaque décision, avec un plafond de 50 000 lignes pour les obligations et pour les inscriptions. Les décisions déjà prises sont lues sans plafond. **Le volume n'a pas été mesuré.**
- Aucun signal actif ne prévient qu'un réexamen attend : les interfaces web devront l'afficher.
- Le remboursement d'un trop-perçu n'est pas traité : il suppose un encaissement, donc le futur module de paiement.
- Le dû ne devient jamais négatif : une correction ne peut pas créer un avoir.
- Les statuts se fondent sur la présence d'écritures actives, sans tenir compte de leur ordre. Si une remise et une correction ont toutes deux contribué à un dû nul, le statut est `WAIVED`.
