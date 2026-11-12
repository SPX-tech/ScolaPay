# 0028 : projections des règlements et préservation des ajustements

- **Statut** : Invariants M003 acquis et orientations A01 à A05 arbitrées suivant leur périmètre explicite. Tranche de règlement intégrée par #32 ; A06 à A08 restent ouverts pour leurs autres modalités, voir [0031](0031-identification-et-autorisation-de-restitution.md).
- **Date** : 2026-11-11
- **Décideur** : propriétaire du projet pour les invariants existants, arbitrages de Mission 004 encore attendus.
- **Implémentation** : première chaîne intégrée, projections et couverture A02 pour les cas pris en charge. Voir [0029](0029-premier-reglement-especes.md). Aucune activation réelle.
- **Complète** : [0012](0012-convention-monetaire.md), [0015](0015-modele-de-facturation.md), [0021](0021-obligations-et-journal.md), [0022](0022-ajustements-et-reexamen.md), [0023](0023-personnes-liees-et-vues.md), [0026](0026-contrats-encaissements-especes.md) et [0027](0027-restitution-excedents-jamais-affectes.md).

## Contexte vérifié

Au terme de M003, V6 refusait les règlements et imposait une seconde personne pour un ajustement éteignant le dû. V9 étend les gardes aux règlements prouvés sans supprimer les protections d'ajustement. Le contexte initial ci-dessous explique les adaptations réalisées ; les détails actuels sont dans 0029.

Le cumul sensible de `AdjustmentService.activeAdjustmentsMinor` ne distinguait initialement que `ISSUED` et `REVERSAL`. Il exclut maintenant aussi `SETTLEMENT`. La contrepassation générique interdit les règlements ; après paiement, les chemins non définis de correction, annulation et contrepassation restent fermés.

`FamilyBillingService` calculait les ajustements par différence entre dû et montant émis. Il utilise désormais le total des seuls ajustements et filtre les règlements individuels de l'historique familial. Les champs autorisés et les barrières d'accès restent préservés.

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

## Statuts techniques de l'implémentation, proposés pour revue

Ces codes sont implémentés et testés pour distinguer les effets financiers. Ils ne constituent pas un arbitrage métier A08 ; leur usage opérationnel reste à décider.

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

L'échéancier d'origine reste immuable. A02 couvre les échéances impayées les plus anciennes et réduit par remise les échéances non couvertes les plus éloignées. La couverture dérivée conserve les totaux. Les corrections actives n'ont pas de ventilation inventée et bloquent le règlement. La prochaine échéance ne présente pas comme impayé un montant déjà couvert ou totalement remis.

Les projections ci-dessus sont les projections internes pour personnel autorisé. Les surfaces familiales gardent leur politique typée de 0023. Un contributeur conserve sa vue minimale. Les motifs restent masqués au caissier selon les droits existants.

Les nouvelles écritures de paiement ne sont pas copiées brutes dans les vues des familles. Les noms des autres payeurs, références de leurs reçus et versements individuels restent protégés. Les nouvelles informations agrégées et classifications d'historique doivent être validées en A08, sans étiqueter des règlements comme ajustements. Families reste fermé.

## Critères et arbitrages

Les garanties ci-dessus sont requises dès le premier lot activant les paiements. A01 à A05 sont arbitrés suivant leurs décisions explicites ; A06 à A08 restent ouverts sur leurs autres modalités. Les parcours incomplets restent fermés. La [matrice de portée par lot](../missions/mission-004-cash-collections.md#portée-des-scénarios-par-lot) distingue le socle des variantes multi-obligations, multi-élèves et des rectifications. S17 exige le refus d'une rectification non autorisée ; son parcours complet doit être prouvé lorsqu'A07 en permet l'introduction. Les validations sont exécutées dans GitHub Actions sur PostgreSQL jetable, en conservant les preuves de la Mission 003.

Le lot 1 documentaire n'étendait aucun enum, route ou contrainte. La tranche #32 utilisait des codes techniques sans collecter l'identité du remettant ; ce constat ne valait aucun arbitrage implicite. A05 est depuis explicitement validé et implémenté progressivement suivant 0031. Les codes financiers A08, les rectifications et les autres questions restent ouverts ; le code ne les décide pas.
