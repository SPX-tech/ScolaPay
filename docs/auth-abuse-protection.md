# Protection de l'authentification publique

Décision : [0025](decisions/0025-protection-authentification-publique.md). Le périmètre est limité à la connexion et à l'acceptation d'invitations de `core/`. Le portail reste fermé par défaut. Les responsabilités financières, les notifications et les clients ne changent pas.

## Seuils de départ

| Route | Origine | Origine + cible | Cible toutes origines | Global | Calculs simultanés par instance |
| --- | --- | --- | --- | --- | --- |
| Connexion | Rafale 180, recharge 180/min | Rafale 4, recharge 4/min | Rafale 20, recharge 20/15 min | Rafale 1 200, recharge 1 200/min | 2 |
| Invitation | Rafale 60, recharge 60/min | Rafale 10, recharge 10/min | Rafale 20, recharge 20/15 min | Rafale 300, recharge 300/min | 1 |

Ce sont des **rafales et débits de recharge GCRA**, pas un nombre maximal strict sur une fenêtre glissante. Après quatre connexions immédiates pour un même couple origine-identifiant, un nouveau créneau se recharge en quinze secondes. Le quota d'origine plus généreux permet des comptes distincts derrière une même IP. Il ne dispense pas des quotas de cible ni du budget global.

Les succès, échecs et refus métier consomment leur admission. Les requêtes mal formées consomment l'admission d'origine et globale lorsqu'elles atteignent le parsing. Une longueur annoncée déjà excessive peut être refusée avant ces accès SQL. Les calculs saturés ne sont pas mis en attente : le client reçoit 503 et peut réessayer après le délai indiqué.

## Configuration

Les paramètres sont lus sous `scolapay.auth-abuse`. Il n'existe pas de mode `disabled`.

| Paramètre | Défaut | Contraintes |
| --- | --- | --- |
| `login.origin.capacity`, `login.origin.period` | `180`, `PT1M` | Capacité entière de 1 à 100 000, période de 1 s à 1 h |
| `login.global.capacity`, `login.global.period` | `1200`, `PT1M` | Idem |
| `login.pair.capacity`, `login.pair.period` | `4`, `PT1M` | Idem |
| `login.target.capacity`, `login.target.period` | `20`, `PT15M` | Idem |
| `invitations.origin.capacity`, `invitations.origin.period` | `60`, `PT1M` | Idem |
| `invitations.global.capacity`, `invitations.global.period` | `300`, `PT1M` | Idem |
| `invitations.pair.capacity`, `invitations.pair.period` | `10`, `PT1M` | Idem |
| `invitations.target.capacity`, `invitations.target.period` | `20`, `PT15M` | Idem |
| `login.concurrency`, `invitations.concurrency` | `2`, `1` | Entiers de 1 à 64 |
| `counter-concurrency` | `2` | Entier de 1 à 16 |
| `max-body-bytes` | `8192` | Entier de 1 024 à 16 384 |
| `proxy.mode` | `direct` | `direct` ou `trusted` |
| `proxy.trusted-cidrs` | Vide | Chaîne de CIDR séparés par des virgules, obligatoire seulement en mode trusted, 32 maximum |
| `proxy.max-hops` | `8` | Entier de 1 à 16 |

La mauvaise configuration des paramètres contrôlés empêche le démarrage. Le Binder prend en charge les noms canoniques et les alias d'environnement Spring Boot. Les paramètres doivent être identiques sur toutes les instances. Une variation de politique ou de clé pendant un déploiement progressif peut changer les quotas effectifs ; ne pas la traiter comme une opération transparente.

Les valeurs de départ n'ont pas valeur de capacité certifiée pour un établissement ou un volume donné. Les ajuster après mesure du trafic et des contraintes des instances et du pool, en conservant une réserve pour les autres fonctions du backend. Par défaut, les calculs coûteux de ces deux routes publiques occupent au plus trois connexions, et les admissions de compteurs au plus deux, par instance, sur les dix connexions Hikari configurées. Ce n'est pas une borne pour les routes authentifiées ou pour l'ensemble du backend.

## Reverse proxy

En mode direct, `Forwarded`, `X-Forwarded-For`, `X-Real-IP` et les en-têtes de CDN ne sont pas des sources d'autorité.

Pour un proxy :

- Garder `server.forward-headers-strategy=none` et ne pas ajouter de `ForwardedHeaderFilter`, de valve RemoteIp ou de réécriture programmatique avant la chaîne.
- Déclarer le mode `trusted` et les adresses exactes des proxys, de préférence /32 ou /128. Ne pas approuver tout un réseau d'utilisateurs ou l'ensemble des réseaux privés.
- Le proxy doit écraser les en-têtes clients non fiables ou ajouter correctement le pair réseau observé. Utiliser un seul `X-Forwarded-For`, sans `Forwarded` concurrent.
- La liste est lue de droite à gauche. Les sauts approuvés sont retirés jusqu'au premier pair non approuvé, qui devient l'origine. Un suffixe invalide ou une chaîne composée uniquement de proxys approuvés produit `400 CLIENT_ORIGIN_INVALID`.
- La chaîne est plafonnée à 1 024 caractères et au nombre de sauts configuré. Pas de nom DNS, port, IPv4 abrégé, notation octale, crochets ou zone IPv6.
- Un appel direct depuis une adresse non approuvée reste limité sur son vrai pair, même s'il apporte ces en-têtes.

