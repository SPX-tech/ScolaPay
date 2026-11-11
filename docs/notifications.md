# Notifications et invitations

Le module `core/notifications` prépare une remise technique réutilisable. Aucun envoi réel, déploiement ou ouverture de Families n'est effectué par ce chantier. Décision : [0024](decisions/0024-notifications-et-remise-des-invitations.md).

Le développement a été validé par le propriétaire et #27 est intégrée dans `main` au commit `212b6d3`. Le [protocole d'intégration](integration-notifications-billing.md) décrit les vérifications sur PostgreSQL vierge. La remise reste désactivée par défaut. La configuration réelle de Resend relève d'une mise en service distincte.

## Configurer l'e-mail

| Variable | Valeur attendue |
| --- | --- |
| `SCOLAPAY_NOTIFICATIONS_EMAIL_PROVIDER` | `disabled` par défaut, ou `resend` explicitement |
| `SCOLAPAY_NOTIFICATIONS_EMAIL_API_KEY` | Clé d'envoi Resend, injectée hors dépôt |
| `SCOLAPAY_NOTIFICATIONS_EMAIL_FROM` | Adresse e-mail normalisée d'un domaine vérifié chez le fournisseur |
| `SCOLAPAY_NOTIFICATIONS_ENCRYPTION_KEY` | 32 octets aléatoires, encodés en Base64, indépendants du secret JWT |

Le délai `scolapay.notifications.email.timeout` vaut `PT10S`, borné entre 1 et 30 secondes. Le worker se réveille toutes les 10 secondes, traite au plus 25 réservations par passage et purge les charges expirées. Le transport est HTTPS, sans redirection ni URL configurable.

La configuration suffit à assembler l'adaptateur, elle ne prouve ni l'autorisation du domaine d'envoi, ni la délivrabilité. Avant tout usage réel, vérifier le contrat du fournisseur, le domaine, ses quotas, son traitement des données et la supervision dans un environnement approuvé. Aucun appel au fournisseur réel n'est fait par la CI.

Sans configuration, les invitations répondent `409 INVITATION_CHANNEL_UNAVAILABLE`. Aucun SMS n'est simulé : un téléphone produit ce refus même avec Resend actif. La création de l'établissement et de son premier administrateur est atomique avec cette vérification.

Le portail reste fermé. Dans le code actuel, même avec l'e-mail configuré et les règles juridiques confirmées, l'absence de SMS empêche son ouverture. Le principe d'une [politique fondée sur les seuls canaux Families autorisés](integration-notifications-billing.md#politique-des-canaux-families) est validé pour permettre ultérieurement un périmètre e-mail sans SMS. Sa mise en œuvre est différée et devra être testée avant toute ouverture, selon le modèle d'accès final et sans affaiblir la vérification des identités ni les autorisations familiales. Aucun changement juridique ni fiscal n'est apporté.

## Mode désactivé et amorçage des établissements

Avec `disabled` et sans autre transport disponible, le serveur peut démarrer et l'opérateur d'amorçage peut se connecter. **Cela ne permet pas de créer un premier établissement utilisable** : `POST /api/v2/platform/establishments` crée l'établissement et son invitation d'administrateur dans une seule transaction. Le refus `409 INVITATION_CHANNEL_UNAVAILABLE` annule l'ensemble, y compris l'audit d'établissement. Aucun établissement partiel, aucune invitation et aucune notification ne sont conservés pour cette création. Le code d'établissement n'est pas réservé par la tentative annulée.

Les invitations de personnel et réémissions administratives sont également refusées en l'absence de canal. Sur un établissement déjà existant, le mode désactivé ne supprime ni les comptes ni les adhésions ; les opérations du personnel restent soumises aux droits existants. Families demeure fermé par défaut. Désactiver un transport ne simule pas une livraison des messages déjà en file : ils peuvent être bloqués jusqu'à résolution ou expiration selon leur état.

| Contexte | Parcours maîtrisé sans fournisseur réel |
| --- | --- |
| Vérification du mode désactivé | Démarrer sans adaptateur de test et vérifier le refus et l'absence de création partielle |
| Tests des parcours métier, création d'établissement comprise | Import explicite de `TestInvitationDelivery` sur une base jetable ; le destinataire de test lit la boîte de test pour accepter |
| Tests du worker, des reprises et du transport HTTP | Transport contrôlé ou serveur HTTP local au runner, sans appel externe |
| Démonstration à partir du JAR de production, sur base vide et sans fournisseur | Création du premier établissement refusée ; aucun mode de démonstration intégré n'existe |

