# Protection des endpoints publics d'authentification

Branche `feat/auth-abuse-protection`, issue de `main` c28862c. Worktree séparé des PR #26 et #27.
Aucun déploiement, VPS, fournisseur, changement financier ou notification.

## Décisions retenues

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

- [x] Tests HTTP de régression observés en échec dans GitHub Actions.
- [x] Limiteur PostgreSQL et politique de proxy, tests des bornes et de concurrence.
- [x] Intégration avant les endpoints et les calculs coûteux ; confidentialité et mot de passe dépassant 72 octets.
- [x] Parcours légitimes derrière NAT, multi-instance, erreurs et pannes vérifiés sur vrai PostgreSQL.
- [x] ADR, configuration, modèle de menace, limites et intégration des trois migrations documentés.
- [x] Validation complète et PR indépendante : [PR #28](https://github.com/SPX-tech/ScolaPay/pull/28), 360 tests Java et démarrage du JAR verts. Aucun droit de fusion ou de déploiement n'est donné par ce plan.

## Preuves acquises

- Régression d'origine observée : [CI initiale](https://github.com/SPX-tech/ScolaPay/actions/runs/42864193612).
- Régression de troncature BCrypt observée : [CI](https://github.com/SPX-tech/ScolaPay/actions/runs/42866152616).
- 30 tests ciblés, sans échec ni test ignoré : [CI](https://github.com/SPX-tech/ScolaPay/actions/runs/42868786498), code `5a02b9b`.
- Validation complète du code identique, `985ca9b` : [360 tests Java et JAR](https://github.com/SPX-tech/ScolaPay/actions/runs/42869629507), [conteneurs](https://github.com/SPX-tech/ScolaPay/actions/runs/42869629777).
- Aucun test ni build lourd local. Aucun VPS, fournisseur, fusion ou déploiement.

## Acceptation et intégration avec #26 et #27

Le propriétaire accepte le développement et l'ADR 0025 le 2026-11-10. PostgreSQL est retenu ; Redis ne sera pas introduit sans intérêt opérationnel démontré. Aucun code ni seuil ne change maintenant.

- [x] Remettre #28 en brouillon, sans fusion automatique.
- [x] Consigner l'acceptation de l'ADR et le suivi de la contention et des collisions.
- [x] Reprendre `main` au SHA `212b6d3` après #26 et #27 et résoudre les conflits documentaires.
- [x] Ajouter au scénario de reprise la vérification de V7 puis V7_1 puis V7_2 et du quota d'acceptation après redémarrage.
- Les résultats GitHub Actions et le SHA d'intégration sont consignés dans [#28](https://github.com/SPX-tech/ScolaPay/pull/28), en brouillon pour revue.
- [ ] Avant exploitation à grande échelle, mesurer contention et collisions sur données synthétiques selon le [guide](../docs/auth-abuse-protection.md#mesures-avant-montée-en-charge).