Les CIDR d'exemple éventuels doivent utiliser les plages de documentation, jamais une adresse réelle du déploiement dans ce dépôt public. L'option concerne la limitation d'origine ; elle ne transforme pas les en-têtes de protocole en preuve de HTTPS. Le proxy doit assurer TLS, HSTS et les limites de connexion à sa frontière.

## Contrat client et diagnostic

| Statut et code | Sens | Comportement du client |
| --- | --- | --- |
| `429 AUTH_RATE_LIMITED` | Une admission de débit n'est pas disponible | Respecter `Retry-After`, ne pas multiplier les essais |
| `503 AUTH_BUSY` | Un créneau local de calcul ou de compteur est occupé | Réessayer après le délai, avec dispersion des reprises |
| `503 AUTH_PROTECTION_UNAVAILABLE` | La protection ne peut pas accéder à son stockage | Réessayer après le délai ; pas de passage sans protection |
| `400 CLIENT_ORIGIN_INVALID` | Le proxy approuvé n'établit pas une origine acceptable | Corriger la chaîne de proxy, pas les identifiants |
| `413 PAYLOAD_TOO_LARGE` | Corps public trop grand | Réduire le corps ; longueur annoncée et chunked sont contrôlés |
| `400 AUTH_REQUEST_INVALID` | JSON ou données publiques non valides | Corriger la demande sans relance automatique |

Les refus utilisent `application/problem+json`, un `code` stable, un message générique et `Cache-Control: no-store`. Les 429 et 503 portent un `Retry-After` entier en secondes. Ils ne renseignent ni la règle exacte déclenchée, ni l'existence d'un compte, ni l'IP calculée. Pour un futur client cross-origin, l'exposition CORS de ce header devra être configurée avec les règles d'origine du client ; ce chantier ne modifie pas CORS.

Les compartiments stockent uniquement scope, numéro de compartiment et temps théorique d'arrivée. Ils peuvent agréger plusieurs sujets : examiner les compteurs agrégés et les taux HTTP, sans chercher à exporter contacts, jetons ou corps JSON pour déboguer.

## Limites et vérification avant service

- Une attaque ciblée depuis plusieurs origines peut toujours verrouiller un compte après cinq échecs. Le seuil existant est conservé ; récupération sécurisée, MFA et protections adaptatives restent des sujets distincts.
- Une attaque suffisamment distribuée peut consommer le budget global ou les créneaux locaux et affecter des utilisateurs légitimes. La protection borne le travail, elle ne garantit pas la disponibilité sous toute attaque.
- Les collisions de compartiments peuvent limiter des sujets sans lien. C'est une restriction supplémentaire, jamais une façon d'obtenir plus d'essais pour une même cible.
- Le contrôle applicatif ne bloque ni un DDoS réseau, ni toutes les connexions lentes. Configurer et vérifier au proxy les délais totaux de lecture des en-têtes et corps, tailles d'en-têtes, connexions, files et bande passante. Les [timeouts et files Tomcat](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html) ne constituent pas à eux seuls une garantie de durée totale d'envoi d'un corps.
- Le pool de données reste partagé. Les attentes et délais SQL sont bornés ; une base indisponible rend l'authentification temporairement indisponible.
- Les routes authentifiées, y compris le changement de mot de passe, et le healthcheck ne reçoivent pas ces quotas publics. Aucune protection globale de toutes les routes n'est revendiquée.
- Les résultats de tests attestent les règles d'admission, pas une capacité de charge de production ni une conformité juridique.

L'intégration persistante doit réunir V7 (#26), V7_1 (#27) et V7_2 (ce chantier), dans cet ordre, avec validation complète sur base vierge. Aucun déploiement n'est inclus.

## Preuves

Les suites `AuthAbuseHttpTest` et `AuthAbuseProxyTest` tournent dans le workflow GitHub Actions `Auth abuse protection`. Les fixtures des parcours métier existants ont des seuils élevés sur le seul classpath de test ; les tests de ce chantier fixent explicitement de petits budgets. Elles ne changent ni le défaut du JAR ni les contrôles d'invitation.

Résultats du code `5a02b9b`, repris sans changement fonctionnel dans `985ca9b` :

| Vérification | Résultat | Preuve |
| --- | --- | --- |
| Protection ciblée, HTTP et PostgreSQL réels | 30 tests, aucun échec ni test ignoré | [CI ciblée](https://github.com/SPX-tech/ScolaPay/actions/runs/42869363613) |
| Suite Java complète, compilation, démarrage et surface publique du JAR | 360 tests, aucun échec ni test ignoré ; JAR validé | [CI générale](https://github.com/SPX-tech/ScolaPay/actions/runs/42869629507) |
| Images et proxy | Contrôles verts | [Containers](https://github.com/SPX-tech/ScolaPay/actions/runs/42869629777) |

La [PR #28](https://github.com/SPX-tech/ScolaPay/pull/28) contient les résultats et le statut des contrôles de clôture documentaires.

La première exécution a démontré l'absence de limitation d'origine. Les vérifications ultérieures ont détecté l'acceptation d'un suffixe après 72 octets. Elles couvrent également les alias d'environnement du forwarding, avec une source de configuration représentative de celle de Spring Boot. Les liens et résultats définitifs sont consignés dans la PR. La validation des chantiers #26, #27 et de ce chantier réunis reste à effectuer après leur intégration.
