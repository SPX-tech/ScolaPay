# 0024 : notifications et remise sécurisée des invitations

- **Statut** : Socle, protocole d'intégration et principe de politique des canaux acceptés par le propriétaire le 2026-11-10. Intégration après #26 ; mise en œuvre de la politique différée avant toute ouverture de Families.
- **Date** : 2026-11-10
- **Décideur** : propriétaire du projet, validation du développement de la PR #27
- **Remplace** : les clauses de remise provisoire de [0010](0010-identite-et-acces.md) et de détection du canal de [0017](0017-controles-de-mise-en-service.md). Complète [0016](0016-outbox-durable.md), sans changer ses garanties.

## Contexte

`InvitationDelivery` n'était pas appelé : le personnel recevait le jeton dans les réponses HTTP. Le registre Modulith peut rejouer simultanément un listener et garde une connexion pendant sa transaction. Un appel fournisseur dans ce listener enfreindrait la limite 5 de 0016.

Ce chantier est indépendant de la Mission 003, PR #26 : aucune table financière, aucun handler familial et aucune règle de contribution ne changent.

## Décision

Un module `notifications` dépend uniquement des API publiques `identity` et `shared`. L'identité conserve son port `InvitationDelivery`, implémenté par l'adaptateur du nouveau module. Elle ne dépend pas du module de notifications : pas de cycle.

La remise se fait en deux étapes :

1. Dans la transaction d'émission, l'identité stocke l'empreinte du jeton, écrit l'audit et appelle le port. Celui-ci insère une intention dans `notification_messages`, puis publie `NotificationQueued(UUID)`. Invitation, charge et publication sont validées ou annulées ensemble. Le résultat HTTP contient seulement `id` et `expiresAt`.
2. Le listener `@ApplicationModuleListener` passe `QUEUED` à `READY`, par une mise à jour conditionnelle idempotente. Il n'appelle aucun transport. Le worker réclame une ligne avec `FOR UPDATE SKIP LOCKED`, valide le bail de 120 secondes et ferme la transaction. Le réseau s'exécute ensuite sans transaction SQL. Un exécuteur dédié, sans accumulation de tâches, empêche le réseau de bloquer le scheduler partagé de reprise Modulith. Une courte transaction confirme le résultat, uniquement si le jeton de possession correspond encore.

Le registre Modulith reste responsable des reprises avant activation. Après activation, la table technique et les baux sont responsables de la remise. Ces deux états sont suivis séparément : la complétion du listener signifie que le worker peut travailler, jamais que le destinataire a reçu le message.

### Intention, idempotence et incidents

