-- Préparation scolaire seulement : aucune session externe, réception ou écriture de règlement.
CREATE TABLE digital_payment_intents (
  id uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  obligation_id uuid NOT NULL,
  origin_type text NOT NULL CHECK (origin_type='SCHOOL_OBLIGATION'),
  provider text NOT NULL CHECK (provider='KADRYZA'),
  purpose text NOT NULL CHECK (purpose='PREPARATION_ONLY_V1'),
  state text NOT NULL CHECK (state='PREPARED'),
  reference text NOT NULL CHECK (reference='DPI-'||id::text),
  idempotency_key uuid NOT NULL,
  currency text NOT NULL CHECK (currency='XAF'),
  amount_minor bigint NOT NULL CHECK (amount_minor BETWEEN 1 AND 999999999999999),
  issued_minor bigint NOT NULL CHECK (issued_minor BETWEEN 1 AND 999999999999999),
  net_billed_minor bigint NOT NULL CHECK (net_billed_minor BETWEEN 1 AND 999999999999999),
  settled_before_minor bigint NOT NULL CHECK (settled_before_minor>=0),
  due_before_minor bigint NOT NULL CHECK (due_before_minor>=amount_minor AND due_before_minor=net_billed_minor-settled_before_minor),
  journal_size_before bigint NOT NULL CHECK (journal_size_before>=1),
  prepared_at timestamptz NOT NULL CHECK (isfinite(prepared_at)),
  prepared_by uuid NOT NULL,
  UNIQUE (establishment_id,id),
  UNIQUE (establishment_id,idempotency_key),
  FOREIGN KEY (establishment_id,obligation_id,currency) REFERENCES obligations(establishment_id,id,currency),
  FOREIGN KEY (establishment_id,prepared_by) REFERENCES memberships(establishment_id,account_id)
);
CREATE INDEX digital_payment_intents_source_idx ON digital_payment_intents(establishment_id,obligation_id);
CREATE TRIGGER digital_payment_intents_immutable BEFORE UPDATE OR DELETE ON digital_payment_intents
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER digital_payment_intents_no_truncate BEFORE TRUNCATE ON digital_payment_intents
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE FUNCTION digital_payment_intent_source_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source obligations%ROWTYPE; due numeric; paid numeric; version bigint; year_state text;
BEGIN
  IF current_setting('transaction_isolation')<>'read committed' THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Les preparations exigent READ COMMITTED';
  END IF;
  SELECT * INTO source FROM obligations WHERE establishment_id=NEW.establishment_id AND id=NEW.obligation_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Obligation introuvable'; END IF;
  SELECT status INTO year_state FROM academic_years WHERE establishment_id=NEW.establishment_id AND id=source.academic_year_id FOR SHARE;
  IF year_state IS DISTINCT FROM 'OPEN' THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Annee non ouverte'; END IF;
  IF NOT EXISTS (SELECT 1 FROM memberships m JOIN membership_roles r ON r.membership_id=m.id JOIN establishments e ON e.id=m.establishment_id
      WHERE m.establishment_id=NEW.establishment_id AND m.account_id=NEW.prepared_by AND m.status='ACTIVE' AND e.status='ACTIVE'
        AND r.role IN ('ESTABLISHMENT_ADMIN','ACCOUNTANT')) THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Preparateur non habilite';
  END IF;
  IF EXISTS (SELECT 1 FROM obligation_entries e WHERE e.obligation_id=source.id AND e.kind IN ('CORRECTION','CANCELLATION')
      AND NOT EXISTS (SELECT 1 FROM obligation_entries r WHERE r.reverses_entry_id=e.id)) THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Politique des ajustements non definie pour ce paiement';
  END IF;
  SELECT COALESCE(sum(amount_minor),0),-COALESCE(sum(amount_minor) FILTER (WHERE kind='SETTLEMENT'),0),count(*)
    INTO due,paid,version FROM obligation_entries WHERE establishment_id=NEW.establishment_id AND obligation_id=source.id;
  IF source.currency IS DISTINCT FROM NEW.currency OR NEW.issued_minor IS DISTINCT FROM source.issued_amount_minor
      OR NEW.due_before_minor IS DISTINCT FROM due OR NEW.settled_before_minor IS DISTINCT FROM paid
      OR NEW.net_billed_minor IS DISTINCT FROM due+paid OR NEW.journal_size_before IS DISTINCT FROM version OR NEW.amount_minor>due THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='La preparation ne correspond pas au journal presente';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER digital_payment_intent_source_guard BEFORE INSERT ON digital_payment_intents
  FOR EACH ROW EXECUTE FUNCTION digital_payment_intent_source_guard();
-- Le journal ne doit pas changer ensuite dans la transaction qui crée cette préparation.
CREATE CONSTRAINT TRIGGER digital_payment_intent_source_at_commit AFTER INSERT ON digital_payment_intents
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION digital_payment_intent_source_guard();

CREATE INDEX audit_digital_payment_intents_idx ON audit_events(establishment_id,target_id,actor_account_id)
  WHERE action='payments.digital.intent_prepared' AND target_type='digital_payment_intent';
CREATE FUNCTION digital_payment_intent_complete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM audit_events WHERE establishment_id=NEW.establishment_id AND target_id=NEW.id
      AND actor_account_id=NEW.prepared_by AND action='payments.digital.intent_prepared' AND target_type='digital_payment_intent'
      AND details->>'reference'=NEW.reference AND details->>'obligationId'=NEW.obligation_id::text) THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une preparation exige son audit lie';
  END IF;
  RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER digital_payment_intent_complete AFTER INSERT ON digital_payment_intents
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION digital_payment_intent_complete();
