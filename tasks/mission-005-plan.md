# Mission 005 : intégration Kadryza et intention scolaire

Base ScolaPay `0bb790f171b3cf55942d760aa76233f77aed2d5b`. Contrat Kadryza étudié sur `willyldx/Kadryza-Pay`, `323b7b1463caa2d5b59aab079a0bc46c37360c28`, en lecture seulement. Aucun fichier ou service Kadryza modifié ; les documents locaux non publiés ne sont pas une référence contractuelle.

## Orientation et périmètre

Le numérique est prioritaire pour les écoles et familles du Tchad. La Mission 003 reste clôturée ; les compléments cash de Mission 004 sont suspendus sans dégrader ses fondations. Core Java conserve obligations, autorisations, intentions, écritures et audit. Kadryza conserve les opérations de paiement et intégrations opérateurs. Aucun nouveau backend, service Go ou microservice.

Première tranche : préparation locale interne d'une intention pour une obligation, en XAF, montant exact et borné au dû constaté sous verrou, référence stable prévue pour une future Payment Session Kadryza, idempotence et audit. Aucun envoi, confirmation, règlement, reçu définitif, frais ou mouvement de fonds. Le préparateur est un membre habilité du personnel, pas un payeur déduit. L'accès Families et le modèle marchand avant envoi restent distincts.

## Étapes et preuves

- [x] Vérifier main et lire les contrats de billing, permissions, audit, outbox et contrôles Families.
- [x] Examiner avant implémentation la documentation Kadryza versionnée, les DTO/handlers, scopes/env des clés, unicité marchand/référence/test, création, recherche par référence, pricing, webhooks HMAC/event_id, finalisation et rapprochement. Distinguer code, documentation parfois divergente et état d'exploitation non attesté.
- [ ] Observer en CI l'échec fonctionnel des tests de préparation avant code de production.
- [ ] Ajouter un port de préparation billing sans écriture de règlement ni permission cash ; conserver les règles financières existantes.
- [ ] Ajouter V12 sans réécrire V1 à V11 : intention immuable, origine scolaire numérique distincte, instantané exact, audit et contrôles au commit, aucune preuve d'argent reçue.
- [ ] Vérifier API réelles, permissions, montants, NULL/SQL direct, idempotence, concurrence avec le journal, transaction annulée, arrêt et reprises entre JVM.
- [ ] Formaliser Mission 005, l'audit de compatibilité Kadryza et l'ADR ; actualiser la vision, le contexte, les statuts cash et les priorités.
- **Livraison** : PR en brouillon, CI complète et Containers sur bases jetables, preuves exactes sur la tête finale. Pas de build/test lourd local ni de VPS.

## Questions avant les tranches externes

Compte Kadryza par établissement ou compte plateforme ; accès du payeur Families ; frais et principal scolaire ; paiement tardif après ajustement/autre règlement ; procédure d'issue inconnue ; test de Payment Session confirmée sans crédit réel ; preuve de versement à l'établissement. Aucune réponse n'est inventée et aucune session préparée n'est envoyée automatiquement lors d'une future activation.

La préparation n'est pas une réservation du dû ou des fonds. Les intentions distinctes peuvent exister sans effet monétaire ; avant tout envoi, une sélection/revalidation explicite et la maîtrise des sessions encore incertaines seront requises. L'outbox ne garantit ni ordre ni exécution unique : future confirmation par inbox durable, unicité de l'opération financière et audit transactionnel. Aucun événement sans consommateur n'est présenté comme une livraison garantie.

Aucun prestataire réel, appel réseau Kadryza, migration persistante, déploiement, activation financière, frontend, ouverture Families, fusion ou changement des protections de main.
