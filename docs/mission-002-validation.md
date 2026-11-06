# Mission 002 : journal de validation

Fondation du backend Java de ScolaPay 2.0. Les choix sont dans [0009](decisions/0009-fondation-technique-core.md), [0010](decisions/0010-identite-et-acces.md) et [0011](decisions/0011-transition-depuis-nestjs.md).

## Versions retenues

Relevées sur Maven Central, Docker Hub et Adoptium le 2026-11-06, en retenant des versions stables publiées, sans préversion.

| Technologie | Version |
| --- | --- |
| Java (Temurin) | 25 LTS |
| Spring Boot | 4.1.2, avec Spring Framework 7.0, Spring Security 7.1, Jackson 3.1, Flyway 12.4 |
| Spring Modulith | 2.1.2 |
| PostgreSQL | 18.6, pilote 42.7 |
| Testcontainers | 2.0.5 |
| Maven | 3.9.16, somme de contrôle figée dans le wrapper |

## Résultats

Exécutés sur la CI de la [PR #17](https://github.com/SPX-tech/ScolaPay/pull/17), job `core`, sans aucune exécution locale.

| Suite | Tests | Ce qu'elle prouve |
| --- | --- | --- |
| `FoundationJourneyTest` | 9 | Parcours complet, parent à deux établissements, rôles par établissement, étudiant majeur, révocation, dernier administrateur |
| `TenantIsolationTest` | 13 | Matrice sur toutes les routes réelles : anonyme, autre établissement, opérateur, parent, membre suspendu ou révoqué. Identifiants étrangers, établissement suspendu |
| `SecurityHardeningTest` | 16 | Connexion et verrouillage, jetons falsifiés, révocation, invitations, validation, plafond de corps, surface Actuator |
| `ConcurrencyTest` | 4 | Inscriptions, acceptations, rétrogradations et échecs de connexion simultanés |
| `DatabaseGuardsTest` | 7 | Clés composites, immuabilité, contraintes, version de PostgreSQL, rejeu des migrations |
| `ModularityTest` | 2 | Frontières de modules vérifiées par Spring Modulith |
| Tests unitaires | 18 | Identifiants, permissions des rôles, règle de majorité |

Les tests d'intégration utilisent le vrai serveur HTTP, la vraie chaîne de sécurité et un vrai PostgreSQL 18.6.

La CI vérifie aussi le JAR lui-même : il refuse de démarrer sans secret ou avec un secret trop court, puis, correctement configuré, il applique ses migrations sur une base vide, répond sur `/actuator/health` et renvoie 401 sur les routes protégées. L'image Docker est construite par le workflow `Containers`, sans être démarrée.

### Les tests savent-ils échouer ?

La première exécution ayant tout passé, 14 garanties ont été cassées volontairement sur une branche jetable, supprimée depuis : [exécution de contrôle](https://github.com/SPX-tech/ScolaPay/actions/runs/42200563613). Résultat : 24 tests sur 68 en échec, et **toutes les mutations détectées**.

| Garantie cassée | Détectée par |
| --- | --- |
| Barrière d'adhésion retirée | Matrice d'isolation |
| Lecture d'un dossier sans filtre d'établissement | Identifiants étrangers, annuaire de facturation |
| Liste de dossiers sans filtre d'établissement | `lesDossiersNeSeMelangentPasDansLesListes` |
| Index d'inscription active unique retiré | Concurrence des inscriptions |
| Clé composite des inscriptions remplacée par une clé simple | `DatabaseGuardsTest` |
| Trigger d'immuabilité de l'audit retiré | `leJournalDAuditEstImmuable` |
| Compte et version du jeton non revérifiés | Compte désactivé, changement de mot de passe |
| Invitation acceptable sans être le bon compte | Détournement d'invitation, parent à deux établissements |
| Révocation ignorée à l'accès direct | `laRevocationCoupeLAccesImmediatementEtGardeLaTrace` |
| Verrouillage de compte retiré | Verrouillage, concurrence des connexions |
| Protection du dernier administrateur retirée | Dernier administrateur, concurrence |
| Audience du jeton non vérifiée | `lesJetonsFalsifiesSontRefuses` |
| Permission d'écriture ajoutée à la caisse | Permissions par rôle, `StaffRoleTest` |
| Dépendance vers un paquet interne d'un autre module | `ModularityTest` |

## Limites de cette validation

- Aucun déploiement, aucun paiement, aucune donnée réelle.
- Aucune remise réelle d'invitation : le jeton est renvoyé à la personne qui invite.
- L'image Docker est construite mais pas démarrée, et `core/compose.yaml` n'est pas exécuté en CI. Seul le JAR l'est.
- La résistance à la charge n'a pas été mesurée. La concurrence testée reste modeste : 2 à 8 requêtes simultanées.
- Les clients web et Android n'utilisent pas encore `/api/v2`.
- Les mutations choisies couvrent les garanties jugées critiques, pas l'ensemble du code.
- Aucun outil de mesure de couverture n'a été mis en place.

Les limites fonctionnelles sont listées dans [0010](decisions/0010-identite-et-acces.md#limites-connues).
