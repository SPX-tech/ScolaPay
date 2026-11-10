# Socle de notifications et remise des invitations

Branche `feat/notification-foundation`, créée depuis `main` (c28862c). Worktree indépendant.
Aucun changement de facturation, NestJS, Android ou interface. Aucun déploiement ni fusion.

## Contrat et risques

- Invitation, audit, charge chiffrée et publication du réveil sont atomiques.
- Le listener Modulith prépare la file, sans réseau. Un worker réclame une ligne avec bail et jeton de possession, puis appelle le transport hors transaction.
- Réexécutions du listener et du worker possibles, doublons externes possibles. Clé stable et corps figé pour l'idempotence du fournisseur. Aucun nouvel essai après la fenêtre de déduplication.
- AES-256-GCM, clé externe indépendante du JWT, identifiant de ligne comme AAD. Purge des charges terminales et expirées.
- E-mail Resend optionnel, désactivé par défaut, HTTP JDK sans dépendance ajoutée. SMS absent : refus explicite.
- Le portail reste fermé. La condition exige les deux canaux que l'identité accepte et conserve le contrôle juridique.
- La révocation ne peut rappeler un message déjà en vol. Le destinataire doit encore accepter un jeton valable.
- Le statut SUBMITTED signifie accepté par le fournisseur, sans preuve de réception dans la boîte.

## Tâches et preuves

- [x] Régression initiale en CI : aucun jeton dans les réponses HTTP, téléphone refusé sans SMS.
- [x] File chiffrée, listener durable, reprise et exclusion concurrente sur PostgreSQL réel.
- [x] Adaptateur HTTP borné, erreurs neutres, configuration fermée et tests de transport.
- [x] Adaptations minimales des tests : les jetons proviennent du canal de test, jamais du corps HTTP.
- [x] Tests : succès, échec, incident, double traitement, révocation, expiration, confidentialité, isolation.
- [x] ADR, guide d'exploitation, contexte du projet et résultats exacts.
- [x] CI complète verte, PR indépendante ouverte, sans fusion.

Migration `V7_1` réservée à ce chantier pour éviter `V7` de la PR #26. Aucun environnement persistant migré.
Les seuls recouvrements avec #26 sont les helpers de test et la documentation de contexte.

## Résultats

- Code `d7224bd` : 369 tests Java et démarrage du JAR verts, [CI](https://github.com/SPX-tech/ScolaPay/actions/runs/42851059251).
- 50 contrôles ciblés verts, dont 39 nouveaux tests, [preuve](https://github.com/SPX-tech/ScolaPay/actions/runs/42850922300).
- Conteneurs verts, [preuve](https://github.com/SPX-tech/ScolaPay/actions/runs/42851227492).
- Deux fenêtres d'arrêt brutal vérifiées avec de vraies JVM ; corps identique et clé stable au redémarrage.
- PR indépendante [#27](https://github.com/SPX-tech/ScolaPay/pull/27), aucune fusion, aucun déploiement, aucun fournisseur réel appelé.
- Configuration, SMS, confirmation juridique, supervision et réception réelle restent des conditions explicites, voir [le guide](../docs/notifications.md).
