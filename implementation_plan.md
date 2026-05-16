# 📋 PLAN D'IMPLÉMENTATION — EDU-CASH TCHAD (SaaS B2B2C)

> **Mission :** Système SaaS de gestion financière pour écoles et universités au Tchad
> **Architecture :** Multi-Tenant (isolation par `tenantId`) | Numérisation du Cash
> **Stack :** NestJS + PostgreSQL + Prisma (Backend) → Next.js (Dashboard) → Flutter (Mobile)

---

## ✅ ÉTAT DES LIEUX — Ce qui EXISTE DÉJÀ

Le backend NestJS est déjà en place avec une architecture solide :

### Base de données (Prisma)
| Modèle | Statut | Notes |
|---|---|---|
| `Tenant` (École) | ✅ Fait | `code` unique, currency, timezone |
| `User` (Admin/Caissier) | ✅ Fait | Rôles : TENANT_ADMIN, CASHIER, ACCOUNTANT, AUDITOR |
| `Student` | ✅ Fait | `debtCents`, `creditCents`, statuts |
| `Transaction` | ✅ Fait | Type CASH_PAYMENT, idempotency, receiptHash SHA-256 |
| `OutboxEvent` | ✅ Fait | Outbox pattern pour évolutivité async |

### API Backend (NestJS)
| Module | Statut | Ce qui existe |
|---|---|---|
| `auth` | ✅ Fait | Login JWT, AuthGuard |
| `tenants` | ✅ Fait | getTenantById |
| `users` | ✅ Fait | listTenantUsers |
| `students` | ✅ Fait | CRUD (create, list, getById) |
| `ledger` | ✅ Fait | recordCashPayment (ACID, idempotent) |
| `transactions` | ✅ Fait | List/filter transactions |
| `outbox` | ✅ Fait | Outbox pattern avec pending events |
| `notifications` | ⚠️ Placeholder | Prêt pour branchement SMS |

### Sécurité
| Composant | Statut |
|---|---|
| AuthGuard (JWT) | ✅ Fait |
| TenantGuard (isolation) | ✅ Fait |
| BigInt Serializer | ✅ Fait |
| ValidationPipe global | ✅ Fait |

---

## 🗺️ ROADMAP D'IMPLÉMENTATION

### Phase 1 : Renforcement du Backend Existant (Semaine 1)

#### 1.1 Améliorations Prisma & Base de données
- [x] Schéma existant : Tenant, User, Student, Transaction, OutboxEvent
- [ ] **Ajouter des index composites** pour les performances (recherches par date, statut)
- [ ] **Ajouter champ `metadata` pour les étudiants** (classe, filière, année académique)
- [ ] **Ajouter une table `Receipt`** dédiée avec QR code data
- [ ] **Ajouter une table `SchoolClass`** (gestion des classes/groupes)

