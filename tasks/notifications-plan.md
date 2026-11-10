# Socle de notifications et remise des invitations

Branche `feat/notification-foundation`, créée depuis `main` (c28862c). Worktree indépendant.
Aucun changement de facturation, NestJS, Android ou interface. Aucun déploiement ni fusion.

## Contrat et risques

- Invitation, audit, charge chiffrée et publication du réveil sont atomiques.
- Le listener Modulith prépare la file, sans réseau. Un worker réclame une ligne avec bail et jeton de possession, puis appelle le transport hors transaction.
- Au moins une tentative, doublons externes possibles. Clé stable et corps figé pour l'idempotence du fournisseur. Aucun nouvel essai après la fenêtre de déduplication.
- AES-256-GCM, clé externe indépendante du JWT, identifiant de ligne comme AAD. Purge des charges terminales et expirées.
- E-mail Resend optionnel, désactivé par défaut, HTTP JDK sans dépendance ajoutée. SMS absent : refus explicite.
- Le portail reste fermé. La condition exige les deux canaux que l'identité accepte et conserve le contrôle juridique.
- La révocation ne peut rappeler un message déjà en vol. Le destinataire doit encore accepter un jeton valable.
- Le statut SUBMITTED signifie accepté par le fournisseur, sans preuve de réception dans la boîte.

## Tâches et preuves

- [ ] Régression initiale en CI : aucun jeton dans les réponses HTTP, téléphone refusé sans SMS.
- [ ] File chiffrée, listener durable, reprise et exclusion concurrente sur PostgreSQL réel.
- [ ] Adaptateur HTTP borné, erreurs neutres, configuration fermée et tests de transport.
- [ ] Adaptations minimales des tests : les jetons proviennent du canal de test, jamais du corps HTTP.
- [ ] Tests : succès, échec, incident, double traitement, révocation, expiration, confidentialité, isolation.
- [ ] ADR, guide d'exploitation, contexte du projet et résultats exacts.
- [ ] CI complète verte, PR indépendante ouverte, sans fusion.

Migration `V7_1` réservée à ce chantier pour éviter `V7` de la PR #26. Aucun environnement persistant migré.
Les seuls recouvrements prévus avec #26 sont les helpers de test et la documentation de contexte.
