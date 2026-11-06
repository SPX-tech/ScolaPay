# 0018 : catalogue académique

- **Statut** : Acceptée
- **Date** : 2026-11-06
- **Implémentation** : en place dans `core/`, le [catalogue de frais](0020-catalogue-de-frais-et-periodes.md) s'appuie dessus.

## Contexte

`enrollments.level` était un texte libre : « Terminale D » et « Tle D » auraient été deux niveaux différents, et on ne facture pas de façon fiable sur un texte libre. Le propriétaire demande un catalogue structuré, adapté aux écoles et capable d'évoluer vers les filières, programmes et semestres universitaires, sans sur-ingénierie prématurée.

## Décision

Une seule table arborescente, `academic_units`, propre à chaque établissement.

| Nature | Rôle | On peut s'y inscrire ? | Peut regrouper d'autres unités ? |
| --- | --- | --- | --- |
| `PROGRAM` | Programme, filière, parcours | Non | Oui |
| `LEVEL` | Niveau | Oui | Non |

| Établissement | Exemple |
| --- | --- |
| Lycée | `LEVEL` Sixième, `LEVEL` Terminale D |
| Université | `PROGRAM` Licence Informatique, sous lequel `PROGRAM` Parcours Réseaux, sous lequel `LEVEL` L1 |

Règles :

- **L'identité est figée en base** : le code, la nature et le parent ne changent jamais, et une unité ne se supprime pas. La tarification pourra donc s'y fier. Le parent étant figé et antérieur à l'unité, aucun cycle n'est possible.
- **Renommer ne réécrit pas le passé** : `enrollments.level` garde le libellé du moment de l'inscription.
- **Archiver remplace supprimer** : une unité archivée refuse toute nouvelle inscription et tout nouvel enfant, les inscriptions existantes restent. Un programme ne s'archive pas avant ses enfants, et un enfant n'est pas réactivé avant son programme.
- **Permission** : `ACADEMIC_CATALOG_MANAGE` pour le secrétariat et l'administrateur, lecture pour tout le personnel.
- **Isolation** : clés composites et surface `/establishments/{id}/academic-units`.

## Ce qui n'est volontairement pas construit

- **Semestres dans le catalogue.** Le propriétaire conserve l'inscription annuelle. La facturation par semestre ou par session passe par des [périodes de facturation](0020-catalogue-de-frais-et-periodes.md) indépendantes, et ne change rien à ce modèle.
- **Reprise des inscriptions existantes.** Rien n'est déployé. Une inscription existante ferait échouer la migration, ce qui vaut mieux que d'inventer son rattachement.
- **Classes et groupes**, qui ne servent pas à tarifer.

## Preuves

`AcademicCatalogTest` passe par la vraie API et écrit directement en base pour les garanties de PostgreSQL. Sur 15 défauts injectés un à un, dont la suppression du verrou entre archivage et création, tous sont détectés. Trois exécutions du code intact sont vertes.

## Conséquences

- L'inscription demande désormais `academicUnitId` au lieu d'un texte. **Le contrat `/api/v2` change**, sans conséquence : aucun client ne l'utilise encore.
- `StudentEnrolled` porte `academicUnitId`, point d'entrée de l'émission des obligations.
