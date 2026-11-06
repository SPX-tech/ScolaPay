# ScolaPay : contexte du projet

Référence stratégique et technique du projet, lisible sans l'historique des conversations. En cas de désaccord entre ce document et le code, **le code prime** : corriger le document.

**Dernière mise à jour : 2026-11-06.** Le statut des fonctionnalités décrit le code du dépôt. **Aucune instance de ScolaPay n'est en service** : « implémenté » ne signifie pas « déployé ».

## État du projet en bref

| Sujet | État |
| --- | --- |
| Mission 001 (socle et durcissement financier) | **Finalisée**, PR #1 et #2 fusionnées dans `main` le 2026-11-06 |
| Ancien déploiement | **Retiré** le 2026-11-06. Il ne contenait qu'un établissement de démonstration |
| Données de l'ancienne instance | **Archivées**, chiffrées, hors du serveur et hors du dépôt, avec restauration et relecture vérifiées. La clé est conservée séparément |
| Mission 002 (fondation Java) | **Finalisée** : identités, établissements, inscriptions et liens familiaux dans `core/`. Testée en CI, jamais déployée |
| Facturation, paiements, portail familles | **Pas commencés** |
| Mission 003 (moteur de facturation) | **En cadrage**, aucun code. Voir le [cadrage](missions/mission-003-billing-engine.md) et ses décisions attendues |
| Android | **Hors de la feuille de route active**, code conservé comme référence ([0014](decisions/0014-android-hors-feuille-de-route.md)) |
| Ordre de construction | Backend Java, puis interfaces web. L'APK viendra une fois les parcours métier et financiers stabilisés |

## Vision

ScolaPay veut devenir une plateforme financière scolaire qui connecte les établissements, les parents et les étudiants, d'abord au Tchad (XAF, fuseau `Africa/Ndjamena`).

**Deux backends coexistent dans le dépôt.** `backend/` (NestJS) porte l'outil historique du personnel, centré sur l'encaissement cash, et sert désormais de référence. `core/` (Java) est le nouveau backend et concentre désormais tout l'effort : il modélise déjà les familles, mais aucune interface ne l'utilise encore. Aucun des deux n'est déployé.

## Lire les statuts

| Statut | Signification |
| --- | --- |
| **Vérifié** | Implémenté et couvert par la CI |
| **Non activé** | Le code existe mais ne doit ni ne peut traiter d'argent réel en production |
| **Prévu** | Décision prise, rien n'est implémenté |

## État réel

### Nouveau backend : `core/`

| Domaine | Statut | Ce qui existe | Preuve |
| --- | --- | --- | --- |
| Identité globale | Vérifié | Compte unique, e-mail ou téléphone, connexion, verrouillage après 5 échecs, jetons révocables | `identity`, `SecurityHardeningTest` |
| Invitations | Vérifié, remise non construite | Usage unique, empreinte seule stockée, liées au contact. Le jeton est renvoyé à la personne qui invite | `identity`, `SecurityHardeningTest` |
| Établissements | Vérifié | Création par un opérateur, type, fuseau, devise, pays, suspension | `establishment` |
| Personnel et permissions | Vérifié | Adhésion par établissement, rôles multiples, dernier administrateur protégé | `establishment`, `TenantIsolationTest` |
| Isolation entre établissements | Vérifié | Barrière d'adhésion, 404 aux non-membres, clés composites en base | `TenantIsolationTest`, `DatabaseGuardsTest` |
| Années, dossiers, inscriptions | Vérifié | Inscription active unique par année, historique immuable | `schooling`, `ConcurrencyTest` |
| Liens familiaux | Vérifié | Liens révocables entre compte et dossier, plusieurs enfants et établissements. Accès académique et contribution financière séparés | `schooling`, `FoundationJourneyTest`, `FamilyAccessTest` |
| Majorité et consentement | Vérifié, modalités juridiques à confirmer | Âge légal par pays, non configurable. Un majeur contrôle l'accès académique des tiers | `AcademicAccess`, `FamilyAccessTest`, [0013](decisions/0013-majorite-et-autorisations-familiales.md) |
| Audit | Vérifié | Immuable par trigger, écrit dans la transaction | `audit`, `DatabaseGuardsTest` |
| Frontière vers la facturation | Vérifié, sans consommateur | `StudentDirectory`, événements `StudentEnrolled` et `EnrollmentEnded`, contributeurs financiers | `schooling` |
| Facturation | En cadrage | Rien : modèle proposé, décisions attendues | [Cadrage](missions/mission-003-billing-engine.md), [0015](decisions/0015-modele-de-facturation.md) |
| Outbox durable | Proposée | Rien : événements synchrones uniquement | [0016](decisions/0016-outbox-durable.md) |
| Contrôles de mise en service | Principe accepté | **Rien : aucun code n'empêche encore d'ouvrir le portail** | [0017](decisions/0017-controles-de-mise-en-service.md) |
| Encaissement réel, comptabilité | Prévu | Rien | [0006](decisions/0006-backend-java-spring-boot.md) |
| Portail web des familles | Prévu | L'API `/api/v2/me` existe, aucune interface | [0007](decisions/0007-portail-familles-avant-apk.md) |
| Application Android des familles | Hors feuille de route active | Rien. L'application `android/` sert le personnel | [0014](decisions/0014-android-hors-feuille-de-route.md) |
| Services Go de paiement | Envisagé, sans échéance | Aucun code | [0008](decisions/0008-go-services-paiement.md) |

