# Finalisation du lot 6 de la Mission 003

Le périmètre du lot 6 a été validé par le propriétaire puis intégré par #26 au commit `0164876`. Les choix restrictifs du code livré sont conservés et les questions métier de l'ADR 0023 restent ouvertes. Ce document conserve les preuves de la reprise. La consolidation après #27 et #28 relève du [lot 7](mission-003-validation.md#lot-7-et-code-consolidé).

La reprise part du commit publié `253312a2f76904ab673b0de68f483fec0313dd49`. Le propriétaire a confié la finalisation à un seul responsable après arrêt du travail concurrent de l'agent initial. Aucun historique Git n'est réécrit.

## Reconstitution des documents

Sept documents de travail ont été retrouvés localement : `core/README.md`, `docs/PROJECT_CONTEXT.md`, les décisions 0013 et 0015, leur index, le cadrage de la Mission 003 et l'ADR 0023 non suivie. Ils ont été copiés dans le worktree de finalisation ; les originaux sont conservés et une sauvegarde locale ignorée par Git garde leurs octets initiaux.

Leur contenu n'a pas été traité comme une décision déjà validée. Le modèle approuvé par 0015 et les demandes de correction du propriétaire du 2026-11-10 justifient les changements publiés. Les questions sur l'étudiant sans rôle, plusieurs redevables, le niveau de détail du responsable et la validité juridique d'un engagement restent identifiées dans 0023. Les résultats manquants de l'ancien brouillon sont remplacés par des preuves CI explicites.

## Vérification préalable de V7

Avant modification : #26 ouverte et non fusionnée, `main` sur `c28862c` avec V1 à V6 uniquement, aucun déploiement GitHub recensé, et aucune instance Core en service selon le contexte et la confirmation du propriétaire.

Ce contrôle ne constitue pas un audit de bases externes : aucune base persistante n'est contactée. L'autorisation de corriger V7 s'applique à sa première version pré-service. V1 à V6 restent inchangées. Toute migration déjà appliquée sur une base persistante demanderait une correction additive, sans réécriture, `repair` ou `outOfOrder`.

L'ordre futur reste **V7 → V7_1 → V7_2**. Ce chantier ne fusionne, ne déploie et ne modifie ni #27 ni #28. Leur validation conjointe reste à exécuter lors de l'intégration autorisée.

## Confidentialité avant pagination

Le SQL paginé ne sélectionne que les établissements actifs et les obligations pour lesquelles une désignation est active. Un simple contributeur doit aussi avoir un lien actif au dossier et un dû positif. Redevable et responsable conservent les droits attachés à leur désignation même si leur lien académique a été révoqué.

Les institutions candidates viennent des rôles du compte connecté ; leurs états sont obtenus par `Establishments`. `StudentDirectory.linkedStudentIds(establishmentId, accountId)` fournit les identifiants de liens actifs sans données académiques. Aucun SQL de `billing` ne lit les tables de `schooling` ou d'`establishment`. Des tableaux UUID JDBC sont liés aux paramètres, sans construction de SQL depuis les entrées.

Les tests couvrent une obligation inaccessible A précédant une obligation visible B : le passage du dû de A à zéro ne change ni la première page ni la page d'offset suivant. Un établissement suspendu ne prend pas non plus une place masquée.

## Cohérence des lectures

Les transactions de lecture composites suivantes utilisent `REPEATABLE_READ` :

- Liste et détail des obligations familiales.
- Détail d'une obligation pour le personnel : dû, statut, écritures et décisions.
- Comptage et page des obligations par état de redevable.
- Liste des situations à réexaminer, composée de plusieurs lectures métier.

La liste simple d'obligations et les balances calculent leurs agrégats dans une même instruction SQL et gardent leur traitement existant. Les commandes conservent leurs transactions d'écriture et leurs verrous d'obligation ; une lecture qui y participe voit leurs propres écritures, sans prétendre changer l'isolation de la transaction extérieure.

Les tests retiennent un vrai résultat de dépôt pour faire valider un ajustement par une seconde requête HTTP avant la suite de la lecture. Le spy appelle toujours la vraie méthode ; il sert seulement à ordonner les transactions. Aucun délai arbitraire ne décide si la course s'est produite.

## Corriger une personne sans transférer de droits

`POST /api/v2/establishments/{establishmentId}/billing/parties/{partyId}/corrections` exige `BILLING_PARTIES_MANAGE`.

| Champ | Contrat |
| --- | --- |
| `expectedRevision` | Révision entière lue sur la personne, obligatoire |
| `displayName` | Nouveau nom normalisé, de 2 à 200 caractères |
| `identifier` | Nouvel identifiant de 1 à 100 caractères, ou `null` pour le retirer |
| `reason` | Motif normalisé obligatoire, de 3 à 500 caractères |

Le corps décrit un état complet de ces informations. Le compte, le type et les identifiants de personne et d'établissement ne sont pas des champs corrigibles. Leur modification est refusée dans le JSON et par la base.

