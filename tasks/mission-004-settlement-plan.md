# Mission 004 lot 2 : règlement d'une obligation

Base `6a2fdaaac64b66b684a97829b124801bb8cb4423`, branche `mission004/lot2-single-settlement`.

## Décisions et périmètre

Le propriétaire confirme pour cette tranche l'utilisation d'une réception existante sans identité de remettant, sans création de payeur, redevable ou bénéficiaire (A05 limité). Il valide les statuts techniques OPEN, PARTIALLY_SETTLED, SETTLED et SETTLED_WITH_ADJUSTMENTS, avec conservation des états historiques sans paiement (A08 limité).

Une commande affecte un montant explicite à une obligation, depuis une réception déjà confirmée. La réception n'est jamais recréée ni supprimée par un échec d'affectation. Une réception reste liée à une seule obligation dans cette tranche ; plusieurs versements à cette obligation sont possibles.

Les corrections actives non couvertes par A02 et les années non ouvertes empêchent le premier règlement. Après règlement, les corrections, annulations et contrepassations génériques restent fermées ; les remises compatibles avec le reste suivent A02 et les validations sensibles existantes. Aucun remboursement ou restitution n'est activé. A05 à A08 restent ouverts hors des points limités ci-dessus.

## Étapes

- [x] Examiner les contrats et gardes, isoler les arbitrages indispensables et recevoir leurs réponses.
- [ ] Observer en CI l'échec fonctionnel des nouveaux parcours avant implémentation.
- [ ] Construire le port public de billing, les projections monétaires et la couverture A02 sans modifier l'échéancier initial.
- [ ] Ajouter la transaction d'affectation, son idempotence et sa preuve durable ; conserver la réception source et les lectures après désactivation.
- [ ] Ajouter une migration additive avec intégrité bilatérale, non-négativité et refus des chemins non autorisés, y compris SQL direct et isolations non prises en charge.
- [ ] Préserver les seuils, doubles validations, demandes périmées et la confidentialité des vues financières.
- [ ] Vérifier les API, SQL direct, concurrence, reprise, migrations et régressions sur PostgreSQL jetable dans GitHub Actions.
- Le bilan de PR consigne les SHA et résultats exacts, sans prétendre clôturer le lot 2 ni rendre un pilote possible.

Les écritures financières restent désactivées par défaut. Aucun frontend, fournisseur, déploiement, migration persistante ou ouverture Families. Les contrôles A03/A04 restent nécessaires avant pilote. Aucune fusion sans autorisation.