### Code historique : `backend/`, `web/`, `android/`

Conservé comme référence : voir la [transition](decisions/0011-transition-depuis-nestjs.md). NestJS n'est plus modifié et Android est hors de la feuille de route active. Le web et Android utilisent `/api/v1`.

| Domaine | Statut | Ce qui existe | Preuve |
| --- | --- | --- | --- |
| Établissements et isolation | Vérifié | `tenantId` issu du jeton signé, en-tête contradictoire rejeté, classes contrôlées à l'écriture | `common/guards/tenant.guard.ts`, `students.service.ts` |
| Personnel et rôles | Vérifié | Connexion JWT, rôles `TENANT_ADMIN`, `CASHIER`, `ACCOUNTANT`, `AUDITOR` | `modules/auth`, `modules/users` |
| Élèves et classes | Vérifié | CRUD, import, dette et crédit par élève | `modules/students` |
| Encaissement cash | Vérifié | Verrou par élève, idempotence, reçu avec empreinte et QR, outbox et audit transactionnels | `modules/ledger/ledger.service.ts` |
| Annulation | Vérifié | Cash uniquement, refusée si un mouvement ou une modification de solde a suivi | `modules/transactions/transactions.service.ts` |
| Rapports | Vérifié | Tableau de bord, encaissements par jour et créances, dans le fuseau de l'établissement | `modules/dashboard`, `modules/ledger/dashboard.service.ts` |
| Notifications SMS | Vérifié, envoi réel non testé | Outbox avec relance, transport Africa's Talking, simulé sans clé. La CI n'envoie aucun SMS | `modules/outbox`, `modules/notifications` |
| Dashboard web | Vérifié | Next.js 16, toutes les pages ci-dessus, trésorerie, tests navigateur à 360, 768 et 1440 px | `web/`, `web/e2e/` |
| Application Android du personnel | Vérifiée au 2026-11-06, plus à chaque PR | Kotlin/Compose : connexion, élèves, encaissement, transactions, rapports, utilisateurs. Contrôlée seulement si `android/` change | `android/`, workflow `android-legacy.yml` |
| Paiements mobiles | Non activé | Initiation, webhook, crédit du compte virtuel, mais `PaymentProvider` désactivé par défaut | `modules/payment`, `modules/ledger` |
| Compte virtuel et retraits | Non activé | Grand livre immuable, verrou, cycle `PENDING` vers `COMPLETED` ou `FAILED`. Sans prestataire, tout virement échoue et libère les fonds | `modules/finance` |
| Connecteur Kadryza | Non activé | Squelette sans contrat d'API, refusé en production | `kadryza-payment.provider.ts` |

Les chemins `modules/` et `common/` sont relatifs à `backend/src/`. Les modules Java sont sous `core/src/main/java/com/scolapay/core/`, leurs tests sous `core/src/test/`.

## Architecture

### Nouveau backend : `core/`

```text
Portail familles (prévu)     ─┐
                              ├─ API Spring Boot /api/v2 ─ PostgreSQL 18
Dashboard Next.js (à porter) ─┘    monolithe modulaire

shared ← audit ← identity ← establishment ← schooling ← billing (prévu)
```

- **Java 25, Spring Boot 4.1, Spring Modulith 2.1, SQL explicite avec `JdbcClient`, Flyway.** Choix et motifs dans [0009](decisions/0009-fondation-technique-core.md).
- **Modèle d'identité** : identité, affiliation, rôle et accès à un dossier sont quatre notions séparées, et trois surfaces HTTP étanches. Voir [0010](decisions/0010-identite-et-acces.md).
- **Modèle de données** : [`V1__foundation.sql`](../core/src/main/resources/db/migration/V1__foundation.sql). Démarrage et conventions dans [`core/README.md`](../core/README.md).

