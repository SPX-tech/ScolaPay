# 0019 : nature des documents et numérotation

- **Statut** : Principes acceptés par le propriétaire. Le traitement fiscal reste **à valider avec un conseil fiscal et la DGI**.
- **Date** : 2026-11-06
- **Implémentation** : la référence interne et la séparation de l'obligation sont en place, voir [0021](0021-obligations-et-journal.md). Aucun appel de frais, facture fiscale ni reçu n'existe.

## Décision

**ScolaPay ne présente aucun document comme fiscalement conforme tant que les conditions et les éventuelles intégrations réglementaires ne sont pas validées.**

Quatre choses distinctes, à ne jamais confondre :

| Document | Ce que c'est | Effet | Numérotation |
| --- | --- | --- | --- |
| **Obligation scolaire** | Ce qu'un dossier doit à l'établissement | Interne, source du montant dû | Référence interne |
| **Appel de frais** | La demande de paiement adressée à une personne | Informatif, ne fait pas naître le dû | Référence interne |
| **Facture fiscale** | Le document exigé par l'administration fiscale, quand il l'est | Effet fiscal | Série fiscale, si elle s'applique |
| **Reçu de paiement** | La preuve d'un encaissement confirmé | N'existe qu'après une opération financière réelle, voir [0015](0015-modele-de-facturation.md) | À définir avec le module de paiement |

Conséquences pour le modèle :

- **Une obligation n'est pas une facture.** Rien ne présume qu'une obligation scolaire donne lieu à une facture fiscale. Aucune colonne, route ou libellé d'obligation n'emploie le mot « facture ».
- **Référence interne.** Chaque obligation porte une référence unique par établissement, stable et jamais réutilisée. Une obligation ne se supprimant pas, une référence ne peut pas se libérer. Des trous sont permis : cette référence n'a pas de valeur fiscale.
- **Numérotation fiscale préparée, pas construite.** Elle se distingue par sa continuité : une par établissement ou par série autorisée, attribuée dans la transaction qui émet le document, par verrou sur la ligne de la série, pour qu'une annulation ne laisse aucun trou. Une séquence PostgreSQL ne convient pas, car elle laisse des trous. Selon la réponse de la DGI, ce numéro pourrait aussi être **attribué par le système agréé lui-même**, et ScolaPay n'aurait alors qu'à le conserver. La table n'est donc pas créée avant de le savoir.
- **Le lien est porté par le document fiscal**, qui référence les obligations concernées, et non l'inverse : une obligation ne dépend pas de l'existence d'une facture.

## Étude de la FEN au Tchad

Menée le 2026-11-06 à partir de sources publiques. **Ce n'est pas un avis juridique ou fiscal.**

### Établi par un document de la DGI

