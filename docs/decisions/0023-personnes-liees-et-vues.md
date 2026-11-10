# 0023 : personnes liées à une obligation et vues par rôle

- **Statut** : Proposée pour revue finale. Les principes viennent de [0013](0013-majorite-et-autorisations-familiales.md) et [0015](0015-modele-de-facturation.md). Les corrections de confidentialité, de cohérence transactionnelle et de contraintes, ainsi que la correction tracée des personnes, ont été demandées par le propriétaire le 2026-11-10. Les choix métier encore ouverts sont distingués ci-dessous.
- **Date** : 2026-11-07
- **Finalisation** : 2026-11-10, reprise du commit publié `253312a`, sans réécriture de l'historique Git
- **Implémentation** : en place dans `core/`, derrière le portail fermé par défaut. **Aucun encaissement : un contributeur peut voir ce qu'il aurait à régler, sans pouvoir le régler.**
- **Précise** : [0015](0015-modele-de-facturation.md#personnes-liées-à-une-obligation), [0013](0013-majorite-et-autorisations-familiales.md)

## Exigences

- Le redevable n'est jamais déduit de l'âge de l'étudiant ni d'un lien de parenté.
- Personnes physiques et morales sont représentables, avec ou sans compte.
- Une obligation peut rester sans redevable, ou avec un redevable non confirmé, et cet état reste visible.
- Une seule règle, côté serveur, décide de ce que chacun voit.
- Un rôle financier n'ouvre jamais l'accès académique.

## Personnes et désignations

`billing_parties` décrit une personne : `PERSON` ou `ORGANIZATION`, un nom, un identifiant facultatif, un compte facultatif. Un compte ne s'associe que par un lien existant avec un dossier de l'établissement : le personnel ne peut pas viser un compte quelconque. Un compte correspond à une seule personne par établissement.

`obligation_parties` dit quel rôle une personne tient sur quelle obligation :

| Rôle | Sens | Compte | En cours par obligation |
| --- | --- | --- | --- |
| `CONTRACTUAL_DEBTOR` | Redevable selon l'engagement établi, `UNCONFIRMED` puis `CONFIRMED` avec sa référence | Non requis | Un seul |
| `FINANCIAL_RESPONSIBLE` | Interlocuteur désigné de l'établissement | Non requis | Un seul |
| `VOLUNTARY_CONTRIBUTOR` | Peut contribuer, sans aucune obligation | **Requis, et lié au dossier** | Plusieurs |

**Aucun code ne désigne une personne sans une demande explicite du personnel** : ni l'émission, ni une inscription, ni un lien familial. Une nouvelle charge n'hérite de personne.

Une désignation ne s'efface jamais. Elle se termine, avec un motif, et l'accès se ferme aussitôt. Une confirmation est immuable : pour changer d'engagement, on termine la désignation et on en crée une autre.

### Corrections d'informations d'une personne

Le nom et l'identifiant facultatif se corrigent sur la même personne, sans créer de doublon. `POST .../billing/parties/{partyId}/corrections` exige `BILLING_PARTIES_MANAGE`, un motif et la révision attendue. Le corps décrit le nouvel état complet : `displayName`, `identifier` (nullable pour le retirer), `reason`, `expectedRevision`.

Le service verrouille la personne. Deux corrections concurrentes portant la même révision donnent une création et un conflit `PARTY_CORRECTION_OUTDATED`, sans écrasement. Une requête inchangée donne `PARTY_UNCHANGED`. Un nouvel essai après un succès doit relire la personne ; aucun rejeu aveugle n'est effectué.

`billing_party_corrections` conserve les valeurs précédentes et nouvelles, l'auteur, la date et le motif. Le journal est immuable. Une mise à jour exige sa trace correspondante ; une trace sans application est refusée à la validation de la transaction. Correction, journal et `AuditTrail` sont atomiques. L'audit général ne recopie que l'identifiant de correction et la révision, sans les textes confidentiels.

Le nom courant des personnes apparaît dans la lecture du personnel. Les désignations conservent leurs identifiants, rôles, dates, confirmations et historique ; leurs personnes ne sont pas recréées. Les noms précédents restent consultables dans le journal de correction par les lecteurs habilités.

`GET .../billing/parties/{partyId}` exige `BILLING_READ`. La lecture paginée de `.../corrections` exige aussi `BILLING_REASONS_READ` : un caissier et les familles ne lisent pas ces motifs et anciennes valeurs.

### Compte associé et identité financière

L'identifiant de personne, l'établissement, le type, le compte et les métadonnées de création restent immuables, y compris en SQL. Le parcours de correction ne prend ni `accountId`, ni `grantId`, ni `kind`. Les champs inconnus sont refusés. Corriger le texte `identifier` ne change aucun identifiant d'authentification ni aucun compte.

Rattacher un compte à une personne existante sans compte, en retirer un ou le remplacer pourrait ouvrir ou retirer toutes les vues de ses désignations actives. Ces opérations sont exclues de ce lot : elles nécessitent un parcours sensible distinct avec vérification des comptes, aperçu des droits affectés, traitement de l'unicité, contrôle explicite et audit. Aucun doublon ou rapprochement déduit du nom ne sert de contournement. Le [cadrage de la finalisation](../lot6-finalization.md#réassociation-de-compte-évolution-distincte) décrit cette évolution.

| Route | Permission |
| --- | --- |
| `POST .../billing/parties`, `GET .../billing/parties` | `BILLING_PARTIES_MANAGE` pour créer, `BILLING_READ` pour lire |
| `POST .../billing/obligations/{id}/parties`, `GET` | Idem. Désigner est idempotent |
| `POST .../billing/students/{id}/parties` | Pour toutes les charges de l'année d'un élève. **Une charge tenue par une autre personne est comptée, jamais écrasée** |
| `POST .../billing/obligation-parties/{id}/confirmation`, `.../end` | `BILLING_PARTIES_MANAGE` |
| `GET .../billing/obligations-by-debtor-state?state=NONE\|UNCONFIRMED` | `BILLING_READ` : ce qui reste à régulariser |

`BILLING_PARTIES_MANAGE` revient à l'administrateur et au comptable. Le motif de fin d'une désignation est masqué aux rôles sans `BILLING_REASONS_READ`, comme les autres motifs : voir [0022](0022-ajustements-et-reexamen.md#les-motifs).

## Ce que chacun voit

`GET /api/v2/me/billing/obligations` et `.../{id}`, en lecture seule. **Portail fermé, ces routes n'existent pas.**

`FinancialView` est la seule règle, calculée à chaque lecture. Le rôle le plus large l'emporte. Chaque vue est un type distinct qui ne contient que ses champs : un champ absent ne peut pas fuiter par mégarde, et les tests comparent la liste exacte des champs.

Pour une liste, l'éligibilité est appliquée dans la sélection SQL **avant** `LIMIT/OFFSET` : établissement actif, désignation active et, pour un simple contributeur, lien actif et dû positif. Une obligation inaccessible ne prend aucune place dans une page. Les identifiants des liens sont obtenus via l'API publique de `schooling`, sans lire ses tables depuis `billing`. `FinancialView` décide toujours de la projection et des champs après cette présélection.

La liste et le détail des familles utilisent un instantané `REPEATABLE_READ`, partagé par les appels de modules. Les lectures détaillées du personnel, les comptes/listes par état de redevable et la lecture du réexamen ont été examinés et renforcés de la même façon lorsqu'ils agrègent plusieurs requêtes. Un dû, son statut et les écritures ne proviennent pas de deux états différents du journal. Les commandes d'ajustement conservent leurs verrous et leurs transactions d'écriture ; une réponse construite dans ces transactions voit leurs propres écritures.

| Information | Redevable | Responsable | Contributeur |
| --- | --- | --- | --- |
| Établissement, référence, devise, nom de l'élève | Oui | Oui | Oui |
| Montant restant dû, prochaine échéance | Oui | Oui | **Oui** |
| Libellé de la charge, montant émis | Oui | Oui | Non |
| Échéancier complet | Oui | Oui | Non |
| Statut | Réel | `OPEN` ou `NOTHING_DUE` | Non |
| Remises et corrections | Chaque écriture | **Leur total seulement** | Non |
| Motif d'une remise ou d'une bourse | **Oui** | Non | Non |
| Note interne d'une correction ou d'une annulation | Non | Non | Non |
| Historique | Écritures détaillées | Dates, sans nature ni montant | Non |
| État de son propre engagement | Oui | Sans objet | Sans objet |
| Autres personnes liées | Non | Non | Non |

Un compte sans rôle en cours reçoit un 404, identique à une obligation inexistante, **y compris un parent lié au dossier et le personnel de l'établissement** sur cette surface.

**Le contributeur** ne voit plus une obligation dont rien n'est dû : un solde nul révélerait une remise ou une annulation. Il doit aussi rester lié au dossier : révoquer le lien ferme aussitôt sa vue. **Le redevable et le responsable**, eux, gardent leur vue si leur lien est révoqué : leur rôle tient à leur désignation, pas à ce lien. Seule la fin de la désignation ferme leur vue.

## Accès académique

Un rôle financier n'ouvre jamais le dossier : `GET /api/v2/me/students/{id}` applique toujours [`AcademicAccess`](0013-majorite-et-autorisations-familiales.md).

**Le libellé reste neutre (« Frais scolaires ») dès que cette même règle refuse l'accès au compte.** C'est plus large que le cas prévu par [0015](0015-modele-de-facturation.md#ce-que-chacun-voit), qui ne citait que le majeur sans consentement : un mineur dont le tiers n'a pas d'accès académique et une date de naissance inconnue sont couverts de la même façon. Le code de la charge n'est jamais exposé sur cette surface.

La règle se calcule à chaque lecture : le passage à la majorité rend le libellé neutre le jour même, et le consentement de l'étudiant rend le vrai libellé.

## Retrait de `financial_contributor`

Ce booléen disparaît de `record_access_grants`, de l'API et de `StudentDirectory`. Un lien dit désormais seulement « ce compte est lié à ce dossier », et n'exige plus d'accès académique. Il ne donne à lui seul que le nom de l'élève et de l'établissement.

**La migration `V7` s'arrête si un lien actif porte encore ce droit.** Aucune instance de `core/` n'étant déployée, il n'y a rien à transformer, et transformer à l'aveugle aurait pu créer des droits financiers que personne n'a décidés.

V7 est révisée avant sa première mise en service, sur autorisation explicite du propriétaire. Le contrôle de reprise a confirmé que #26 n'était pas fusionnée, que `main` (`c28862c`) ne contenait que V1 à V6, qu'aucun déploiement GitHub n'était recensé et que le contexte du projet n'indiquait aucune instance Core en service. Il s'appuie aussi sur la confirmation du propriétaire ; aucune base persistante n'a été contactée ou auditée. V1 à V6 restent inchangées. Cette exception pré-service n'autorise aucune réécriture de migration appliquée : dans ce cas une correction additive serait requise, sans `repair` ni `outOfOrder`.

Les contraintes de confirmation utilisent des prédicats totaux : un non-redevable n'a ni statut ni engagement ni confirmation ; un redevable non confirmé n'a pas de champs de confirmation ; un confirmé porte référence, date et auteur. Les comparaisons nullables ne peuvent pas faire accepter une ligne via un résultat `NULL` de `CHECK`.

## Preuves

Les neuf premières régressions ont été observées en échec sur GitHub Actions, avant correction. Les tests ciblés passent ensuite sur HTTP et PostgreSQL réels, avec ordonnancement déterministe de transactions concurrentes et une matrice de 144 combinaisons de confirmation. Les résultats complets, les SHA et la portée de la campagne de mutations figurent dans [le journal de finalisation](../lot6-finalization.md). Aucun test lourd n'est exécuté sur le poste de développement.

## Décisions ouvertes pour le propriétaire

1. **L'étudiant sans rôle.** Un étudiant majeur qui n'est pas désigné redevable ne voit pas ses propres obligations. C'est la conséquence stricte de « rien n'est déduit », mais cela peut surprendre. Faut-il lui ouvrir une vue, et laquelle ?
2. **Le responsable et les remises.** « Montants de remises : dans le total » a été lu strictement : le responsable voit le total et des dates, ni le détail ni la nature des ajustements, et le statut ne dit pas pourquoi plus rien n'est dû. Est-ce trop restrictif pour un parent qui gère les paiements ?
3. **Plusieurs redevables.** Une obligation n'a qu'un seul redevable en cours. Des parents co-débiteurs ou un partage avec un employeur exigeraient de faire évoluer ce modèle : le cas se présente-t-il dans vos écoles pilotes ?
4. **Ce que constitue un engagement valable.** Le modèle ne retient qu'une référence textuelle. Reste à valider juridiquement, comme le prévoit [0015](0015-modele-de-facturation.md#questions-ouvertes).

## Limites

- **Portail fermé** : ces vues ne servent à personne tant que les conditions de [0017](0017-controles-de-mise-en-service.md) ne sont pas remplies.
- **La réassociation de compte et le changement de type** ne sont pas implémentés. Ils restent interdits dans le parcours de correction ; aucune permission de transfert silencieux n'est introduite.
- **Les autres personnes liées** ne sont visibles sur aucune vue. Le « non par défaut » de [0015](0015-modele-de-facturation.md#ce-que-chacun-voit) n'a pas d'option d'ouverture.
- **Un solde qui baisse** reste observable par un contributeur d'une lecture à l'autre, et peut suggérer une remise sans la nommer. C'est inévitable tant qu'il voit ce qu'il aurait à régler.
- **Un étudiant majeur ne peut pas mettre fin lui-même à un lien** : question déjà ouverte par [0013](0013-majorite-et-autorisations-familiales.md#questions-ouvertes).
- Le périmètre des liens est collecté par compte et établissement avant pagination. Son coût et les lectures de détail restent à mesurer avant les grands établissements ; aucune capacité de charge de production n'est revendiquée.
