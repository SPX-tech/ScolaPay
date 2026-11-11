# Mission 004, lot 2 : première PR intermédiaire

Base `2bb5922aef4bf141525a13b9d1c67a815c9d8925`. Branche `mission004/lot2-cash-foundation`.

Le propriétaire autorise le développement du lot 2 et des PR intermédiaires sans règlement incomplet. Cette PR construit la déclaration durable d'espèces non affectées. `SETTLEMENT` reste interdit par V6. Elle ne clôture pas le lot 2 et n'autorise aucun pilote.

## Décisions reçues

- A01 : encaissements ordinaires par caissier, comptable ou administrateur habilité ; directeur pouvant approuver sans encaisser ; auditeur en lecture seule. Les restitutions gardent une seconde approbation distincte.
- A02 : couverture des échéances les plus anciennes, remises sur les échéances non couvertes les plus éloignées, échéancier initial immuable. Application obligatoire avant le premier règlement, sans changement dans cette PR.
- A03 : secours manuel avec références uniques, justificatifs, rapprochement et contrôle indépendant. Aucun système hors ligne complet.
- A04 : contrôle quotidien et validation indépendante à réaliser avant un pilote. Auteur et date de déclaration sont conservés pour le préparer.
- A05 à A08 : arbitrages partiels encore ouverts. Pas de bénéficiaire, mandat, date antidatée, rectification, restitution ni document fiscal dans cette PR.

## Ordre de travail

- [x] Examiner les frontières, l'audit transactionnel, les contraintes de facturation et les conventions de tests réels.
- [ ] Écrire les tests HTTP de déclaration et observer leur échec fonctionnel en CI avant implémentation.
- [ ] Ajouter `payments` avec enregistrement fermé par défaut, permissions explicites, montants bornés et auteur serveur.
- [ ] Ajouter V8 sans modifier les migrations existantes : déclaration immuable, preuve interne, devise et auteur cohérents, unicité idempotente et audit obligatoire au commit.
- [ ] Vérifier concurrence, rejeux, droits, SQL direct, désactivation, redémarrage et absence de règlement.
- [ ] Vérifier les régressions de facturation, la modularité et la CI complète sur PostgreSQL jetable.
- [ ] Publier la PR en brouillon avec preuves et limites précises.

La prochaine PR fonctionnelle autorisant `SETTLEMENT` devra relier réception, affectation, preuve et journal dans une même transaction et prouver toutes les garanties de 0028. Les restitutions et rectifications restent fermées tant que leurs règles et preuves ne sont pas complètes.
