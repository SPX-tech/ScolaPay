# 0014 : Android hors de la feuille de route active

- **Statut** : Acceptée
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet
- **Complète** : [0007](0007-portail-familles-avant-apk.md)

## Décision

L'ordre de construction est : backend Java, puis interfaces web des établissements, des parents et des étudiants. L'APK viendra dans une phase ultérieure, lorsque les principaux parcours métier et financiers seront stabilisés.

**Aucun développement Android, refonte Kotlin, test d'émulateur ou chantier APK n'est à proposer d'ici là.** Le code `android/` reste conservé comme référence.

## Conséquences

- Les contrôles Android sont dans un workflow séparé, `android-legacy.yml`, qui ne se déclenche que si `android/` ou ce workflow change, ou à la demande. Un émulateur instable ne peut plus faire échouer le travail sur le backend Java, et un échec reste visible dans ce workflow.
- Dependabot ne propose plus de mises à jour pour `android/`.
- Le code Android n'étant plus vérifié à chaque PR, il peut se dégrader sans signal. C'est accepté : une reprise commencera par une remise à niveau.
- Le contrat `/api/v1` que ce code consomme reste celui de NestJS. Rien dans `core/` ne doit être conçu pour lui.