Les fixtures sont sous `core/src/test/` et ne sont pas assemblées dans le JAR de production. Leur boîte peut conserver un jeton en clair dans la mémoire ou la base jetable des tests ; elle n'est exposée par aucune route HTTP et ne doit jamais être transposée dans une base persistante. Les exemples de démonstration doivent utiliser ces scénarios contrôlés, sans extraire le jeton de `firstAdminInvitation` ou du JSON d'une invitation. Aucun fallback de journal, réponse API enrichie ou configuration réelle de Resend n'est ajouté.

## Contrat HTTP

Les réponses d'émission gardent `id` et `expiresAt`, et **ne contiennent plus `token`**. Cela vaut aussi pour `firstAdminInvitation`. Le seul destinataire du jeton est le contact invité. L'e-mail contient un jeton à utiliser avec le parcours d'acceptation existant ; aucune URL vers une interface encore inexistante n'est inventée.

Suivi technique :

- Personnel habilité : `GET /api/v2/establishments/{establishmentId}/invitations/{invitationId}/delivery`, permission `MEMBERS_MANAGE`.
- Opérateur : `GET /api/v2/platform/establishments/{establishmentId}/admin-invitations/{invitationId}/delivery`, uniquement pour les invitations administratives qu'il a émises.

Champs : `state`, `attempts`, `updatedAt`, `failureCode`. Le nombre `attempts` est un nombre de réservations de worker, pas une preuve d'appels ou de réceptions. Un 404 protège une référence d'un autre établissement. Le statut ne donne aucun accès aux données académiques ou financières.

## États et résolution

| État | Sens | Action |
| --- | --- | --- |
| `QUEUED` | Transaction validée, listener encore attendu | Le registre Modulith assure la reprise. Examiner son alerte si ancien |
| `READY` | Disponible pour le worker | Traitement automatique |
| `PROCESSING` | Bail acquis, appel éventuellement en cours | Reprise après expiration du bail de 120 s |
| `RETRY` | Incident temporaire, prochaine tentative planifiée | Attendre le délai exponentiel, vérifier le fournisseur |
| `BLOCKED` | Transport retiré ou charge indéchiffrable | Restaurer la configuration ou la clé, sans exporter le contenu ; nouveau contrôle après 5 min |
| `SUBMITTED` | Accepté par le fournisseur | Aucune preuve de réception en boîte. Charge purgée |
| `FAILED` | Refus permanent | Corriger la cause, puis révoquer et réémettre si nécessaire. Charge purgée |
| `CANCELLED` | Invitation inutilisable, portail fermé ou charge expirée | Aucun nouvel envoi. Charge purgée |
| `UNKNOWN` | Fenêtre ou nombre de reprises épuisé, résultat externe incertain | Examiner côté fournisseur avec la clé technique. Ne pas relancer la même intention. Charge purgée |
| `UNAVAILABLE` | Invitation antérieure au nouveau socle, sans trace de remise | Ne pas considérer l'absence de trace comme une livraison |

Le résultat externe peut être incertain après un incident. Une réémission est une nouvelle invitation, avec un nouveau jeton et un nouvel UUID. Elle ne révoque pas implicitement les précédentes dans le modèle existant : une révocation explicite reste nécessaire si l'ancienne doit être invalidée. Pour les toutes premières invitations administratives, sans personnel déjà habilité, cette résolution reste une opération d'exploitation relue ; aucun renvoi aveugle n'est automatisé.

Les alertes sont des compteurs sans destinataire ni jeton. Relier les états terminaux en échec, les blocages et les attentes anciennes à une supervision réelle avant mise en service. Ne pas activer de traces de paramètres JDBC, de corps HTTP ou de secrets d'environnement sur une instance réelle.

## Chiffrement et conservation

La charge contient expéditeur, destinataire, sujet et texte. Elle est chiffrée en AES-256-GCM avec un nonce neuf et l'UUID comme AAD. L'événement Modulith ne contient que l'UUID. Aucun jeton clair n'est ajouté à une table de production.

