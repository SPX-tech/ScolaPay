# ScolaPay : contexte du projet

Référence stratégique et technique du projet, lisible sans l'historique des conversations. En cas de désaccord entre ce document et le code, **le code prime** : corriger le document.

**Dernière mise à jour : 2026-11-06.** Le statut des fonctionnalités décrit le code des branches `mission001/ci-baseline` et `mission001/financial-hardening`, **pas encore fusionnées dans `main`**. **Aucune instance de ScolaPay n'est en service** : « implémenté » ne signifie pas « déployé ».

## Vision

ScolaPay veut devenir une plateforme financière scolaire qui connecte les établissements, les parents et les étudiants, d'abord au Tchad (XAF, fuseau `Africa/Ndjamena`).

**Aujourd'hui, le produit est un outil de gestion pour le personnel des établissements**, centré sur l'encaissement cash. Les parents et les étudiants n'y ont aucun accès : ils sont seulement concernés par les fiches élèves, les reçus et les SMS.

## Lire les statuts

| Statut | Signification |
| --- | --- |
| **Vérifié** | Implémenté et couvert par la CI |
| **Non activé** | Le code existe mais ne doit ni ne peut traiter d'argent réel en production |
| **Prévu** | Décision prise, rien n'est implémenté |

## État réel

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
| Application Android du personnel | Vérifié | Kotlin/Compose : connexion, élèves, encaissement, transactions, rapports, utilisateurs. La CI ne contrôle que le lancement et l'écran de connexion sur émulateur, sans parcours métier | `android/`, job `android-runtime` |
| Paiements mobiles | Non activé | Initiation, webhook, crédit du compte virtuel, mais `PaymentProvider` désactivé par défaut | `modules/payment`, `modules/ledger` |
| Compte virtuel et retraits | Non activé | Grand livre immuable, verrou, cycle `PENDING` vers `COMPLETED` ou `FAILED`. Sans prestataire, tout virement échoue et libère les fonds | `modules/finance` |
| Connecteur Kadryza | Non activé | Squelette sans contrat d'API, refusé en production | `kadryza-payment.provider.ts` |
| Comptes parents et étudiants | Prévu | Rien | [0007](decisions/0007-portail-familles-avant-apk.md) |
| Portail web des familles | Prévu | Rien | [0007](decisions/0007-portail-familles-avant-apk.md) |
| Application Android des familles | Prévu, après le portail | Rien. L'application `android/` sert le personnel | [0007](decisions/0007-portail-familles-avant-apk.md) |
| Facturation, échéanciers, réconciliation, administration SaaS | Prévu | Rien. Le grand livre n'est pas une comptabilité en partie double | [D-001](architecture-scolapay-2.md#décision-001--conserver-le-monolithe-modulaire) |
| Remboursement d'un paiement numérique | Prévu | Rien. L'annulation générique le refuse volontairement | [D-004](architecture-scolapay-2.md#décision-004--prestataires-financiers-fermés-par-défaut) |
| Nouveau backend Java/Spring Boot : identités, établissements, inscriptions, facturation | Prévu | Aucun code, migration non commencée | [0006](decisions/0006-backend-java-spring-boot.md) |
| Services Go de paiement | Envisagé, sans échéance | Aucun code | [0008](decisions/0008-go-services-paiement.md) |

Les chemins `modules/` et `common/` sont relatifs à `backend/src/`.

## Architecture

### En place

```text
Web Next.js 16 ─┐
                ├─ Nginx ─ API NestJS 12 /api/v1 ─ PostgreSQL 16
Android Compose ┘            monolithe modulaire, Prisma 7
```

- **Node 24**, TypeScript 6 côté backend et 5.9 côté web, React 19, Kotlin 2.4, AGP 9.3. Détail et motifs dans le [journal de validation](mission-001-validation.md).
- **Déploiement par `docker compose`** : une image `migrate` applique les migrations avant l'API. Une seule instance d'API est supposée (voir les risques). L'ancienne instance a été retirée le 2026-11-06 : elle ne contenait qu'un établissement de démonstration, et ses données sont archivées hors du dépôt.
- **Modèle de données** : [`backend/prisma/schema.prisma`](../backend/prisma/schema.prisma). Montants en `BIGINT`, exposés en chaînes dans le JSON.

### Cible validée pour ScolaPay 2.0

**Ces décisions sont prises, mais rien n'est construit.** Tout ce qui suit est prévu, pas livré.

| Élément | Décision | Référence |
| --- | --- | --- |
| Nouveau backend | Java et Spring Boot, en monolithe modulaire. Spring Modulith est une option à évaluer | [0006](decisions/0006-backend-java-spring-boot.md) |
| Nouveaux domaines | Identités, établissements, inscriptions et facturation scolaire directement en Java | [0006](decisions/0006-backend-java-spring-boot.md) |
| Code NestJS | Conservé dans le dépôt comme référence. Le nouveau backend est construit directement, sans maintenir durablement deux backends, à condition de ne perdre ni dépendance de production ni donnée nécessaire | [0006](decisions/0006-backend-java-spring-boot.md) |
| Web et données | Next.js/TypeScript et PostgreSQL conservés | [0006](decisions/0006-backend-java-spring-boot.md) |
| Familles | Véritable site web d'abord, application Android ensuite | [0007](decisions/0007-portail-familles-avant-apk.md) |
| Services financiers | Go envisagé, notamment pour les intégrations de prestataires, sans introduction immédiate | [0008](decisions/0008-go-services-paiement.md) |

**Règle :** aucun nouveau domaine métier n'est ajouté à NestJS, et les sources de vérité financières ne sont jamais dupliquées.

Les questions ouvertes de chaque décision ne doivent pas être tranchées par supposition. Celles du nouveau backend sont dans [0006](decisions/0006-backend-java-spring-boot.md#questions-ouvertes).

## Invariants financiers

À préserver quel que soit le langage ou le framework. Ils sont exercés sur PostgreSQL réel par [`backend/test/financial-integrity.e2e-spec.ts`](../backend/test/financial-integrity.e2e-spec.ts).

1. **Solde sérialisé.** Toute écriture de `debtCents` ou `creditCents` lit le solde sous le verrou de ligne de l'élève (`lockStudentBalance`), dans la transaction de l'écriture.
2. **Atomicité.** Mouvement, outbox et audit d'un encaissement, d'une confirmation ou d'une annulation sont validés ensemble.
3. **Ordre de verrouillage.** Élève, puis compte virtuel. Ne jamais l'inverser.
4. **Une seule comptabilisation.** Clé d'idempotence unique par établissement, confirmation mobile gardée par le statut `PENDING`, référence de grand livre unique.
5. **Établissement explicite.** Toute ressource est cherchée avec son `tenantId`, y compris les références vers une classe. La clé étrangère seule ne le garantit pas.
6. **Grand livre immuable.** Un trigger PostgreSQL interdit la modification et la suppression de `ledger_entries`.
7. **Cash et numérique séparés.** Le cash ne crédite pas le compte virtuel. Un paiement numérique ne s'annule pas sans remboursement réel.
8. **Fermé par défaut.** Sans configuration, aucun prestataire n'est actif. `mock` et `kadryza` n'existent qu'avec `NODE_ENV` à `development` ou `test`. Une valeur inconnue empêche le démarrage.
9. **Webhook.** Signature vérifiée sur le corps brut, puis contenu validé strictement, avant toute mutation.
10. **Montants exacts.** Jamais de flottant pour un montant stocké. `Cents` est une convention historique, y compris pour XAF, voir [D-003](architecture-scolapay-2.md#décision-003--conserver-les-unités-financières-historiques).

## Décisions

L'index complet est dans [`docs/decisions/`](decisions/README.md). À retenir :

- **D-001 à D-005** ([détail](architecture-scolapay-2.md)) : monolithe modulaire, isolation par établissement, unités historiques, prestataires fermés par défaut, migrations séparées du démarrage.
- **0006 et 0007**, acceptées le 2026-11-06 : nouveau backend en Java/Spring Boot, construit directement, avec le code NestJS conservé comme référence, puis portail web des familles avant l'application Android.
- **0008**, orientation validée : Go envisagé pour de futurs services financiers, sans introduction immédiate.

## Priorités actuelles

1. **Clore la Mission 001** : faire examiner et fusionner les PR [#1](https://github.com/SPX-tech/ScolaPay/pull/1) puis [#2](https://github.com/SPX-tech/ScolaPay/pull/2), selon la [procédure ci-dessous](#intégrer-les-pr-de-la-mission-001). La fusion et le déploiement attendent l'autorisation explicite du propriétaire.
2. **Préparer la fondation Java/Spring Boot** : chantier distinct, à cadrer avec ses objectifs métier et architecturaux. **Il n'est pas commencé.**

### Intégrer les PR de la Mission 001

État au 2026-11-06 : `main` n'a pas bougé depuis la création du socle, aucun conflit, CI et conteneurs verts sur les deux têtes.

1. **Fusionner #1 dans `main`** avec un commit de fusion, sans squash : #2 repose sur ses commits.
2. **Recibler #2 sur `main`** : elle vise le socle, et le dépôt ne le fait pas automatiquement.
3. **Fusionner #2** aussitôt, puis vérifier la CI du commit de `main`. Le socle seul ne contient pas les correctifs financiers : ne rien déployer entre ces deux fusions.

L'arbre obtenu est identique à la tête de `mission001/financial-hardening`, déjà validée en CI.

## Risques connus

| Risque | Conséquence | Où |
| --- | --- | --- |
| Solde modifiable par `PATCH /students/:id` | Change la dette sans `Transaction` : seul l'audit la trace. `DEBT_ADJUSTMENT` et `REFUND` existent dans le schéma mais rien ne les produit | `students.service.ts` |
| Rapports limités au cash | `MOBILE_PAYMENT` n'entre ni dans le rapport d'encaissements ni dans le tableau de bord. À trancher avant d'activer un prestataire | `reports.controller.ts`, `dashboard.service.ts` |
| Contrat Kadryza inconnu | Webhook provisoire : montant et devise non rapprochés, états incertains après un délai réseau non définis | [D-004](architecture-scolapay-2.md#décision-004--prestataires-financiers-fermés-par-défaut) |
| Unités monétaires | `Cents` affiché divisé par 100 pour XAF, sans exposant ISO. À contractualiser avant un prestataire | [D-003](architecture-scolapay-2.md#décision-003--conserver-les-unités-financières-historiques) |
| Isolation applicative seule | Pas de clé étrangère composite `(tenantId, id)` | [D-002](architecture-scolapay-2.md#décision-002--isolation-explicite-par-établissement) |
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

**Prochaine grande mission : fondation Java/Spring Boot de ScolaPay 2.0**

Chantier distinct, à préparer avec ses objectifs. Il devra notamment répondre aux [questions ouvertes de 0006](decisions/0006-backend-java-spring-boot.md#questions-ouvertes) et évaluer Spring Modulith.

**Ensuite, sans ordre arrêté**

- Concevoir l'identité et le lien famille-élève, puis le portail ([0007](decisions/0007-portail-familles-avant-apk.md)).
- Remplacer la modification directe des soldes par des écritures d'ajustement.
- Obtenir le contrat d'un prestataire réel, puis évaluer les besoins de services dédiés ([0008](decisions/0008-go-services-paiement.md)).

## Vérifier le projet

La CI GitHub (`.github/workflows/ci.yml`) est la source de preuve : backend avec PostgreSQL jetable, web avec Playwright, Android avec émulateur. `containers.yml` valide les images et le proxy. Aucun workflow ne déploie, ne lit de secret de production ni n'appelle de prestataire réel.

Le poste de développement (8 Go) est réservé aux éditions légères. Les commandes locales figurent dans le [README](../README.md).

Dernier résultat complet (2026-11-06) : 190 tests unitaires et 35 E2E backend, 47 tests Playwright, compilation et lancement Android. Détail et limites dans le [journal de validation](mission-001-validation.md#résultats-finaux).

## Carte de la documentation

| Document | Rôle |
| --- | --- |
| `docs/PROJECT_CONTEXT.md` | Ce document : état et orientation |
| [`docs/decisions/`](decisions/README.md) | Décisions majeures |
| [`docs/architecture-scolapay-2.md`](architecture-scolapay-2.md) | D-001 à D-005 et conditions de production |
| [`docs/mission-001-validation.md`](mission-001-validation.md) | Versions retenues et résultats de validation |
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
