# Protection des endpoints publics d'authentification

Branche `feat/auth-abuse-protection`, issue de `main` c28862c. Worktree séparé des PR #26 et #27.
Aucun déploiement, VPS, fournisseur, changement financier ou notification.

## Décisions à vérifier

- Conserver le verrouillage de compte et la révocation JWT. La limitation ne dépend pas de l'existence du compte.
- PostgreSQL plutôt que mémoire seule ou Redis : quotas atomiques GCRA, horloge de la base, transactions courtes et bornées avant BCrypt.
- Origine généreuse pour les IP partagées, couple origine-identifiant plus strict, cible indépendante de l'origine, budget global par endpoint.
- Cardinalité bornée par compartiments HMAC (65 536 par dimension), sans IP, identifiant, mot de passe ni jeton stockés.
- Bornes locales de concurrence pour les opérations coûteuses, distinctes de la lecture HTTP. Corps publics plafonnés à 8 Kio.
- Connexion directe par défaut, forwarding automatique désactivé. Mode proxy explicite avec CIDR, chaîne bornée et lecture de droite à gauche.
- Refus 429 avec Retry-After ; indisponibilité de la protection ou saturation locale : 503 sans laisser passer.
- Limite restante : verrouillage ciblé distribué, DDoS réseau et connexions lentes nécessitent d'autres défenses.
- Migration V7_2 : aucune application persistante isolée avant intégration ordonnée de V7 (#26), V7_1 (#27) et V7_2. Migrations existantes intactes.

## Tâches

- [ ] Tests HTTP de régression observés en échec dans GitHub Actions.
- [ ] Limiteur PostgreSQL et politique de proxy, tests des bornes et de concurrence.
- [ ] Intégration avant les endpoints et les calculs coûteux ; confidentialité et mot de passe dépassant 72 octets.
- [ ] Parcours légitimes derrière NAT, multi-instance, erreurs et pannes vérifiés sur vrai PostgreSQL.
- [ ] ADR, configuration, modèle de menace, limites et intégration des trois migrations documentés.
- [ ] CI complète, JAR et PR indépendante ; aucune fusion.
