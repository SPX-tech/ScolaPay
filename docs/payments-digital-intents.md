# Préparation locale d'une intention numérique

Première tranche de [Mission 005](missions/mission-005-digital-school-payments.md), fondée sur l'[étude Kadryza](mission-005-kadryza-contract-review.md) et la [0032](decisions/0032-intentions-numeriques-et-integration-kadryza.md). Aucun appel externe ou paiement confirmé n'est construit ici.

## Contrat HTTP

Préfixe : `/api/v2/establishments/{establishmentId}/payments/digital-intents`.

| Méthode | Route | Résultat |
| --- | --- | --- |
| POST | Préfixe | Préparation locale : 201 à la création, 200 pour le rejeu exact |
| GET | `/{id}` | Fait historique autorisé, pas lecture actuelle du dû |
| GET | `/by-key/{key}` | Reprise après réponse perdue, même après fermeture des écritures |

```json
{
  "obligationId": "c591961a-ef20-49de-95ee-45c204a3bcfd",
  "idempotencyKey": "f026a161-b160-4265-8287-7ee29b03472a",
  "currency": "XAF",
  "amountMinor": "40000",
  "expectedJournalSize": 1
}
```

`amountMinor` est une chaîne entière positive d'au plus 15 chiffres, sans décimales ou exposant. La devise doit être XAF et correspondre à la source. `expectedJournalSize` est un entier positif d'au plus 18 chiffres, reçu en nombre entier JSON ou en chaîne de chiffres ; une valeur fractionnaire n'est jamais tronquée. Aucun auteur, marchand, environnement, opérateur, téléphone, montant de frais ou statut de paiement ne peut être fourni. Les champs inconnus sont refusés.

La source doit être une obligation existante dans le même établissement, d'une année ouverte, avec un dû positif. Les corrections et annulations actives restent fermées dans ce périmètre conservateur, sans nouvelle règle d'arriéré ou de correction. Le montant ne dépasse pas le dû et la version présentée correspond au journal sous verrou et au commit.

## Réponse et signification

La réponse contient identifiants d'intention, d'établissement et d'obligation, référence `DPI-{uuid}`, même `providerReference`, `originType=SCHOOL_OBLIGATION`, `provider=KADRYZA`, `purpose=PREPARATION_ONLY_V1`, `state=PREPARED`, devise, montant exact, date et compte préparateur.

Les champs `issuedAtPreparationMinor`, `netBilledAtPreparationMinor`, `settledAtPreparationMinor`, `dueAtPreparationMinor` et `journalSizeAtPreparation` sont des instantanés historiques. Ils ne sont jamais présentés comme situation actuelle. Les montants sont des chaînes exactes selon 0012.

Les indicateurs restent toujours :

```json
{
  "paymentConfirmed": false,
  "settlementCreated": false,
  "providerDispatchAvailable": false,
  "requiresRevalidation": true
}
```

Le montant préparé n'est ni reçu, ni réglé, ni réservé. La référence n'est pas un ticket Kadryza, un lien de checkout ou un reçu. Aucun identifiant de session externe n'est inventé. Une remise postérieure ou un autre règlement peut changer le dû sans réécrire ce fait. Avant tout futur envoi, il faudra relire le dû, les droits et la liaison marchande, définir les frais et maîtriser toute session précédente encore incertaine.

## Autorisations et reprise

La capacité dédiée `DIGITAL_PAYMENT_PREPARE` est proposée pour revue à l'administrateur et au comptable de l'établissement. Elle ne dérive pas de `CASH_ALLOCATE`, d'une parenté ou d'une désignation financière familiale. Le préparateur n'est pas déclaré payeur ou redevable. Les lecteurs `BILLING_READ` peuvent relire le fait technique ; aucune donnée de payeur, contact, motif ou secret n'est collectée. Non-membre : 404 ; membre sans capacité : 403. Families reste fermé.

La clé permanente `(établissement, idempotencyKey)` est liée au préparateur, à l'obligation, à la devise, au montant et à la version présentée. Même clé et intention : même fait, sans nouveau journal ou appel réseau. Autre contenu ou auteur : `409 DIGITAL_INTENT_KEY_CONFLICT`, sans renvoyer le fait original. Rejouer une intention historique ne restaure pas le dû ni n'autorise sa transmission.

Version périmée : `409 DIGITAL_INTENT_OUTDATED`. Montant supérieur au dû : `422 AMOUNT_EXCEEDS_DUE`. Devise incompatible : `422 DIGITAL_CURRENCY_UNSUPPORTED`. Contention : `409 DIGITAL_INTENT_BUSY`, conserver la même clé pour la reprise. Une erreur n'efface aucune obligation, réception ou opération déjà enregistrée.

## Intégrité et activation

Le port public `BillingPaymentPreparation` est une lecture verrouillée `MANDATORY`, sans méthode de règlement. Les dépôts Java payments n'accèdent pas aux tables de billing. V12 ajoute les contraintes d'intégration SQL sans réécrire V1 à V11 : origine numérique, source et devise liées, rôle de préparation actif, instantanés exacts, audit au commit, unicité et immutabilité y compris DELETE/TRUNCATE. Les écritures exigent `READ COMMITTED`. Une modification du journal après insertion dans la même transaction est refusée au commit ; une modification dans une transaction ultérieure laisse le fait historique intact.

`SCOLAPAY_DIGITAL_PAYMENT_INTENTS_ENABLED=false` par défaut. Ce flag active seulement les préparations locales sur une base de test ; aucune valeur ne rend possible un appel Kadryza ou un SETTLEMENT numérique dans ce code. Aucune clé cash ou API Kadryza n'est nécessaire à cette préparation. Les contrôles cash, notifications, authentification et Families restent inchangés.

Plusieurs préparations distinctes ne sont pas plusieurs paiements autorisés : elles n'ont aucun effet monétaire ou réservation. Aucune activation ultérieure ne devra les transmettre en masse. Le futur compte marchand par établissement est validé, ses rattachements concrets restent à provisionner et vérifier hors de cette tranche.

## Vérification et limites

Les tests utilisent PostgreSQL jetable et les API réelles, avec permissions, montants, clé/contenu, appels concurrents, ajustements, SQL direct, source modifiée avant commit, audit manquant, fausse confirmation et reprise après interruption entre instances. Les régressions cash et billing sont conservées. Aucun test ne constitue un essai bout en bout de Kadryza ni une preuve de paiement réel. Les comptes et liens exacts des campagnes sont consignés dans la PR.

Prochaine tranche : rattachement marchand et qualification du test contractuel Kadryza, puis ordre explicite de création/reprise de session. La réception HMAC/inbox, la confirmation financière numérique, les règles de frais/excédent et la preuve de versement viennent ensuite avec leurs garanties complètes. Aucun prestataire réel, déploiement, migration persistante, frontend ou ouverture de Families.
