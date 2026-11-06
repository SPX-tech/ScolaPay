# 0020 : catalogue de frais et périodes de facturation

- **Statut** : Acceptée
- **Date** : 2026-11-06
- **Implémentation** : en place dans `core/`. **Aucune dette n'existe encore**, l'émission viendra au lot 4.
- **Précise** : [0015](0015-modele-de-facturation.md)

## Exigences

- Une université doit pouvoir facturer par semestre ou par session, avec une inscription qui reste annuelle : **période d'inscription et période de facturation sont deux choses distinctes**.
- Un établissement crée des frais adaptés à son organisation, sans jamais modifier rétroactivement un tarif publié ou appliqué.
- Aucun élève déjà inscrit n'est facturé deux fois, et aucune dette ne naît silencieusement d'un catalogue non publié.
- **Une correction de code ou une modification de tarif n'est jamais une nouvelle charge. Une véritable nouvelle charge reste possible, mais se crée intentionnellement.**

## Modèle

| Table | Rôle |
| --- | --- |
| `billing_periods` | Période de facturation d'une année : `ANNEE`, `T1`, `S1`, `RATTRAPAGE`. Plusieurs peuvent se chevaucher. Ses dates et son code sont figés |
| `fee_definitions` | **L'identité stable d'une charge pour une année**, liée à sa période. Sans montant ni code d'affichage, et immuable |
| `fee_catalog_versions` | Version du catalogue d'une année : `DRAFT`, puis `PUBLISHED`, puis `SUPERSEDED` |
| `fee_items` | Le tarif d'une charge pour une unité académique, ou pour tout l'établissement, avec son code et son libellé |
| `fee_item_installments` | Ses échéances, dont la somme égale le montant |

| Établissement | Exemple |
| --- | --- |
| École | Période `ANNEE`, frais `SCOLARITE` par niveau en trois échéances |
| Université | Périodes `S1` et `S2`, deux charges distinctes aux codes `SCOL-S1` et `SCOL-S2`, sur un niveau ou un programme, et `INSCRIPTION` sur `ANNEE` |

### L'identité d'une charge

**Une charge a une identité propre, `fee_definitions`, distincte de son code et de son libellé.** La protection contre la double facturation repose sur cette identité, et non sur un texte que l'établissement peut corriger.

| Situation | Ce qui se passe |
| --- | --- |
| Corriger un code : `SCOL` devient `SCOLARITE` | Même charge. L'aperçu d'impact indique `RENAMED` et l'ancien code |
| Modifier un tarif ou un libellé | Même charge. L'aperçu indique `CHANGED` |
| Créer une nouvelle charge | Acte explicite : `POST .../billing/fee-definitions`. L'aperçu indique `NEW` |
| Saisir une ligne sans identité existante | Refusé : **un code seul ne crée jamais une charge** |
| Réutiliser un ancien code pour une autre charge | Refusé : ce code désigne pour toute l'année la charge qui l'a publié |
| Déplacer une charge sur une autre période | Impossible : la période appartient à l'identité. Il faut créer une autre charge, intentionnellement |

Une charge porte **un seul code par version publiée**, et un code désigne **une seule charge**. La première règle se vérifie à la publication, car renommer une charge présente sur plusieurs unités passe par un brouillon où ses lignes portent un temps deux codes. La base refuse de publier une version qui romprait l'une de ces règles. Un brouillon abandonné ne retient aucun code, mais l'identité qu'il utilisait reste disponible.

La contrainte d'unicité du [lot 4](0015-modele-de-facturation.md#empêcher-la-double-facturation) portera donc sur `(établissement, dossier, fee_definition_id)`. L'identité appartenant à une seule année, celle-ci n'a pas à y figurer.

Cette identité vaut **pour une année**. D'une année à l'autre, une charge est nouvelle, ce qui est le comportement attendu : la scolarité 2027-2028 n'est pas celle de 2026-2027.

### Règles d'applicabilité