- Une intention a un UUID stable, unique en base. Même UUID avec un autre contenu : conflit, détecté par HMAC. L'empreinte reste après la purge pour refuser une réutilisation contradictoire.
- Le fournisseur, l'expéditeur, le destinataire, le sujet et le texte sont figés au moment de l'émission. La clé externe est `notification/<UUID>`. L'ordre JSON du corps HTTP est déterministe, y compris entre deux JVM.
- Le bail peut expirer pendant qu'une instance est toujours vivante. Un autre worker peut alors faire un second appel. Le premier ne peut plus écraser le résultat en base.
- L'adaptateur initial est l'API e-mail Resend, **optionnelle et désactivée par défaut**. Elle utilise le client HTTP du JDK : aucune dépendance ajoutée. Sa [documentation d'idempotence](https://resend.com/docs/dashboard/emails/idempotency-keys) annonce 24 heures. Le système utilise 23 heures depuis la première réservation, avec une minute de marge avant de commencer un appel. La [route d'envoi](https://resend.com/docs/api-reference/emails/send-email) fournit un identifiant de reçu.
- Les reprises réseau sont espacées de 1, 2, 4, 8, 16, 32 puis 60 minutes, avec un maximum de 8 réservations. Hors fenêtre ou au plafond, l'état devient `UNKNOWN` et aucun renvoi automatique n'a lieu.
- Un appel peut avoir réussi avant une panne ou un délai dépassé. **Aucune garantie de livraison exactement une fois**. Une reprise idempotente dépend du contrat et de la conservation des clés par le transport. Un autre fournisseur devra déclarer et respecter sa propre fenêtre.
- Un transport sans déduplication déclare une fenêtre nulle : après une réservation abandonnée ou un résultat ambigu, l'intention devient `UNKNOWN` sans nouvel appel automatique. Aucun fournisseur SMS n'est imposé.
- Aucun ordre de livraison n'est garanti. L'invitation est relue avant chaque appel : acceptée, expirée, révoquée ou familiale avec portail fermé, elle est annulée dans la file.
- La révocation ne rappelle pas un message déjà en vol. L'acceptation conserve ses contrôles d'usage unique, de contact, de validité et de portail.

### Confidentialité

La charge est chiffrée par AES-256-GCM, nonce aléatoire de 96 bits et identifiant d'intention comme AAD. La clé, externe et indépendante du JWT, doit décoder exactement 32 octets Base64. Les erreurs de clé ne citent jamais sa valeur.

L'événement archivé ne contient que l'UUID. Les journaux de notifications ne produisent qu'un nombre agrégé de remises à examiner. Les réponses de statut exposent état, nombre de réservations, horodatage et code technique fixe, sans contact, texte, reçu fournisseur, jeton ou charge chiffrée. Les corps d'erreur et exceptions du fournisseur sont abandonnés.

Les charges terminales sont effacées dans la transaction du résultat. Les charges expirées sont effacées par le worker, y compris quand l'événement attend encore sa reprise. Une tentative avec bail vivant n'est pas effacée avant son résultat ou l'expiration du bail. Les charges bloquées restent chiffrées jusqu'à résolution ou expiration. Le contact nécessaire à l'identité reste dans `invitations`, selon son modèle existant.

Les métadonnées et événements archivés ne sont pas purgés automatiquement. Les sauvegardes doivent avoir leur propre conservation et leur propre contrôle de clé. Une rotation de clé exige de conserver l'ancienne jusqu'au traitement ou à la migration chiffrée des charges en attente : **remplacer la clé directement ne les rechiffre pas**.

### Configuration et canaux

Seuls `disabled` et `resend` sont acceptés. Une configuration explicite incomplète ou invalide bloque le démarrage. Sans canal capable de traiter le contact, la création répond `409 INVITATION_CHANNEL_UNAVAILABLE` et sa transaction est annulée. Une création d'établissement dont le premier administrateur ne peut être contacté est annulée aussi.

Aucun transport SMS, aucun fournisseur SMS ni faux succès ne sont inclus. Une invitation téléphonique est refusée même si l'e-mail fonctionne. L'API des transports distingue déjà EMAIL et SMS ; ajouter le futur adaptateur n'exige pas une nouvelle file.

Le client e-mail utilise une URL HTTPS fixe, sans redirection, une réponse limitée à 8 Kio et un délai total de 10 secondes, configurable entre 1 et 30 secondes. Les traces HTTP du JDK sont refusées lorsque Resend est activé.

### Portail et accès

Le portail reste fermé par défaut. La condition d'invitation vérifie désormais les capacités e-mail **et** téléphone, puisque l'identité admet les deux. Un bean technique ou un e-mail seul ne satisfait plus le contrôle. Le contrôle juridique reste intact : aucun changement de `legally_confirmed`, aucune option de contournement.

Cette description correspond au code livré. Le propriétaire a validé le principe d'une [politique des canaux](../integration-notifications-billing.md#politique-des-canaux-families) exigeant la couverture des seuls canaux explicitement autorisés, pour ne pas imposer durablement le SMS à une future cohorte Families limitée à l'e-mail. Sa mise en œuvre est différée avant toute ouverture et devra tenir compte du modèle d'accès final : vérification des identités, autorisations familiales et contrôles juridiques demeurent obligatoires. Aucun développement de cette évolution n'est autorisé maintenant ; le code actuel et la fermeture du portail sont inchangés.

Le suivi d'établissement exige `MEMBERS_MANAGE`, puis vérifie le rattachement de l'invitation à l'établissement. Le suivi de plateforme est limité aux invitations administratives émises par l'opérateur connecté. Il n'ouvre aucune route vers les dossiers.

## Preuves et limites

Les suites `Notification*Test`, `PayloadCipherTest`, `ResendEmailTransportTest`, `GoLiveTest` et `ModularityTest` tournent sur GitHub Actions. `NotificationRecoveryTest` tue de vraies JVM avant le listener et après l'acceptation du fournisseur de test. Dans ce dernier scénario, le serveur voit deux appels identiques, un seul effet.

Le faux fournisseur est local au runner, sans secret ni envoi réel. La remise en boîte, les rebonds, plaintes et SMS ne sont pas construits. `SUBMITTED` signifie uniquement « accepté par le fournisseur ». La supervision reste une alerte de journal à raccorder avant mise en service. Les identifiants de reçu de ce premier transport sont des UUID ; un adaptateur avec un autre format devra faire évoluer le reçu technique sans exposer sa réponse brute.

Les résultats exacts et le guide de résolution sont dans [le guide des notifications](../notifications.md).

## Intégration avec les travaux parallèles

#26 est intégrée dans `main` et #27 reprend ce commit par fusion. Le [protocole d'intégration](../integration-notifications-billing.md) décrit les tests conjoints et les contrôles du redémarrage. Les résultats du commit d'intégration sont consignés dans #27, qui reste en brouillon. Aucune configuration réelle de Resend n'est autorisée à ce stade.

La migration `V7_1` évite `V7__billing_parties.sql` de #26. Aucun déploiement n'a lieu : les deux branches devront être intégrées et validées ensemble sur une base vierge avant toute future migration persistante. Si une base a déjà appliqué 7.1 avant d'intégrer 7, Flyway refusera la migration manquante par défaut : ne pas activer `outOfOrder` pour masquer ce problème, organiser une migration relue.

Les recouvrements de tests avec #26 utilisent les helpers du canal de test. Les assertions existantes sont conservées et les nouveaux contrôles couvrent la confidentialité des invitations familiales, ainsi que la gestion d'une personne financière par un administrateur invité via la file. Le détail des personnes et de leurs droits reste à la Mission 003.
