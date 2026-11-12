# 0031 : identité du remettant et autorisation nominative de restitution

- **Statut** : principes A05 et deux précisions métier acceptés par le propriétaire ; architecture de cette tranche proposée à la revue. A06 à A08 restent ouverts sur leurs autres modalités.
- **Date** : 2026-11-12
- **Références** : [0027](0027-restitution-excedents-jamais-affectes.md), [0029](0029-premier-reglement-especes.md), [0030](0030-reservations-excedents-especes.md).

## Décisions acquises

Le remettant est identifié à chaque nouvel encaissement, sans obligation de compte ScolaPay. La restitution est destinée par défaut à cette personne. Un tiers nécessite une autorisation explicite vérifiée ou un droit justifiable ; ni parenté, ni compte connecté, ni obligation scolaire ne valent désignation.

L'identité du bénéficiaire doit être vérifiée au moment de la remise, avec conservation des éléments nécessaires sans copies intégrales systématiques de pièces. Demandeur et approbateur sont deux comptes distincts. Un caissier habilité peut remettre après approbation ; une troisième personne n'est pas systématiquement imposée.

Le propriétaire a validé deux précisions pour la V1 :

1. Ancienne réception sans identité : déclaration signée du remettant, rapprochée du reçu d'origine et examinée par un second compte habilité.
2. Justificatif papier signé par bénéficiaire et caissier, conservé par l'établissement, avec référence unique et attestation de contrôle enregistrées. Le stockage numérique signé et les incidents incertains restent à cadrer séparément. Cette précision ne ferme pas tout A08.

## Tranche préparatoire retenue

Cette PR construit l'identification à la réception, la vérification indépendante des anciennes sources, la préparation nominative du bénéficiaire et une nouvelle autorisation liée à cette identité. Elle ne construit aucun endpoint de confirmation physique, sortie d'espèces ou preuve finale de restitution. Il n'existe aucun flag capable d'activer cette confirmation dans ce code.

Le rapprochement d'identité préparatoire n'est pas la vérification finale au guichet : les réponses indiquent `finalIdentityCheckRequired=true`. La future tranche de remise devra vérifier de nouveau le bénéficiaire au moment du geste et lier la preuve signée à cette vérification et à l'autorisation. L'approbation seule ne constate toujours aucune sortie.

Seul le remettant source est pris en charge comme bénéficiaire dans cette tranche. Les tiers, y compris membres de la famille ou mandataires, restent explicitement refusés. La règle de principe A05 est acquise ; il reste à préciser le contrôle du mandat ou du droit et les preuves minimales avant d'ouvrir ce cas particulier.

## Identité distincte des comptes et personnes financières

Les informations de remettant sont un constat propre à la réception, pas une nouvelle personne de `billing`, un compte d'identité ou une désignation de redevable. Aucun rapprochement automatique ou transfert de droits financiers n'est créé. Une même personne peut remettre à plusieurs reprises sans portefeuille ni identité financière globale déduite de ces constats.

La nouvelle réception conserve nom et éléments minimaux permettant de distinguer le remettant, confirmés explicitement par le déclarant. Ces textes sont bornés et chiffrés. La référence de justificatif est une référence interne opaque, pas un chemin ou une URL à récupérer. Aucun téléchargement de pièce d'identité n'est proposé et aucun catalogue de documents juridiquement admis n'est inventé.

## Réceptions anciennes et compatibilité

V11 classe les sources préexistantes `LEGACY_UNIDENTIFIED` sans modifier leurs montants, acteurs, dates, preuves, affectations ou réservations. Les nouveaux INSERT exigent `IDENTIFIED_V1` et l'identification liée dans la même transaction, même en SQL direct. Une nouvelle réception ne peut donc pas emprunter artificiellement le régime historique.

Une déclaration signée d'identité ancienne est un nouveau fait immuable lié à sa source ; elle ne réécrit jamais la réception. Dans ce périmètre conservateur, seul son déclarant d'origine peut la présenter. Le second compte habilité doit attester le contrôle de la déclaration signée, le rapprochement du reçu original et l'identité. Une source non examinée ne peut servir à la préparation nominative. La délégation lorsque le déclarant d'origine est absent reste fermée et nécessite une procédure explicite.