#### 1.2 Améliorations API
- [ ] **Pagination** sur les listes (students, transactions, users)
- [ ] **Filtres avancés** (par date, statut, montant)
- [ ] **Export CSV/Excel** des transactions
- [ ] **Endpoint de summary** (dashboard stats : total encaissé, nombre d'étudiants, etc.)
- [ ] **Gestion des utilisateurs complète** (invite, suspend, change role)
- [ ] **Void/Annulation de transaction** avec raison et audit trail

#### 1.3 Système de Notifications (SMS)
- [ ] Implémenter le **NotificationService** complet
- [ ] Intégration avec **Africa's Talking API** ou Twilio
- [ ] Worker de polling **Outbox** → envoi SMS après paiement cash
- [ ] Template SMS personnalisable par école

#### 1.4 Tests & Qualité
- [ ] Tests unitaires pour chaque service (Jest)
- [ ] Tests d'intégration pour les endpoints critiques
- [ ] Tests E2E (déjà configurés dans `test/`)

---

### Phase 2 : Portail Web B2B — Next.js (Semaine 2-3)

#### 2.1 Initialisation du projet Next.js
- [ ] Créer un dossier `web/` avec Next.js 14+ (App Router)
- [ ] Installer TailwindCSS + Shadcn UI
- [ ] Configurer les librairies : react-query, axios, react-hook-form, zod

#### 2.2 Authentification & Layout
- [ ] Page de login avec sélection d'école (tenant code)
- [ ] Layout avec Sidebar + TopBar responsives
- [ ] Guard de routes (protégées par rôle)
- [ ] Gestion de session (JWT stocké, refresh)

#### 2.3 Dashboard Principal (Role-based)
- [ ] **Dashboard Admin** : KPIs globaux (encaissements du jour, dette totale, etc.)
- [ ] **Dashboard Caissier** : Interface d'encaissement rapide
- [ ] **Dashboard Comptable** : Rapports et exports

#### 2.4 Gestion des Étudiants
- [ ] Liste paginée avec recherche et filtres
- [ ] Fiche étudiant détaillée (solde, historique)
- [ ] Création/Modification d'étudiant
- [ ] Import CSV d'étudiants

#### 2.5 Encaissement Cash (Record Payment)
- [ ] Formulaire d'encaissement rapide (sélection étudiant, montant)
- [ ] Reçu imprimable avec QR Code
- [ ] Confirmation et animation de succès

#### 2.6 Gestion des Utilisateurs (Admin)
- [ ] Liste des utilisateurs de l'école
- [ ] Invitation par email
- [ ] Gestion des rôles

#### 2.7 Rapports & Export
- [ ] Rapport journalier/mensuel des encaissements
- [ ] État des impayés (dettes étudiants)
- [ ] Export PDF et Excel

---

### Phase 3 : Application Mobile B2C — Flutter (Semaine 3-4)

#### 3.1 Initialisation du projet Flutter
- [ ] Créer un dossier `mobile/` avec Flutter
- [ ] Configurer l'architecture (Provider / Riverpod / Bloc)
- [ ] Thème et design system

#### 3.2 Authentification Mobile
- [ ] Login avec code école + email + mot de passe
- [ ] Stockage sécurisé du token
- [ ] Biometric authentication (optionnel)

#### 3.3 Interface Étudiant
- [ ] **Dashboard** : Solde actuel, statut, dernier paiement
- [ ] **Historique** : Liste des transactions avec détails
- [ ] **Reçus** : Visualisation des reçus avec QR Code
- [ ] **Profil** : Informations personnelles

#### 3.4 Notifications Push
- [ ] Réception des notifications de paiement
- [ ] Alerte de rappel de paiement

---

### Phase 4 : Production & Déploiement (Semaine 4-5)

#### 4.1 Infrastructure
- [ ] Docker Compose (NestJS + PostgreSQL + Redis optionnel)
- [ ] CI/CD avec GitHub Actions
- [ ] Déploiement sur VPS (ou services cloud)

#### 4.2 Configuration de Production
- [ ] HTTPS (Let's Encrypt)
- [ ] Variables d'environnement sécurisées
- [ ] Backup automatique PostgreSQL
- [ ] Monitoring (logs, erreurs, uptime)

#### 4.3 Seed & Fixtures
- [ ] Script de seed pour données de démo
- [ ] Création d'un tenant "demo" avec données fictives

---

## 🏛️ STRUCTURE DES DOSSIERS PROPOSÉE

```
edu-cash/
├── backend/                    # API NestJS (EXISTANT)
│   ├── prisma/
│   │   └── schema.prisma      # ✅ Schéma Prisma complet
│   ├── src/
│   │   ├── common/            # ✅ Guards, decorators, interceptors
│   │   ├── modules/
│   │   │   ├── auth/          # ✅ Login JWT
│   │   │   ├── health/        # ✅ Health check
│   │   │   ├── ledger/        # ✅ RecordCashPayment
│   │   │   ├── notifications/ # ⚠️ À compléter (SMS)
│   │   │   ├── outbox/        # ✅ Outbox pattern
│   │   │   ├── students/      # ✅ CRUD étudiant
│   │   │   ├── tenants/       # ✅ Gestion école
│   │   │   ├── transactions/  # ✅ Historique transactions
│   │   │   └── users/         # ✅ Gestion utilisateurs
│   │   └── prisma/            # ✅ PrismaModule, PrismaService
│   └── test/                  # ✅ Tests E2E configurés
│
├── web/                       # 🌐 Dashboard Next.js (À CRÉER)
│   ├── src/
│   │   ├── app/               # App Router (pages, layouts)
│   │   ├── components/        # Composants réutilisables (Shadcn)
│   │   ├── lib/               # Utilitaires, API client
│   │   └── hooks/             # Custom hooks
│   └── ...
│
├── mobile/                    # 📱 App Flutter (À CRÉER)
│   ├── lib/
│   │   ├── screens/           # Écrans de l'app
│   │   ├── services/          # Services API
│   │   ├── models/            # Modèles de données
│   │   └── widgets/           # Widgets réutilisables
│   └── ...
│
└── implementation_plan.md     # 📄 Ce document
```

---

## 🔒 CONFIRMATION DE COMPRÉHENSION

### Multi-Tenant (Isolation des données)
✅ **Compris et déjà implémenté.**
- Chaque table métier (`User`, `Student`, `Transaction`, `OutboxEvent`) a une colonne `tenantId`
- `TenantGuard` valide le contexte à chaque requête
- Les clés `@@unique([tenantId, email])` et `@@unique([tenantId, studentNumber])` empêchent les collisions inter-écoles
- Relations `Restrict` pour empêcher la suppression accidentelle

### Numérisation du Cash
✅ **Compris et déjà implémenté dans `LedgerService.recordCashPayment()`.**
- Transaction `CASH_PAYMENT` enregistrée avec `amountCents` (BigInt, pas de float)
- Calcul automatique : `newDebtCents = previousDebtCents - amountCents`
- Gestion du trop-perçu : `creditCents` incrémenté si le montant dépasse la dette
- Reçu infalsifiable : `receiptHash` = SHA-256(tenantId + studentId + amount + receiptNumber + secret)
- Idempotence : clé unique `idempotencyKey` pour éviter les doublons
- Outbox pattern : événement créé en transaction pour notification SMS future

### Event-Driven / Notifications
✅ **Compris et partiellement implémenté.**
- `OutboxService` : stocke les événements dans une table dédiée
- Les événements sont créés **dans la même transaction** que le paiement (ACID)
- `NotificationsService` : placeholder prêt à être branché sur Africa's Talking / Twilio
- Reste à implémenter : le worker de polling et l'intégration SMS

---

## ⚠️ RISQUES & DÉCISIONS À VALIDER

1. **QR Code** : Sur le reçu imprimable — utiliser une lib standard (qrcode) ou une API externe ?
2. **Africa's Talking** : API SMS réelle ou mock en dev ?
3. **Stockage fichiers** : Les reçus PDF/QR — stockage local S3/cloud ?
4. **Monnaie** : XAF par défaut — support multi-devises nécessaire ?
5. **Dette étudiant** : Montant saisi manuellement ou importé depuis un système existant ?

---

> **📌 Prochaine étape :** J'attends ton approbation explicite pour commencer l'implémentation des phases ci-dessus. Tu peux également ajuster les priorités ou me demander de commencer par une partie spécifique.
