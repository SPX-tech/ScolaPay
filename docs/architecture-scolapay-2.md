# Préparation de ScolaPay 2.0

> Index des décisions : [`docs/decisions/`](decisions/README.md). Les nouvelles décisions y sont ajoutées, ce fichier conserve D-001 à D-005.

## Décision 001 : conserver le monolithe modulaire

> **Amendée le 2026-11-06.** La [décision 0006](decisions/0006-backend-java-spring-boot.md) conserve le monolithe modulaire mais remplace le choix de NestJS pour les futurs domaines, qui seront développés en Java/Spring Boot. Le code NestJS reste dans le dépôt comme référence. Le tableau ci-dessous conserve la répartition des domaines, pas leur technologie.

NestJS et PostgreSQL couvrent les besoins actuels. Les modules existants fournissent déjà l'authentification, les établissements, les élèves, les encaissements, la trésorerie, l'audit et l'outbox. Les clients web et Android consomment la même API `/api/v1`. Aucun bénéfice mesuré ne justifie des microservices, un courtier de messages ou une seconde base.

Les futurs domaines seront des modules NestJS avec des services applicatifs explicites. Les contrôleurs ne porteront pas les règles financières. Les modules consommateurs devront passer par les services du domaine propriétaire plutôt que modifier directement ses tables.

| Domaine | Responsabilité future | Dépendances principales |
| --- | --- | --- |
| Identité | Comptes parents, tuteurs et étudiants, sessions | Établissements et autorisations explicites |
| Familles | Relations entre identités et élèves, preuves et droits | Identité, élèves |
| Scolarité | Inscriptions, années, classes et catalogue des frais | Établissements, élèves |
| Facturation | Factures, lignes, remises et échéances | Scolarité |
| Paiements | Intentions, prestataires, confirmations et remboursements | Facturation, trésorerie |
| Réconciliation | Rapprochement des relevés et gestion des écarts | Paiements, grand livre |
| Administration SaaS | Abonnements, habilitations et exploitation | Identité, établissements |

Chaque domaine fera l'objet d'une mission séparée avec ses contrats et migrations. Cette mission ne crée aucune de ces fonctionnalités.

## Décision 002 : isolation explicite par établissement

Le contexte d'établissement est dérivé du jeton signé. Un en-tête contradictoire doit être rejeté. Les lectures et mutations recherchent les ressources dans ce contexte, y compris les références de classe lors d'une inscription ou d'une modification.

Le modèle `User` actuel appartient à un établissement. Le transformer directement en compte parent global casserait les contraintes d'unicité et les autorisations existantes. Introduire une identité globale et des adhésions explicites est préférable dans une future migration additive. Une relation famille-élève n'accordera jamais implicitement un accès à tout l'établissement.

Les clés étrangères actuelles n'encodent pas systématiquement le couple `(tenantId, id)`. Une mission dédiée devra auditer les relations historiques, corriger les anomalies avec validation humaine, puis introduire des contraintes composites. La protection applicative et les tests croisés constituent le garde-fou immédiat. Le contrôle d'accès PostgreSQL par ligne reste une option de défense supplémentaire, à évaluer avec le pool de connexions et les tâches asynchrones.

## Décision 003 : conserver les unités financières historiques

> **Limitée au code NestJS le 2026-11-06.** Le nouveau backend applique la [décision 0012](decisions/0012-convention-monetaire.md) : montants entiers dans l'unité minimale ISO de chaque devise, soit des francs entiers pour le XAF.

Les montants persistés sont des `BIGINT` PostgreSQL. Les réponses JSON les représentent par des chaînes décimales pour préserver leur précision dans JavaScript. Les écritures en chaînes sont validées avant conversion ; les routes historiques acceptant encore des nombres doivent rester dans la plage entière sûre.

Le code existant nomme ces unités `Cents` et affiche un montant divisé par 100, y compris pour XAF. **Cette convention historique ne doit pas être confondue avec l'exposant monétaire ISO de XAF.** Ne pas changer d'échelle des données dans une mise à niveau technique. La définition des unités par devise, l'éventuelle migration et les arrondis devront être contractualisés avant l'intégration d'un prestataire.

Les opérations concurrentes sur un élève doivent partager un verrou transactionnel. Les mouvements de trésorerie numériques possèdent déjà un verrou sur le compte virtuel et une référence unique de grand livre. Un encaissement cash ne crédite pas ce compte virtuel, conformément au comportement existant.

Le grand livre actuel est un journal de mouvements du compte virtuel, pas une comptabilité complète en partie double. La facturation et la réconciliation demanderont des écritures compensatoires et des invariants comptables explicites ; ne pas présenter le modèle actuel comme fournissant ces garanties.

## Décision 004 : prestataires financiers fermés par défaut

Aucun prestataire réel n'est activé par cette mission. Le connecteur Kadryza existant est un squelette dont le contrat n'est pas documenté. Avant toute activation : vérifier les signatures sur le corps brut, rapprocher montant/devise/référence, définir les états incertains après un délai réseau et tester les reprises sans double débit.

Les simulations doivent rester dans des bases de développement ou de test séparées. Le fournisseur simulé ne peut pas être une solution de secours en production. L'annulation générique d'une transaction ne doit pas simuler le remboursement d'un paiement numérique.

## Décision 005 : migrations séparées du démarrage

Conserver PostgreSQL 16 avec un correctif de maintenance évite une migration de format des volumes. Une montée majeure PostgreSQL demandera une sauvegarde restaurée en test, une répétition de `pg_upgrade` ou de la restauration logique, puis une fenêtre d'exploitation distincte.

Prisma 7 change le client généré, la configuration CLI et le pilote PostgreSQL. Le générateur produit des sources TypeScript CommonJS compatibles avec NestJS 12. Les tests adaptent leur résolution des imports `.js` des sources générées ; les fichiers générés ne sont ni édités ni suivis dans Git.

Une image Docker `migration` applique les migrations avant l'API. L'image d'exécution n'installe pas le CLI Prisma. Ne jamais utiliser `prisma migrate dev`, `db push` ou un reset sur une base persistante. Les migrations existantes restent immuables.

## Conditions avant une future ouverture en production

- Faire examiner et fusionner les PR, puis vérifier la CI du commit fusionné.
- Répéter la restauration d'une sauvegarde et comparer les soldes et volumes de données.
- Définir et vérifier la terminaison HTTPS, les origines autorisées et les limites du proxy.
- Contractualiser l'identité multi-établissements, les unités monétaires et les workflows de remboursement.
- Réaliser la migration additive des factures et échéanciers sans recalcul implicite des dettes historiques.

Aucun déploiement, changement de secret ou appel à un prestataire réel n'a sa place dans les scripts de vérification de cette mission.
