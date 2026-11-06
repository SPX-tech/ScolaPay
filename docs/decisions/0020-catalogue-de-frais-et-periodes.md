# 0020 : catalogue de frais et périodes de facturation

- **Statut** : Exigences du propriétaire, modèle proposé dans la PR du lot 3 de la Mission 003
- **Date** : 2026-11-06
- **Implémentation** : en place dans `core/`. **Aucune dette n'existe encore**, l'émission viendra au lot 4.
- **Précise** : [0015](0015-modele-de-facturation.md)

## Exigences

- Une université doit pouvoir facturer par semestre ou par session, avec une inscription qui reste annuelle : **période d'inscription et période de facturation sont deux choses distinctes**.
- Un établissement crée des frais adaptés à son organisation, sans jamais modifier rétroactivement un tarif publié ou appliqué.
- Aucun élève déjà inscrit n'est facturé deux fois, et aucune dette ne naît silencieusement d'un catalogue non publié.

## Modèle

| Table | Rôle |
| --- | --- |
| `billing_periods` | Période de facturation d'une année : `ANNEE`, `T1`, `S1`, `RATTRAPAGE`. Plusieurs peuvent se chevaucher. Ses dates et son code sont figés |
| `fee_catalog_versions` | Version du catalogue d'une année : `DRAFT`, puis `PUBLISHED`, puis `SUPERSEDED` |
| `fee_items` | Un frais pour une unité académique, ou pour tout l'établissement |
| `fee_item_installments` | Ses échéances, dont la somme égale le montant |

| Établissement | Exemple |
| --- | --- |
| École | Période `ANNEE`, frais `SCOLARITE` par niveau en trois échéances |
| Université | Périodes `S1` et `S2`, frais `SCOL-S1` et `SCOL-S2` sur un niveau ou un programme, et `INSCRIPTION` sur `ANNEE` |

### Le code de frais et sa période

**`fee_code` est l'identité stable d'une charge, et il reste attaché à une seule période pour toute l'année**, d'une version à l'autre. Un semestre a donc son propre code.

C'est ce qui garde valable la contrainte de [0015](0015-modele-de-facturation.md#empêcher-la-double-facturation), `UNIQUE (établissement, dossier, année, fee_code)`. Si un même code pouvait changer de période, déplacer un frais de S1 à S2 le ferait passer pour une nouvelle charge et le facturerait deux fois. La base refuse donc de publier une version qui romprait ce lien. Un brouillon abandonné ne retient rien.

### Règles d'applicabilité

- **Cible** : une unité et ses descendants, ou tout l'établissement.
- **La ligne la plus proche l'emporte** pour un même code : l'unité, puis ses programmes en remontant, puis l'établissement. Une unité reçoit donc au plus une ligne par code, sans ambiguïté, et un tarif général peut être précisé pour un niveau.
- **Application** : `AUTOMATIC` pour tous les concernés, `ON_REQUEST` pour un frais facultatif comme la cantine ou le transport. Ce frais n'est jamais imputé d'office.
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

Pour le lot 4, proposition à valider :

| Situation | Comportement |
| --- | --- |
| Inscription après la publication | Émission automatique des frais `AUTOMATIC` en vigueur, via l'[outbox](0016-outbox-durable.md) |
| Inscription sans tarif publié | Aucune dette. L'inscription reste valable |
| Élèves inscrits avant la publication | **Émission en lot, explicite et confirmée après un aperçu**, jamais déclenchée par la publication |
| Tarif modifié alors qu'un frais est déjà émis | Rien ne change sur l'obligation émise. Un écart passe par un ajustement autorisé |
| Frais retiré d'une nouvelle version | Rien ne change sur l'obligation émise |

Dans tous les cas, la contrainte d'unicité par `fee_code` empêche de facturer deux fois.

## Montants

Convention de [0012](0012-convention-monetaire.md) : `BIGINT` nommé `*_minor`, chaîne décimale dans le JSON, devise figée sur la version. **Aucun arrondi n'existe à ce stade** : les échéances sont saisies en montants exacts, et leur somme est vérifiée.

## Volontairement non construit

- **Autres critères d'applicabilité** : nouvel ou ancien élève, âge, fratrie. Ils s'ajouteraient comme des règles supplémentaires si un besoin réel apparaît.
- **Pénalités et remises** : les remises relèvent du lot 5, les pénalités sont exclues.
- **Traitement fiscal** : voir [0019](0019-nature-des-documents-et-numerotation.md). Une catégorie n'a aucune portée fiscale.
- **Contenance d'une période dans l'année** : non imposée, car une session de rattrapage peut déborder de l'année académique.

## Risques

- Si un établissement renomme un code au lieu de le conserver (`SCOL` devient `SCOLARITE`), le système y voit une nouvelle charge. L'aperçu d'impact la signale `NEW` avec le nombre d'inscrits concernés, et l'émission en lot l'affichera avant confirmation : **la protection est humaine sur ce point**.
