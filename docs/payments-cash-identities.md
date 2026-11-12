# Identification et autorisation nominative de restitution

Cette tranche prolonge #33, intégrée dans `main` au commit `7b38965`. Les principes A05 sont validés, ainsi que la déclaration signée pour les sources anciennes et le justificatif papier signé pour la future remise. La [0031](decisions/0031-identification-et-autorisation-de-restitution.md) distingue ces décisions du code livré et des modalités encore ouvertes d'A06 à A08.

## Capacités effectivement construites

| Capacité | État de cette tranche |
| --- | --- |
| Remettant identifié à la nouvelle réception, sans compte obligatoire | Construit, données privées chiffrées et audit lié dans la transaction |
| Déclaration signée d'identité d'une source ancienne et examen indépendant | Construit, source initiale conservée et budget inchangé |
| Rapprochement nominatif préparatoire et nouvelle autorisation indépendante | Construit pour le seul remettant source ; aucune ancienne approbation transformée |
| Mandat ou droit d'un autre bénéficiaire | Refus explicite ; politique de contrôle à préciser |
| Vérification finale au moment de la remise, justificatif final signé et sortie d'espèces | Non construits, aucune route ou configuration ne permet de les activer |
| Incident de remise incertaine et sa résolution indépendante | Non construit ; politique détaillée à arbitrer et aucune tentative physique activable |
| Correction d'identité, réassociation, remboursement ou compensation | Fermés, aucun budget de provenance recréé |

Une autorisation nominative n'est pas une restitution comptabilisée. Toutes les réponses de préparation exposent `physicalHandoverAvailable=false`. Les rapprochements et autorisations indiquent `finalIdentityCheckRequired=true`. Le lot 2 reste ouvert.

## Nouvelle réception et compatibilité

Le POST existant `/api/v2/establishments/{tenant}/payments/cash-receptions` exige désormais ce bloc pour une **nouvelle** réception :

```json
{
  "remitter": {
    "fullName": "Nom déclaré du remettant",
    "identificationDetails": "Éléments minimaux permettant de distinguer cette personne",
    "identityRecordedConfirmed": true
  }
}
```

Il s'ajoute aux montants, à la confirmation physique de réception et à la clé d'idempotence. Nom non vide de 160 caractères au plus, éléments minimaux de 300 caractères au plus. Ne pas saisir de copie intégrale de pièce ; aucun téléchargement de document n'est proposé. Aucun `accountId`, élève ou rôle financier n'est déduit de ces données. La liste des pièces ou droits juridiquement admissibles n'est pas inventée par le backend.

Une nouvelle commande sans identité donne 400 et ne crée ni réception partielle ni audit isolé. Même clé, autres montants, identité ou auteur : 409. Le rejeu exact d'un reçu **historique** sans identité reste possible avec son corps d'origine ; y ajouter une identité est refusé. Il faut alors la procédure signée distincte. Le GET de la réception et son rejeu ne diffusent jamais l'identité privée.

Cette modification du contrat d'écriture est intentionnelle pour A05. Les réceptions et règlements déjà enregistrés restent lisibles et leurs faits immuables. Les tests antérieurs fournissent désormais une identité valide pour continuer d'exercer leurs propres contrôles de montants, d'audit et de concurrence.

## Procédure historique

Préfixe `P = /api/v2/establishments/{tenant}/payments`.

1. `POST P/cash-receptions/{source}/remitter-declarations` : `idempotencyKey`, bloc `remitter`, `statementReference`, `signedStatementConfirmed=true`. La déclaration signée est conservée par l'établissement et rapprochée du reçu original. Cette tranche réserve la présentation au déclarant d'origine ; la délégation en cas d'absence reste fermée.
2. `POST P/cash-receptions/{source}/remitter-reviews` par un autre compte habilité : `idempotencyKey`, `originalReceiptMatchedConfirmed=true`, `signedStatementCheckedConfirmed=true`, `identityCheckedConfirmed=true`.

La référence papier est opaque, unique par établissement, de 1 à 80 caractères parmi lettres ASCII, chiffres, point, tiret, souligné et barre oblique ; premier caractère alphanumérique. Il ne s'agit pas d'une URL à récupérer ni d'un emplacement de fichier exploité par le serveur.

Avant examen : `LEGACY_PENDING_REVIEW`. Après contrôle indépendant : `LEGACY_VERIFIED`. Une réception sans déclaration reste `LEGACY_UNIDENTIFIED`. Une nouvelle réception vaut `IDENTIFIED_AT_RECEPTION`. Ces codes de procédure n'arbitrent pas les statuts financiers A08. Les faits historiques de réception et décisions V10 ne sont jamais mis à jour.

## Préparation nominative et nouvelle autorisation

La réservation doit déjà avoir sa décision technique V10 `APPROVED`, qui reste de portée `RESERVATION_ONLY_V1`.

`POST P/excess-requests/{request}/beneficiary-verifications`, par le demandeur :

```json
{
  "idempotencyKey": "0b306141-453c-45f7-86e8-35905349aeb6",
  "beneficiaryIsRemitter": true,
  "identityCheckedConfirmed": true,
  "verificationDetails": "Éléments minimaux de concordance examinés"
}
```

