# Mission 004 : encaissements espèces et affectations

**Compléments suspendus au profit de la [Mission 005 numérique](mission-005-digital-school-payments.md), sans clôture du lot 2 ni suppression de ses fondations.** Lot 1 terminé ; réceptions #31 (`6a2fdaa`), premier règlement #32 (`e49d802`), réservations #33 (`7b38965`) et identification/autorisation nominative #34 (`0bb790f`) intégrés. Aucune confirmation physique ou restitution effective n'est opérationnelle. Les capacités restent désactivées par défaut et la Mission 003 techniquement clôturée.

Le cash est désormais secondaire et facultatif. A03, A04 et les règles restantes gardent leur portée pour un futur pilote espèces ; suspendre cette mission ne les valide pas et ne les implémente pas. Les garanties propres à un futur pilote numérique restent à définir dans Mission 005, sans autoriser d'exploitation actuelle.

La restitution en V1, les orientations A01 à A04 et désormais A05 sont validées. La procédure historique de déclaration signée examinée indépendamment et le justificatif papier signé pour la future remise sont aussi validés, suivant [0031](../decisions/0031-identification-et-autorisation-de-restitution.md). Les autres modalités d'A06 à A08 ne sont pas implicitement arbitrées.

## Objectif

Enregistrer la réception effective d'espèces, affecter les montants aux obligations et conserver une preuve durable. Le registre doit aussi tracer les espèces déjà reçues qui ne peuvent pas être affectées. Les ajustements, encaissements, règlements et restes à payer gardent des sens et sources distincts.

Le socle retenu est un module Java `payments` distinct de `billing`, avec transaction PostgreSQL commune, audit et intégrité au commit. Les frontières et contrats proposés figurent dans [0026](../decisions/0026-contrats-encaissements-especes.md), la restitution dans [0027](../decisions/0027-restitution-excedents-jamais-affectes.md), les projections et garanties de facturation dans [0028](../decisions/0028-projections-reglements-et-ajustements.md).

## Décisions acquises

| Référence | Décision | Origine |
| --- | --- | --- |
| V01 | Domaine d'encaissement espèces distinct de `billing`, affectations explicites et registre durable | Validation du cadrage par le propriétaire |
| V02 | Réception, affectations, règlements, preuve et audit cohérents dans une transaction locale | Cadrage retenu et invariants de la Mission 003 |
| V03 | Paiements partiels conservés, paiements multi-obligations et multi-élèves introduits progressivement | Demande de consolidation du propriétaire |
| V04 | Restitution en V1 des seuls excédents d'espèces jamais affectés | Décision explicite du propriétaire lors de l'engagement du lot 1 |
| V05 | Justification, approbation par une seconde personne habilitée, confirmation de remise physique et traçabilité intégrale de cette restitution | Même décision explicite |
| V06 | Une restitution d'excédent ne permet jamais de rembourser indirectement un règlement déjà affecté | Même décision explicite |
| V07 | Les protections de `billing` sont présentes dès la première livraison autorisant `SETTLEMENT` | Consolidation demandée par le propriétaire |
| V08 | Aucun encaissement fictif, solde négatif, perte d'isolation ou affaiblissement de la double validation | ADR 0012, 0015, 0021, 0022 et 0023 |

Les archives d'outbox ne remplacent ni le registre monétaire ni l'audit. L'acteur habilité atteste la réception et la remise physiques. La base garantit la cohérence de ces déclarations, sans pouvoir constater les billets.

## Périmètre proposé pour la V1

| Inclus dans la proposition | Évolutions ultérieures |
| --- | --- |
| Réception d'espèces dans un établissement et une devise | Paiements numériques et rapprochement fournisseur |
| Affectations explicites, partielles ou complètes | Crédit réutilisable, portefeuille ou transfert entre établissements |
| Réceptions à régulariser et restitution de leurs fractions jamais affectées | Remboursement commercial d'un règlement déjà affecté |
| Références et données justificatives durables | Facture fiscale et intégration réglementaire |
| Rectifications motivées et contrôlées, périmètre à arbitrer | Fermeture physique complète de caisse si hors du périmètre pilote retenu |
| Consultation autorisée des opérations et incidents | Portail Families, frontend et application mobile |

Le montant remis par le payeur, la monnaie effectivement rendue au guichet, le montant conservé, les affectations et les restitutions ultérieures sont distincts. Une restitution est comptabilisée seulement après confirmation de remise réelle.

## Réception et régularisation