Source : [Obligations générales de FEN](https://fen.finances.gouv.td/assets/landing/documents/Obligations%20g%C3%A9n%C3%A9rales%20de%20FEN.pdf), publié sur le portail officiel, créé en juin 2024 d'après ses métadonnées.

- **Base légale** : article 17 de la loi n° 016/PT/2022 de finances pour 2023, à compter du 1er janvier 2023.
- **Champ** : les contribuables enregistrés à la TVA, et plus largement « toute opération réalisée par une personne physique ou morale soumise à l'impôt sur les sociétés, à l'impôt Général Libératoire ou assujettie à la Taxe sur la Valeur Ajoutée ». **Le critère n'est donc pas la seule TVA.**
- **Documents** : une facture normalisée « ou un document en tenant lieu : décompte de travaux, feuilles d'honoraires, acomptes, etc. »
- **Système agréé** : le document parle d'un « système de facturation électronique agréé ». **ScolaPay n'en est pas un.**
- **Émission** : à chaque opération de vente, que le client la demande ou non, avec le nom réel du bien ou service et le taux de taxe.
- **Correction** : par une « facture de remboursement certifiée », classée dans l'ordre consécutif de la facture d'origine. La suppression d'une facture sans motif valable est sanctionnée.
- **Panne** : notification sous six heures, factures manuscrites en double exemplaire dont le second est conservé au moins dix ans, puis reprise dans le système.
- **Sanctions** : notamment 100 000 FCFA par mois de non-adoption après notification, et pour une transaction sans facture, 100 000 FCFA plus deux fois la TVA éludée, ou plus 10 % de la transaction pour un non-assujetti à la TVA. Fermeture possible d'un mois après deux avertissements.

### Rapporté par la presse ou des tiers, non vérifié dans les textes

- La [loi de finances 2026](https://tchadinfos.com/2026/01/29/loi-de-finances-2026-la-direction-generale-des-impots-detaille-les-nouvelles-mesures-fiscales/) rendrait la facturation électronique obligatoire pour les entreprises, ONG, projets, professions libérales et organismes publics, et interdirait toute dépense publique sans facture normalisée. Voir aussi [une synthèse professionnelle](https://europe.thomsonreuters.com/compliance/regulatory-updates/chad) et [la circulaire d'exécution du budget](https://www.alwihdainfo.com/tchad-une-circulaire-fixe-les-nouvelles-regles-d-execution-du-budget-general-de-l-etat-2026-a147609/). **Une école associative ou un établissement payé sur fonds publics pourrait donc être concerné par une autre voie.**
- Une recherche signale que l'article 230 du Code général des impôts, dans sa rédaction de la [loi de finances 2019](https://www.droit-afrique.com/uploads/Tchad-LF-2019.pdf), vise les établissements d'enseignement agréés par le ministère de l'Éducation nationale et pratiquant un prix homologué. **Nous n'avons pas pu lire ce texte**, ni vérifier qu'il est encore en vigueur. Le [CGI à jour](http://www.droit-afrique.com/boutique/tchad-code-general-des-impots/) n'est pas librement accessible, et une exonération de TVA, si elle existe, ne dispense pas forcément de la FEN.

### Article 46 de la loi de finances 2026

Le propriétaire demande de suivre cet article. La loi est identifiée : [n° 008/AN/SÉNAT/2025 du 26 décembre 2025](https://www.lepaystchad.com/tchad-la-loi-de-finances-2026-officiellement-promulguee-par-decret-presidentiel/), promulguée par le décret n° 3151/PR/2025.

**Nous n'avons pas pu en lire le texte.** Aucune des synthèses consultées ne cite cet article, [celle de KPMG](https://kpmg.com/us/en/taxnewsflash/news/2026/02/tnf-chad-direct-and-indirect-tax-changes-in-finance-law-2026.html) comprise, et le site du ministère présente un certificat invalide : la connexion n'a pas été forcée. Rien n'est donc affirmé ici sur son contenu, et **aucun régime n'en est déduit pour un établissement scolaire**.

### Non trouvé

Aucune source consultée ne traite de l'enseignement dans le cadre de la FEN, d'une API ou d'une procédure d'agrément pour un logiciel tiers, ni du format des numéros. Le [portail FEN](https://fen.finances.gouv.td/) n'expose publiquement qu'une page de connexion.

## Questions à poser à un conseil fiscal et à la DGI

Le lot 3 n'en dépend pas. **Elles conditionnent l'exploitation réelle.** Le lot 4 n'en dépend pas, car il sépare l'obligation de tout document fiscal.

1. Une école privée ou une université est-elle soumise à la FEN pour ses droits de scolarité ? Selon son statut : privée lucrative, associative, confessionnelle, publique ?
2. Un établissement agréé à prix homologué est-il dispensé d'émettre, ou seulement exonéré de TVA avec obligation d'émettre ?
3. **Quel fait déclenche l'émission : l'obligation, l'appel de frais ou l'encaissement ?** Un acompte en déclenche-t-il une à chaque versement ?
4. Un logiciel tiers peut-il obtenir l'agrément ou s'intégrer à un système agréé ? Par quelle API, et avec quelle homologation ?
5. Émet-on au nom de chaque établissement, sous son NIF, avec un accès qui lui est propre ?
6. Qui attribue le numéro, et selon quel format ? La continuité s'entend-elle par établissement, par point de vente ou par système ?
7. Comment une remise ou un ajustement s'articule-t-il avec une facture de remboursement certifiée ?
8. Quelle durée de conservation s'applique aux documents financiers ? Le document ne cite dix ans que pour les factures manuscrites.
9. Le statut de ces règles depuis la loi de finances 2026 et sa circulaire, que nous n'avons pas lues.
10. **La portée de l'article 46 de la loi de finances 2026**, et son application éventuelle à chaque type d'établissement scolaire.

## Conséquences

- Le lot 4 sépare l'obligation de tout document fiscal, et sa référence interne n'a aucune prétention fiscale.
- Le journal immuable de [0015](0015-modele-de-facturation.md) est compatible avec ce qui est connu : aucune suppression, et une correction par une écriture nouvelle liée à l'originale.
- Avant toute exploitation réelle, la validation du traitement fiscal devra devenir une condition de [mise en service](0017-controles-de-mise-en-service.md).
- Toute interface ou tout document destiné aux utilisateurs emploie « appel de frais » ou « obligation », et jamais « facture », tant que cette décision n'a pas évolué.
