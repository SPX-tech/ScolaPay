# 0010 : modèle d'identité et d'accès

- **Statut** : Acceptée
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet, sur proposition de la Mission 002
- **Remplace** : aucune

## Contexte

Dans NestJS, un `User` appartient à un seul établissement. ScolaPay 2.0 doit représenter une personne liée à plusieurs établissements, un parent dont les enfants sont dans des établissements différents, et un étudiant majeur. Un compte global ne doit pourtant jamais devenir un moyen de contourner un établissement.

## Décision

Quatre notions distinctes, dans quatre structures distinctes :

| Notion | Structure | Question à laquelle elle répond |
| --- | --- | --- |
| Identité | `accounts`, `account_identifiers` | Qui est cette personne ? E-mail ou téléphone, car beaucoup de familles n'ont qu'un téléphone |
| Affiliation | `memberships` | Fait-elle partie du personnel de cet établissement ? |
| Rôle | `membership_roles`, puis `StaffRole` et `Permission` | Que peut-elle y faire ? |
| Accès à un dossier | `record_access_grants` (`THIRD_PARTY` ou `SELF`), avec des droits distincts | Quels dossiers la concernent, et pour quoi faire ? Voir [0013](0013-majorite-et-autorisations-familiales.md) |

Un dossier (`student_records`) appartient à un établissement. Une personne scolarisée dans deux établissements a deux dossiers, reliés le cas échéant au même compte.

### Trois surfaces HTTP, sans passage de l'une à l'autre

| Surface | Qui | Règle |
| --- | --- | --- |
| `/api/v2/establishments/{id}/**` | Personnel | Barrière d'adhésion **avant** tout décodage du corps, puis permission dans le service. Non-membre : 404, identique à un établissement inexistant |
| `/api/v2/me/**` | Tout compte | Aucun identifiant d'établissement en entrée : tout part du compte connecté et de ses liens actifs |
| `/api/v2/platform/**` | Opérateurs | Créer, suspendre, inviter un premier administrateur. **Aucune route vers les dossiers** |

Conséquences voulues : un opérateur n'entre dans aucun établissement sans y être membre, et un parent n'accède jamais à la surface du personnel.

### Autres règles

- **Le jeton prouve seulement qui parle.** Il ne porte ni rôle ni établissement. Le compte et ses droits sont relus à chaque requête, donc une suspension ou une révocation agit immédiatement.
- **Invitations à usage unique.** Seule une empreinte du jeton est stockée. Si un compte existe pour le contact, seul ce compte connecté peut accepter. Un autre compte connecté est refusé.
- **Le dernier administrateur** d'un établissement ne peut être ni retiré ni rétrogradé, sous verrou.
- **La majorité est une règle juridique liée au pays**, évaluée dans le fuseau de l'établissement. Elle n'est pas configurable par lui : voir [0013](0013-majorite-et-autorisations-familiales.md).
- **Défense en profondeur.** Les clés composites `(establishment_id, id)` rendent impossible en base toute référence vers un autre établissement. C'était l'objectif différé de [D-002](../architecture-scolapay-2.md#décision-002--isolation-explicite-par-établissement).
- **La contribution financière** est un droit explicite du lien, distinct de l'accès aux informations académiques.

## Conséquences

- Une route ajoutée sous `/establishments/{id}` est protégée d'office et testée automatiquement par `TenantIsolationTest`, qui lit les routes de l'application.
- Une route `/api/v2` hors de ces surfaces fait échouer ce test : sa protection doit être décidée.

## Limites connues

- **Aucune remise des invitations.** Le jeton est renvoyé à la personne qui invite, à charge pour elle de le transmettre. Un service de notification reste à construire.
- **Pas de vérification du contact** autre que la réception de l'invitation, pas de réinitialisation de mot de passe, pas de jeton de rafraîchissement (validité de 30 minutes).
- **Limitation de débit par compte uniquement.** Après cinq échecs, le compte se verrouille 15 minutes. Un tiers peut donc gêner un compte connu, et rien ne limite encore par adresse IP.
- **Rôles fixés dans le code**, identiques pour tous les établissements.
- **Signature HS256** à secret partagé, adaptée à un seul service. Un autre service validateur demandera une signature asymétrique.

## Questions ouvertes

- Mode d'authentification du portail familles : jeton porteur ou cookie.