Un aperçu permet de déterminer le montant affectable avant réception. Une confirmation ordinaire applique le plan explicitement accepté. Un plan devenu invalide produit un conflit sans affectation partielle silencieuse.

Si les espèces sont déjà reçues, leur déclaration peut être enregistrée comme réception à régulariser, même sans affectation valide. Le justificatif distingue ce qui a été reçu, affecté et reste en attente. Un échec d'affectation ne vaut jamais disparition de la réception physique.

Une répartition partielle doit être explicitement confirmée. Exemple de référence, 120 000 XAF conservés pour 100 000 XAF de règlements et 20 000 XAF jamais affectés. Seuls 100 000 XAF produisent des `SETTLEMENT`. Les 20 000 XAF restent liés à cette réception et peuvent suivre le circuit de restitution approuvé.

L'approbation réserve le montant éligible et autorise la remise. Elle ne constate aucune sortie. La confirmation physique crée la restitution et son justificatif dans une transaction auditée. Un délai dépassé ou une réponse réseau ambiguë impose la relecture de la même opération avant tout nouveau geste physique.

L'éligibilité dépend de la provenance historique. Une désaffectation, une contrepassation, une remise postérieure ou un changement de statut ne recrée aucun budget de restitution des excédents. Voir [0027](../decisions/0027-restitution-excedents-jamais-affectes.md).

## Projections et refus proposés

Les noms et règles de projection sont proposés dans [0028](../decisions/0028-projections-reglements-et-ajustements.md). Le montant initial, les ajustements nets, le net facturé, les règlements nets affectés et le reste sont exposés distinctement selon les droits.

Une remise qui ferait descendre le net facturé sous les règlements actifs est refusée en V1. Un signal de suivi peut conserver la demande et l'excédent hypothétique, sans créer d'écriture, crédit ou somme restituable. Une autre personne ne peut pas approuver une opération qui viole cette règle.

L'annulation administrative d'une obligation avec règlement actif serait également refusée en V1 jusqu'à définition du traitement des fonds déjà affectés. Il s'agit d'une proposition à valider en A07.

Une demande d'ajustement antérieure à un paiement devient périmée si le journal change, conformément à 0022. Sa confirmation ne reprend pas automatiquement le nouveau montant.

## Décisions reçues et arbitrages restants

Les orientations A01 à A04 ont été validées au lancement du lot 2, puis A05 et les précisions consignées en 0031. A06 à A08 restent ouverts pour leurs autres modalités ; les codes techniques ne les arbitrent pas. Les contrats de [réception](../payments-cash-receptions.md) et d'[identification](../payments-cash-identities.md) décrivent les capacités réellement construites.

| Référence | Choix à soumettre | Proposition et impact |
| --- | --- | --- |
| A01 | Orientation validée, précisée par A05 | Déclaration ordinaire par caissier, comptable ou administrateur habilité. Demandeur et approbateur distincts. Directeur pouvant approuver sans encaisser, auditeur en lecture seule. Un caissier habilité peut confirmer après approbation sans troisième personne systématique ; le parcours physique reste à construire avec tous ses contrôles |
| A02 | Orientation validée, application requise avant règlement | Paiements sur les échéances impayées les plus anciennes, remises sur les échéances non couvertes les plus éloignées, échéancier initial immuable. Définir encore les cas de correction et d'arriérés concernés par A06/A07 |
| A03 | Secours manuel autorisé, procédure à réaliser | Références uniques, justificatifs, rapprochement et contrôle indépendant à la reprise. Définir supports, responsables et limites. Aucun système de paiement hors ligne complet n'est autorisé |
| A04 | Contrôle quotidien requis avant pilote | Rapprochement des espèces physiques par caissier, écarts constatés et validation indépendante. Architecture prévue dès la première PR, réalisation obligatoire avant pilote, sans imposer sa livraison dans cette tranche intermédiaire |
| A05 | Principes validés, implémentation progressive | Remettant identifié sans compte obligatoire, bénéficiaire par défaut égal au remettant ; tiers seulement sur droit ou mandat vérifié, aucune déduction familiale. Identité vérifiée au moment de la remise, sans copies intégrales systématiques ; demandeur et approbateur distincts, caissier habilité sans troisième personne imposée. Anciennes sources : déclaration signée rapprochée du reçu et revue indépendamment. Justificatif papier signé validé. Le contrôle des tiers, le numérique signé et les incidents restent à cadrer pour les parcours concernés |
| A06 | Dates métier, arriérés et années clôturées | Horodatage serveur immuable. Autorisation d'une date de réception distincte, antidatage et encaissement d'arriérés à décider. Les exigences de validation des ajustements d'année clôturée restent acquises |
| A07 | Rectifications et refus post-paiement | Définir les erreurs corrigeables, la seconde validation, le remplacement d'affectations et le traitement d'une correction de montant qui contredit des mouvements déjà confirmés. Valider le refus V1 des réductions créant un excédent et de l'annulation administrative avec règlement actif |
| A08 | Arbitrage métier ouvert | OPEN, PARTIALLY_SETTLED, SETTLED et SETTLED_WITH_ADJUSTMENTS sont des codes techniques implémentés et proposés pour revue, avec montants séparés et états historiques sans paiement conservés. Ils ne valent pas validation métier ; usage opérationnel, informations imprimables/fiscales, justificatifs et bornes des paniers restent à décider |

