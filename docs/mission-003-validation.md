# Mission 003 : journal de validation

Moteur de facturation scolaire. Les choix sont dans [0015](decisions/0015-modele-de-facturation.md) à [0023](decisions/0023-personnes-liees-et-vues.md), le périmètre dans le [cadrage](missions/mission-003-billing-engine.md).

## Méthode

Tout s'exécute sur GitHub Actions, sur PostgreSQL 18.6 réel, sans aucune exécution locale. Trois niveaux de preuve :

1. **La suite de tests** passe par la vraie API, et écrit directement en base pour les garanties de PostgreSQL.
2. **Une campagne de défauts injectés** vérifie que ces tests savent échouer : un défaut à la fois, et au moins un test doit le détecter.
3. **Des exécutions de contrôle du code intact** détectent les tests instables.

Une campagne n'atteste que le commit qu'elle a testé. Ces défauts sont choisis à la main sur les garanties critiques : ils ne couvrent pas tous les défauts possibles.

## Résultats par lot

| Lot | Contenu | Tests au total | Défauts détectés | Contrôles verts | PR |
| --- | --- | --- | --- | --- | --- |
| 1 | Outbox durable, portail fermé par défaut | 111 | 17 sur 17 | 5 sur 5 | [#21](https://github.com/SPX-tech/ScolaPay/pull/21), fusionnée |
| 2 | Catalogue académique | 129 | 15 sur 15 | 3 sur 3 | [#22](https://github.com/SPX-tech/ScolaPay/pull/22), fusionnée |
| 3 | Catalogue de frais, identité stable des charges | 178 | 52 sur 52 | 3 sur 3 | [#23](https://github.com/SPX-tech/ScolaPay/pull/23), fusionnée |
| 4 | Obligations, échéanciers, journal, émission | 236 | 46 sur 46 | 3 sur 3 | [#24](https://github.com/SPX-tech/ScolaPay/pull/24), fusionnée |
| 5 | Remises, corrections, annulations, réexamen | 330 | 124 sur 124, dont 41 repris à l'identique du lot 4 | 3 sur 3 | [#25](https://github.com/SPX-tech/ScolaPay/pull/25), fusionnée |
| 6, finalisation | Personnes et rôles explicites, vues confidentielles cohérentes, corrections tracées | 390 | 15 sur 15 ciblés sur la reprise ; campagne historique distincte | 1 sur 1 | [#26](https://github.com/SPX-tech/ScolaPay/pull/26), fusionnée |
| 7 | Consolidation sur le code intégré, catalogue, obligations, ajustements et vues | 461 en CI, 276 pour les mutations | 195 sur 195 | 3 sur 3 | [#29](https://github.com/SPX-tech/ScolaPay/pull/29), fusionnée |

## Lot 4 en détail

**Commit validé : `f2b89a323f7197fe580e2ec9b39a6bf2c180625c`.** Les commits suivants de cette PR ne modifiaient que la documentation.

- [Campagne concluante](https://github.com/SPX-tech/ScolaPay/actions/runs/42297448883) : 46 défauts détectés sur 46, 3 contrôles verts sur 3, 236 tests.
- [Première campagne](https://github.com/SPX-tech/ScolaPay/actions/runs/42292909680), sur `b27a573` : 44 sur 46, non concluante.

### Le scénario critique

Une obligation est émise, une nouvelle version du catalogue est publiée, l'élève est retiré puis réinscrit la même année.

| Preuve | Test |
| --- | --- |
| Tarif, libellé et code changés, réinscription confirmée sur le nouveau tarif : **toujours une seule obligation**, même identifiant, même référence, même montant. Le lot ni l'émission unitaire ne la recréent | `uneReinscriptionApresUneNouvelleVersionNeRefacturePasLaCharge` |
| Réinscription sur un niveau au tarif spécifique : la même charge n'est pas refacturée | `uneReinscriptionSurUnNiveauAuTarifSpecifiqueNeRefacturePasLaCharge` |
| **La clé lue dans le catalogue de PostgreSQL** : `establishment_id`, `student_record_id`, `fee_definition_id`, ni version ni inscription | `laCleDUniciteNeContientNiLaVersionNiLInscription` |
| Écriture directe en base, sur une autre version et une autre inscription : refusée par `obligations_one_per_charge` | `laBaseRefuseUneDeuxiemeObligationSurUneAutreVersionEtUneAutreInscription` |

Ajouter la version du catalogue ou l'inscription à la clé fait échouer ces tests : les défauts `key-includes-catalog-version` et `key-includes-enrollment` les exigent nommément.

### Inscriptions sans confirmation

`GET .../billing/pending-issuances` liste nominativement les inscriptions actives à qui il manque des frais automatiques du tarif en vigueur. La liste se déduit de ce qui manque réellement, sans indicateur posé à l'inscription. Y figurent donc aussi les élèves inscrits avant toute publication et ceux concernés par une nouvelle charge. Un frais facultatif n'y figure pas, ni un élève retiré.

### Ce que les campagnes ont révélé

| Découverte | Conséquence |
| --- | --- |
| Lot 1 : un test ne démarrait jamais son instance | Test corrigé |
| Lot 1 : affirmation fausse dans la décision 0016 sur `@EnableScheduling` | Décision corrigée |
| Lot 3 : verrou de la base masqué par celui du service | Test direct sur deux connexions |
| Lot 3 : renommer une charge sur plusieurs unités aurait été impossible | Règle déplacée à la publication |
| Lot 4 : un élève remplacé par un autre entre l'aperçu et la confirmation, à effectif constant, n'était pas testé | Nouveau test |
| Lot 4 : le verrou du journal n'était prouvé que par le hasard : le test à dix fils passait sans lui à la première campagne et échouait à la seconde | Test déterministe sur deux connexions |

### Incident sur l'outillage

Deux campagnes pilotées depuis le poste de développement ont été interrompues faute de mémoire, car elles créaient un worktree et un push par défaut. Elles passent désormais par [`mutation-campaign.yml`](../.github/workflows/mutation-campaign.yml) : un seul push de référence, par groupes de six sur GitHub Actions. Le poste ne fait plus que lire un rapport. L'outil distingue aussi un défaut détecté d'un défaut inapplicable ou qui ne compile pas, qui ne comptent plus comme détectés. Voir [son mode d'emploi](../.github/mutation/README.md).

## Lot 5 en détail

**Commit validé : `542efe76e1698f636222f185275f06c308306dad`.** Les commits suivants de la PR ne modifient que la documentation.

- [Campagne concluante](https://github.com/SPX-tech/ScolaPay/actions/runs/42377590705) : 124 défauts détectés sur 124, 3 contrôles verts sur 3, 330 tests. Sur les 46 défauts du lot 4 : 41 sont rejoués à l'identique, dont ceux de la clé d'unicité, 3 sont redéfinis sur `V6` sous le même identifiant, et 2 ne sont pas rejoués tels quels. `db-reason-optional` cède la place à `db-discount-without-reason` et `db-correction-without-reason`. `status-always-open` cède la place à `status-always-corrected`, `status-mix-shown-as-waiver` et `status-cancelled-seen-as-other`.
- [Avant-dernière campagne](https://github.com/SPX-tech/ScolaPay/actions/runs/42367092171), sur `8223ca3` après la revue du propriétaire : 123 sur 124, non concluante.
- [Campagne avant la revue](https://github.com/SPX-tech/ScolaPay/actions/runs/42315265001), sur `94dec6e` : 97 sur 97. Elle attestait une version dont la revue a montré les limites : elle ne vaut pas pour le code actuel.
- [Première campagne](https://github.com/SPX-tech/ScolaPay/actions/runs/42307031300), sur `0e0f433` : 95 sur 97, non concluante.

| Exigence | Preuve |
| --- | --- |
| Un écart ou un retrait ne modifie jamais l'obligation | `unNiveauPlusCherEstSignaleSansFacturerNiModifierLObligation`, `unRetraitEstSignaleSansSupprimerNiAnnuler` : montant, dû, statut et taille du journal inchangés |
| Aucun supplément ni remboursement présumé | `unNiveauMoinsCherEstSignaleSansSupposerUnRemboursement` : une différence signée, sans plus |
| Le même solde garde des sens différents | `troisOperationsAuMemeSoldeGardentDesStatutsDistincts` |
| Une combinaison ne se présente pas comme une remise totale | `uneCombinaisonDeRemisesEtDeCorrectionsResteNeutre` |
| Pas de double approbation pour une petite remise | `unePetiteRemiseSAppliqueDirectementAvecSonMotifDansLeJournal` |
| Le premier seuil atteint s'applique | `lePourcentageSAppliqueAuPremierFrancAuDela`, `leMontantAbsoluSAppliqueSurUneGrosseObligation`, `surUnePetiteObligationLePourcentageArriveLePremier` |
| Le seuil ne se contourne pas | `unHaussePuisUneBaisseNeSeCompensentPasPourEchapperAuSeuil`, `uneAlternanceDeHaussesEtDeBaissesConsommeLeSeuil`, `fractionnerNeRepousseJamaisLeSeuil`, `contrepasserPuisRecommencerNeFaitJamaisDepasserLeSeuil`, `desHaussesEtDesBaissesSimultaneesRespectentLeSeuil` |
| Rien ne se confirme sans avoir été présenté | `uneAnnulationDontLeDuAChangeExigeUneNouvelleConfirmation`, `uneRemiseDontLaPorteeAChangeExigeUneNouvelleConfirmation`, `unSoldeRevenuIdentiqueNeSuffitPasAConfirmer` |
| Cela tient en concurrence | `uneEcritureEtUneConfirmationSimultaneesRestentCoherentes`, `confirmerEtRetirerEnMemeTempsNeLaissentQuUneDecision` |
| Cela tient même sans le code | `laBaseRefuseDeConfirmerUneDemandePerimee`, `laBaseRefuseDeConfirmerAvecUneEcritureDifferenteDeLaDemande`, `laBaseRefuseDAmenerLeDuAZeroSansSecondePersonne` |
| Une demande ne bloque pas indéfiniment | `lAuteurRetireSaDemandeAvecUnMotifEtLaTraceReste`, `seulLAuteurRetireEtUnAutreRefuse` |
| Toujours deux personnes, sans auto-approbation | `uneEcoleAUnSeulUtilisateurNePeutPasSeConfirmerElleMeme`, `laBaseRefuseQueLAuteurDecideSaPropreDemande` |
| Le directeur confirme sans gérer | `leDirecteurConfirmeOuRefuseSansPouvoirOperer`, `leDirecteurConfirmeSansGererLesFinances` |
| Le caissier n'accède à aucun motif | `aucuneRouteDeLectureDeLaFacturationNeDonneUnMotifAuCaissier` parcourt les vraies routes, `leCaissierVoitLesMontantsSansAucunMotif`, `leCaissierVoitLesDemandesSansMotifNiNoteDeDecision` |
| Les rôles de contrôle gardent l'accès | `lesRolesDeControleLisentLesMotifs` |
| La réinscription reste protégée | Défauts `key-includes-catalog-version` et `key-includes-enrollment` rejoués, `uneChargeAnnuleeNEstPasRefactureeParUneReinscriptionNiParUnLot` |
| Aucun encaissement simulé | `aucunStatutNiEcritureNeSimuleUnReglement`, `aucuneRouteNeModifieNiNeRegleUneObligation` |

Toutes les validations ont tourné sur GitHub Actions. Ni le poste de développement ni le VPS n'ont exécuté de test.

### Ce que la revue et les campagnes ont révélé

| Découverte | Conséquence |
| --- | --- |
| Revue : le cumul net laissait une hausse et une baisse se compenser, ce qui contournait le seuil | Cumul brut des écritures actives |
| Revue : une annulation en attente portait sur le reste dû à la confirmation, un montant jamais présenté à l'approbateur | La demande fige son montant et le journal, la base le vérifie |
| Revue : rien ne masquait les motifs dans l'API | Masquage dans les services, test sur toutes les routes de lecture |
| Revue : les décisions de réexamen n'avaient aucune route de lecture | Exposées dans le détail, avec le même masquage |
| Revue : refuser sa propre demande remontait une erreur générique de contrainte | Refus explicite, et route de retrait dédiée |
| Campagne : un test refusait la contrepassée de l'émission pour une mauvaise raison, une remise ayant rendu le dû insuffisant | Scénario refait, le message exact est exigé |
| Campagne : une annulation contrepassée, puis un dû de nouveau nul, n'était pas testée | Nouveau test |
| Campagne : le contrôle de permission du retrait se cachait derrière celui de l'auteur, tous deux en 403 | Le code d'erreur exact est exigé |

## Lot 6 en détail

**Code validé : `974e9c45eac9b1ed3725cbdeb4b68594d589b713`.** La reprise conserve le commit initial `253312a` et ses garanties. Les commits suivants de clôture sont documentaires.

- [Régressions avant correction](https://github.com/SPX-tech/ScolaPay/actions/runs/42897808109) : neuf échecs attendus, sans erreur de compilation ni test ignoré.
- [CI complète](https://github.com/SPX-tech/ScolaPay/actions/runs/42901332810) : 390 tests Java verts, compilation, vérification du JAR et de sa surface publique.
- [Campagne ciblée](https://github.com/SPX-tech/ScolaPay/actions/runs/42901353861) : 15 défauts détectés sur 15, un contrôle sain de 390 tests. Pagination confidentielle, instantanés financiers, contraintes NULL et garanties de correction des personnes sont ciblés.
- Les 19 nouvelles régressions incluent des transactions concurrentes ordonnées, une matrice de 144 combinaisons SQL, les permissions, l'isolation, le refus de transfert de compte, le journal immuable et le rollback après échec de l'audit.
- La campagne historique du lot 6 attestait `253312a` : 176 défauts détectés et trois contrôles sains. Ses motifs restent applicables au code corrigé, mais cette campagne complète n'est pas revendiquée comme rejouée après la reprise.

La [finalisation](lot6-finalization.md) détaille la provenance des documents locaux, la vérification pré-service de V7, le contrat de correction et la réassociation de compte exclue. L'ADR [0023](decisions/0023-personnes-liees-et-vues.md#décisions-ouvertes-pour-le-propriétaire) distingue les exigences validées des arbitrages métier encore ouverts. Le portail reste fermé ; aucun règlement n'est simulé.

Les contrôles du commit final publié sont référencés dans #26. #27 puis #28 ont été fusionnées par merge commit. La CI de `main` sur `7eb7ddb6c268b8ca503c1b6518decaa5397fb88d` a validé 461 tests Java et Containers. Le scénario de reprise applique **V7 → V7_1 → V7_2** sur base vierge, conserve le même historique après arrêt brutal et valide les sommes de contrôle. Les résultats sont référencés dans #28.

## Lot 7 et code consolidé

La [PR #29](https://github.com/SPX-tech/ScolaPay/pull/29) part de `7eb7ddb`. Le moteur, ses migrations et ses tests métier sont conservés. Les changements portent sur la campagne, la qualification des preuves et les statuts documentaires.

### Couverture des invariants

| Invariant | Garanties relues et scénarios exercés | Mutations représentatives |
| --- | --- | --- |
| Exactitude du dû | Somme du journal, soldes, signes, statuts distincts à dû nul, montant négatif refusé | `due-from-issued-amount`, `balance-from-issued-amount`, `db-negative-open`, `status-mix-shown-as-waiver` |
| Échéanciers et catalogue | Montants entiers, somme exacte, dates ordonnées, charge stable et ligne applicable la plus proche | `catalog-installment-total-unchecked`, `catalog-installment-order-unchecked`, `catalog-charge-identity-mutable`, `catalog-nearest-unit-loses-priority` |
| Intégrité et immutabilité | Obligation complète au commit, journal sans modification ni troncature, tarif publié protégé | `db-complete-open`, `db-entries-mutable`, `db-entries-truncate`, `catalog-published-item-mutable` |
| Idempotence | Identité stable dossier/charge, trois voies d'émission concurrentes, rejeu d'opération et audit | `key-includes-catalog-version`, `key-includes-enrollment`, `entry-replay-gone`, `request-replay-gone` |
| Concurrence | Verrou d'obligation dans le service et en base, dix écritures, confirmation simultanée | `no-obligation-lock`, `db-entries-no-lock`, `no-lock-on-designation` |
| Double validation | Cumul brut, pourcentage et seuil XAF, seconde personne distincte, demande et journal figés | `threshold-net-instead-of-gross`, `self-approval-open`, `db-zero-without-second-person`, `db-approved-stale` |
| Isolation | Requêtes contextualisées, permissions, clés étrangères composites et matrice HTTP multi-établissement | `find-across-tenants`, `db-enrollment-fk-simple`, `db-party-other-establishment`, `view-any-account` |
| Confidentialité | Projections par rôle, motifs protégés, éligibilité avant pagination et instantanés cohérents | `responsible-shown-as-debtor`, `pagination-without-link`, `family-list-without-snapshot`, `correction-history-visible-to-cashier` |
| Identité financière | Révision, journal de correction immuable, audit atomique et compte associé immuable | `correction-ignores-revision`, `correction-without-audit`, `correction-account-mutable` |
| Absence d'encaissement fictif | Types SQL et statuts du service, routes réelles sans règlement | `db-settlement-allowed`, `status-mix-shown-as-waiver`, tests `aucunStatutNiEcritureNeSimuleUnReglement` et `aucuneRouteNeModifieNiNeRegleUneObligation` |

### Scénarios historiques retenus

Les 176 motifs du lot 6 et les 15 de sa finalisation s'appliquent au code intégré. La campagne finale conserve 175 des premiers et les 15 derniers. `db-party-mutable` cible exactement la même garde que `correction-without-trace`. Seul ce dernier est retenu avec son test requis. Cinq mutants complètent la couverture du catalogue, soit 195 mutants et trois contrôles.

Les redéfinitions de V6 dans `lot5.mjs` restent nécessaires. Les variantes originales `status-always-open` et `db-reason-optional` de V5 sont remplacées par les statuts et motifs distincts introduits au lot 5. Les variantes `db-negative-open`, `db-entries-no-lock` et `db-settlement-allowed` ciblent la fonction ou contrainte effective de V6. Réintroduire leurs anciennes étapes V5 donnerait un défaut neutralisé par la migration suivante.

Les suites financières, de catalogue, d'isolation et de modularité sont sélectionnées pour les mutants et leurs contrôles. La CI complète sur le même code couvre aussi les notifications, l'anti-abus et les contrôles de mise en service. Le rapport affiche séparément les nombres de tests de ces deux périmètres.

### Qualification des preuves

La revue de l'outillage a trouvé qu'un résultat absent ou dupliqué pouvait passer inaperçu dans la conclusion. Le rapport compare son inventaire au plan de campagne. Une erreur de test sans échec d'assertion ne vaut pas détection. Un test requis doit avoir échoué par assertion, et un contrôle du code intact refuse tout test ignoré. Une compilation échouée reste toujours `NE_COMPILE_PAS`.

La revue suivante de #29 a identifié deux défauts supplémentaires. Le rapport acceptait indifféremment `VERT` et `DETECTE` pour chaque résultat. Le workflow pouvait masquer l'échec du générateur avec son pipeline et acceptait l'absence de conclusion négative. La correction exige `DETECTE` pour chaque mutant et `VERT` pour chaque contrôle. La génération utilise Bash avec propagation des erreurs, puis la conclusion positive exacte est obligatoire.

### Résultats de consolidation

Les preuves suivantes portent sur `1278ca717dbc374b6376904d94a1edf713b216c8`, dérivé du `main` intégré `7eb7ddb`. Les sources du moteur, les tests métier, les migrations et les dépendances sont identiques à cette base.

| Vérification | Résultat | Preuve |
| --- | --- | --- |
| Campagne finale sur PostgreSQL jetable | 195 mutants détectés sur 195, inventaire complet et trois contrôles sains de 276 tests | [Campagne finale](https://github.com/SPX-tech/ScolaPay/actions/runs/42988964544) |
| Tests de qualification du rapport | Cinq tests réussis dans le plan de campagne | [Plan de campagne](https://github.com/SPX-tech/ScolaPay/actions/runs/42988964544) |
| Suite Java complète, compilation, démarrage et surface publique du JAR | 461 tests, zéro échec, erreur ou test ignoré | [CI complète](https://github.com/SPX-tech/ScolaPay/actions/runs/42988964481) |
| Images et vérification de la pile historique | Vert | [Containers](https://github.com/SPX-tech/ScolaPay/actions/runs/42988964500) |
| Régressions du rapport et du workflow avant correction | Six échecs attendus parmi 13 tests, dont les statuts inversés, rapports sans conclusion positive et générateurs en erreur | [Régressions avant correction](https://github.com/SPX-tech/ScolaPay/actions/runs/43026340130) |
| Requalification des 198 artefacts JSON originaux par l'outillage `2bf81a8` | 13 tests d'outillage verts, 195 mutants `DETECTE` et trois contrôles `VERT` de 276 tests | [Requalification corrigée](https://github.com/SPX-tech/ScolaPay/actions/runs/43026773053) |

Le rapport final confirme notamment `size-limit-ignored` détecté par `IssuanceLimitTest.unLotTropGrandEstRefuseEtSeDecoupeParUnite`. Aucun mutant ne reste survivant, inapplicable, non compilable ou sans assertion de détection.

La requalification télécharge les résultats bruts du run `42988964544`, vérifie son commit source `1278ca717dbc374b6376904d94a1edf713b216c8`, puis applique le rapport corrigé. L'artefact conserve séparément le SHA des tests financiers d'origine et celui de l'outillage de requalification. L'inventaire contient exactement les 195 mutants et les trois contrôles attendus. Les artefacts disponibles permettent cette vérification sans relancer la campagne financière.

Les commits ultérieurs de clôture documentaire conservent le code et les campagnes testés. Les contrôles de la tête finale et son SHA figurent dans #29.

La [première campagne](https://github.com/SPX-tech/ScolaPay/actions/runs/42983559011), sur `629feb3`, est non concluante avec 194 détections sur 195 et trois contrôles verts de 275 tests. Le survivant `size-limit-ignored` a révélé l'omission d'`IssuanceLimitTest` dans la sélection de campagne. Ce test existant passe dans la suite complète de 461 tests. La sélection corrigée l'inclut et le mutant exige désormais l'échec de son assertion. Les preuves de cette première exécution sont conservées.

### Portée du bilan

| Catégorie | Traitement pour la clôture |
| --- | --- |
| Défaut bloquant | Aucun défaut bloquant du moteur mis en évidence par la revue et les validations finales. L'omission du test de plafond dans la première sélection est corrigée et la campagne complète est concluante |
| Limites conservées | Volumes et latences des grands lots non mesurés, erreurs possibles au délai de verrou, supervision et purge d'archives encore à cadrer, contention et collisions des quotas à mesurer. Ces preuves fonctionnelles ne donnent aucune capacité d'exploitation chiffrée |
| Décisions et évolutions futures | Questions juridiques, accès de l'étudiant sans rôle, détail du responsable, pluralité de redevables, réassociation de compte, parcours d'une école à un seul utilisateur, politique des canaux Families et encaissement réel relèvent de décisions ou chantiers distincts |

**Mission 003 techniquement clôturée pour le périmètre livré, après validation du propriétaire.** #29 est fusionnée au commit `9f540291d2e9c2d0108e9a0249d2cfde69fe6a3e`, avec [CI](https://github.com/SPX-tech/ScolaPay/actions/runs/43031124770) et [Containers](https://github.com/SPX-tech/ScolaPay/actions/runs/43031124951) verts après fusion. Les exigences juridiques, les autorisations familiales et les contrôles de mise en service gardent leur portée. Les expérimentations utilisent des données et bases jetables, avec les transports de test existants.

## Limites restantes

**Non testé**

- **Le volume.** Aucun lot de 5 000 inscriptions n'a été exécuté : le plafond est testé, pas la durée d'une telle transaction. Le réexamen, recalculé sur toute l'année à chaque lecture, n'a pas non plus été mesuré.
- **Deux lots longs en concurrence.** Le test porte sur de petits lots. Au-delà du `lock_timeout` de 5 secondes, le second échouerait avec une erreur. Une erreur, et non une double facturation : la contrainte reste le dernier rempart.

**Non construit**

- **Aucun signal actif** n'avertit qu'il reste des élèves à facturer ou un réexamen à trancher : les listes existent, encore faut-il les consulter. Les interfaces web devront les afficher.
- **Au-delà de 5 000 inscriptions**, l'aperçu et la liste exigent de filtrer par unité académique.
- **Les écoles à un seul utilisateur** ne peuvent pas passer d'opération sensible sans seconde personne : sujet ouvert pour les pilotes, voir [0022](decisions/0022-ajustements-et-reexamen.md#questions-ouvertes).
- Les catégories structurées de remises sont reportées.
- Le remboursement d'un trop-perçu suppose le futur module de paiement.
- La réassociation d'un compte à une personne financière existante reste un parcours sensible distinct. Rôles, vues et corrections de nom/identifiant sont construits au lot 6, voir [la finalisation](lot6-finalization.md).
- L'alerte de l'outbox n'est qu'une ligne de journal, et l'archive des événements n'a pas de purge.

**À valider hors du code**

- **Traitement fiscal et FEN** : à valider avec un conseil fiscal et la DGI avant toute exploitation réelle. L'article 46 de la loi de finances 2026 n'a pas pu être lu : voir [0019](decisions/0019-nature-des-documents-et-numerotation.md).
- **Règles juridiques** : majorité, redevable et conservation restent à confirmer.
