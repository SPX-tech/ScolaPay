# ScolaPay : contexte du projet

Référence stratégique et technique du projet, lisible sans l'historique des conversations. En cas de désaccord entre ce document et le code, **le code prime** : corriger le document.

**Dernière mise à jour : 2026-11-11.** Le statut des fonctionnalités décrit le code du dépôt. **Aucune instance de ScolaPay n'est en service** : « implémenté » ne signifie pas « déployé ».

## État du projet en bref

| Sujet | État |
| --- | --- |
| Mission 001 (socle et durcissement financier) | **Finalisée**, PR #1 et #2 fusionnées dans `main` le 2026-11-06 |
| Ancien déploiement | **Retiré** le 2026-11-06. Il ne contenait qu'un établissement de démonstration |
| Données de l'ancienne instance | **Archivées**, chiffrées, hors du serveur et hors du dépôt, avec restauration et relecture vérifiées. La clé est conservée séparément |
| Mission 002 (fondation Java) | **Finalisée** : identités, établissements, inscriptions et liens familiaux dans `core/`. Testée en CI, jamais déployée |
| Facturation, paiements, portail familles | **Facturation Java intégrée jusqu'au lot 6.** Aucun encaissement réel ni portail web construit |
| Mission 003 (moteur de facturation) | **Lots 1 à 6 intégrés, lot 7 en revue de consolidation.** [Cadrage](missions/mission-003-billing-engine.md) et [bilan de validation](mission-003-validation.md) |
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
| Invitations | Vérifié, transport e-mail optionnel | Usage unique, contact lié, jeton retiré du JSON et remis via une file chiffrée. Sans canal disponible, création refusée | `identity`, `NotificationConfidentialityTest`, `NotificationDeliveryTest` |
| Notifications | Vérifié en CI, aucun envoi réel | File chiffrée, listener durable, worker hors transaction, baux, reprises bornées et suivi autorisé. Resend désactivé par défaut. Aucun SMS ni preuve de réception en boîte | `notifications`, `NotificationRecoveryTest`, [0024](decisions/0024-notifications-et-remise-des-invitations.md) |
| Intégration des notifications | #27 intégrée dans `main` au commit `212b6d3` | PostgreSQL vierge, V7 avant V7_1 et contrôle du redémarrage. Principe des canaux accepté, mise en œuvre différée avant Families | [Protocole et décision de canaux](integration-notifications-billing.md), [résultats de #27](https://github.com/SPX-tech/ScolaPay/pull/27) |
| Protection des API publiques d'authentification | Développement et intégration validés, #28 fusionnée | Quotas PostgreSQL partagés, concurrence et corps bornés, proxy explicite. Intégrés après #26 et #27 au commit `7eb7ddb` | `AuthAbuseHttpTest`, `AuthAbuseProxyTest`, [0025](decisions/0025-protection-authentification-publique.md), [résultats de #28](https://github.com/SPX-tech/ScolaPay/pull/28) |
| Établissements | Vérifié | Création par un opérateur, type, fuseau, devise, pays, suspension | `establishment` |
| Personnel et permissions | Vérifié | Adhésion par établissement, rôles multiples, dernier administrateur protégé | `establishment`, `TenantIsolationTest` |
| Isolation entre établissements | Vérifié | Barrière d'adhésion, 404 aux non-membres, clés composites en base | `TenantIsolationTest`, `DatabaseGuardsTest` |
| Années, dossiers, inscriptions | Vérifié | Inscription active unique par année, historique immuable | `schooling`, `ConcurrencyTest` |
| Liens familiaux | Vérifié | Liens révocables entre compte et dossier, plusieurs enfants et établissements. Un lien n'ouvre l'accès académique que selon la règle de majorité, et aucun rôle financier | `schooling`, `FoundationJourneyTest`, `FamilyAccessTest` |
| Majorité et consentement | Vérifié, modalités juridiques à confirmer | Âge légal par pays, non configurable. Un majeur contrôle l'accès académique des tiers | `AcademicAccess`, `FamilyAccessTest`, [0013](decisions/0013-majorite-et-autorisations-familiales.md) |
| Audit | Vérifié | Immuable par trigger, écrit dans la transaction | `audit`, `DatabaseGuardsTest` |
| Frontière vers la facturation | Vérifié, utilisée par la facturation | `StudentDirectory`, événements `StudentEnrolled` et `EnrollmentEnded`, lien d'un compte à un dossier et accès académique du jour | `schooling` |
| Catalogue académique | Vérifié | Programmes et niveaux par établissement, inscription sur un niveau. Sans semestres | `core/`, `AcademicCatalogTest`, [0018](decisions/0018-catalogue-academique.md) |
| Catalogue de frais | Vérifié | Périodes de facturation, identité stable des charges, frais par unité académique, versions et publication immuables, aperçu d'impact | `core/`, `FeeCatalogTest`, [0020](decisions/0020-catalogue-de-frais-et-periodes.md) |
| Obligations et dû | Vérifié | Émission par inscription confirmée, par lot après aperçu ou à l'unité, idempotente. Liste nominative de ce qui reste à facturer. Échéancier et journal immuables, dû calculé. **Aucun encaissement, aucune obligation ne peut être réglée** | `core/`, `ObligationTest`, [0021](decisions/0021-obligations-et-journal.md) |
| Remises, corrections, annulations, réexamen | Vérifié | Écritures distinctes et motivées, seconde personne pour les opérations sensibles, écarts de tarif et retraits signalés pour décision. **Aucune n'est un encaissement** | `core/`, `AdjustmentTest`, `AdjustmentPolicyTest`, `ReasonConfidentialityTest`, `ObligationReviewTest`, [0022](decisions/0022-ajustements-et-reexamen.md) | [Cadrage](missions/mission-003-billing-engine.md), [0015](decisions/0015-modele-de-facturation.md) |
| Outbox durable | Vérifié | Registre Spring Modulith, reprise après arrêt brutal et entre deux instances. **Utilisé par la facturation et la préparation des notifications** | `core/`, `OutboxReliabilityTest`, [0016](decisions/0016-outbox-durable.md) |
| Contrôles de mise en service | Vérifié | Portail fermé par défaut, démarrage refusé si une condition manque. **Il ne peut pas être ouvert aujourd'hui** : règle du Tchad non confirmée, aucun canal SMS ; un e-mail seul ne suffit pas | `core/`, `GoLiveTest`, [0017](decisions/0017-controles-de-mise-en-service.md) |
| Redevables, responsables, contributeurs | Vérifié, intégré dans `main`, portail fermé | Désignations explicites, vues typées, autorisations avant pagination, instantanés cohérents et confirmations NULL cohérentes. Corrections de nom/identifiant motivées et historisées ; compte et identité immuables | `BillingPartyTest`, `FamilyBillingViewTest`, `Lot6FinalizationRegressionTest`, `PartyCorrectionTest`, [0023](decisions/0023-personnes-liees-et-vues.md) |
| Encaissement réel, comptabilité | Prévu | Rien | [0006](decisions/0006-backend-java-spring-boot.md) |
| Portail web des familles | Prévu | L'API `/api/v2/me/students` existe mais reste fermée par défaut, aucune interface | [0007](decisions/0007-portail-familles-avant-apk.md) |
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