Conserver et sauvegarder la clé séparément des données. Une mauvaise clé bloque la charge, sans exposer l'erreur cryptographique. Restaurer la bonne clé avant les limites de reprise. Pour une rotation, vider la file avec l'ancienne clé ou préparer une migration de rechiffrement relue ; aucun gestionnaire de plusieurs clés n'est inclus.

Les charges sont purgées au résultat terminal ou à l'expiration (sept jours au plus pour les invitations). Si le worker est arrêté, la purge attend son retour. Les métadonnées techniques et archives Modulith restent conservées sans purge automatique ; la politique et la conservation des sauvegardes restent à arrêter avant lancement.

## Ajouter une fonctionnalité

Un module consommateur peut appeler `Notifications.enqueue(Request)` dans sa transaction métier. Il fournit un UUID d'intention stable, une catégorie, un canal, un contact normalisé, un sujet, un texte et une expiration bornée à sept jours. Il doit vérifier ses propres droits et son périmètre : la file ne remplace pas les autorisations métier.

Les invitations utilisent leur UUID comme intention. Leur garde relit l'identité avant chaque appel. Une autre catégorie avec annulation métier devra ajouter sa propre garde, plutôt que réutiliser celle des invitations.

Un transport implémente `NotificationTransport`, avec un nom stable, un canal et une fenêtre d'idempotence contractuelle de 5 minutes à 23 heures, ou `Duration.ZERO` sans déduplication. Sans déduplication, une réservation abandonnée ou un résultat ambigu devient `UNKNOWN`, sans reprise automatique. Un adaptateur ne doit jamais déclarer une fenêtre fictive. Changer les octets du corps d'une intention en attente exige une migration ou la résolution de la file.

## Validation

Le workflow `Notifications` couvre les scénarios du chantier sur un PostgreSQL jetable et un faux fournisseur local. La CI générale couvre le reste du backend Java et démarre le JAR. Aucun build ni test lourd ne s'exécute sur le poste ou le VPS.

Validation du socle avant intégration : `d7224bd`. Les résultats du commit réunissant les notifications et le lot 6 sont consignés séparément dans [#27](https://github.com/SPX-tech/ScolaPay/pull/27).

| Vérification | Résultat | Preuve |
| --- | --- | --- |
| Suite ciblée, PostgreSQL réel et fournisseur local | 50 tests, aucun échec ni test ignoré | [Notifications](https://github.com/SPX-tech/ScolaPay/actions/runs/42850922300) |
| Suite Java complète et JAR exécutable | 369 tests, dont 39 nouveaux, aucun échec ni test ignoré. JAR démarré et surface publique vérifiée | [CI générale](https://github.com/SPX-tech/ScolaPay/actions/runs/42851059251) |
| Images et proxy | Contrôle vert | [Containers](https://github.com/SPX-tech/ScolaPay/actions/runs/42851227492) |
| Confidentialité avant correction | Deux échecs sur le jeton présent dans le JSON | [Régression initiale](https://github.com/SPX-tech/ScolaPay/actions/runs/42844917675) |
| Annulation HTTP avant correction | Un échec prouve qu'une expiration de future n'annulait pas l'appel | [Régression de ressources](https://github.com/SPX-tech/ScolaPay/actions/runs/42850557595) |

Deux JVM sont réellement tuées : après le commit et avant le listener, puis après l'acceptation par le fournisseur et avant la confirmation en base. Le second cas produit deux appels identiques et un seul effet côté fournisseur de test. Le test a aussi détecté puis fait corriger l'ordre JSON instable entre JVM.

Le délai HTTP porte sur la lecture complète du corps. Au dépassement, la future encore en cours reçoit `cancel(true)`, selon le [contrat du JDK](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html). Ce geste vise à libérer les ressources ; il ne prouve pas que le fournisseur n'a pas reçu la demande. Les réponses excessives sont annulées à 8 Kio.

Les contrôles d'intégration couvrent V7 puis V7_1, la reprise sur la même base, les parcours financiers et les permissions réunis. Le transport de test reste local au runner. La réception en boîte, les SMS et les rebonds restent à traiter avant les mises en service concernées.
