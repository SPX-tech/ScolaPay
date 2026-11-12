# 0032 : intention scolaire numérique et intégration Kadryza

- **Statut** : priorité numérique, recours à Kadryza existant et compte marchand par établissement validés par le propriétaire. Première architecture technique proposée pour revue ; les autres arbitrages restent ouverts.
- **Date** : 2026-11-12
- **Complète** : 0006, 0008, 0012, 0015, 0016, 0017, 0019, 0021 à 0025 et les fondations de Mission 004.
- **Références** : [Mission 005](../missions/mission-005-digital-school-payments.md), [revue Kadryza](../mission-005-kadryza-contract-review.md).

## Décisions acquises et frontières

Le numérique est le cœur du produit pour les établissements et familles du Tchad ; les espèces sont secondaires et facultatives. Les compléments de Mission 004 sont suspendus, sans retirer ou affaiblir le code intégré jusqu'à #34. La Mission 003 reste clôturée.

ScolaPay intègre l'API Kadryza existante. Il ne crée pas de backend de traitement des paiements, de microservice ou de service Go. Kadryza garde les opérations de paiement et les intégrations opérateurs. Core Java garde les obligations, droits scolaires, intentions, décisions de comptabilisation et audit. Les bases ne sont pas partagées et Core n'écrit pas dans le ledger Kadryza.

Un compte marchand Kadryza par établissement est validé. L'association à des identifiants marchands, des clés serveur et au mode test/live devra être explicite. Cette décision ne configure aucun compte ni secret et ne prouve aucun versement ou disponibilité opérateur.

## Préparation locale sans effet monétaire

La première tranche conserve `digital_payment_intents` dans le module `payments`, séparée des tables et preuves cash. Source `SCHOOL_OBLIGATION`, prestataire cible `KADRYZA`, portée immuable `PREPARATION_ONLY_V1` et seul état `PREPARED`. La référence `DPI-{uuid}` est interne et pourra servir de référence marchand ; elle n'est ni un ticket Kadryza, ni un identifiant externe, ni un reçu de paiement.

Un montant XAF exact, positif et borné au dû est présenté avec la version du journal. Le préparateur est un compte membre autorisé, pas un payeur inféré. Pour ce périmètre technique soumis à revue, administrateur et comptable reçoivent une capacité dédiée `DIGITAL_PAYMENT_PREPARE`. Les permissions cash n'accordent pas cette capacité par elles-mêmes. Les lectures utilisent `BILLING_READ` et ne contiennent ni identité de payeur, téléphone, clé ou motif confidentiel.

Les obligations corrigées ou annulées activement et les années non ouvertes restent fermées à cette préparation conservatrice, sans inventer une nouvelle politique d'arriérés ou d'ajustements. Le port numérique réutilise la projection financière existante ; il ne dépend ni d'une activation cash ni d'une réception physique.

L'intention ne réserve pas la dette, ne bloque pas une remise ou un autre règlement et ne déclenche aucune collecte. Des préparations distinctes peuvent exister ; leur somme n'est pas un montant dû, reçu ou réservé. La maîtrise des sessions externes simultanées appartient au futur ordre d'envoi, jamais à une activation automatique de tous ces faits historiques.

## Contrat billing et intégrité PostgreSQL

`BillingPaymentPreparation` expose une lecture verrouillée `MANDATORY` et aucun `post`. Les permissions, montants et projections cash existants restent inchangés. `payments` ne lit pas les tables de billing par un dépôt Java ; V12 contient les contraintes d'intégration SQL de la base commune, comme V9 pour les sources cash.

V12 suit V11 sans réécrire V1 à V11. Les écritures exigent `READ COMMITTED`, verrouillent l'obligation puis l'année en lecture partagée et contrôlent établissement, devise, préparateur actif habilité, borne et version du journal. Les instantanés initial, net facturé, réglé et dû sont cohérents à la création. Un contrôle différé refuse aussi une transaction qui changerait le journal après insertion de sa préparation avant le commit.

