# Mission 005 : intégration Kadryza et intention scolaire

Base ScolaPay `0bb790f171b3cf55942d760aa76233f77aed2d5b`. Contrat Kadryza étudié sur `willyldx/Kadryza-Pay`, `323b7b1463caa2d5b59aab079a0bc46c37360c28`, en lecture seulement. Aucun fichier ou service Kadryza modifié ; les documents locaux non publiés ne sont pas une référence contractuelle.

## Orientation et périmètre

Le numérique est prioritaire pour les écoles et familles du Tchad. La Mission 003 reste clôturée ; les compléments cash de Mission 004 sont suspendus sans dégrader ses fondations. Core Java conserve obligations, autorisations, intentions, écritures et audit. Kadryza conserve les opérations de paiement et intégrations opérateurs. Aucun nouveau backend, service Go ou microservice.

Première tranche : préparation locale interne d'une intention pour une obligation, en XAF, montant exact et borné au dû constaté sous verrou, référence stable prévue pour une future Payment Session Kadryza, idempotence et audit. Aucun envoi, confirmation, règlement, reçu définitif, frais ou mouvement de fonds. Le préparateur est un membre habilité du personnel, pas un payeur déduit. L'accès Families et le modèle marchand avant envoi restent distincts.

## Étapes et preuves

- [x] Vérifier main et lire les contrats de billing, permissions, audit, outbox et contrôles Families.
- [x] Examiner avant implémentation la documentation Kadryza versionnée, les DTO/handlers, scopes/env des clés, unicité marchand/référence/test, création, recherche par référence, pricing, webhooks HMAC/event_id, finalisation et rapprochement. Distinguer code, documentation parfois divergente et état d'exploitation non attesté.
- [x] Observer en CI l'échec fonctionnel avant code : [43308450442](https://github.com/SPX-tech/ScolaPay/actions/runs/43308450442), deux assertions 201/404, zéro erreur ou ignoré ; [43310739257](https://github.com/SPX-tech/ScolaPay/actions/runs/43310739257), premier vert de deux tests.
- [x] Ajouter un port de préparation billing sans écriture de règlement ni permission cash ; conserver les règles financières existantes.
- [x] Ajouter V12 sans réécrire V1 à V11 : intention immuable, origine scolaire numérique distincte, instantané exact, audit et contrôles au commit, aucune preuve d'argent reçue.
- [x] Vérifier API réelles, permissions, montants, NULL/SQL direct, idempotence, concurrence avec le journal, transaction annulée, arrêt et reprises entre JVM : [43317488645](https://github.com/SPX-tech/ScolaPay/actions/runs/43317488645), **277 tests dont 26 nouveaux**, zéro échec, erreur ou ignoré ; rapports XML téléchargés et recomptés. Aucun essai réseau Kadryza ou paiement bout en bout n'est revendiqué.
- [x] Formaliser Mission 005, l'audit de compatibilité Kadryza et la 0032 ; actualiser la vision, le contexte, les statuts cash et les priorités. L'API Kadryza existante et le compte marchand par établissement sont des décisions validées, pas de nouveaux composants à construire.
- **Livraison** : PR en brouillon, CI complète et Containers sur bases jetables, preuves exactes sur la tête finale. Pas de build/test lourd local ni de VPS.

La campagne élargie [43313677393](https://github.com/SPX-tech/ScolaPay/actions/runs/43313677393) a exécuté 277 tests : une assertion échouée, zéro erreur ou ignoré. Le parseur JSON normalisait une version fractionnaire `1.5` vers le `long` 1. La nouvelle API valide désormais une forme entière positive bornée avant conversion, sans changer les montants ou les parcours cash. Le correctif doit repasser la campagne et la CI complète ; ce run échoué n'est pas présenté comme vert.

## Questions avant les tranches externes

**Compte marchand par établissement : validé par le propriétaire.** Rattachements et secrets concrets restent non configurés. Questions restantes : accès du payeur Families ; frais et principal scolaire ; paiement tardif après ajustement/autre règlement ; procédure d'issue inconnue ; test de Payment Session confirmée sans crédit réel ; preuve de versement à l'établissement. Aucune réponse n'est inventée et aucune préparation n'est envoyée automatiquement lors d'une future activation.

La préparation n'est pas une réservation du dû ou des fonds. Les intentions distinctes peuvent exister sans effet monétaire ; avant tout envoi, une sélection/revalidation explicite et la maîtrise des sessions encore incertaines seront requises. L'outbox ne garantit ni ordre ni exécution unique : future confirmation par inbox durable, unicité de l'opération financière et audit transactionnel. Aucun événement sans consommateur n'est présenté comme une livraison garantie.

Aucun prestataire réel, appel réseau Kadryza, migration persistante, déploiement, activation financière, frontend, ouverture Families, fusion ou changement des protections de main.
