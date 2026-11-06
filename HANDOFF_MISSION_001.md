# Mission 001 : état de reprise

Travail interrompu à la demande du propriétaire pour préserver son quota hebdomadaire. **La mission est inachevée. Aucun déploiement, activation financière réelle ou changement de secret n'a été effectué. `main` est intact.**

## Branches

- `mission001/ci-baseline` : modernisation, compatibilité des trois applications, CI, conteneurs et documentation.
- `mission001/financial-hardening` : tests de régression financiers ajoutés avant les corrections. Cette branche doit recevoir les derniers changements de `mission001/ci-baseline` avant de poursuivre. Ses échecs sont attendus ; les correctifs financiers ne sont pas encore implémentés.

Ne pas fusionner en production en l'état. Préparer des PR cohérentes et les faire examiner après stabilisation.

## Résultats réellement obtenus

- Backend NestJS 12.3.3, TypeScript 6.0.3, Prisma 7.10.1 et Node.js 24.22.1 : installation, génération, build, migrations, rejeu des migrations et seed sur PostgreSQL 16.15 jetable réussis.
- Comparaison du schéma migré avec Prisma : aucune dérive.
- **155 tests unitaires backend et 16 tests E2E réussis**, audit npm de production : **0 vulnérabilité**. Un 17e test de connexion positive a été ajouté à la fin et reste à vérifier sur le dernier commit.
- Web Next.js 16.4.5, React 19.3.1 : installation, lint, build, types, tests monétaires et audit de production réussis.
- Images Docker API, migration et web : builds et démarrage derrière Nginx réussis, santé API et rejet des requêtes non authentifiées vérifiés.
- Android : compilation, tests JVM, lint et lancement sur émulateur réussis. L'écran de connexion a été vérifié sans crash.
- Contrôle navigateur isolé : débordement de 11 px reproduit à 768 px dans l'en-tête et le cachet du reçu. Corrections écrites, à revérifier avec le dernier build.
- Contrôle navigateur du paiement : montants envoyés en chaînes exactes et clé conservée lors d'une reprise après erreur confirmés. Un second encaissement identique réutilisait la clé du premier ; défaut reproduit puis corrigé en réinitialisant `paymentIntent` dans `resetForm`. Cette dernière correction doit être rejouée dans le navigateur.

Exécutions utiles :

