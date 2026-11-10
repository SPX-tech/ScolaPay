# 0025 : protection des API publiques d'authentification

- **Statut** : Proposée, implémentée pour revue
- **Date** : 2026-11-10
- **Décideur** : propriétaire du projet, à la revue
- **Complète** : [0010](0010-identite-et-acces.md), sans remplacer le verrouillage des comptes, les contrôles d'invitation ou les JWT révocables

## Risques évalués

Les deux routes publiques `POST /api/v2/auth/login` et `POST /api/v2/invitations/accept` n'avaient pas de limite par origine. La connexion effectue un calcul BCrypt même pour un identifiant inconnu, et garde une connexion SQL pendant ce calcul. L'acceptation d'une invitation peut créer un compte et hacher son mot de passe. Changer d'identifiant contournait donc le seul verrouillage de compte pour consommer des ressources.

Le [guide OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) recommande des réponses génériques et des contrôles contre les attaques automatisées, tout en soulignant le risque de déni de service associé au verrouillage. Ici, cinq échecs continuent à verrouiller un compte quinze minutes. Une attaque ciblée distribuée peut encore provoquer ce verrouillage ; aucun mécanisme de récupération ou MFA n'est ajouté.

## Options et décision

| Option | Intérêt | Limite | Choix |
| --- | --- | --- | --- |
| Compteurs en mémoire uniquement | Peu de composants | Quotas multipliés par les instances, remis à zéro au redémarrage, cardinalité à borner | Réservée aux bornes locales de concurrence |
| Redis ou service spécialisé | Admission distribuée rapide | Nouvelle infrastructure, disponibilité et secrets supplémentaires, sans besoin mesuré à ce stade | Non retenue |
| PostgreSQL existant | Cohérence inter-instance, horloge commune, transactions et contraintes explicites | Écritures et contention ; dépendance de l'admission à la base | Retenue avec bornes strictes |

Aucune dépendance n'est ajoutée.

## Architecture

La chaîne Spring Security installe `PublicAuthFilter` avant le traitement du jeton Bearer et après le firewall, sans enregistrement parallèle dans le conteneur. Elle reconnaît les chemins décodés utilisés par le servlet. Le filtre détermine une origine fiable, applique les budgets d'origine et global, et plafonne le corps à 8 Kio, longueur annoncée ou flux chunked.

Le contrôleur, après validation du JSON, appelle `PublicAuthGuard`. Celui-ci réserve immédiatement un créneau local, sans file d'attente, applique les budgets du couple origine-cible et de la cible, puis appelle le service existant. Les identifiants suivent exactement la normalisation `Identifier.parse` ; les jetons d'invitation gardent leur sensibilité à la casse. Un identifiant de connexion mal formé rejoint une cible commune, sans lecture de compte pour décider du quota.

Les créneaux coûteux sont distincts de la lecture du corps : un client lent ne les conserve pas pendant son envoi. Par défaut, deux connexions et une acceptation d'invitation peuvent effectuer leur travail coûteux simultanément par instance. Deux admissions de compteurs au plus peuvent accéder à SQL simultanément par instance. Ces bornes locales ne sont pas des quotas distribués.

### Quotas partagés

Chaque budget utilise GCRA, un débit de recharge régulier avec une rafale bornée. Le temps vient de PostgreSQL, pas des horloges applicatives. La contrainte unique `(scope, slot)` et `INSERT ... ON CONFLICT DO UPDATE ... WHERE ... RETURNING` assurent l'admission atomique ; voir le [contrat PostgreSQL](https://www.postgresql.org/docs/18/sql-insert.html).

Les transactions des compteurs sont distinctes et terminées avant BCrypt et la transaction métier. Elles utilisent `statement_timeout=250ms`, `lock_timeout=100ms` et un délai transactionnel d'une seconde. L'attente initiale du pool reste soumise au délai Hikari de cinq secondes du dépôt. Une erreur ou un verrou prolongé produit 503, jamais un passage sans protection.

Une requête refusée par son budget d'origine ne consomme pas le budget global. Les autres débits déjà admis restent consommés si une étape suivante échoue : aucune transaction d'authentification annulée ne rembourse les tentatives.

### Cardinalité et confidentialité

Une clé HMAC dédiée est dérivée du secret JWT partagé avec un contexte propre à ce mécanisme. Chaque dimension utilise les seize premiers bits d'un HMAC, soit 65 536 compartiments. Six dimensions et deux compteurs globaux donnent **393 218 lignes au maximum**, garanti en base. Aucun identifiant de compte, IP, contact, mot de passe, jeton ou HMAC complet n'est stocké dans ces lignes.