### Code historique

```text
Web Next.js 16 ─┐
                ├─ Nginx ─ API NestJS 12 /api/v1 ─ PostgreSQL 16
Android Compose ┘            monolithe modulaire, Prisma 7
```

- **Node 24**, TypeScript 6 côté backend et 5.9 côté web, React 19, Kotlin 2.4, AGP 9.3. Détail dans le [journal de la Mission 001](mission-001-validation.md).
- **Déploiement par `docker compose`**, plus utilisé depuis le retrait de l'ancienne instance. Une seule instance d'API est supposée (voir les risques).
- **Modèle de données** : [`backend/prisma/schema.prisma`](../backend/prisma/schema.prisma). Les deux backends ne partagent aucune table.

### Décisions validées, non encore construites

| Élément | Décision | Référence |
| --- | --- | --- |
| Facturation (Mission 003) | Frais, obligations, échéances, remises et ajustements, en module Java au-dessus de `schooling`, indépendant de l'encaissement réel. Redevable, responsable et contributeur sont distincts | [0015](decisions/0015-modele-de-facturation.md), proposée |
| Outbox | Durable, avant les premiers événements asynchrones critiques | [0016](decisions/0016-outbox-durable.md), proposée |
| Ouverture du portail | L'application refuse de démarrer tant que les règles juridiques ne sont pas confirmées | [0017](decisions/0017-controles-de-mise-en-service.md), non implémentée |
| Montants | Entiers dans l'unité minimale ISO : francs entiers pour le XAF. Conversions de prestataire dans leurs connecteurs | [0012](decisions/0012-convention-monetaire.md) |
| Web | Next.js/TypeScript conservé, écrans portés vers `/api/v2` au fil des domaines | [0011](decisions/0011-transition-depuis-nestjs.md) |
| Familles | Véritable site web d'abord. L'APK vient dans une phase ultérieure | [0007](decisions/0007-portail-familles-avant-apk.md), [0014](decisions/0014-android-hors-feuille-de-route.md) |
| Services financiers | Go envisagé, notamment pour les intégrations de prestataires, sans introduction immédiate | [0008](decisions/0008-go-services-paiement.md) |

**Règles :** NestJS n'est plus modifié, les sources de vérité financières ne sont jamais dupliquées, et facturer n'est pas encaisser : aucun paiement fictif ne rend une obligation réglée.

## Invariants financiers

À préserver quel que soit le langage ou le framework. **Ils s'imposent aux futurs modules Java.** `core/` ne manipule encore aucun montant : aujourd'hui, seuls le 5 et le principe d'immuabilité y sont en vigueur. Les autres sont exercés sur PostgreSQL réel par [`backend/test/financial-integrity.e2e-spec.ts`](../backend/test/financial-integrity.e2e-spec.ts), qui décrit le comportement à reproduire.

1. **Solde sérialisé.** Toute écriture de `debtCents` ou `creditCents` lit le solde sous le verrou de ligne de l'élève (`lockStudentBalance` côté NestJS), dans la transaction de l'écriture.
2. **Atomicité.** Mouvement, outbox et audit d'un encaissement, d'une confirmation ou d'une annulation sont validés ensemble.
3. **Ordre de verrouillage.** Élève, puis compte virtuel. Ne jamais l'inverser.
4. **Une seule comptabilisation.** Clé d'idempotence unique par établissement, confirmation mobile gardée par le statut `PENDING`, référence de grand livre unique.
5. **Établissement explicite.** Toute ressource est cherchée avec son établissement, y compris les références entre ressources. Dans `core/`, les clés composites le garantissent aussi en base.
6. **Grand livre immuable.** Un trigger PostgreSQL interdit la modification et la suppression de `ledger_entries`.
7. **Cash et numérique séparés.** Le cash ne crédite pas le compte virtuel. Un paiement numérique ne s'annule pas sans remboursement réel.
8. **Fermé par défaut.** Sans configuration, aucun prestataire n'est actif. `mock` et `kadryza` n'existent qu'avec `NODE_ENV` à `development` ou `test`. Une valeur inconnue empêche le démarrage.
9. **Webhook.** Signature vérifiée sur le corps brut, puis contenu validé strictement, avant toute mutation.
10. **Montants exacts.** Jamais de flottant pour un montant stocké. Dans `core/` : entiers dans l'unité minimale ISO de la devise, voir [0012](decisions/0012-convention-monetaire.md). `Cents` est une convention propre au code NestJS, voir [D-003](architecture-scolapay-2.md#décision-003--conserver-les-unités-financières-historiques).

