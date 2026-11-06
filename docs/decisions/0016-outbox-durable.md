# 0016 : outbox durable

- **Statut** : Acceptée, sur la base des tests ci-dessous
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet, évaluation menée au lot 1 de la Mission 003
- **Implémentation** : registre en place dans `core/`. Le premier listener de production est l'[émission des obligations](0021-obligations-et-journal.md).

## Contexte

`StudentEnrolled` et `EnrollmentEnded` étaient publiés de façon synchrone, sans persistance. Un traitement asynchrone perdu après validation serait une anomalie financière : une obligation jamais émise.

## Décision

Le **registre de publication de Spring Modulith** (JDBC, sur PostgreSQL) est retenu. Il a satisfait tous les tests prévus, et ses limites se gèrent par le code et la supervision. La solution PostgreSQL dédiée, avec `FOR UPDATE SKIP LOCKED`, reste le repli si l'une des conditions ci-dessous cessait d'être vraie.

Mise en œuvre :

- Schéma créé par la migration `V2`, initialisation automatique désactivée.
- Mode de complétion `archive` : la table active ne contient que ce qui reste à traiter.
- Publications bloquées surveillées dans les trois états, après 5 minutes. **Sans ces durées, le moniteur de la bibliothèque reste inactif et rien ne reprend après un arrêt brutal, sans aucune erreur** : un test protège leur présence.
- `@EnableScheduling` explicite : sans lui, l'ordonnancement ne tient qu'à un effet de bord de Spring Modulith Moments, sur un fil non géré par Spring Boot.
- Reprise par `OutboxRecovery` : délai doublé à chaque essai, plafonné à une heure, **sans nombre maximal d'essais**, car une obligation financière ne s'abandonne pas.
- Alerte dans les journaux au-delà de 15 minutes sans traitement, calculée en SQL.

## Preuves

`OutboxReliabilityTest` lance de vraies JVM sur un vrai PostgreSQL et consomme le vrai événement de la vraie route d'inscription. L'arrêt brutal est un `SIGKILL`.

| Condition | Scénario | Résultat |
| --- | --- | --- |
| 1. Un arrêt brutal ne perd pas l'événement | Entre la validation et le traitement, puis pendant le traitement | Repris par une nouvelle instance, effet produit une fois |
| 2. Une reprise ne crée pas de doublon | Arrêt après l'effet, avant la fin du traitement. Listener en échec | Listener rejoué, effet unique |
| 3. Une transaction annulée ne publie rien | Échec à la validation elle-même, après la publication | Aucune ligne, aucun effet |
| 4. Deux instances ne doublent aucun effet | Traitement plus long que le délai de blocage, donc reprise par l'autre instance pendant qu'il tourne | Exécutions réellement en double, 20 effets pour 20 événements |
| Événement irrécupérable | Échec permanent | Jamais abandonné, n'empêche pas les autres, alerte |
| Type d'événement disparu | Ligne d'une classe renommée | Ignorée par le registre, signalée par l'alerte |
| Délai de reprise | Délai d'une heure | Aucun nouvel essai pendant ce délai |

Ces tests savent échouer : sur 17 défauts injectés un à un dans le lot 1, outbox et mise en service réunies, tous sont détectés. Cinq exécutions du code intact sont toutes vertes.

Une première campagne a aussi corrigé ce document : il affirmait à tort qu'aucune reprise n'aurait lieu sans `@EnableScheduling`, et un test ne démarrait même pas son instance.

## Limites à respecter

Elles viennent de la lecture du code source 2.1.2 et sont confirmées par les tests.

1. **Un listener peut s'exécuter deux fois, y compris en même temps.** Le registre ne l'empêche pas : `markResubmitted` ne refuse que l'état `RESUBMITTED`, et une publication `PROCESSING` peut être reprise. **Tout listener doit donc être idempotent, garanti par une contrainte PostgreSQL**, comme celle de [0015](0015-modele-de-facturation.md#empêcher-la-double-facturation). Le test 4 montre que ce cas arrive vraiment.
2. **Aucun ordre n'est garanti.** `EnrollmentEnded` peut être traité avant `StudentEnrolled`. Le lot 4 doit relire l'état de l'inscription au lieu de se fier à l'ordre d'arrivée.
3. **La taille de lot s'applique avant le délai de reprise.** Si plus de 1 000 publications anciennes attendent leur délai, les plus récentes en échec ne sont plus reprises. Rien n'est perdu, l'alerte se déclenche, et un test fige ce comportement.
4. **Renommer un événement est une migration.** Le registre ignore une ligne dont la classe n'existe plus, avec un simple avertissement. Une évolution d'événement doit rester additive, car les lignes en attente restent à relire.
5. **Un listener occupe une connexion pendant tout son traitement.** Huit fils de traitement pour dix connexions : un listener ne doit ni appeler un service externe ni attendre dans sa transaction.

## Conséquences

- Dépendance ajoutée : `spring-modulith-starter-jdbc`, de la même famille que Spring Modulith déjà présent, sans service supplémentaire à exploiter.
- L'alerte n'est qu'une ligne de journal : elle devra être reliée à une vraie supervision avant la mise en service.
- La livraison vers un service externe (notifications, futur service Go) n'est pas évaluée ici. Le mode `outbox` d'externalisation du registre reste à examiner le moment venu.

## Conservation

Politique **provisoire**, à confirmer avec les obligations de conservation applicables :

- **180 jours** pour les événements archivés, traités correctement.
- Les événements en échec ou en attente sont conservés **jusqu'à leur résolution**, quelle que soit leur ancienneté.
- **Aucune purge automatique n'est développée pour le moment.** `event_publication_archive` croît donc sans limite, et rien n'applique encore les 180 jours.
- **Ces archives de transport ne remplacent ni le journal d'audit ni les documents financiers**, qui suivent leurs propres règles : voir [0019](0019-nature-des-documents-et-numerotation.md). Rien ne doit dépendre d'elles pour reconstituer un montant ou une décision.