Une correction crée une nouvelle révision sous verrou de personne. Une version périmée donne `409 PARTY_CORRECTION_OUTDATED`. Une absence de changement donne `409 PARTY_UNCHANGED`. Le client relit `GET .../billing/parties/{partyId}` avant une nouvelle demande ; un rejeu n'écrit pas une seconde correction.

Le journal conserve anciennes et nouvelles valeurs, motif, auteur et date. Ses mutations et suppressions sont refusées. Une correction sans trace correspondante et une trace sans application effective sont refusées en base. Toute défaillance de l'audit annule les deux écritures. L'audit général ne recopie pas le motif ni les valeurs personnelles.

Le journal est paginé et requiert `BILLING_READ` et `BILLING_REASONS_READ`. Les familles et les caissiers ne reçoivent pas les motifs et anciennes valeurs. Les désignations, références d'engagement, dates, rôles, écritures et montants ne sont pas modifiés par une correction de personne.

## Réassociation de compte : évolution distincte

Le rattachement initial d'un compte à une nouvelle personne reste encadré par un lien actif de l'établissement et ne crée aucun rôle automatiquement. En revanche, rattacher un compte à une personne existante sans compte, le retirer ou le remplacer reste interdit dans le parcours de correction, y compris avec une trace de modification de texte valide.

Un parcours ultérieur devra :

1. Vérifier le nouveau compte et sa possession, sans correspondance déduite du nom ou de l'identifiant corrigé.
2. Présenter les désignations et droits actifs affectés, distinguer les droits financiers des autorisations académiques et traiter les comptes déjà associés à une autre personne.
3. Définir une confirmation renforcée adaptée au transfert de droits, avec motivation, historique et contrôle de concurrence.
4. Fermer les anciens accès et n'ouvrir que les droits explicitement validés pour le nouveau compte, dans une transaction maîtrisée.
5. Tester isolement, unicité, révocation, concurrence, échec et confidentialité. Aucun doublon de personne ne doit servir de contournement.

Le nom et l'identifiant sont des informations administratives d'une même personne ; le mécanisme ne doit pas être utilisé pour substituer une autre entité sous le même identifiant financier. La réassociation complète est volontairement hors de ce lot.

## Validation

La [première exécution de régressions](https://github.com/SPX-tech/ScolaPay/actions/runs/42897808109) observe neuf échecs attendus, sans erreur de compilation ni test ignoré, sur le code initial : pages observables, lectures concurrentes, trou NULL de confirmation et correction de personne absente.

Après correction, [19 nouveaux tests ciblés](https://github.com/SPX-tech/ScolaPay/actions/runs/42900073926) passent, dont une matrice de 144 combinaisons d'insertion et mise à jour. Ils couvrent aussi permissions, version concurrente, refus de réassociation, isolement, confidentialité, journal immuable, trace sans effet et rollback à la défaillance de l'audit.

La campagne historique `lot6` est conservée, avec adaptation de ses motifs aux gardes renforcées. Son applicabilité et celle de la campagne ciblée sont vérifiées dans le workflow de régression. La campagne `lot6-finalization` retire une garantie à la fois, avec un test précis requis pour chaque détection. Une erreur de compilation ne compte jamais comme une détection.

La CI Java complète et la campagne ciblée portent sur `974e9c45eac9b1ed3725cbdeb4b68594d589b713` :

| Vérification | Résultat | Preuve |
| --- | --- | --- |
| Suite Java complète, modularité, compilation, démarrage et surface publique du JAR | 390 tests, aucun échec, erreur ou test ignoré ; JAR vérifié | [CI complète](https://github.com/SPX-tech/ScolaPay/actions/runs/42901332810) |
| Mutations ciblées de confidentialité, instantanés, NULL, autorisation, version, audit et traces | 15 défauts détectés sur 15 ; un contrôle sain de 390 tests | [Campagne ciblée](https://github.com/SPX-tech/ScolaPay/actions/runs/42901353861) |
| Applicabilité des campagnes historique et ciblée sur le code corrigé | Contrôle vert, 176 motifs historiques et 15 nouveaux | [Workflow de régression](https://github.com/SPX-tech/ScolaPay/actions/runs/42901192214) |

Les 176 mutations historiques détectées et trois contrôles sains sur `253312a` restent des preuves antérieures à cette finalisation. Elles ne sont pas additionnées aux 15 mutants ciblés pour revendiquer une nouvelle exécution exhaustive de 191 mutants. La suite complète couvre les lots précédents ; la campagne nouvelle cible les garanties modifiées par la reprise.

Les commits suivants de clôture documentaire ne changent pas le code testé. Le statut des contrôles du commit publié et les résultats définitifs sont consignés dans la PR #26. La validation des trois branches réunies reste distincte de ces preuves du lot 6.

Aucun test lourd, build ou Docker n'est exécuté sur le poste. Aucun runner PC-02 n'étant disponible pour ce dépôt, les workflows GitHub Actions hébergés existants sont utilisés. Aucun VPS ni fournisseur réel n'est sollicité.
