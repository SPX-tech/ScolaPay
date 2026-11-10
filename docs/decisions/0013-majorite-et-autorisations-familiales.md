# 0013 : majorité légale et autorisations familiales

- **Statut** : Acceptée. **Les modalités juridiques restent à confirmer avant toute ouverture réelle du portail.**
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Remplace** : la règle « la majorité est un réglage de l'établissement » de la première version de [0010](0010-identite-et-acces.md)

## Contexte

La première version confondait deux questions : l'âge de la majorité, qu'un établissement pouvait régler librement, et ce qu'un parent peut voir, qu'aucune règle ne faisait évoluer à la majorité de l'enfant.

## Décision

### La majorité est une règle juridique

Elle dépend du pays de l'établissement (`legal_majority_rules`), pas de son choix. Un établissement n'a aucun moyen de la modifier : l'API refuse ce champ. Une règle ne change que par migration, donc par une décision relue.

Un établissement ne peut être créé que dans un pays pourvu d'une règle.

### L'accès aux informations et la contribution aux frais sont deux droits distincts

Sur chaque lien entre un tiers et un dossier (`record_access_grants`) :

| Droit | Permet | Ne permet pas |
| --- | --- | --- |
| `academicAccess` | Voir le matricule et les inscriptions d'un élève mineur | Rien pour un élève majeur, voir ci-dessous |
| Le lien lui-même | Voir le nom de l'élève et l'établissement | Voir le dossier, ni aucun montant |

Le droit `financialContributor` a été retiré : un rôle financier se désigne désormais sur chaque obligation, voir [0023](0023-personnes-liees-et-vues.md).

### La règle d'accès académique

Une seule règle, `AcademicAccess`, calculée à chaque lecture :

| Situation de l'élève | Accès académique d'un tiers |
| --- | --- |
| Mineur | Selon `academicAccess`, accordé par l'établissement |
| Majeur | **Uniquement avec son consentement explicite** |
| Date de naissance inconnue | Fermé : la minorité n'est pas établie |

L'étudiant lui-même (lien `SELF`) voit toujours son dossier.

Conséquences de cette règle, vérifiées par `FamilyAccessTest` :

- **Le passage à la majorité prend effet le jour même**, sans traitement différé. L'accès académique du tiers se ferme, sa contribution financière demeure.
- **Seul l'étudiant décide.** Il voit les tiers liés à son dossier, accorde ou retire son consentement à tout moment, et chaque décision est tracée dans le journal d'audit.
- **L'établissement ne peut pas se substituer à lui.** Il ne peut pas inviter un tiers avec accès académique sur le dossier d'un majeur, ni rouvrir un accès fermé.
- **Sans compte de l'étudiant, aucun consentement n'est possible**, et l'accès reste fermé. Le choix se fait en faveur de la protection des données.
- **Un tiers peut contribuer sans voir**, pour un élève mineur comme majeur.

## Limites et conditions

- **Non confirmé juridiquement.** L'âge de 18 ans enregistré pour le Tchad est une valeur provisoire, sans validation par un juriste, et `legally_confirmed` reste à `false`. L'application refuse d'ouvrir le portail tant que ce champ est faux : voir [0017](0017-controles-de-mise-en-service.md).
- **À confirmer avec un juriste avant l'ouverture :** l'âge légal au Tchad et ses éventuelles exceptions, la portée exacte de l'autorité parentale sur les données scolaires, la forme valable du consentement, les règles de protection des données applicables.
- **Représentation légale d'un majeur** (tutelle, par exemple) : non modélisée, et un tuteur légal d'un majeur est aujourd'hui traité comme tout autre tiers.
- **La modification d'un lien existant** passe par sa révocation puis une nouvelle invitation : l'établissement ne peut pas changer les droits d'un lien.
- Ce que voient le redevable, le responsable et le contributeur est décrit dans [0023](0023-personnes-liees-et-vues.md#ce-que-chacun-voit).

## Questions ouvertes

- Un étudiant majeur peut-il mettre fin lui-même à un lien ? Aujourd'hui, seul l'établissement révoque un lien.
