# 0016 : outbox durable

- **Statut** : Proposée. Le besoin est exigé par le propriétaire, le choix technique reste à prouver par le lot 1 de la Mission 003.
- **Date** : 2026-11-06
- **Implémentation** : aucune.

## Contexte

`StudentEnrolled` et `EnrollmentEnded` sont publiés de façon synchrone, sans persistance. Un traitement asynchrone perdu après validation (émission d'une obligation, notification) serait une anomalie financière.

## Options

| Critère | Registre de publication Spring Modulith (JDBC) | Table maison avec `FOR UPDATE SKIP LOCKED` |
| --- | --- | --- |
| Écriture dans la transaction métier | Oui, automatique pour les listeners transactionnels | Oui, explicite |
| Code à maintenir | Faible, déjà dans la stack | Worker, reprises, purge, supervision |
| Reprise des échecs | États `FAILED` et `RESUBMITTED`, resoumission à déclencher par nous | À écrire |
| Plusieurs instances | Republication au redémarrage déconseillée par sa documentation : à vérifier | Sûr par construction |
| Sérialisation | Liée aux types Java : renommer un événement affecte ceux en attente | Schéma JSON versionné, sous notre contrôle |
| Lecture par établissement | Pas de colonne dédiée | Colonne `establishment_id` |

Ces éléments du registre viennent de sa documentation 2.1, **ils n'ont pas été testés ici**.

## Décision proposée

Utiliser le registre Spring Modulith, déjà dans la stack, plutôt qu'écrire un worker :

- Schéma géré par Flyway, initialisation automatique désactivée.
- Mode de complétion `archive`, pour garder la trace sans croissance de la table active.
- Événements en records versionnés dans l'API publique des modules, jamais renommés sans migration.
- Listeners idempotents : la livraison est « au moins une fois », un doublon ne doit rien changer. Les contraintes d'unicité de [0015](0015-modele-de-facturation.md) en sont la garantie.
- Resoumission planifiée des échecs, bornée, et supervision de ce qui reste en attente.

**Condition de sortie du lot 1, sur PostgreSQL réel.** Si l'un de ces tests échoue, on bascule sur la table maison :

1. Un arrêt brutal entre la validation et le traitement ne perd pas l'événement.
2. Un listener en échec est repris sans doublon métier.
3. Une transaction annulée ne publie rien.
4. Deux instances simultanées ne produisent aucun effet métier en double.

## Conséquences

- Aucun événement asynchrone critique n'existe avant que ces tests soient verts.
- La livraison vers un service externe (notifications, futur service Go) devra être réévaluée le moment venu : le mode `outbox` d'externalisation du registre n'est pas évalué ici.