- **Cible** : une unité et ses descendants, ou tout l'établissement.
- **La ligne la plus proche l'emporte** pour une même charge : l'unité, puis ses programmes en remontant, puis l'établissement. Une unité reçoit donc au plus une ligne par charge, sans ambiguïté, et un tarif général peut être précisé pour un niveau.
- **Application** : `AUTOMATIC` pour tous les concernés, `ON_REQUEST` pour un frais facultatif comme la cantine ou le transport. Ce frais n'est jamais imputé d'office.
- **Ces deux critères sont les seuls, par décision du propriétaire.** Les règles sur les fratries, les nouveaux élèves ou les avantages commerciaux **ne passent pas par un frais facultatif** : elles relèvent des remises et ajustements du lot 5, puis éventuellement de futures règles d'éligibilité.
- **Catégorie** : `REGISTRATION`, `TUITION`, `EXAM`, `TRANSPORT`, `CANTEEN`, `OTHER`. Elle fournira le libellé neutre prévu par [0015](0015-modele-de-facturation.md#ce-que-chacun-voit).

### Publication et versions

- Un seul brouillon et une seule version publiée par année, garantis par des index uniques.
- Un nouveau brouillon part du tarif en vigueur.
- Publier fige la version et remplace la précédente, qui reste consultable à l'identique.
- **Une version publiée est immuable en base** : ses lignes et ses échéances refusent toute écriture, et elle ne se supprime pas.
- La base refuse aussi de publier un catalogue vide, ou dont les échéances ne couvrent pas exactement le montant.
- Le service refuse en plus une référence archivée ou une année clôturée.
- Permissions : `FEES_MANAGE` pour le comptable et l'administrateur, `FEES_PUBLISH` pour l'administrateur seul. Lecture pour tout le personnel.

### Élèves déjà inscrits

**Publier fixe un prix, cela ne crée aucune dette.** La publication ne publie même aucun événement.

`GET .../fee-catalogs/{id}/impact` montre, sans rien écrire, ce que la version changerait : frais nouveaux, modifiés, inchangés ou retirés, et nombre d'inscrits concernés.

Décisions du propriétaire pour le lot 4 :

| Situation | Comportement |
| --- | --- |
| Élèves inscrits avant la publication | **Émission en lot, explicite et confirmée après un aperçu**, jamais déclenchée par la publication |
| Nouvelle inscription | Les obligations qui seront générées sont **visibles avant la confirmation**. Une inscription ne crée pas de dette inattendue pour le personnel |
| Inscription sans tarif publié | Aucune dette. L'inscription reste valable |
| Tarif modifié alors qu'un frais est déjà émis | Rien ne change sur l'obligation émise. Un écart passe par un ajustement autorisé |
| Frais retiré d'une nouvelle version | Rien ne change sur l'obligation émise |

Les émissions sont idempotentes, y compris en concurrence, à la reprise d'un événement ou après une nouvelle publication. Voir [0021](0021-obligations-et-journal.md).

## Montants

Convention de [0012](0012-convention-monetaire.md) : `BIGINT` nommé `*_minor`, chaîne décimale dans le JSON, devise figée sur la version. **Aucun arrondi n'existe à ce stade** : les échéances sont saisies en montants exacts, et leur somme est vérifiée.

## Volontairement non construit

- **Autres critères d'applicabilité** : voir ci-dessus, ils relèvent des remises et ajustements.
- **Pénalités et remises** : les remises relèvent du lot 5, les pénalités sont exclues.
- **Traitement fiscal** : voir [0019](0019-nature-des-documents-et-numerotation.md). Une catégorie n'a aucune portée fiscale.
- **Contenance d'une période dans l'année** : non imposée, car une session de rattrapage peut déborder de l'année académique.

## Preuves

`FeeCatalogTest` passe par la vraie API, et écrit directement en base pour les garanties de PostgreSQL. Tous les défauts injectés un à un sont détectés, y compris la suppression du verrou de la base pendant une publication, qui survivait d'abord parce que le service verrouille aussi : un test sur deux connexions directes la couvre désormais.