## Décisions

L'index complet est dans [`docs/decisions/`](decisions/README.md). À retenir :

- **D-001 à D-005** ([détail](architecture-scolapay-2.md)) : monolithe modulaire, isolation par établissement, unités historiques, prestataires fermés par défaut, migrations séparées du démarrage.
- **0006 et 0007**, acceptées : nouveau backend en Java/Spring Boot, construit directement, avec le code NestJS conservé comme référence, puis portail web des familles avant l'application Android.
- **0008**, orientation validée : Go envisagé pour de futurs services financiers, sans introduction immédiate.
- **0009 à 0013**, acceptées : fondation technique, modèle d'identité et d'accès, transition depuis NestJS, convention monétaire, majorité légale et autorisations familiales. Les modalités juridiques de 0013 restent à confirmer.
- **0014**, acceptée : Android hors de la feuille de route active.
- **0015 à 0017**, proposées pour la Mission 003 : modèle de facturation, outbox durable, contrôles de mise en service.

## Priorités actuelles

1. **Mission 003, ScolaPay Billing Engine** : priorité absolue, **en cadrage et sans code**. Elle s'appuie sur `schooling` sans refaire les utilisateurs ni les établissements. La construction attend les [décisions du propriétaire](missions/mission-003-billing-engine.md#décisions-attendues-du-propriétaire).
2. **Interfaces web des établissements, des parents et des étudiants**, sur `/api/v2`, une fois le cœur financier en place.

## Risques connus