shared ← audit ← identity ← establishment ← schooling ← billing
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

### Orientations et conditions de mise en service

| Élément | Décision | Référence |
| --- | --- | --- |
| Facturation (Mission 003) | Frais, obligations, échéances, remises et ajustements, en module Java au-dessus de `schooling`, indépendant de l'encaissement réel. Redevable, responsable et contributeur sont distincts | [0015](decisions/0015-modele-de-facturation.md) |
| Outbox | Durable, avant les premiers événements asynchrones critiques | [0016](decisions/0016-outbox-durable.md) |
| Ouverture du portail | L'application refuse de démarrer tant que les règles juridiques ne sont pas confirmées | [0017](decisions/0017-controles-de-mise-en-service.md) |
| Montants | Entiers dans l'unité minimale ISO : francs entiers pour le XAF. Conversions de prestataire dans leurs connecteurs | [0012](decisions/0012-convention-monetaire.md) |
| Web | Next.js/TypeScript conservé, écrans portés vers `/api/v2` au fil des domaines | [0011](decisions/0011-transition-depuis-nestjs.md) |
| Familles | Véritable site web d'abord. L'APK vient dans une phase ultérieure | [0007](decisions/0007-portail-familles-avant-apk.md), [0014](decisions/0014-android-hors-feuille-de-route.md) |
| Services financiers | Go envisagé, notamment pour les intégrations de prestataires, sans introduction immédiate | [0008](decisions/0008-go-services-paiement.md) |

**Règles :** NestJS n'est plus modifié, les sources de vérité financières ne sont jamais dupliquées, et facturer n'est pas encaisser : aucun paiement fictif ne rend une obligation réglée.

## Invariants financiers

