# ScolaPay Core

Nouveau backend de ScolaPay 2.0, en Java et Spring Boot. Il se construit, se teste et s'exécute sans le backend NestJS.

Décisions : [fondation technique](../docs/decisions/0009-fondation-technique-core.md), [identité et accès](../docs/decisions/0010-identite-et-acces.md), [transition](../docs/decisions/0011-transition-depuis-nestjs.md), [convention monétaire](../docs/decisions/0012-convention-monetaire.md), [majorité et autorisations familiales](../docs/decisions/0013-majorite-et-autorisations-familiales.md), [outbox durable](../docs/decisions/0016-outbox-durable.md), [contrôles de mise en service](../docs/decisions/0017-controles-de-mise-en-service.md), [catalogue académique](../docs/decisions/0018-catalogue-academique.md), [catalogue de frais](../docs/decisions/0020-catalogue-de-frais-et-periodes.md), [obligations](../docs/decisions/0021-obligations-et-journal.md), [ajustements et réexamen](../docs/decisions/0022-ajustements-et-reexamen.md).

## Prérequis

Java 25. Docker pour les tests, qui démarrent eux-mêmes un PostgreSQL 18 jetable.

## Tester

```bash
./mvnw verify
```

Les suites sont lourdes pour le poste de développement de 8 Go : elles passent normalement par la CI (job `core`). Les campagnes de défauts injectés s'exécutent aussi à distance : voir [leur mode d'emploi](../.github/mutation/README.md).

## Exécuter en local

```bash
POSTGRES_PASSWORD=<mot-de-passe-local> docker compose up -d

export SCOLAPAY_DB_URL=jdbc:postgresql://127.0.0.1:5433/scolapay
export SCOLAPAY_DB_USERNAME=scolapay
export SCOLAPAY_DB_PASSWORD=<mot-de-passe-local>
export SCOLAPAY_JWT_SECRET=<au-moins-32-octets-aleatoires>
export SCOLAPAY_BOOTSTRAP_OPERATOR_CONTACT=<e-mail-ou-telephone>
export SCOLAPAY_BOOTSTRAP_OPERATOR_PASSWORD=<12-caracteres-minimum>

./mvnw spring-boot:run
```

Sans ces variables, l'application refuse de démarrer. Il n'existe aucun identifiant par défaut. Les variables d'amorçage ne créent le premier opérateur que si aucun n'existe.

Le portail des familles est fermé par défaut : `/me/students` n'existe pas et les invitations familiales sont refusées avec le code `PORTAL_CLOSED`. `SCOLAPAY_PORTAL_ENABLED=true` ne l'ouvre que si toutes les [conditions de mise en service](../docs/decisions/0017-controles-de-mise-en-service.md) sont remplies, sinon l'application refuse de démarrer. Il n'existe aucune option de contournement.

## Notifications et invitations

Les invitations sont remises au contact par une file chiffrée et ne renvoient plus de jeton dans le JSON. La remise est désactivée par défaut : sans configuration, leur création répond `409 INVITATION_CHANNEL_UNAVAILABLE`. Le transport e-mail Resend est optionnel, sans SMS. Le portail reste fermé et un e-mail seul ne satisfait pas ses conditions d'ouverture. Configuration, états, reprises et limites dans [le guide des notifications](../docs/notifications.md), décision [0024](../docs/decisions/0024-notifications-et-remise-des-invitations.md).

En mode désactivé, l'opérateur peut se connecter mais la création du premier établissement est refusée et entièrement annulée, puisque son administrateur doit être invité dans la même transaction. Les parcours de test et de démonstration sans fournisseur passent par les fixtures de `src/test/` sur base jetable, sans exposition des jetons dans le JSON ou les journaux. Aucun mode de démonstration n'est inclus dans le JAR. L'intégration attend #26 ; la [règle de canaux Families et le protocole de validation](../docs/integration-notifications-billing.md) sont documentés, sans activation ni configuration réelle de Resend.

## Parcours de base

Toutes les routes sont sous `/api/v2`. Les erreurs suivent la RFC 9457, avec un champ `code` stable.