Cette limite implique des collisions : deux sujets différents peuvent partager un quota. Elles rendent l'admission plus restrictive, jamais plus permissive. Le choix est adapté au démarrage commercial et doit être réévalué avec les volumes réels. Il n'est pas présenté comme une limitation exacte par utilisateur à toute échelle.

Les compartiments entièrement rechargés sont supprimés par lots de 1 000 toutes les cinq minutes, avec `SKIP LOCKED`. Sous charge, aucun délai maximal de purge n'est promis ; la borne de stockage reste effective même si cette tâche est arrêtée.

Les instances doivent partager les mêmes paramètres et le même secret de signature. Une rotation de ce secret change les compartiments associés aux origines et aux cibles ; elle peut reconstituer leurs rafales. Le budget global reste au même emplacement. Il ne faut ni déployer des politiques contradictoires entre instances ni considérer cette dérivation comme une protection indépendante d'une mauvaise gestion des clés.

### Origine et proxy

Le mode direct utilise exclusivement l'adresse du pair réseau et ignore les en-têtes de forwarding. IPv4 est normalisé ; IPv6 est regroupé par /64, pour éviter la rotation triviale de suffixes.

Le mode proxy exige des CIDR explicites, jamais une confiance automatique dans les réseaux privés. Pour un pair de confiance, `X-Forwarded-For` est obligatoire, unique et borné ; lecture de droite à gauche jusqu'au premier saut non approuvé. Un préfixe fourni par un client ne devient pas une autorité. Une chaîne ambiguë, absente, trop longue, contenant un saut nécessaire invalide ou seulement des proxys approuvés est refusée. `Forwarded` concurrent est refusé. Un pair non approuvé ne peut activer ce traitement.

La résolution est littérale, sans DNS, via le [JDK](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/InetAddress.html). Les noms, ports, zones et formes IPv4 ambiguës sont refusés. Les réseaux /0 sont interdits.

Le forwarding automatique Spring/Tomcat est désactivé et les configurations incompatibles empêchent le démarrage. La validation utilise le Binder Spring Boot pour couvrir les alias de variables d'environnement, pas seulement les noms littéraux. L'exploitation doit aussi empêcher tout filtre ou valve programmatique de réécrire l'adresse avant cette politique. Le proxy doit retirer les valeurs non fiables ou ajouter correctement le pair qu'il a observé ; aucun code applicatif ne peut compenser un proxy approuvé qui ment sur ce pair.

## Réponses et vulnérabilité connexe

Les refus de débit sont des 429 RFC 9457, code `AUTH_RATE_LIMITED`, avec `Retry-After` numérique et `Cache-Control: no-store`. La saturation locale produit `503 AUTH_BUSY`, une panne de protection `503 AUTH_PROTECTION_UNAVAILABLE`. Aucun motif n'indique si un compte existe. Les erreurs JSON publiques ne recopient ni leur cause ni les valeurs rejetées dans les journaux ou la réponse. Seuls des compteurs agrégés de refus sont journalisés.

Un test a aussi démontré qu'un mot de passe valide de 72 octets suivi d'un suffixe pouvait être accepté par BCrypt. La borne de 72 octets est donc appliquée à la connexion et à la vérification du mot de passe actuel, en plus de la création déjà bornée. Les entrées trop longues reçoivent un refus générique et ne déclenchent pas une vérification tronquée. Le hachage et la révocation des jetons ne changent pas.

## Vérification et intégration

Les suites `AuthAbuse*Test` utilisent un vrai serveur HTTP et un vrai PostgreSQL. Deux contextes applicatifs indépendants partagent la même base pour vérifier les quotas inter-instance, y compris en concurrence. Une sonde de calcul retenue par un verrou de test vérifie la saturation et la libération des créneaux. Les anciennes suites métier ont des quotas élevés mais finis sur le seul classpath de test ; les nouvelles suites fixent explicitement leurs petits seuils. Aucun interrupteur de désactivation n'est ajouté au code de production.

`V7_2__auth_abuse_protection.sql` ne touche aucune table financière ou de notifications et évite les versions des PR #26 et #27. **Ne pas appliquer cette branche seule à une base persistante** : l'intégration doit inclure V7, V7_1 et V7_2 dans cet ordre, sur PostgreSQL vierge, avant toute décision de mise en service. Ni `outOfOrder`, ni renumérotation des migrations existantes, ni déploiement ne sont autorisés par cette mission.

Configuration et limites : [guide d'exploitation](../auth-abuse-protection.md).