- [CI complète verte](https://github.com/SPX-tech/ScolaPay/actions/runs/42115202377) : backend, web, Android et `android-runtime` réussis.
- [Conteneurs réussis](https://github.com/SPX-tech/ScolaPay/actions/runs/42115202324).
- [Régressions financières](https://github.com/SPX-tech/ScolaPay/actions/runs/42114172282) : exécution annulée après blocage du processus E2E. Inspecter ses journaux et garantir la fermeture de l'application dans un `finally` pendant le nettoyage des fixtures. Ne pas utiliser `--forceExit` pour masquer une fuite.
- [Lockfiles et formatage produits pour NestJS 12](https://github.com/SPX-tech/ScolaPay/actions/runs/42111025553).

## Matrice technique actuelle

Node 24.22.1, npm 11.19.1, NestJS 12.3.3, Schedule 12.0.2, Prisma 7.10.1 avec adaptateur PostgreSQL, PostgreSQL 16.15, Next.js 16.4.5, React 19.3.1, TypeScript 6.0.3 backend / 5.9.3 web.

Android : Kotlin et compilateur Compose 2.4.21, AGP 9.3.3, Gradle 9.6.0, JDK 17, Hilt 2.60.1, KSP 2.3.13, Compose BOM 2026.09.00, compile SDK 37. Le paquet SDK stable s'appelle **`platforms;android-37.0`**. Le support Kotlin intégré d'AGP est utilisé, sans désactivation de compatibilité. L'URL debug cible l'émulateur ; l'URL release est un paramètre HTTPS et ne cible plus le VPS en dur.

NestJS 11 conservait une version de Multer vulnérable : la migration vers 12 est justifiée par l'audit. Le SDK Africa's Talking verrouillait lui aussi des dépendances vulnérables ; son seul usage a été remplacé par `sms-client.ts`, un transport HTTP natif borné, avec tests et sans changement de fournisseur. Aucun SMS réel n'a été envoyé.

## Corrections financières prioritaires restantes

1. **Simulation en production** : `PaymentModule` sélectionne encore le mock par défaut et pour les valeurs inconnues. Implémenter un fournisseur désactivé, refuser explicitement le mock en production et les valeurs inconnues. Ne pas activer Kadryza : son connecteur demeure un squelette.
2. **Concurrence sur les élèves** : `recordCashPayment` lit et calcule les soldes avant sa transaction. La confirmation mobile relit dans la transaction sans verrou de ligne. Partager un verrou PostgreSQL par élève, avec filtre d'établissement, et effectuer calculs, écritures, outbox et audit dans la même transaction.
3. **Annulations** : lectures et contrôle du statut actuellement hors transaction ; l'annulation générique accepte les paiements mobiles sans contrepassation du compte virtuel. Sécuriser les transitions et réserver cette annulation aux encaissements cash tant qu'un vrai remboursement n'est pas conçu.
4. **Isolation** : valider `classId` contre `tenantId` lors de la création et de la mise à jour d'un élève. Les clés étrangères actuelles ne suffisent pas à assurer cette relation.
5. **Reporting** : SQL en `recorded_at`, `amount_cents`, `tenant_id` alors que les migrations créent des colonnes camelCase entre guillemets. Corriger et typer les sommes et dates de sortie. Valider les dates et l'ordre de la période ; tenir compte de la journée finale pour les dates sans heure.
6. **Webhook** : le corps est seulement converti par assertion TypeScript. Une valeur de statut autre que `FAILED` suit actuellement le chemin de succès. Ajouter une validation stricte avant toute mutation.
7. **Cache web** : le `QueryClient` du dashboard est global au module et les clés finance ne contiennent pas l'établissement. Isoler les caches entre sessions et établissements.

Les tests déjà écrits dans `backend/test/financial-integrity.e2e-spec.ts` couvrent PostgreSQL réel : concurrence, doublons de clé, reporting, dates, classes inter-établissements, en-têtes forgés, cash avec paiement mobile concurrent et refus d'annulation mobile. Ne pas supprimer ces scénarios pour obtenir une CI verte. Adapter les doubles des tests existants aux verrous ajoutés.

Résultats négatifs obtenus avant correction : 2 tests unitaires fournisseur en échec et 155 réussis ; 6 tests E2E financiers en échec et 18 réussis sur les deux suites. La dette restante après dix encaissements concurrents valait `8000` au lieu de `0`. Le cash avec paiement mobile concurrent laissait `3000` au lieu de `0`. Le reporting échoue explicitement sur la colonne absente `recorded_at`. Le nettoyage des fixtures échoue sur une clé étrangère puis laisse des ressources ouvertes, d'où l'annulation du run.

## Playwright et validation visuelle

**Le propriétaire a explicitement approuvé l'ajout de Playwright comme dépendance de développement web pour la CI. Cet ajout n'a pas encore été réalisé.** Ne pas redemander cette autorisation. Ajouter les tests navigateur pour le cycle erreur/reprise/nouvelle intention et les largeurs 360, 768 et 1440 px, puis produire et vérifier les lockfiles sur GitHub.

Le navigateur pilotable de la session est indisponible. Un Playwright déjà installé sur le VPS a permis les contrôles, sans installation supplémentaire. Scripts et résultats temporaires dans `/home/spencer/work/scolapay-m001/` : `visual.cjs`, `layout.cjs`, `visual-payment.log`, `visual-results/`. Le module installé se résout via `NODE_PATH=/home/spencer/node_modules`.

Le workflow CI publie un artefact `web-preview` contenant le build standalone et ses assets. Utiliser un répertoire isolé et un port lié à `127.0.0.1`, jamais le Nginx de production. Le code de prévisualisation utilisé jusqu'ici provenait d'un build antérieur aux toutes dernières corrections ; télécharger l'artefact du dernier commit avant de revérifier.

## Contraintes d'exécution et transfert Git

Les builds et tests lourds restent interdits sur le ThinkPad. Le propriétaire a confirmé l'utilisation de GitHub Actions en attendant PC-02. Aucun runner PC-02 n'apparaissait dans l'inventaire ; les groupes des runners VPS refusaient les dépôts publics.

Les pushes HTTPS locaux échouent souvent en HTTP 408, même en HTTP/1.1. Un transfert de bundle Git vers le répertoire isolé du VPS, puis un push depuis cet hôte, fonctionne. Cela conserve exactement les commits locaux, sans secret modifié.

- Clone de transfert : `/home/spencer/work/scolapay-m001/baseline`.
- Branche socle : transférer un bundle vers `mission.bundle`, puis exécuter `bash /home/spencer/work/scolapay-m001/push-bundle.sh`.
- Branche financière : transférer vers `scolapay-finance.bundle`, puis exécuter `bash /home/spencer/work/scolapay-m001/scolapay-finance-push.sh`.
- Les scripts utilisent le gestionnaire de credentials GitHub déjà présent. Ne jamais afficher le jeton.
- Après un push distant, faire `git fetch origin` localement pour resynchroniser les références de suivi.

## Terminer la mission

Vérifier le dernier commit, terminer les corrections financières et leurs tests, intégrer les tests navigateur approuvés, confirmer le lancement Android, puis actualiser `docs/mission-001-validation.md` et `tasks/todo.md` avec les résultats finaux exacts. Ouvrir des PR cohérentes, sans fusion sur `main` et sans déploiement. Les décisions d'architecture préparatoires sont dans `docs/architecture-scolapay-2.md`.
