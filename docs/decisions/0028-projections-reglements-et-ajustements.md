# 0028 : projections des règlements et préservation des ajustements

- **Statut** : Proposée pour revue du lot 1. Les invariants hérités de la Mission 003 restent acquis. Les noms de statuts, les projections et les restrictions V1 ci-dessous restent à valider en A02, A07 et A08.
- **Date** : 2026-11-11
- **Décideur** : propriétaire du projet pour les invariants existants, arbitrages de Mission 004 encore attendus.
- **Implémentation** : aucune modification de `billing`.
- **Complète** : [0012](0012-convention-monetaire.md), [0015](0015-modele-de-facturation.md), [0021](0021-obligations-et-journal.md), [0022](0022-ajustements-et-reexamen.md), [0023](0023-personnes-liees-et-vues.md), [0026](0026-contrats-encaissements-especes.md) et [0027](0027-restitution-excedents-jamais-affectes.md).

## Contexte vérifié

Le journal actuel ne permet aucun règlement. V6 interdit les types autres que `ISSUED`, `DISCOUNT`, `CORRECTION`, `CANCELLATION` et `REVERSAL`. Son trigger interdit un dû négatif et impose une seconde personne pour une écriture non initiale qui éteint le dû.

`AdjustmentService.activeAdjustmentsMinor` prend les écritures autres que `ISSUED` et `REVERSAL`. Une insertion naïve de `SETTLEMENT` le ferait entrer dans le cumul sensible. La contrepassation générique refuse aujourd'hui `ISSUED` et `REVERSAL`, mais ne connaît pas encore le futur règlement.

`FamilyBillingService` calcule le total des ajustements du responsable par différence entre dû et montant émis et classe les écritures non initiales dans son historique d'ajustements. Ces calculs devront être spécialisés dès l'introduction des paiements.

Ce sont des points d'intégration du nouveau domaine. Les comportements existants du périmètre sans encaissement restent validés.

## Projections proposées par obligation

| Symbole | Projection |
| --- | --- |
| I | Montant initial immuable |
| A | Somme signée des ajustements de facturation, avec leurs contrepassations |
| B | Montant net facturé, I + A |
| P | Règlements nets affectés, après compensations autorisées |
| R | Reste à payer, B − P |

Les compensations sont classées par leur source. Une compensation de règlement change P. Une contrepassation de remise ou correction change A. Les deux catégories ne se mélangent pas dans les agrégats.

Le journal de `billing` contient les règlements avec leur signe et leurs compensations. R reste la somme de ce journal. Le registre `payments` ne doit pas être soustrait une seconde fois. Il apporte la preuve et la ventilation des fonds.

Dans la proposition V1, B, P et R sont positifs ou nuls. P ne dépasse pas B. Un montant en attente de restitution ne réduit pas R et ne constitue pas un règlement.

Les montants reçus, monnaie rendue et restitutions sont des projections de réception distinctes selon 0027. La somme P de cette obligation n'est pas le montant total reçu d'une réception multi-obligations.

## Statuts proposés

| Condition | État interne proposé | Sens |
| --- | --- | --- |
| R positif et P nul | `OPEN` | Aucun règlement actif, reste à payer |
| R positif et P positif | `PARTIALLY_SETTLED` | Une partie du net facturé est réglée |
| R nul, P positif et aucun ajustement actif | `SETTLED` | Extinction du solde par les règlements confirmés |
| R nul, P positif et ajustement actif | `SETTLED_WITH_ADJUSTMENTS` | Solde éteint par règlements et ajustements, montants distincts |
| R nul et P nul | États existants `WAIVED`, `CORRECTED`, `ADJUSTED_TO_ZERO` ou `CANCELLED` | Classification de la Mission 003 conservée |

Un ajustement actif se détermine après prise en compte des contrepassations. Le seul signe du total A ne suffit pas. Deux ajustements de sens opposés peuvent se compenser en montant tout en restant actifs.

Exemple de référence, I de 100 000 XAF, A de −20 000 XAF, P de 80 000 XAF, R nul. Le statut mixte expose 80 000 XAF réglés et 20 000 XAF de remise. Il ne présente pas 100 000 XAF comme encaissés.

Une réception peut conserver un excédent à régulariser alors que l'obligation ciblée est réglée. Les statuts de réception ou de restitution ne deviennent pas des statuts d'obligation.

## Refus et suivi proposés en V1

Si I vaut 100 000 XAF et P vaut 80 000 XAF, une remise de 30 000 XAF donnerait B de 70 000 et R négatif. L'exécution est refusée avant toute écriture ou demande exécutable.

La réponse décrit le conflit et l'excédent hypothétique. Le journal, P, les espèces et le budget jamais affecté restent inchangés. Un suivi peut conserver l'intention commerciale, son auteur et son motif, sans effet monétaire. Le statut de suivi ne vaut ni remise appliquée ni approbation différée.

