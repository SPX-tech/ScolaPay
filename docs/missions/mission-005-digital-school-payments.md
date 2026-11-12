# Mission 005 : paiements scolaires numériques avec Kadryza

**Priorité produit validée.** Le numérique est le cœur de ScolaPay pour les établissements et familles du Tchad ; le cash devient secondaire et facultatif. Les compléments de Mission 004 sont suspendus sans suppression ni dégradation des PR #31 à #34. La Mission 003 reste techniquement clôturée.

## Résultat produit recherché

Remplacer progressivement convocations papier, déplacements et dépendance aux espèces : connaître les frais, recevoir avis et rappels, payer à distance et obtenir une confirmation vérifiable. L'établissement distingue dû scolaire, paiement confirmé, impayé, net commercial et versement effectivement reçu. Une notification ou un écran de retour ne fait foi ni d'identité ni de paiement.

L'API **Kadryza Pay existante**, confirmée par le propriétaire, assure les opérations de paiement et les intégrations opérateurs. Core Java garde les obligations, échéances, droits, intentions, écritures et audit scolaires. Aucun nouveau backend, microservice, service Go ou implémentation Airtel/Moov n'est introduit dans ScolaPay. Le choix antérieur d'envisager Go ne constitue pas un chantier autorisé.

**Modèle marchand validé : un compte Kadryza par établissement.** Les identités marchandes et clés test/live seront liées explicitement à cet établissement côté serveur, sans collecte commune implicite. Cette décision ne provisionne aucun compte ou secret et ne prouve aucun rail disponible.

## Références et limites de l'étude

Le [contrat Kadryza](../mission-005-kadryza-contract-review.md) est étudié sur le commit `323b7b1463caa2d5b59aab079a0bc46c37360c28`, à partir des sources versionnées. Les documents locaux modifiés ne sont pas une preuve de contrat publié ; le code versionné ne prouve pas l'état des services exploités. Aucun appel à Kadryza, transaction, changement de configuration ou test terrain n'est effectué par cette étude.

Le cadrage respecte 0012, 0015, 0016, 0017, 0019, 0021, 0022 et 0023. Les questions juridiques, fiscales, de responsabilité financière et d'accès aux familles restent ouvertes selon ces ADR.

## Première tranche : préparer une intention, sans argent reçu

Une intention interne porte établissement, obligation source, devise XAF, principal scolaire exact, préparateur habilité, référence stable et clé d'idempotence. Elle conserve le dû et la version du journal observés sous verrou, avec audit dans la transaction PostgreSQL. Les gardes vérifient cet instantané au commit.

Le périmètre technique proposé pour revue réserve la préparation au comptable et à l'administrateur de l'établissement, avec une capacité numérique dédiée. Il ne désigne pas un payeur et ne donne aucun droit familial ou rôle financier. Le caissier n'obtient pas la préparation numérique par sa seule permission cash. Les lecteurs financiers existants peuvent relire les références techniques sans données personnelles de payeur.

L'intention est de portée `PREPARATION_ONLY_V1`, reste `PREPARED` et n'est jamais envoyée automatiquement. Elle ne réserve ni le dû ni des fonds, ne crée aucun règlement ou reçu définitif et ne diminue pas la dette. Des intentions distinctes restent des préparations sans effet monétaire ; leur existence n'autorise pas plusieurs sessions externes actives. La politique de sélection, d'envoi et de sessions incertaines doit précéder toute émission vers Kadryza.

La référence locale est prévue pour le champ `reference` de Payment Sessions, sans ticket artificiel ou identifiant de session inventé. Les confirmations, webhooks, tentatives réseau, frais, expirations, annulations et remboursements ne sont pas construits dans cette tranche. Une nouvelle API de confirmation reste absente, même sous activation de la préparation.

## Lots proposés

