# 0017 : contrôles de mise en service

- **Statut** : Principe accepté par le propriétaire, conception proposée
- **Date** : 2026-11-06
- **Implémentation** : **aucune. Aujourd'hui, rien n'empêche techniquement d'ouvrir `/api/v2/me` si l'application est déployée.** Rien n'est déployé.

## Contexte

Une documentation ne suffit pas à bloquer une ouverture. `legal_majority_rules.legally_confirmed` vaut `false` pour le Tchad, mais aucun code ne lit ce champ ([0013](0013-majorite-et-autorisations-familiales.md#limites-et-conditions)).

## Décision

Avant toute ouverture réelle du portail, l'application doit **refuser de démarrer** si une condition n'est pas remplie, plutôt que de compter sur une liste à relire.

Conception proposée :

- `scolapay.portal.enabled`, **faux par défaut**. Faux : la surface `/api/v2/me/students`, et plus tard celle des obligations, répond 404.
- Vrai : au démarrage, un contrôle parcourt des conditions déclarées par les modules et arrête l'application au premier échec, en nommant la condition.
- Toute condition s'évalue sur l'état réel, sans option de contournement.

Conditions initiales :

| Condition | Vérifiée par |
| --- | --- |
| Règle juridique confirmée pour le pays de chaque établissement actif | `legally_confirmed` |
| Les invitations ont un canal de remise réel | Présence d'un service de notification configuré |
| Secrets et base de production, HTTPS | À préciser à l'approche de la mise en service |

Confirmer une règle juridique reste un acte relu : `legally_confirmed` ne passe à `true` que par une migration citant sa source, jamais par une API. Les tests posent leur propre état de confirmation dans leur base jetable.

## Conséquences

- Les environnements de développement gardent le portail fermé par défaut et l'ouvrent explicitement.
- Toute nouvelle obligation légale s'ajoute comme une condition, avec un test prouvant qu'elle bloque le démarrage.