Les détails sont privés, chiffrés, non vides et bornés à 500 caractères. La source doit posséder une identité initiale ou une déclaration historique examinée. Un tiers ou un bénéficiaire fourni par compte, parenté ou obligation est refusé ; les champs inconnus ne sont pas ignorés. Ce rapprochement préparatoire ne remplace pas le contrôle au moment de la future remise.

`POST P/excess-requests/{request}/restitution-authorizations`, par un compte habilité distinct du demandeur :

```json
{
  "idempotencyKey": "579f82b5-19c2-40c2-b7ad-c04e6525eeaf",
  "beneficiaryVerificationId": "240b057b-b4bc-4a94-ac59-939fd700ee2b",
  "beneficiaryReviewedConfirmed": true
}
```

Le fait `NAMED_REMITTER_V1` lie explicitement montant, devise, réception, réservation et rapprochement nominatif. Il ne remplace pas la décision historique, ne libère aucun fonds et constate toujours `restitutedMinor="0"`. Un rapprochement d'une autre réservation ou d'un autre établissement est refusé. Il n'existe pas de choix de montant, de devise ou d'auteur permettant d'altérer ce rattachement.

## Relecture, permissions et confidentialité

201 crée un nouveau fait, 200 retrouve la même intention. Les clés permanentes sont propres à l'établissement et à la famille d'opération. Nouvelle clé pour modifier un fait déjà arrêté : conflit, sans remplacement silencieux. Les lectures restent possibles après désactivation des écritures, avec permissions actives et clé de chiffrement conservée.

| GET sous P | Résultat |
| --- | --- |
| `/cash-receptions/{source}/remitter` | Identité privée, état de procédure et examen éventuel |
| `/remitter-declarations/by-key/{key}` | Identification retrouvée après réponse inconnue, avec état courant |
| `/remitter-reviews/by-key/{key}` | Preuve d'examen historique |
| `/beneficiary-verifications/{id}` ou `/by-key/{key}` | Rapprochement privé |
| `/restitution-authorizations/{id}` ou `/by-key/{key}` | Preuve immuable d'autorisation nominative |

La demande de restitution conserve sa permission de caisse et son auteur. Comptable, administrateur et directeur peuvent examiner et autoriser, sans auto-approbation. Auditeur : lecture seule. La lecture d'identité est limitée au déclarant de la réception et aux rôles de contrôle habilités `CASH_IDENTITY_READ` ; un autre caissier est refusé. Un non-membre, opérateur sans adhésion ou lien familial n'y accède pas. Les familles ne reçoivent aucun champ supplémentaire.

Les références techniques sont `CI-`, `CIR-`, `CBV-` et `CRA-` suivies d'un UUID. Elles ne sont pas des numéros fiscaux. L'audit général ne contient que les identifiants et références techniques ; noms, détails d'identification et références de déclaration signée restent hors de cet audit.

## Configuration, SQL et limites

`SCOLAPAY_CASH_IDENTITIES_ENABLED=false` par défaut ferme les écritures de procédure historique, rapprochement et autorisation. Ce flag ne dispense jamais une nouvelle réception d'A05. Activer `SCOLAPAY_CASH_RECEPTIONS_ENABLED` ou les nouvelles procédures exige `SCOLAPAY_CASH_IDENTITY_ENCRYPTION_KEY`, une clé dédiée de 32 octets encodée en Base64. Aucun secret réel n'est fourni dans le dépôt. Clé absente ou invalide : démarrage refusé. Donnée indéchiffrable : 503 générique, sans texte privé ni cause cryptographique exposée.

V11 est additive. Les anciens reçus sont explicitement classés non identifiés, jamais vérifiés automatiquement ; les nouveaux INSERT exigent une identité et son audit au commit. Les tables de constat, d'examen, de rapprochement et d'autorisation sont immuables, y compris contre DELETE et TRUNCATE. Les gardes imposent source, établissement, acteurs distincts, confirmations non NULL, références, unicité et audit. Elles ne peuvent constater une signature ou les billets : elles protègent les déclarations auditées.

Les écritures partagent le verrou de réception `READ COMMITTED` ; les lectures composées restent en `REPEATABLE READ`. Les budgets F/H/Q et gardes V8 à V10 restent inchangés. Ni nouvelle identité, référence ou autorisation ne peut rendre éligible une fraction anciennement affectée. Toute réservation approuvée reste immobilisée, sans expiration ni libération automatique.

La future remise doit réunir le justificatif papier signé validé, son contrôle, une tentative durable avant geste, la sortie monétaire atomique, les reprises et une résolution indépendante des incidents. Le numérique signé, les contrôles de mandats ou droits des tiers et les règles de rectification ne sont pas construits ici. A03 et A04 restent nécessaires avant pilote. Aucun usage financier réel, déploiement, migration persistante, fournisseur, frontend ou ouverture de Families.

Les résultats exacts des campagnes sur PostgreSQL jetable et de la tête finale figurent dans la PR. Les tests ne prétendent pas démontrer une remise physique exactement une fois ni un parcours d'incident qui n'existe pas encore.