| Lot | Capacité | Critères de réussite |
| --- | --- | --- |
| 1 | Cadrage, étude contractuelle, intention scolaire locale | Dû exact et version cohérente au commit, droits, idempotence, audit, SQL direct et reprise ; aucun effet monétaire ou réseau |
| 2 | Liaison marchande et création/reprise de session en environnement de test | Comptes par établissement, clés serveur et environnement vérifiés, principal/frais figés et présentés ; référence stable, réponse perdue retrouvée par référence avant tout nouvel envoi ; essais sans fonds réels |
| 3 | Réception durable et rapprochement des confirmations de test | HMAC sur octets bruts, limites de corps, contexte marchand/test, `event_id` durable, répétitions et désordre ; rapprochement canonique, aucune écriture financière réelle pour du test |
| 4 | Premier règlement numérique confirmé | Provenance numérique bilatérale avec billing, preuve fiable, unicité de l'opération indépendamment de l'événement, concurrence et non-négativité au commit ; traitement explicite du dû modifié et des issues incertaines |
| 5 | Communication et parcours Families | Avis/rappels autorisés, livraison suivie, interface mobile légère et reprise réseau ; aucune identité ou preuve de paiement tirée d'un SMS ; accès financier explicite, canaux et contrôles juridiques satisfaits avant ouverture |
| 6 | Rapprochement des versements et consolidation | Montants dus, principal confirmé, frais, net marchand et versements effectivement reçus distincts ; incidents, charge et mutations pertinentes, sans substitution de la simple cotation à un fonds disponible |

Chaque capacité n'est activable qu'avec ses garanties complètes. Le lot 4 n'est pas un assouplissement de V9 : il doit ajouter une source numérique réellement confirmée, ses contraintes et son lien au journal, sans fabriquer de réception d'espèces ou réutiliser une preuve de test.

Les avis de frais et rappels sont une priorité produit et peuvent être cadrés en parallèle de l'intégration, en réutilisant le module notifications. Aucun fournisseur SMS n'est présumé disponible, pas davantage une fonction d'envoi de rappels dans Kadryza. Le contenu, le destinataire financier autorisé, les canaux et les droits doivent être explicites. Le futur mobile léger doit pouvoir reprendre par référence après coupure, sans exposer de clé serveur ou recréer une collecte ; aucun frontend n'est développé dans cette tranche.

## Décisions encore nécessaires

1. **Payeur et autorisations Families** : quels rôles financiers peuvent initier quel montant et pour quelle obligation, y compris un contributeur ; aucun accès déduit de l'âge ou de la parenté. La préparation interne n'arbitre pas ce parcours.
2. **Frais** : qui les supporte et quel principal scolaire éteint la dette ; ne pas confondre `pricing.original_amount`, total payeur ou net marchand.
3. **Dû modifié et plusieurs tentatives** : avant envoi puis à confirmation, que faire d'une remise ou d'un autre paiement concurrent, d'un retard ou d'un excédent numérique ? Aucun portefeuille ou remboursement implicite.
4. **Preuve et exploitation** : qualifier le rail Kadryza retenu, ses conditions de confirmation et l'état réellement exploité ; un SMS de notification n'est jamais une preuve et une observation Gateway ne vaut pas une identité familiale.
5. **Test complet sans argent** : les Payment Sessions de test documentées nécessitent un crédit observé pour réussir. Un webhook `transaction.test` ne simule pas à lui seul `payment_session.succeeded`. Définir avec Kadryza une QA contrôlée ou des fixtures de contrat, puis distinguer leurs preuves d'un essai réel bout en bout.
6. **Versement, incidents et conservation** : preuve de disponibilité/versement à l'établissement, gestion des retours et contestations, justificatifs et fiscalité selon 0019. Une écriture de compensation n'est pas un remboursement effectif.

## Scénarios de référence

| Cas | Garantie |
| --- | --- |
| Préparer 40 000 sur 100 000 dus | Dette 100 000 inchangée, aucun SETTLEMENT ou reçu de paiement |
| Montant nul, flottant, autre devise, supérieur au dû ou version périmée | Refus sans intention partielle |
| Même clé, même contenu et auteur, y compris plusieurs instances | Une intention et un audit ; résultat retrouvé |
| Même clé, contenu, source ou auteur différents | Conflit sans fuite de données |
| Ajustement pendant la préparation ou après celle-ci | Instantané cohérent à la création ; fait historique conservé, revalidation requise avant envoi |
| Succès déclaré par client, callback inconnu, SMS ou événement de test | Aucun paiement réel, aucun règlement créé |
| Réponse perdue après commit | Relecture par clé même si préparation désactivée, aucune nouvelle opération automatique |
| Une institution ou famille non autorisée tente de lire/préparer | Refus selon l'isolation et les permissions, aucune dette d'autrui révélée |
| Écriture SQL directe incohérente ou double comptabilisation tentée | Contraintes de source, montants, audit, immutabilité et gardes de billing maintenues |

Les vérifications lourdes passent exclusivement par GitHub Actions et PostgreSQL jetable. Aucun fournisseur réel, déploiement, migration persistante, frontend ou ouverture Families. Les flags financiers restent faux par défaut ; aucune fusion ou modification des protections sans autorisation.
