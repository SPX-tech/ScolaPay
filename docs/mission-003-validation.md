# Mission 003 : journal de validation

Moteur de facturation scolaire. Les choix sont dans [0015](decisions/0015-modele-de-facturation.md) à [0022](decisions/0022-ajustements-et-reexamen.md), le périmètre dans le [cadrage](missions/mission-003-billing-engine.md).

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
| 5 | Remises, corrections, annulations, réexamen | 330 | 124 sur 124, dont 41 repris à l'identique du lot 4 | 3 sur 3 | [#25](https://github.com/SPX-tech/ScolaPay/pull/25), en revue |

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
- Redevable, responsable, contributeur et vues associées. **Lot 6.**
- L'alerte de l'outbox n'est qu'une ligne de journal, et l'archive des événements n'a pas de purge.

**À valider hors du code**

- **Traitement fiscal et FEN** : à valider avec un conseil fiscal et la DGI avant toute exploitation réelle. L'article 46 de la loi de finances 2026 n'a pas pu être lu : voir [0019](decisions/0019-nature-des-documents-et-numerotation.md).
- **Règles juridiques** : majorité, redevable et conservation restent à confirmer.
