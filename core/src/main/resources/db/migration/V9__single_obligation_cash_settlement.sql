-- Les sources et preuves sont immuables. Les écritures sont sérialisées sur leurs parents en READ COMMITTED.
-- REPEATABLE READ est réservé aux lectures : un instantané ancien ne doit jamais servir à écrire un solde.
ALTER TABLE obligations ADD UNIQUE (establishment_id,id,currency);
ALTER TABLE cash_receptions ADD UNIQUE (establishment_id,id,currency);
ALTER TABLE obligation_entries
  DROP CONSTRAINT obligation_entries_kind_check,
  DROP CONSTRAINT obligation_entries_shape,
  ADD COLUMN cash_allocation_id uuid,
  ADD CONSTRAINT obligation_entries_kind_check CHECK (kind IN ('ISSUED','DISCOUNT','CORRECTION','CANCELLATION','REVERSAL','SETTLEMENT')),
  ADD CONSTRAINT obligation_entries_shape CHECK (
    (kind='ISSUED' AND amount_minor>0 AND reason IS NULL AND reverses_entry_id IS NULL)
    OR (kind='DISCOUNT' AND amount_minor<0 AND reason IS NOT NULL AND reverses_entry_id IS NULL)
    OR (kind='CORRECTION' AND amount_minor<>0 AND reason IS NOT NULL AND reverses_entry_id IS NULL)
    OR (kind='CANCELLATION' AND amount_minor<0 AND reason IS NOT NULL AND reverses_entry_id IS NULL)
    OR (kind='REVERSAL' AND amount_minor<>0 AND reason IS NOT NULL AND reverses_entry_id IS NOT NULL)
    OR (kind='SETTLEMENT' AND amount_minor<0 AND reason IS NULL AND reverses_entry_id IS NULL AND approved_by IS NULL)),
  ADD CONSTRAINT obligation_entries_cash_shape CHECK ((kind='SETTLEMENT')=(cash_allocation_id IS NOT NULL));

