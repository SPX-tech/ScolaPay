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

## Revue du propriétaire et attente d'intégration

Le développement du socle a été validé le 2026-11-10. La suite est distincte du chantier terminé ; aucune autorisation de fusion, de déploiement ou de configuration réelle de Resend n'a été donnée.

- [x] Vérifier que #26 est encore ouverte et différer la mise à jour depuis `main`.
- [x] Documenter la proposition de canaux Families autorisés, sans changer la fermeture ni les règles juridiques.
- [x] Documenter le refus transactionnel de création d'établissement en mode désactivé et les fixtures de démonstration.
- [ ] Après intégration autorisée de #26 dans `main`, remettre #27 à jour et résoudre les recouvrements des helpers de test.
- [ ] Exécuter la validation conjointe complète dans GitHub Actions sur PostgreSQL vierge, avec V7 puis V7_1 et vérification du rejeu des migrations.
- [ ] Consigner les résultats conjoints avant toute nouvelle décision de fusion.

Le [protocole d'intégration](../docs/integration-notifications-billing.md) et le principe d'une politique fondée sur les canaux Families autorisés sont validés par le propriétaire. La mise en œuvre de cette politique est explicitement différée ; elle devra être développée et testée avant toute ouverture, selon le modèle d'accès final, sans affaiblir les identités, les droits familiaux ou les contrôles juridiques. Aucun développement supplémentaire n'est engagé maintenant. #27 reste en brouillon jusqu'à #26, puis à la validation conjointe et à une nouvelle revue.