La préparation et son audit sont atomiques. Référence et clé sont uniques ; données et états sont immuables contre UPDATE, DELETE et TRUNCATE. Le rejeu exact retrouve le fait avant d'évaluer le nouvel état du dû ; une clé réutilisée avec un autre acteur, montant, source, devise ou version est refusée. Aucune clé financière n'expire automatiquement.

Après un ajustement ultérieur, le fait conserve son instantané historique. Il n'est pas une lecture actuelle du dû et les réponses indiquent toujours `requiresRevalidation=true`, `providerDispatchAvailable=false`, `paymentConfirmed=false` et `settlementCreated=false`. Les gardes de billing n'acceptent aucun SETTLEMENT fondé sur ce seul identifiant.

## Transmission et confirmation différées

Aucun client réseau Kadryza, callback ou registre de confirmation n'est activable dans cette tranche. Il n'y a ni faux succès, ni réception d'espèces synthétique, ni reçu définitif, ni frais devinés. `SCOLAPAY_DIGITAL_PAYMENT_INTENTS_ENABLED=false` ferme les écritures par défaut ; les lectures autorisées restent disponibles.

Le contrat Kadryza versionné montre une unicité marchand/référence/environnement et un 409 en cas de doublon. La future transmission doit donc retrouver une session par la même référence après réponse inconnue, puis vérifier contenu et contexte avant toute reprise. Créer une nouvelle référence ou conclure à l'échec sur timeout serait dangereux.

Le futur ordre d'envoi devra revalider dû, droits, identité marchande, environnement, montant original, frais et session déjà incertaine ; il enregistrera la demande et son travail durable atomiquement. Un événement sans consommateur n'est pas une outbox de transmission garantie. Le worker externe ne restera pas dans une transaction Modulith pendant son appel réseau.

La future réception vérifie HMAC sur corps brut borné, contexte marchand et mode, puis enregistre une inbox avant l'accusé. Les reprises peuvent être concurrentes et désordonnées : déduplication d'événement et unicité financière de la session sont deux garanties distinctes. Un `SUCCESS` de test, un SMS, un retour navigateur, une cotation ou une notification signée non rapprochée ne créent aucun encaissement scolaire.

Le premier SETTLEMENT numérique exigera une provenance numérique confirmée, sa preuve, l'audit, les liens bilatéraux et la politique de montant au commit. Le port cash et ses contraintes ne sont pas détournés. Un paiement confirmé, les frais, le net marchand et le versement effectivement reçu par l'établissement restent distincts.

## Limites et prochaines décisions

Le compte par établissement est acquis ; ses rattachements concrets et l'environnement de QA restent à qualifier. Les droits des payeurs Families, les frais, les paiements tardifs ou concurrents, les excédents numériques et les justificatifs ne sont pas décidés par cette préparation. Les écarts entre documentation et code Kadryza doivent être clarifiés avant les parcours concernés ; aucune lecture du dépôt ne prouve les paramètres réellement exploités.

Les essais de cette tranche sont locaux à Core sur PostgreSQL jetable, sans appel à Kadryza. Une confirmation Payment Session de test sans crédit réel n'est pas attestée ; elle demande des fixtures de contrat ou une QA Kadryza contrôlée avant le lot suivant. Les archives Modulith et les notifications ne remplacent pas le registre monétaire ou l'audit.

Families reste fermé avec ses exigences d'identité, d'autorisations financières, de canaux et de conformité. Le principe de canaux explicites accepté après #27 n'est pas implémenté par cette mission. Les SMS sont importants pour l'accessibilité mais n'authentifient pas une personne ou un paiement à eux seuls. La législation et la nature fiscale restent à confirmer selon les ADR existantes.

Aucun déploiement, migration persistante, fournisseur réel, capacité financière réelle, frontend, fusion ou changement de protection n'est autorisé par cette ADR.