Dans `core/`, les limites connues sont listées dans [0010](decisions/0010-identite-et-acces.md#limites-connues). Les plus structurantes :

| Risque | Conséquence | Où |
| --- | --- | --- |
| Invitations non remises | Le jeton transite par la personne qui invite. Un service de notification est nécessaire avant tout usage réel | `core`, `identity` |
| Règles juridiques non confirmées | L'âge légal enregistré pour le Tchad est provisoire, et **aucun code n'empêche encore l'ouverture du portail** | [0013](decisions/0013-majorite-et-autorisations-familiales.md#limites-et-conditions), [0017](decisions/0017-controles-de-mise-en-service.md) |
| Dégradation silencieuse d'Android | Plus vérifié à chaque PR : accepté tant qu'il est hors feuille de route | [0014](decisions/0014-android-hors-feuille-de-route.md) |
| Pas de limitation de débit par adresse | Le verrouillage par compte permet de gêner un compte connu | `core`, `identity` |
| Événements non durables | Publiés de façon synchrone, sans outbox : à résoudre avant la facturation | [0016](decisions/0016-outbox-durable.md) |

Dans le code historique :

| Risque | Conséquence | Où |
| --- | --- | --- |
| Solde modifiable par `PATCH /students/:id` | Change la dette sans `Transaction` : seul l'audit la trace. `DEBT_ADJUSTMENT` et `REFUND` existent dans le schéma mais rien ne les produit | `students.service.ts` |
| Rapports limités au cash | `MOBILE_PAYMENT` n'entre ni dans le rapport d'encaissements ni dans le tableau de bord. À trancher avant d'activer un prestataire | `reports.controller.ts`, `dashboard.service.ts` |
| Contrat Kadryza inconnu | Webhook provisoire : montant et devise non rapprochés, états incertains après un délai réseau non définis | [D-004](architecture-scolapay-2.md#décision-004--prestataires-financiers-fermés-par-défaut) |
| Unités monétaires | `Cents` affiché divisé par 100 pour XAF, sans exposant ISO. Tranché pour le nouveau backend, à ne jamais copier tel quel | [0012](decisions/0012-convention-monetaire.md) |
| Isolation applicative seule | Pas de clé étrangère composite `(tenantId, id)`. Résolu dans `core/`, pas dans `backend/` | [D-002](architecture-scolapay-2.md#décision-002--isolation-explicite-par-établissement) |
| Une seule instance d'API | Le garde du worker outbox est en mémoire : deux instances pourraient traiter le même événement | `outbox-worker.service.ts` |
| Connexion sans limitation de débit | Aucune limite côté API ni Nginx | `modules/auth`, `nginx/` |
| Jeton web dans `localStorage` | Exposé en cas de XSS | `web/src/lib/auth.tsx` |
| Empreintes SHA-256 historiques acceptées | Comptes anciens et jeu de démonstration, non salés. Ne jamais exécuter le seed sur une base réelle | `password.util.ts`, `prisma/seed.ts` |
| Retrait saisi en nombre flottant | Formulaire et route historique convertissent par `Number` | `web/src/app/dashboard/finance/page.tsx` |
| Exploitation non cadrée | HTTPS, sauvegardes restaurées et surveillance ne sont ni définis ni vérifiés par ce dépôt | [Conditions de production](architecture-scolapay-2.md#conditions-avant-une-future-ouverture-en-production) |
| Conformité CEMAC/BEAC | KYC et plafonds non étudiés ; peut contraindre l'architecture des paiements | `implementation_plan.md`, phase 4 |

## Prochaines étapes

**Si le code NestJS devait être remis en service**

Ce serait un nouveau déploiement, avec des secrets neufs :

- Laisser `PAYMENT_PROVIDER` à `disabled` : `mock` et `kadryza` empêchent le démarrage en production.
- Ne pas exécuter le seed : ses comptes de démonstration ont des mots de passe publics.
- Définir HTTPS, les origines, les limites du proxy et une sauvegarde restaurée : voir les [conditions de production](architecture-scolapay-2.md#conditions-avant-une-future-ouverture-en-production).

**Prochain chantier : Mission 003, ScolaPay Billing Engine**

Périmètre, critères d'acceptation et lots dans le [cadrage](missions/mission-003-billing-engine.md). Le premier lot construit l'outbox durable et le contrôle de mise en service. La construction attend six décisions du propriétaire, chacune avec une recommandation.

**Ensuite, sans ordre arrêté**

- Service de notification, pour remettre les invitations par SMS ou e-mail.
- Portail web des familles ([0007](decisions/0007-portail-familles-avant-apk.md)), avec réinitialisation de mot de passe et renouvellement de session. Son ouverture réelle exige la confirmation juridique de [0013](decisions/0013-majorite-et-autorisations-familiales.md).
- Portage de la caisse, des reçus et de la trésorerie vers `core/`.
- Contrat d'un prestataire réel, puis besoins de services dédiés ([0008](decisions/0008-go-services-paiement.md)).

## Vérifier le projet

La CI GitHub (`.github/workflows/ci.yml`) est la source de preuve : `core` avec Testcontainers et démarrage du JAR, backend NestJS avec PostgreSQL jetable, web avec Playwright. Android a son propre workflow, `android-legacy.yml`, qui ne tourne que si son code change. `containers.yml` valide les images et le proxy. Aucun workflow ne déploie, ne lit de secret de production ni n'appelle de prestataire réel.

Le poste de développement (8 Go) est réservé aux éditions légères. Les commandes locales figurent dans le [README](../README.md).

Derniers résultats : 86 tests pour `core/`, et 23 mutations toutes détectées ([Mission 002](mission-002-validation.md)) ; 190 tests unitaires et 35 E2E pour le backend NestJS, 47 tests Playwright, compilation et lancement Android ([Mission 001](mission-001-validation.md#résultats-finaux)).

## Carte de la documentation

| Document | Rôle |
| --- | --- |
| `docs/PROJECT_CONTEXT.md` | Ce document : état et orientation |
| [`docs/decisions/`](decisions/README.md) | Décisions majeures |
| [`docs/architecture-scolapay-2.md`](architecture-scolapay-2.md) | D-001 à D-005 et conditions de production |
| [`docs/mission-001-validation.md`](mission-001-validation.md) | Socle historique : versions et résultats |
| [`docs/mission-002-validation.md`](mission-002-validation.md) | Fondation Java : versions, résultats et limites |
| [`core/README.md`](../core/README.md) | Démarrer le backend Java, ajouter un module |
| [`docs/missions/mission-003-billing-engine.md`](missions/mission-003-billing-engine.md) | Cadrage du moteur de facturation |
| [`README.md`](../README.md) | Démarrage et déploiement |
| [`AGENTS.md`](../AGENTS.md) | Règles pour les agents de développement |
| [`implementation_plan.md`](../implementation_plan.md) | Historique arrêté au 2026-06-26, à ne pas prendre pour l'état actuel |

## Maintenir ce document

Il se met à jour **dans la PR qui change la réalité qu'il décrit**, ce que rappelle le [modèle de PR](../.github/pull_request_template.md).

- Fonctionnalité livrée ou retirée : tableau « État réel », avec sa preuve.
- Risque découvert ou levé : « Risques connus ».
- Décision majeure : nouvelle entrée dans `docs/decisions/`, puis ligne ici.
- Mission terminée : « Priorités actuelles », « Prochaines étapes » et date en tête.

Rester factuel, concis et vérifiable. Une ligne sans preuve dans le dépôt est une hypothèse et se présente comme telle. Ce dépôt est public : pas de secret, d'adresse de serveur ni de détail d'exposition de la production.