Un rejeu exact de la réception historique reste possible sans identité ajoutée à la commande. Ajouter une identité par ce rejeu est refusé : il faut la procédure signée séparée. Un nouveau reçu ne peut jamais être créé pour identifier ou remplacer les mêmes fonds.

## Nouvelle autorisation et conservation du budget

Les demandes et décisions `RESERVATION_ONLY_V1` de V10 restent immuables. Une nouvelle autorisation `NAMED_REMITTER_V1` est liée à une réservation techniquement approuvée, sa réception, son identité et un rapprochement nominatif immuable. L'approbateur distinct du demandeur confirme avoir examiné le bénéficiaire. Aucun bénéficiaire n'est ajouté silencieusement à une ancienne décision.

Le budget F/H/Q reste celui de 0030. Déclaration d'identité, examen et autorisation ne modifient ni Q ni H ni la réception et ne créent aucun SETTLEMENT. Une autorisation approuvée ne libère pas la réservation ; aucune expiration ou libération automatique n'est introduite. Corrections d'identité, réassociations, substitutions, compensations et remboursements restent fermés.

La réception est verrouillée avant chaque nouveau fait, selon le contrat `READ COMMITTED`. Les relectures composées emploient `REPEATABLE READ`. Les clés d'idempotence permanentes sont liées à la source, au contenu et au compte ; une réponse perdue se reprend par la même identité et non par une nouvelle réception ou un nouveau geste.

## Confidentialité et configuration

Les données privées sont chiffrées en AES-256-GCM avec nonce aléatoire et contexte authentifié établissement, source et domaine. Une clé dédiée est requise avant activation des réceptions ou des nouvelles écritures d'identité. Clé absente ou invalide : refus de démarrage ; donnée indéchiffrable : refus générique sans journaliser le contenu ou la cause cryptographique. La désactivation des écritures n'efface ni preuve ni possibilité de lecture autorisée si la clé est conservée.

La vue générale de réception ne contient aucun nom ou élément de vérification. La lecture privée est réservée au déclarant et aux rôles financiers de contrôle habilités ; un autre caissier ne reçoit pas ces informations. L'audit général relie les identifiants et références techniques sans les données d'identité, motifs privés ou références de déclaration signée. Les vues familiales existantes ne reçoivent aucun de ces champs.

Les contraintes SQL garantissent cohérence des liens, comptes distincts, unicité, immutabilité et audit au commit. Elles ne prouvent pas la présence physique des billets, l'authenticité d'une signature ou une observation d'identité déclarée par l'opérateur. Les accès directs à la base ne remplacent pas les permissions HTTP. La gestion de la clé, sa sauvegarde, sa rotation et la conservation des justificatifs devront être opérationnelles avant pilote.

## Suite nécessaire avant remise

La prochaine tranche devra réunir mouvement de sortie, budget Q/T, confirmation physique, vérification au guichet, référence unique du justificatif signé et audit atomique. Elle devra aussi préparer une identité durable de tentative **avant** tout geste, empêcher une seconde tentative après réponse inconnue et conserver un incident bloquant lorsque l'exécution physique est incertaine.

Recommandation à arbitrer pour les incidents : aucun délai ne libère les fonds ; résolution documentée par un compte habilité distinct de l'auteur de la remise, permettant de constater la remise ou son absence prouvée sans recréer une provenance. Les possibilités de reprise, d'annulation d'une tentative et les justificatifs de résolution restent à décider avant activation du parcours concerné. Les règles de rectification A07 ne sont pas remplacées par une convention technique.

Le lot 2 reste ouvert. Le secours manuel A03 et le contrôle quotidien A04 restent nécessaires avant pilote. Aucun déploiement, migration persistante, fournisseur, frontend ou ouverture de Families n'est autorisé.