Les seuils de 25 % et 100 000 XAF des ajustements ne sont pas transposés automatiquement aux restitutions. Leur double validation est déjà exigée par V05 quel que soit le montant. Une école à une seule personne habilitée ne peut pas auto-approuver une restitution.

Répartition cible : A01 à A05 et les précisions de 0031 sont arbitrés suivant leur périmètre explicite ; les autres modalités restent à préciser. Les capacités construites décrivent le code, sans arbitrage supplémentaire. Les variantes de restitution et rectification du tableau ne sont pas toutes opérationnelles :

| Capacité future | Proposition de rôles | Choix restant |
| --- | --- | --- |
| Constater une réception et affecter | Caissier, comptable, administrateur | Périmètre d'activité et délégations |
| Demander une restitution | Caissier, comptable, administrateur | Demande pour ses actes ou pour toute réception de l'établissement |
| Approuver une restitution | Comptable, administrateur, directeur | Rôles retenus, avec compte distinct du demandeur dans tous les cas |
| Confirmer la remise physique | Caissier, comptable, administrateur | Auteur possible parmi les personnes habilitées, dont éventuellement le demandeur |
| Lire les opérations et justificatifs | Personnel habilité de caisse et de contrôle | Lecture de ses opérations ou de tout l'établissement, accès du directeur et de l'auditeur |
| Demander et approuver une rectification | Répartition analogue à la restitution | Nature des erreurs et portée des capacités en A07 |

Les questions juridiques et fiscales de 0013, 0019 et 0023 restent ouvertes. Ce lot ne leur attribue aucune réponse.

## Lots et critères de sortie

| Lot | Livraison | Dépendances | Critères vérifiables |
| --- | --- | --- | --- |
| 1 | Cadrage et ADR documentaires | Mission 003 clôturée | Décisions acquises séparées des propositions, contrats et scénarios relus, A01 à A08 soumis au propriétaire, aucun code ou schéma ajouté |
| 2 | Première chaîne complète sur une obligation | Lot 1 revu, arbitrages nécessaires validés et développement explicitement autorisé | Réception, affectation, preuve et audit atomiques. Réception à régulariser et restitution limitée opérationnelles. Provenance jamais affectée, concurrence et idempotence garanties. Toutes les protections de 0028 vérifiées dès cette livraison |
| 3 | Plusieurs obligations d'un même élève | Lot 2 | Plan explicite, conservation du total, ordre de verrouillage stable, refus atomique et projections cohérentes |
| 4 | Plusieurs élèves | Lot 3 | Une réception et plusieurs affectations dans le même établissement et la même devise, aucun droit familial créé et justificatifs autorisés |
| 5 | Enrichissement des opérations et rectifications | Lots 2 à 4 | Consultations, suivi et rectifications de paniers selon A07. Les protections monétaires et la restitution limitée sont déjà présentes |
| 6 | Consolidation finale | Lots livrés | Matrices de concurrence, reprises, limites et mutations pertinentes vérifiées sur PostgreSQL jetable dans GitHub Actions |

La préparation technique peut être découpée sans activer de nouveaux règlements. La première livraison qui autorise `SETTLEMENT` contient l'ensemble réception-affectation-preuve-audit et les garanties de [0028](../decisions/0028-projections-reglements-et-ajustements.md#garanties-requises-dès-le-premier-settlement). Les lots suivants enrichissent une chaîne cohérente.

Les tests PostgreSQL directs, d'isolation, de double validation et de reprise sont des critères de chaque lot concerné. Le lot 6 complète ces preuves.