| Étape | Requête | Qui |
| --- | --- | --- |
| Se connecter | `POST /auth/login` | Tous |
| Créer un établissement et inviter son administrateur | `POST /platform/establishments` | Opérateur |
| Accepter une invitation | `POST /invitations/accept` | Destinataire |
| Inviter du personnel | `POST /establishments/{id}/invitations` | Administrateur |
| Créer puis ouvrir une année | `POST /establishments/{id}/academic-years`, `PATCH .../{yearId}` | Secrétariat |
| Définir le catalogue : programmes et niveaux | `POST /establishments/{id}/academic-units`, `PATCH .../{unitId}` | Secrétariat |
| Définir les périodes de facturation | `POST /establishments/{id}/billing/periods` | Comptable |
| Créer une charge, intentionnellement | `POST /establishments/{id}/billing/fee-definitions` | Comptable |
| Préparer un tarif | `POST /establishments/{id}/billing/fee-catalogs`, puis `POST .../{versionId}/items` | Comptable |
| Voir son effet sur les inscrits, sans rien écrire | `GET .../fee-catalogs/{versionId}/impact` | Comptable |
| Publier un tarif, qui devient immuable | `POST .../fee-catalogs/{versionId}/publish` | Administrateur |
| Voir les frais d'une unité | `GET /establishments/{id}/billing/applicable-fees` | Personnel |
| Créer un dossier | `POST /establishments/{id}/students` | Secrétariat |
| Voir ce qu'une inscription générerait | `GET /establishments/{id}/billing/enrollment-preview` | Personnel |
| Inscrire sur un niveau, en citant le tarif confirmé pour émettre les frais | `POST /establishments/{id}/students/{studentId}/enrollments` | Secrétariat |
| Voir qui reste à facturer, nominativement | `GET .../billing/pending-issuances` | Comptable, caissier, auditeur |
| Émettre en lot, après aperçu | `GET .../billing/issuance-preview`, puis `POST .../billing/issuance-runs` | Comptable |
| Émettre à l'unité, notamment un frais facultatif | `POST .../billing/obligations` | Comptable |
| Remise, correction, annulation, contrepassée | `POST .../billing/obligations/{id}/discounts`, `.../corrections`, `.../cancellation`, `.../entries/{entryId}/reversal` | Comptable |
| Confirmer ou refuser une opération sensible | `POST .../billing/adjustment-requests/{id}/approval`, `.../rejection` | Une autre personne : comptable, administrateur ou directeur |
| Retirer sa propre demande | `POST .../billing/adjustment-requests/{id}/withdrawal` | Son auteur |
| Voir ce qui demande un réexamen, et trancher | `GET .../billing/reviews`, `POST .../billing/reviews/decisions` | Comptable |
| Consulter les obligations et le solde | `GET .../billing/obligations`, `GET .../billing/students/{studentId}/balances` | Comptable, caissier, auditeur |
| Inviter un tiers : accès académique pour un mineur, contribution financière (portail ouvert) | `POST /establishments/{id}/students/{studentId}/third-party-invitations` | Secrétariat |
| Inviter un étudiant majeur (portail ouvert) | `POST /establishments/{id}/students/{studentId}/self-access-invitations` | Secrétariat |
| Voir ses établissements | `GET /me/affiliations` | Personnel |
| Voir ses enfants ou son dossier (portail ouvert) | `GET /me/students`, `GET /me/students/{studentId}` | Famille |
| Voir les tiers liés à son dossier (portail ouvert) | `GET /me/students/{studentId}/third-parties` | Étudiant majeur |
| Accorder ou retirer l'accès d'un tiers (portail ouvert) | `PATCH /me/students/{studentId}/third-parties/{grantId}` | Étudiant majeur |

## Ajouter un module

1. Créer `com.scolapay.core.<module>` avec un `package-info.java` déclarant `allowedDependencies`.
2. Le paquet racine est l'API publique, le reste va dans `internal`.
3. Toute méthode de dépôt sur une donnée d'établissement exige `establishmentId`, et toute table reçoit `UNIQUE (establishment_id, id)` et des clés composites.
4. Toute route d'établissement vit sous `/api/v2/establishments/{establishmentId}`. Le service appelle `EstablishmentAccess.require` avec la permission voulue.
5. Une mutation sensible écrit dans `AuditTrail`, dans la même transaction.
6. Une migration existante n'est jamais modifiée : en ajouter une nouvelle.
7. Tout montant suit la [convention monétaire](../docs/decisions/0012-convention-monetaire.md) : entier, unité minimale ISO, devise explicite, conversions de prestataire dans leur connecteur.
8. Un traitement asynchrone passe par `@ApplicationModuleListener`, qui le rend durable. Il respecte les [limites de l'outbox](../docs/decisions/0016-outbox-durable.md#limites-à-respecter) : idempotent par contrainte PostgreSQL, sans dépendre de l'ordre d'arrivée.