La proposition de régularisation est une nouvelle décision explicite :

- reformuler une remise compatible avec le reste si elle correspond à la décision du propriétaire ;
- conserver le dossier à traiter jusqu'à un futur parcours de crédit ou remboursement.

La demande n'est jamais tronquée automatiquement au reste disponible. Une seconde personne ne peut pas approuver un montant interdit. Le chemin de restitution de 0027 ne peut pas transformer cet excédent hypothétique en argent disponible.

Une demande d'ajustement faite avant un nouveau règlement devient périmée si le journal change, même si son solde revient ensuite à la même valeur. La règle `REQUEST_OUTDATED` et sa garantie SQL sont conservées.

L'annulation administrative d'une obligation ayant un règlement actif serait refusée en V1. La commande actuelle porte sur tout le reste dû. Son interprétation après paiement nécessite un arbitrage A07 pour éviter de déclarer toute la charge annulée sans traiter les sommes déjà affectées.

## Garanties requises dès le premier SETTLEMENT

La première livraison qui autorise un règlement doit intégrer simultanément :

1. **Preuve obligatoire.** Chaque règlement possède une source d'affectation et de réception confirmée, de même établissement, devise et montant.
2. **Cohérence bilatérale.** Aucune affectation confirmée sans écriture correspondante et aucun règlement orphelin.
3. **Non-négativité.** Recalcul sous verrou et garde PostgreSQL conservés. Les sommes sont bornées et calculées sans débordement.
4. **Validation sensible.** Les ajustements seuls alimentent le cumul brut de 0022. Un paiement ne consomme ni ne réinitialise ce cumul. Seuils de 25 % et 100 000 XAF et règles d'année clôturée conservés.
5. **Extinction du dû.** Un règlement confirmé peut suivre sa permission propre, sous réserve d'A01. L'exception à la seconde validation est limitée à cette source prouvée. Les remises, corrections et annulations sensibles gardent leurs contrôles existants.
6. **Compensations dédiées.** Les routes génériques d'ajustement refusent les règlements et leurs compensations. La règle d'inverse exact et unique des contrepassations de facturation reste applicable à son périmètre.
7. **Demandes périmées.** Toute nouvelle écriture invalide la présentation antérieure. Une confirmation ne change pas silencieusement le montant présenté.
8. **Projections et états.** Les agrégats A, P et R, les cas mixtes et le traitement des échéances suivent les contrats validés. Un paiement n'est jamais présenté comme une remise.
9. **Autorisation et confidentialité.** Permissions serveur, isolation des établissements et projections typées conservées. Les acteurs et références ne sont pas tirés d'assertions du client.
10. **Atomicité et reprise.** Mouvement, affectations, preuves et audit dans la même transaction, avec idempotence par contraintes de base.

Les garanties déjà portées par PostgreSQL le restent. Les seuils configurables de sensibilité sont aujourd'hui contrôlés par le service sous verrou, conformément à 0022. Les tests doivent distinguer ces deux niveaux, sans présenter les seuils configurables comme des contraintes SQL existantes.

Le lot 2 doit posséder ses tests PostgreSQL directs, de concurrence, d'ajustements sensibles, de régression et de reprise. Un lot ultérieur peut élargir les paniers ou consultations, sans réparer une phase d'encaissement insuffisamment protégée.

## Échéances et confidentialité

L'échéancier d'origine reste immuable. A02 doit définir la priorité d'affectation et l'impact des ajustements sur la couverture des échéances. Le contrat doit garantir que la somme des détails d'affectation correspond à l'affectation d'obligation. La prochaine échéance ne peut pas ignorer les règlements déjà affectés.

Les projections ci-dessus sont les projections internes pour personnel autorisé. Les surfaces familiales gardent leur politique typée de 0023. Un contributeur conserve sa vue minimale. Les motifs restent masqués au caissier selon les droits existants.

Les nouvelles écritures de paiement ne sont pas copiées brutes dans les vues des familles. Les noms des autres payeurs, références de leurs reçus et versements individuels restent protégés. Les nouvelles informations agrégées et classifications d'historique doivent être validées en A08, sans étiqueter des règlements comme ajustements. Families reste fermé.

## Critères et arbitrages

S01 à S04, S11 à S19 et S21 à S22 doivent être couverts dès le premier lot activant les paiements, selon A01, A02, A07 et A08 approuvés. Les validations sont exécutées dans GitHub Actions sur PostgreSQL jetable, en conservant les preuves de la Mission 003.

Le lot documentaire n'étend aucun enum, aucune route ou contrainte. Les propositions de statuts et restrictions restent soumises au propriétaire.