CREATE TABLE cash_allocations (
  id uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  reception_id uuid NOT NULL,
  obligation_id uuid NOT NULL,
  settlement_entry_id uuid NOT NULL UNIQUE,
  idempotency_key uuid NOT NULL,
  reference text NOT NULL CHECK (reference='CA-'||id::text),
  currency text NOT NULL,
  amount_minor bigint NOT NULL CHECK (amount_minor BETWEEN 1 AND 999999999999999),
  issued_minor bigint NOT NULL CHECK (issued_minor BETWEEN 1 AND 999999999999999),
  net_billed_minor bigint NOT NULL CHECK (net_billed_minor BETWEEN 1 AND 999999999999999),
  settled_before_minor bigint NOT NULL CHECK (settled_before_minor>=0),
  due_before_minor bigint NOT NULL CHECK (due_before_minor>=amount_minor AND due_before_minor=net_billed_minor-settled_before_minor),
  journal_size_before bigint NOT NULL CHECK (journal_size_before>=1),
  allocated_at timestamptz NOT NULL CHECK (isfinite(allocated_at)),
  allocated_by uuid NOT NULL,
  UNIQUE (establishment_id,id),
  UNIQUE (establishment_id,idempotency_key),
  FOREIGN KEY (establishment_id,reception_id,currency) REFERENCES cash_receptions(establishment_id,id,currency),
  FOREIGN KEY (establishment_id,obligation_id,currency) REFERENCES obligations(establishment_id,id,currency),
  FOREIGN KEY (establishment_id,allocated_by) REFERENCES memberships(establishment_id,account_id),
  FOREIGN KEY (establishment_id,obligation_id,settlement_entry_id)
    REFERENCES obligation_entries(establishment_id,obligation_id,id) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX cash_allocations_reception_idx ON cash_allocations(establishment_id,reception_id);
ALTER TABLE obligation_entries ADD CONSTRAINT obligation_entries_cash_fk
  FOREIGN KEY (establishment_id,cash_allocation_id) REFERENCES cash_allocations(establishment_id,id);
CREATE UNIQUE INDEX obligation_entries_one_cash_allocation ON obligation_entries(cash_allocation_id) WHERE cash_allocation_id IS NOT NULL;
CREATE TRIGGER cash_allocations_immutable BEFORE UPDATE OR DELETE ON cash_allocations FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_allocations_no_truncate BEFORE TRUNCATE ON cash_allocations FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE FUNCTION cash_allocation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  cash cash_receptions%ROWTYPE;
  obligation obligations%ROWTYPE;
  consumed numeric;
  due numeric;
  paid numeric;
  journal_size bigint;
  year_state text;
BEGIN
  IF current_setting('transaction_isolation') <> 'read committed' THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Les ecritures financieres exigent READ COMMITTED';
  END IF;
  SELECT * INTO cash FROM cash_receptions WHERE establishment_id=NEW.establishment_id AND id=NEW.reception_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Reception introuvable'; END IF;
  SELECT * INTO obligation FROM obligations WHERE establishment_id=NEW.establishment_id AND id=NEW.obligation_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Obligation introuvable'; END IF;
  SELECT status INTO year_state FROM academic_years WHERE establishment_id=NEW.establishment_id AND id=obligation.academic_year_id FOR SHARE;
  IF year_state IS DISTINCT FROM 'OPEN' THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Annee non ouverte'; END IF;
  IF cash.recorded_by_account_id IS DISTINCT FROM NEW.allocated_by THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Seul le declarant affecte cette reception';
  END IF;
  IF EXISTS(SELECT 1 FROM cash_allocations WHERE establishment_id=NEW.establishment_id AND reception_id=NEW.reception_id AND obligation_id<>NEW.obligation_id) THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Reception deja liee a une autre obligation';
  END IF;
  IF EXISTS(SELECT 1 FROM obligation_entries e WHERE e.obligation_id=NEW.obligation_id AND e.kind IN ('CORRECTION','CANCELLATION')
      AND NOT EXISTS(SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id=e.id)) THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Politique des ajustements non definie pour ce reglement';
  END IF;
  SELECT COALESCE(sum(amount_minor),0) INTO consumed FROM cash_allocations WHERE establishment_id=NEW.establishment_id AND reception_id=NEW.reception_id;
  IF consumed+NEW.amount_minor>cash.amount_received_minor-cash.change_given_minor THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Affectation superieure aux especes disponibles';
  END IF;
  SELECT COALESCE(sum(amount_minor),0),-COALESCE(sum(amount_minor) FILTER (WHERE kind='SETTLEMENT'),0),count(*)
    INTO due,paid,journal_size FROM obligation_entries WHERE establishment_id=NEW.establishment_id AND obligation_id=NEW.obligation_id;
  IF NEW.amount_minor>due OR NEW.due_before_minor<>due OR NEW.settled_before_minor<>paid
      OR NEW.journal_size_before<>journal_size OR NEW.issued_minor<>obligation.issued_amount_minor OR NEW.net_billed_minor<>due+paid THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='La preuve ne correspond pas au journal presente';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER cash_allocation_guard BEFORE INSERT ON cash_allocations FOR EACH ROW EXECUTE FUNCTION cash_allocation_guard();

CREATE OR REPLACE FUNCTION obligation_entries_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  due numeric;
  paid numeric;
  journal_size bigint;
  original_kind text;
  original_amount bigint;
  allocation cash_allocations%ROWTYPE;
BEGIN
  IF current_setting('transaction_isolation') <> 'read committed' THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Les ecritures du journal exigent READ COMMITTED';
  END IF;
  PERFORM 1 FROM obligations WHERE establishment_id=NEW.establishment_id AND id=NEW.obligation_id FOR UPDATE;
  SELECT COALESCE(sum(amount_minor),0),-COALESCE(sum(amount_minor) FILTER (WHERE kind='SETTLEMENT'),0),count(*)
    INTO due,paid,journal_size FROM obligation_entries WHERE obligation_id=NEW.obligation_id;
  IF due+NEW.amount_minor<0 THEN RAISE EXCEPTION 'Le montant dû d''une obligation ne peut pas devenir négatif'; END IF;
  IF due+NEW.amount_minor>9223372036854775807 THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Le solde depasse les limites monetaires du journal';
  END IF;
  IF NEW.kind='SETTLEMENT' THEN
    SELECT * INTO allocation FROM cash_allocations WHERE establishment_id=NEW.establishment_id AND id=NEW.cash_allocation_id;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Un reglement exige une affectation source'; END IF;
    IF allocation.obligation_id IS DISTINCT FROM NEW.obligation_id OR allocation.settlement_entry_id IS DISTINCT FROM NEW.id
        OR allocation.amount_minor<>-NEW.amount_minor OR allocation.allocated_by IS DISTINCT FROM NEW.recorded_by
        OR allocation.allocated_at IS DISTINCT FROM NEW.recorded_at OR allocation.journal_size_before<>journal_size
        OR allocation.due_before_minor<>due OR allocation.settled_before_minor<>paid THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Reglement incompatible avec sa preuve';
    END IF;
  ELSIF paid>0 AND NEW.kind IN ('CORRECTION','CANCELLATION','REVERSAL') THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Cette operation apres paiement reste fermee';
  END IF;
  IF NEW.kind NOT IN ('ISSUED','SETTLEMENT') AND due+NEW.amount_minor=0 AND NEW.approved_by IS NULL THEN
    RAISE EXCEPTION 'Amener le dû à zéro exige la confirmation d''une seconde personne';
  END IF;
  IF NEW.kind='CANCELLATION' AND due+NEW.amount_minor<>0 THEN RAISE EXCEPTION 'Une annulation porte sur tout le montant restant dû'; END IF;
  IF NEW.kind='REVERSAL' THEN
    SELECT kind,amount_minor INTO original_kind,original_amount FROM obligation_entries WHERE id=NEW.reverses_entry_id;
    IF original_kind IN ('ISSUED','REVERSAL','SETTLEMENT') THEN RAISE EXCEPTION 'Cette écriture ne se contrepasse pas'; END IF;
    IF original_amount<>-NEW.amount_minor THEN RAISE EXCEPTION 'Une contrepassée est l''exact opposé de son écriture'; END IF;
  END IF;
  RETURN NEW;
END;
$$;

CREATE INDEX audit_cash_allocations_idx ON audit_events(establishment_id,target_id,actor_account_id)
  WHERE action='payments.cash.allocated' AND target_type='cash_allocation';
CREATE FUNCTION cash_allocation_complete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS(SELECT 1 FROM obligation_entries e WHERE e.id=NEW.settlement_entry_id AND e.establishment_id=NEW.establishment_id
      AND e.obligation_id=NEW.obligation_id AND e.kind='SETTLEMENT' AND e.cash_allocation_id=NEW.id
      AND e.amount_minor=-NEW.amount_minor AND e.recorded_by=NEW.allocated_by AND e.recorded_at=NEW.allocated_at) THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une affectation exige son reglement correspondant';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM audit_events WHERE establishment_id=NEW.establishment_id AND target_id=NEW.id
      AND actor_account_id=NEW.allocated_by AND action='payments.cash.allocated' AND target_type='cash_allocation'
      AND details->>'reference'=NEW.reference AND details->>'receptionId'=NEW.reception_id::text
      AND details->>'obligationId'=NEW.obligation_id::text) THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une affectation exige son audit lie';
  END IF;
  RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER cash_allocation_complete AFTER INSERT ON cash_allocations
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cash_allocation_complete();
