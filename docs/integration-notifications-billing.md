# Intégration des notifications avec la Mission 003

Le propriétaire a validé le développement du socle de notifications le 2026-11-10. La [PR #27](https://github.com/SPX-tech/ScolaPay/pull/27) attend l'intégration de la [PR #26](https://github.com/SPX-tech/ScolaPay/pull/26), encore ouverte lors de cette revue. La validation du développement ne vaut autorisation ni de fusion, ni de déploiement, ni de configuration réelle de Resend.

Le protocole d'intégration et le principe de politique des canaux ci-dessous sont également validés par le propriétaire. La mise en œuvre de cette politique est explicitement différée : aucun développement supplémentaire n'est engagé à ce stade.

## Ordre d'intégration et migrations

`V7__billing_parties.sql` appartient à #26 et `V7_1__notification_delivery.sql` à #27. Les migrations restent inchangées.

1. Attendre la fusion autorisée de #26 dans `main`. Ne pas intégrer sa branche de travail dans #27 pendant son développement.
2. Récupérer `main` et remettre la branche de #27 à jour à partir de cet état intégré. Conserver le travail financier de #26 et résoudre les recouvrements des helpers de tests, notamment `IntegrationTest`, `FoundationJourneyTest` et `GoLiveTest`. Les jetons restent lus dans la boîte de test, jamais dans les réponses HTTP.
3. Exécuter la suite Java complète et les contrôles usuels dans GitHub Actions, sur PostgreSQL vierge. Vérifier aussi le démarrage du JAR, le portail fermé et les parcours financiers introduits par #26. Aucun fournisseur externe réel n'est utilisé.
4. Vérifier dans `flyway_schema_history` que les migrations ont réussi dans l'ordre **7, puis 7.1**, avec leurs sommes de contrôle attendues. Un nouveau démarrage ne doit rejouer aucune migration versionnée.
5. Consigner les SHA de `main` et de #27 ainsi que les résultats conjoints dans la PR, puis soumettre l'intégration à une nouvelle revue. La réautorisation de fusion et tout éventuel déploiement restent des étapes distinctes du travail de validation.

Le contrôle des migrations s'appuie sur la [validation Flyway des noms, types et sommes de contrôle](https://github.com/flyway/flyway/blob/flyway-12.11.0/documentation/Reference/Commands/Validate.md), sans modifier un historique pour le faire passer.

**Tant que ces étapes ne sont pas satisfaites, ne pas exécuter le JAR de #27 contre une base persistante.** Ne pas y appliquer 7.1 seule, ne pas renuméroter les migrations et ne pas activer `outOfOrder`, réparer l'historique ou lancer un reset pour masquer un mauvais ordre. Une base persistante déjà dans cet état nécessiterait un diagnostic et un plan de correction relu ; les essais d'intégration restent sur des bases jetables.

Les 369 tests Java déjà verts valident le socle de #27 sur sa base de départ. Ils ne constituent pas une validation conjointe avec #26, qui reste à exécuter après son intégration.

## Politique des canaux Families

**Principe architectural validé, mise en œuvre différée avant toute ouverture de Families.** Le code livré exige encore les capacités e-mail et téléphone simultanément. Cette exigence ne doit pas devenir une obligation permanente d'exploiter les deux canaux si le périmètre Families autorisé n'en utilise qu'un.

La règle retenue est : **chaque canal explicitement autorisé pour les invitations Families doit disposer d'un adaptateur correctement configuré**.

- `A` est l'ensemble des canaux autorisés par la politique Families, distinct des identifiants de connexion globaux et des canaux utilisés par le personnel.
- `D` est l'ensemble des capacités disponibles déclarées par les adaptateurs configurés. Il ne se déduit pas de la seule présence d'un bean.
- L'ouverture exige une politique explicite non vide, `A ⊆ D`, la confirmation juridique et toutes les autres conditions de mise en service applicables.
- Lors de son introduction, une politique absente, vide ou contenant une valeur inconnue doit empêcher l'ouverture. Un transport retiré ne doit jamais réduire automatiquement `A` pour faire passer le contrôle.

| Canaux Families autorisés | Capacités disponibles | Condition technique de remise |
| --- | --- | --- |
| E-mail | E-mail | Satisfaite, aucun SMS requis |
| E-mail | SMS seulement ou aucune | Non satisfaite |
| E-mail et SMS | E-mail seulement | Non satisfaite |
| E-mail et SMS | E-mail et SMS | Satisfaite |
| Aucune politique explicite | Toutes | Non satisfaite |

Une condition technique satisfaite ne suffit jamais à ouvrir le portail. La vérification d'une configuration n'est pas une preuve de délivrabilité ni une validation juridique. Aujourd'hui `scolapay.portal.enabled` reste faux, la règle juridique du Tchad reste non confirmée et aucune configuration réelle de Resend n'est autorisée.

La politique doit être appliquée à tous les points d'entrée avant une éventuelle ouverture limitée :

1. **Création** : refuser une invitation familiale par un canal hors de `A`, même si un transport le prend en charge pour le personnel.
2. **Acceptation** : refuser aussi une ancienne invitation familiale par un canal désormais exclu, avant de créer un compte, un lien ou de consommer le jeton.
3. **Worker** : relire la politique avant l'appel et annuler une remise familiale devenue hors périmètre. Un message déjà en vol reste soumis aux contrôles d'acceptation.
4. **Accès Families** : préciser la cohorte éligible avant l'ouverture. « E-mail uniquement » doit aussi traiter les comptes et liens préexistants ; un simple filtre sur les nouvelles invitations ne suffit pas. Un identifiant de connexion par téléphone ne dit pas, à lui seul, si le compte possède un e-mail dont la possession a été établie.

Ces contrôles doivent partager une même politique, sans recopier les règles dans les handlers scolaires ou financiers. Les invitations du personnel conservent leur propre contrôle de transport disponible. Ni le SMS global ni la connexion globale par téléphone ne sont supprimés par une politique Families limitée à l'e-mail.

Le choix d'un canal ne vérifie pas une identité et n'accorde aucun droit familial. L'implémentation devra préserver la vérification de possession du contact, le rattachement au bon compte, les autorisations sur les dossiers, les consentements et les contrôles juridiques du modèle d'accès final. Une adresse e-mail déclarée ne suffit pas à rendre un utilisateur éligible.

Cette évolution devra être implémentée et testée avant toute ouverture de Families, dans le cadre de son modèle d'accès final. Sa validation en CI couvrira : e-mail seul sans SMS, tous les canaux autorisés requis, canal exclu malgré un adaptateur présent, ancienne invitation exclue non consommée, remise en attente annulée, périmètre des comptes existants et échec d'ouverture tant que le juridique n'est pas confirmé. Aucun test ne doit modifier la confirmation juridique des migrations de production. La validation du principe n'autorise pas son développement maintenant.

## Mode désactivé, tests et démonstrations

Le comportement opérationnel et les fixtures existantes sont détaillés dans [le guide des notifications](notifications.md#mode-désactivé-et-amorçage-des-établissements). Il n'existe pas de mode de démonstration dans le JAR qui rendrait les jetons à l'opérateur ou les écrirait dans les journaux. Une démonstration sans fournisseur réel utilise les parcours de test sur une base jetable, avec une capture explicitement réservée au classpath de test.
