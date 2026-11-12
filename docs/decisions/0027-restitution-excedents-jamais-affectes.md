# 0027 : restitution des excédents jamais affectés

- **Statut** : Décision métier acceptée par le propriétaire lors de l'engagement du lot 1 de la Mission 004. Le modèle de provenance, les noms des états et les contrats proposés sont soumis à la revue documentaire.
- **Date** : 2026-11-11
- **Décideur** : propriétaire du projet
- **Implémentation** : réservations et décisions budgétaires intégrées par #33 ([0030](0030-reservations-excedents-especes.md)). Identification et autorisation nominative en revue suivant [0031](0031-identification-et-autorisation-de-restitution.md). Aucune remise physique ni restitution effective opérationnelle ; la cible V1 reste à compléter.
- **Complète** : [0026](0026-contrats-encaissements-especes.md), [0019](0019-nature-des-documents-et-numerotation.md) et les invariants de la Mission 003.
- **Remplace** : aucune décision de facturation.

## Décision acquise

La V1 doit permettre de restituer un excédent d'espèces qui n'a jamais été affecté. La restitution exige une justification, l'approbation d'une seconde personne habilitée, la confirmation de remise physique et une trace intégrale.

Une somme ayant été affectée ne peut jamais retrouver cette éligibilité par désaffectation, contrepassation, remise, correction, nouveau libellé ou changement de statut. Le remboursement d'un règlement affecté reste un domaine distinct.

La propriété concerne les fractions de la réception. Une réception partiellement affectée peut donc comporter un excédent restituable. Interdire toute restitution dès qu'une partie de la réception a été affectée serait trop restrictif.

## Provenance proposée

Le modèle ci-dessous est la cible complète, distincte du calcul conservateur F/H/Q de la tranche sans compensation ni sortie décrit en 0030. Les états de remise proposés ne sont pas tous implémentés. La décision métier de restitution reste acquise ; le code de réservation ne tranche pas les modalités encore ouvertes.

Les références de provenance sont immuables et suivent les montants à travers les affectations et leurs compensations. L'éligibilité ne se déduit pas du seul montant actuellement non affecté.

Pour une réception valide, les projections proposées sont :

| Symbole | Sens |
| --- | --- |
| F | Espèces conservées à la réception, après monnaie effectivement rendue et corrections valides du constat |
| P | Somme des affectations nettes actives de cette réception |
| T | Restitutions de fractions jamais affectées, effectivement remises et confirmées |
| U | Somme actuellement non affectée, déduite de F − P − T |
| N | Plafond de provenance jamais affectée établi lors du constat de réception, susceptible seulement de diminuer par une correction justifiée |
| L | Montant de cette provenance consommé historiquement par sa première affectation. Il ne diminue pas quand une affectation est annulée |
| Q | Réservations actives pour des demandes de restitution |
| E | Budget de provenance disponible pour restitution, N − L − T − Q |

Les plafonds de provenance peuvent être subdivisés pour identifier les sources consommées. Chaque consommation doit être unique. Une réaffectation des mêmes fonds déjà affectés conserve sa source et ne compte pas comme nouvelle réception ou nouvelle provenance jamais affectée.

Une opération de remplacement ou de rectification garde sa dépendance à la réception d'origine. Elle ne peut pas déclarer les mêmes fonds comme une nouvelle réception pour réamorcer N. Une réception réellement nouvelle possède sa propre preuve. Le contrôle physique des déclarations reste en A04.

Une nouvelle restitution doit respecter E et la disponibilité physique déclarée U − Q, avec source valide et autorisations. Les deux budgets sont vérifiés sous verrou. Les capacités deviennent indisponibles quand une correction invalide la réception. A07 doit préciser les rectifications possibles et les écarts incompatibles avec des mouvements déjà confirmés. Aucune correction ne peut augmenter le budget restituable ou effacer l'historique L.

Les projections sont des calculs du registre et de ses mouvements. Aucun solde de crédit général par élève ou compte n'est créé.

## Exemples normatifs

1. **120 000 reçus, 100 000 affectés immédiatement.** N vaut 20 000. Une restitution de ces 20 000 reste possible selon le circuit autorisé.
2. **100 000 reçus et entièrement affectés.** N vaut zéro. Contrepasser les règlements ne rend aucun montant restituable par ce circuit.
3. **120 000 reçus, 100 000 affectés, puis 10 000 du solde affectés.** N vaut 20 000 et L vaut 10 000. Le plafond restant est 10 000. Une contrepassation des 10 000 ultérieurement affectés ne l'augmente pas.
4. **20 000 éligibles, 15 000 déjà réservés.** Au plus 5 000 peuvent être réservés simultanément. Une requête concurrente ne consomme pas deux fois la même capacité.
5. **20 000 autorisés mais encore dans la caisse.** Aucune restitution confirmée ne figure dans T. La réservation reste suivie et ne prouve aucune remise.

Dans le troisième cas, une contrepassation libre laissant des fonds anciennement affectés disponibles est refusée dans la proposition V1. Une correction peut réaffecter ces fonds atomiquement selon A07. Les essais doivent vérifier séparément que, même si une somme anciennement affectée apparaît en attente dans une évolution future, elle demeure exclue de E.