La [portée des scénarios par lot](#portée-des-scénarios-par-lot) distingue le socle exigé dès le lot 2 des variantes propres aux capacités introduites ensuite. Elle ne modifie ni les décisions V01 à V08 ni les arbitrages A01 à A08.

## Scénarios critiques de référence

Les montants ci-dessous sont exprimés en XAF. Les scénarios portant sur des choix A01 à A08 devront être approuvés avant leur implémentation.

| Référence | Situation | Résultat attendu |
| --- | --- | --- |
| S01 | Obligation 100 000, réception affectée 40 000 | Net facturé 100 000, règlements 40 000, reste 60 000, aucun ajustement produit |
| S02 | Obligation 100 000, remise 20 000, paiement 80 000 | Reste nul, encaissé 80 000 et remise 20 000 distincts, statut mixte proposé |
| S03 | Remise totale 100 000 sans paiement | `WAIVED`, encaissé zéro et seconde validation conservée |
| S04 | Deux paiements de 60 000 sur un reste de 100 000 | Règlements cumulés au plus 100 000. Le second plan est revalidé. Les espèces déjà reçues non affectables restent traçables sans affectation silencieuse |
| S05 | Réception 120 000, affectation 100 000, excédent 20 000 | Seuls 100 000 produisent des règlements. Une restitution de 20 000 est possible sur la fraction jamais affectée, malgré l'affectation partielle de la réception |
| S06 | Demande de restitution approuvée, remise physique non confirmée | Montant réservé, aucune sortie de caisse constatée |
| S07 | Remise physique confirmée deux fois avec la même clé | Une restitution, une preuve et un effet monétaire, résultat existant renvoyé |
| S08 | Deux demandes concurrentes sur le même excédent | Réservations et restitutions cumulées dans le budget jamais affecté disponible |
| S09 | Réception 100 000 totalement affectée, puis affectation contrepassée | Aucun budget de restitution d'excédent recréé. Le chemin de remboursement indirect est refusé |
| S10 | Réception 120 000, 100 000 affectés, puis 10 000 du solde affectés et contrepassés | Au plus 10 000 du solde initial demeurent jamais affectés. Les 10 000 ayant été affectés ne redeviennent pas éligibles |
| S11 | Remise 30 000 demandée après règlement 80 000 sur 100 000 | Refus, reste toujours 20 000, aucun crédit ou excédent restituable créé |
| S12 | Demande sensible puis paiement ou compensation | Ancienne demande périmée même si le reste retrouve sa valeur antérieure, approbation refusée |
| S13 | Paiement 80 000 puis petite correction 5 000 dans le seuil de facturation | Le paiement ne consomme ni ne réinitialise le cumul des ajustements. La politique 0022 reste applicable |
| S14 | SQL direct, règlement sans réception confirmée ou affectation sans écriture | Commit refusé. Une somme en attente documentée peut exister sans affectation |
| S15 | Mauvais établissement, devise, compte ou permission | Refus sans effet monétaire et sans révélation non autorisée |
| S16 | Rejeu de clé avec contenu différent | Conflit, aucun second mouvement ni nouveau justificatif |
| S17 | Erreur de saisie fictive ou d'affectation | Rectification liée et approuvée selon A07, historique conservé, aucun faux remboursement ni rétablissement d'éligibilité |
| S18 | Audit ou publication durable en échec au commit | Ensemble des écritures de l'opération annulé |
| S19 | Arrêt après commit avant réponse HTTP | Relecture ou rejeu renvoie la même opération et ses références |
| S20 | Panne pendant une remise physique ou une réception de secours | Procédure A03/A04, aucun nouveau geste supposé par une simple reprise technique |
| S21 | Tentative par une route générique d'ajustement de contrepasser un règlement | Refus. Les compensations de paiement passent par leur contrat dédié |
| S22 | Dû nul par correction seule ou combinaison de corrections et remises, sans paiement | Statuts et montants hérités de la Mission 003 conservés |
| S23 | Rectification, remplacement ou nouvelle référence technique d'une réception affectée | Provenance d'origine conservée, aucun nouveau budget jamais affecté des mêmes fonds |

## Portée des scénarios par lot

S01 à S23 forment le référentiel de la Mission 004. Ils ne prescrivent pas l'ouverture de toutes les capacités au lot 2. Le socle financier est exigé dès le premier `SETTLEMENT`, puis chaque nouveau parcours en prouve la préservation avant son activation.

| Lot | Portée des preuves attendues |
| --- | --- |
| 2, socle sur une obligation | S01 à S08, S11 à S16 et S18 à S22 : paiements partiels, projections, ajustements et demandes périmées, restitution limitée, intégrité SQL, concurrence, droits, idempotence, atomicité et reprise. Dans S12, un paiement suffit à exercer la péremption ; la variante avec compensation attend l'autorisation de ce parcours. S20 suit la procédure de secours arbitrée, sans imposer une fonctionnalité hors ligne |
| 2, refus des parcours non autorisés | Pour S09, S10, S17 et S23, vérifier le refus applicatif des compensations et rectifications non autorisées et les gardes de provenance, y compris par SQL direct, contre la reconstitution du budget jamais affecté. Ces refus ne nécessitent pas un parcours fonctionnel complet de rectification. Toute rectification qu'A07 autoriserait dès le lot 2 doit toutefois posséder immédiatement ses preuves complètes |
| 3, plusieurs obligations d'un élève | Étendre S01, S02, S04, S05, S12, S14 à S16, S18 et S19 aux paniers multi-obligations : conservation des totaux, plan explicite, verrouillage ordonné, péremption d'une cible et refus atomique de tout le panier. Les garanties du socle sont réexercées sur ces variantes |
| 4, plusieurs élèves | Étendre les variantes de panier du lot 3 à plusieurs élèves du même établissement et de la même devise. S15 couvre en particulier les cibles et lectures autorisées et la confidentialité du justificatif multi-élèves, sans créer de droit familial. Conserver les preuves de total, de concurrence et de reprise du panier |
| 5, rectifications ultérieures | Exercer S17 et S23 sur les parcours de rectification ou de remplacement effectivement autorisés par A07, y compris les paniers. S09 et S10 dans leur forme avec compensation exécutée, ainsi que cette variante de S12, sont exigés dès qu'un tel parcours est autorisé ; s'il reste interdit en V1, conserver et tester le refus explicite. Aucun remboursement effectif n'est ajouté par ces scénarios |
| 6, consolidation | Rejouer et compléter les scénarios des capacités réellement livrées, leurs combinaisons et les mutations pertinentes. Ce lot ne reporte aucune garantie fondamentale ni aucun test requis lors de l'introduction d'une capacité |

Une preuve du lot 2 sur une obligation ne suffit donc pas à valider une extension multi-obligations, multi-élèves ou une nouvelle rectification. Réciproquement, le report de cette extension ne permet pas de différer ses garde-fous : tant que le parcours n'est pas autorisé, son refus et l'absence d'effet monétaire ou de fuite restent vérifiés. La répartition ci-dessus est un plan de validation proposé, sans arbitrage implicite d'A01 à A08.

## Vérification et limites du lot documentaire

Les propositions ont été confrontées aux ports `CurrentActor`, `EstablishmentAccess`, `AuditTrail`, aux services de facturation, aux contraintes V5 à V7 et aux ADR 0012, 0015, 0016, 0019, 0021 à 0025 sur la base `9f54029`.

Le lot documentaire vérifie la cohérence des références, la distinction entre état réel et cible et le périmètre du diff. Les futurs invariants seront prouvés par la vraie API, des transactions concurrentes et du SQL direct sur PostgreSQL jetable dans GitHub Actions.

Le lot 1 est terminé. #31 a intégré les réceptions et #32 le règlement d'une obligation avec les gardes de [0029](../decisions/0029-premier-reglement-especes.md), sous activation de test explicite. La tranche de [0030](../decisions/0030-reservations-excedents-especes.md) réserve seulement le budget jamais affecté et conserve les demandes et décisions ; elle ne réalise pas la restitution limitée exigée pour terminer le lot 2.

Pour S05, seule l'éligibilité de la fraction jamais affectée et sa réservation sont livrées ; S06 distingue approbation et sortie, S08 couvre la double consommation. S07 reste un refus de remise, **pas** une preuve de confirmation physique idempotente. S09/S10 conservent les refus de compensation et la consommation historique ; les variantes de rectification restent fermées. Le modèle complet de provenance et les scénarios cibles ci-dessus ne sont pas déclarés intégralement implémentés.

La tranche nominative de 0031 applique A05 et prépare la transition des réservations `RESERVATION_ONLY_V1` sans les réécrire. Elle ne livre aucune confirmation physique. Avant remise, construire l'identité de tentative durable, le contrôle final au guichet, le justificatif papier signé validé et la sortie atomique ; définir les incidents et le contrôle des droits des tiers pour ces parcours. A06 à A08 demeurent ouverts sur leurs autres modalités ; aucune approbation budgétaire n'est rétroactivement une autorisation physique. Le secours A03 et le contrôle quotidien A04 restent à réaliser avant pilote. La Mission 003 reste clôturée, Families fermé et les fournisseurs désactivés. Aucun frontend, déploiement, import réel ou migration persistante n'est autorisé.
