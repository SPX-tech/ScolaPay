# Mission 003 : journal de validation

Moteur de facturation scolaire. Les choix sont dans [0015](decisions/0015-modele-de-facturation.md) à [0021](decisions/0021-obligations-et-journal.md), le périmètre dans le [cadrage](missions/mission-003-billing-engine.md).

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
| 4 | Obligations, échéanciers, journal, émission | 236 | 46 sur 46 | 3 sur 3 | [#24](https://github.com/SPX-tech/ScolaPay/pull/24), en revue |

## Lot 4 en détail

**Commit validé : `f2b89a323f7197fe580e2ec9b39a6bf2c180625c`.** Les commits suivants de la PR ne modifient que la documentation.

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

## Limites restantes

**Non testé**

- **Le volume.** Aucun lot de 5 000 inscriptions n'a été exécuté : le plafond est testé, pas la durée d'une telle transaction.
- **Deux lots longs en concurrence.** Le test porte sur de petits lots. Au-delà du `lock_timeout` de 5 secondes, le second échouerait avec une erreur. Une erreur, et non une double facturation : la contrainte reste le dernier rempart.

**Non construit**

- **Aucun signal actif** n'avertit qu'il reste des élèves à facturer : la liste existe, encore faut-il la consulter. Les interfaces web devront l'afficher.
- **Au-delà de 5 000 inscriptions**, l'aperçu et la liste exigent de filtrer par unité académique.
- Remises, ajustements et réexamen après retrait : le schéma les accepte, aucune route ne les écrit. **Lot 5.**
- Redevable, responsable, contributeur et vues associées. **Lot 6.**
- L'alerte de l'outbox n'est qu'une ligne de journal, et l'archive des événements n'a pas de purge.

**À valider hors du code**

- **Traitement fiscal et FEN** : à valider avec un conseil fiscal et la DGI avant toute exploitation réelle. L'article 46 de la loi de finances 2026 n'a pas pu être lu : voir [0019](decisions/0019-nature-des-documents-et-numerotation.md).
- **Règles juridiques** : majorité, redevable et conservation restent à confirmer.