## Circuit proposé et points d'autorisation

Les états ci-dessous sont des noms de contrat proposés. A05 valide désormais les principes d'identité, le caissier habilité après seconde approbation sans troisième personne imposée et les modalités précisées en 0031. Le justificatif papier signé et la procédure historique signée sont validés ; les incidents, délais et autres modalités d'A06 à A08 restent à définir. `APPROVED_PENDING_HANDOVER` n'est pas l'état livré : l'examen technique V10 produit `APPROVED_RESERVED`, de portée `RESERVATION_ONLY_V1`, sans autorisation physique. La nouvelle autorisation nominative est un fait distinct.

| État de la demande | Action | Effet |
| --- | --- | --- |
| `REQUESTED` | Demande motivée avec réception, montant, source et bénéficiaire | Montant réservé sous verrou, auteur et état présenté conservés |
| `APPROVED_PENDING_HANDOVER` | Approbation par une autre personne habilitée | Autorisation et réservation conservées, aucun mouvement physique constaté |
| `REJECTED` | Refus motivé | Réservation libérée, aucune sortie |
| `WITHDRAWN` | Retrait motivé par le demandeur selon la politique retenue | Réservation libérée seulement si aucune remise n'a eu lieu ni n'est incertaine |
| `COMPLETED` | Confirmation par une personne autorisée de la remise physique effective | Mouvement de restitution, justificatif et audit atomiques. T augmente et Q est libéré |

Le demandeur et l'approbateur sont obligatoirement deux comptes distincts, quels que soient leurs rôles multiples. Le rôle habilité à confirmer la remise physique reste à arbitrer. La décision acquise n'impose pas une troisième personne.

Toute modification du montant, du bénéficiaire ou de la source après présentation nécessite une nouvelle décision explicite. Une version périmée ne peut pas être approuvée. La réservation empêche simultanément une nouvelle affectation ou restitution de consommer les mêmes fonds.

Une absence d'approbateur laisse la demande en attente. Aucune auto-approbation, valeur par défaut ou reprise technique ne vaut approbation. Une éventuelle expiration de réservation doit être décidée en A04 et ne peut pas libérer des fonds dont la remise serait incertaine.

## Intégrité et reprise

La demande, sa décision, la source réservée et la confirmation physique sont liées à l'établissement. Les budgets sont sérialisés sur la réception et sa provenance. Les contraintes de conservation, références et unicité sont requises au commit, avec tests SQL directs.

La confirmation de remise possède une identité idempotente et une empreinte de contenu. Rejouer la même confirmation ne produit aucun nouveau mouvement. Changer son contenu sous la même clé est refusé.

Une réponse perdue après confirmation impose une relecture ou un rejeu de cette identité. La personne ne doit pas remettre une seconde fois les espèces à partir du seul affichage d'un délai dépassé. Si le geste physique et la confirmation informatique divergent ou restent incertains, un incident est conservé et la procédure de secours A03/A04 s'applique.

La restitution d'une somme jamais affectée diminue U et la caisse conservée. Elle ne produit ni `SETTLEMENT` ni compensation dans `billing`, car aucune obligation n'a été réglée par cette somme. Si un règlement existant devait être compensé pour permettre la sortie, le circuit d'excédent doit refuser l'opération.

## Trace et confidentialité

Le registre conserve réception source, montant, devise, provenance, motif, demandeur, approbateur, auteur de la remise physique, dates et référence justificative. Les détails probants et motifs restent soumis aux permissions. L'audit général relie les références sans recopier les données confidentielles.

L'identité du bénéficiaire et un éventuel mandat doivent être vérifiés selon A05. Un compte connecté ou une parenté ne désignent pas automatiquement la personne à qui remettre les espèces.

Les preuves de réception et restitution sont conservées même après rejet, rectification ou invalidation. Elles gardent leur nature interne suivant 0019, sans prétention fiscale.

## Critères de sortie

Les preuves de restitution, dont le refus d'auto-approbation, deux réservations concurrentes et une confirmation répétée, sont requises dès le lot 2. Les cas de compensation, de rectification et de remplacement S09, S10, S17 et S23 suivent la [matrice de portée par lot](../missions/mission-004-cash-collections.md#portée-des-scénarios-par-lot) : refus des chemins non autorisés et de toute reconstitution du budget dès le socle, puis preuve complète de chaque parcours effectivement autorisé avant activation. Leurs variantes ultérieures ne sont pas des fonctionnalités imposées au premier lot.

Cette décision métier est acquise. A05 et les précisions de 0031 sont maintenant validés. La déclaration signée historique et le justificatif papier signé ne sont plus des hypothèses, mais leur présence dans le cadrage ne signifie pas qu'une sortie est implémentée. La tranche nominative reste sans confirmation physique. Les contrôles de droits des tiers, incidents, rectifications et autres modalités d'A06 à A08 restent à préciser ou construire avant les parcours concernés. Le lot 2 reste ouvert.
