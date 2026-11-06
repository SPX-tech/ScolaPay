# 0017 : contrôles de mise en service

- **Statut** : Acceptée
- **Date** : 2026-11-06
- **Implémentation** : en place dans `core/`, prouvée par `GoLiveTest`. **Le portail ne peut pas être ouvert aujourd'hui** : les deux conditions ci-dessous ne sont pas remplies. Rien n'est déployé.

## Contexte

Une documentation ne suffit pas à bloquer une ouverture. `legal_majority_rules.legally_confirmed` vaut `false` pour le Tchad, et jusqu'ici aucun code ne lisait ce champ ([0013](0013-majorite-et-autorisations-familiales.md#limites-et-conditions)).

## Décision

Avant toute ouverture réelle du portail, l'application doit **refuser de démarrer** si une condition n'est pas remplie, plutôt que de compter sur une liste à relire.

Conception :

- `scolapay.portal.enabled`, **faux par défaut : le portail est fermé pour les familles**. Faux : la surface familiale de `/api/v2/me`, et plus tard celle des obligations, répond 404, et aucune invitation familiale ne peut être créée ni acceptée. La gestion par le personnel reste disponible.
- Vrai : au démarrage, avant que le serveur HTTP n'écoute, un contrôle parcourt les conditions déclarées par les modules et arrête l'application en nommant toutes celles qui ne sont pas remplies.
- Toute condition s'évalue sur l'état réel, sans option de contournement.

Conditions initiales :

| Condition | Vérifiée par |
| --- | --- |
| Toute règle juridique enregistrée est confirmée | `legally_confirmed`, sur toutes les règles et pas seulement les pays des établissements actuels : sinon, un établissement créé après le démarrage échapperait au contrôle |
| Les invitations ont un canal de remise réel | Présence d'une implémentation de `InvitationDelivery`. **Il n'en existe aucune** : le jeton est rendu au personnel qui l'a émis |
| Secrets et base de production, HTTPS | **Pas encore de condition**, à préciser à l'approche de la mise en service |
| Traitement fiscal validé, voir [0019](0019-nature-des-documents-et-numerotation.md) | **Pas encore de condition**, à ajouter avant toute exploitation réelle |

Confirmer une règle juridique reste un acte relu : `legally_confirmed` ne passe à `true` que par une migration citant sa source, jamais par une API. Seules les bases jetables de test posent leur propre état de confirmation, par une migration présente uniquement dans les ressources de test.

## Conséquences

- Les environnements de développement gardent le portail fermé par défaut et l'ouvrent explicitement.
- Toute nouvelle obligation légale s'ajoute comme une condition, avec un test prouvant qu'elle bloque le démarrage.
- Une invitation émise avant une fermeture reste valable mais ne peut pas être acceptée : le refus annule la transaction, sans consommer l'invitation ni créer de compte.

## Limites

- Le contrôle s'exécute au démarrage. Une règle repassée à `false` directement en base n'a d'effet qu'au redémarrage suivant, ce qui est cohérent avec une modification par migration.
- La fermeture couvre la surface `/api/v2/me/students` et les invitations familiales. Les liens déjà créés restent en base et redeviennent visibles à l'ouverture.