À préserver quel que soit le langage ou le framework. **Ils s'imposent aussi aux modules Java.** `core/` manipule les obligations et leurs ajustements, avec journal immuable, idempotence et clés composites, mais aucun encaissement. Les invariants propres aux paiements restent exercés sur PostgreSQL réel par [`backend/test/financial-integrity.e2e-spec.ts`](../backend/test/financial-integrity.e2e-spec.ts), qui décrit le comportement à reproduire lors de leur construction en Java.

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
- **0015 à 0017**, acceptées : modèle de facturation, outbox durable sur le registre Spring Modulith, contrôles de mise en service.
- **0018**, acceptée : catalogue académique.
- **0019**, principes acceptés, traitement fiscal à valider : obligations, appels de frais, factures fiscales et reçus sont distincts. Aucun document n'est présenté comme fiscalement conforme.
- **0020**, acceptée : catalogue de frais, périodes de facturation indépendantes de l'inscription annuelle, identité stable des charges indépendante des codes.
- **0023**, périmètre du lot 6 validé : rôles financiers explicites, vues typées, droits avant pagination et instantanés cohérents. Correction d'informations sur la même personne, sous révision et journal immuable. Réassociation exclue du lot. Les questions métier de l'ADR restent ouvertes.
- **0022**, acceptée : remise, correction et annulation distinctes, seconde personne au-delà de 25 % ou 100 000 XAF en cumul brut, rôle directeur, motifs masqués aux caissiers dans l'API, réexamen déduit de la réalité.
- **0021**, acceptée : une obligation par dossier et par charge, sans la version du catalogue dans la clé, dû et statut déduits du journal, règlement impossible sans le module de paiement.

- **0024**, socle intégré par #27 : file chiffrée, remise des invitations et reprises externes bornées. Le principe des canaux Families explicitement autorisés est accepté. Sa mise en œuvre et ses tests sont différés avant toute ouverture, selon le modèle d'accès final.
- **0025**, acceptée et intégrée par #28 : protection des routes publiques d'authentification par PostgreSQL, quotas bornés et politique explicite d'origine. Contention et collisions restent à mesurer avant montée en charge.

## Priorités actuelles

1. **Mission 003, ScolaPay Billing Engine** : consolidation finale et revue de clôture dans #29. Le périmètre livré et ses limites figurent dans le [cadrage](missions/mission-003-billing-engine.md) et le journal.
2. **Sites web ScolaPay Schools et ScolaPay Families**, sur `/api/v2`, une fois le cœur financier en place.

## Risques connus

Dans `core/`, les limites connues sont listées dans [0010](decisions/0010-identite-et-acces.md#limites-connues). Les plus structurantes :

| Risque | Conséquence | Où |
| --- | --- | --- |
| Remise externe non vérifiée | E-mail optionnel testé sur fournisseur local seulement, aucun SMS. Rebonds et réception en boîte non suivis ; configuration, domaine d'envoi et supervision restent nécessaires | [Guide des notifications](notifications.md), [0024](decisions/0024-notifications-et-remise-des-invitations.md) |
| Réassociation des personnes financières | Le compte et le type sont immuables ; leur changement nécessiterait un parcours sensible distinct, jamais une correction de nom ou un doublon | [0023](decisions/0023-personnes-liees-et-vues.md#compte-associé-et-identité-financière) |
| Règles juridiques non confirmées | L'âge légal enregistré pour le Tchad est provisoire. L'application refuse d'ouvrir le portail tant qu'il n'est pas confirmé | [0013](decisions/0013-majorite-et-autorisations-familiales.md#limites-et-conditions), [0017](decisions/0017-controles-de-mise-en-service.md) |
| Dégradation silencieuse d'Android | Plus vérifié à chaque PR : accepté tant qu'il est hors feuille de route | [0014](decisions/0014-android-hors-feuille-de-route.md) |
| Verrouillage ciblé et saturation résiduels | La limitation borne le coût mais un attaquant distribué peut encore verrouiller un compte ou gêner le trafic légitime. Quotas approximatifs par compartiments, défenses réseau et récupération à compléter | [Guide anti-abus](auth-abuse-protection.md), [0025](decisions/0025-protection-authentification-publique.md) |
| Contention et collisions des quotas | Mesurer les admissions SQL, attentes de verrous et refus supplémentaires sur cohortes synthétiques avant exploitation à grande échelle. Aucune capacité à grande échelle ni nécessité de Redis n'est démontrée à ce stade | [Suivi avant montée en charge](auth-abuse-protection.md#mesures-avant-montée-en-charge) |
| Écoles à un seul utilisateur | Une opération sensible exige une seconde personne habilitée, sans auto-approbation. Sujet ouvert pour les pilotes | [0022](decisions/0022-ajustements-et-reexamen.md#questions-ouvertes) |
| Performance du réexamen non mesurée | À mesurer avant tout lancement auprès d'établissements importants | [0022](decisions/0022-ajustements-et-reexamen.md#limites) |
| Volume d'émission non mesuré | Le plafond de 5 000 inscriptions par lot est testé, pas la durée d'une telle transaction | [Journal](mission-003-validation.md#limites-restantes) |
| Traitement fiscal non validé | La FEN du Tchad vise au-delà de la seule TVA et exige un système agréé. Aucune source ne traite de l'enseignement. **Aucun document ScolaPay n'est fiscalement conforme** | [0019](decisions/0019-nature-des-documents-et-numerotation.md) |
| Archive des événements sans purge | 180 jours à titre provisoire, mais rien ne l'applique encore | [0016](decisions/0016-outbox-durable.md#conservation) |
| Limites de l'outbox | Un listener peut s'exécuter deux fois et sans ordre garanti : l'émission des obligations est idempotente par contrainte PostgreSQL et relit l'inscription. L'alerte n'est qu'une ligne de journal | [0016](decisions/0016-outbox-durable.md#limites-à-respecter) |

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

**Consolidation de la Mission 003**

Périmètre, critères d'acceptation et état des lots dans le [cadrage](missions/mission-003-billing-engine.md). L'outbox durable et le contrôle de mise en service sont en place. Le catalogue académique et le catalogue de frais sont en place. Les obligations, les échéanciers, le journal immuable, l'émission idempotente, les remises, les corrections et le réexamen aussi. Le lot 6, personnes liées et vues financières, a été intégré par [#26](https://github.com/SPX-tech/ScolaPay/pull/26) au commit `0164876`. Les preuves et limites figurent dans [le journal de finalisation](lot6-finalization.md). Les décisions ouvertes de [0023](decisions/0023-personnes-liees-et-vues.md#décisions-ouvertes-pour-le-propriétaire) restent à arbitrer.

**Ensuite, sans ordre arrêté**

- Notifications : configurer et vérifier l'e-mail réel après revue, choisir un fournisseur SMS, raccorder la supervision et préciser la conservation. Le socle est construit, voir [le guide](notifications.md).
- Portail web des familles ([0007](decisions/0007-portail-familles-avant-apk.md)), avec réinitialisation de mot de passe et renouvellement de session. Son ouverture réelle exige la confirmation juridique de [0013](decisions/0013-majorite-et-autorisations-familiales.md).
- Portage de la caisse, des reçus et de la trésorerie vers `core/`.
- Contrat d'un prestataire réel, puis besoins de services dédiés ([0008](decisions/0008-go-services-paiement.md)).

## Vérifier le projet

La CI GitHub (`.github/workflows/ci.yml`) est la source de preuve : `core` avec Testcontainers et démarrage du JAR, backend NestJS avec PostgreSQL jetable, web avec Playwright. Android a son propre workflow, `android-legacy.yml`, qui ne tourne que si son code change. `containers.yml` valide les images et le proxy. Aucun workflow ne déploie, ne lit de secret de production ni n'appelle de prestataire réel.

Le poste de développement (8 Go) est réservé aux éditions légères. Les commandes locales figurent dans le [README](../README.md).

La CI de `main` a validé 390 tests Java après #26, 431 après #27 et 461 après #28, au commit `7eb7ddb`. Le scénario de reprise couvre l'ordre V7 → V7_1 → V7_2, les sommes de contrôle et l'historique Flyway au redémarrage. Il vérifie aussi le quota d'acceptation sur une invitation remise par la file, puis les droits financiers de l'administrateur invité. Les preuves de consolidation et les limites de clôture figurent dans [le journal de la Mission 003](mission-003-validation.md).

Les 15 mutations ciblées de la [finalisation du lot 6](lot6-finalization.md#validation) ont été détectées avant fusion, avec un contrôle sain de 390 tests. Les preuves historiques restent distinctes : 23 mutations à la [Mission 002](mission-002-validation.md), 224 sur les lots 1 à 5 de la [Mission 003](mission-003-validation.md), puis 176 sur la version initiale du lot 6. Pour le backend NestJS et le web : 190 tests unitaires, 35 E2E et 47 tests Playwright ; la compilation et le lancement Android relèvent de la [Mission 001](mission-001-validation.md#résultats-finaux).

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
