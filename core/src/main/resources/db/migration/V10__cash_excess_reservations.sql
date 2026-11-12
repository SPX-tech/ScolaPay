-- Réservations seulement : aucune identité de bénéficiaire ou autorisation de remise physique n'est définie.
CREATE TABLE cash_excess_requests (
  id uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  reception_id uuid NOT NULL,
  currency text NOT NULL,
  amount_minor bigint NOT NULL CHECK (amount_minor BETWEEN 1 AND 999999999999999),
  reason text NOT NULL CHECK (length(reason) BETWEEN 1 AND 500 AND reason ~ '[^[:space:]]'),
  idempotency_key uuid NOT NULL,
  reference text NOT NULL CHECK (reference='XQ-'||id::text),
  authorization_scope text NOT NULL CHECK (authorization_scope='RESERVATION_ONLY_V1'),
  retained_minor bigint NOT NULL CHECK (retained_minor BETWEEN 1 AND 999999999999999),
  historical_allocated_before_minor bigint NOT NULL CHECK (historical_allocated_before_minor>=0),
  reserved_before_minor bigint NOT NULL CHECK (reserved_before_minor>=0),
  created_at timestamptz NOT NULL CHECK (isfinite(created_at)),
  created_by uuid NOT NULL,
  CHECK (amount_minor<=retained_minor-historical_allocated_before_minor-reserved_before_minor),
  UNIQUE (establishment_id,id),
  UNIQUE (establishment_id,idempotency_key),
  FOREIGN KEY (establishment_id,reception_id,currency) REFERENCES cash_receptions(establishment_id,id,currency),
  FOREIGN KEY (establishment_id,created_by) REFERENCES memberships(establishment_id,account_id)
);
CREATE INDEX cash_excess_requests_reception_idx ON cash_excess_requests(establishment_id,reception_id);

CREATE TABLE cash_excess_decisions (
  id uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  request_id uuid NOT NULL UNIQUE,
  kind text NOT NULL CHECK (kind IN ('APPROVED','REJECTED','WITHDRAWN')),
  note text CHECK (length(note) BETWEEN 1 AND 500 AND note ~ '[^[:space:]]'),
  idempotency_key uuid NOT NULL,
  reference text NOT NULL CHECK (reference='XD-'||id::text),
  decided_at timestamptz NOT NULL CHECK (isfinite(decided_at)),
  decided_by uuid NOT NULL,
  CHECK ((kind='APPROVED' AND note IS NULL) OR (kind IN ('REJECTED','WITHDRAWN') AND note IS NOT NULL)),
  UNIQUE (establishment_id,id),
  UNIQUE (establishment_id,idempotency_key),
  FOREIGN KEY (establishment_id,request_id) REFERENCES cash_excess_requests(establishment_id,id),
  FOREIGN KEY (establishment_id,decided_by) REFERENCES memberships(establishment_id,account_id)
);

CREATE TRIGGER cash_excess_requests_immutable BEFORE UPDATE OR DELETE ON cash_excess_requests FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_excess_requests_no_truncate BEFORE TRUNCATE ON cash_excess_requests FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_excess_decisions_immutable BEFORE UPDATE OR DELETE ON cash_excess_decisions FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_excess_decisions_no_truncate BEFORE TRUNCATE ON cash_excess_decisions FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE FUNCTION cash_excess_request_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  cash cash_receptions%ROWTYPE;
  historical numeric;
  reserved numeric;
BEGIN
  IF current_setting('transaction_isolation')<>'read committed' THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Les reservations exigent READ COMMITTED';
  END IF;
  SELECT * INTO cash FROM cash_receptions WHERE establishment_id=NEW.establishment_id AND id=NEW.reception_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Reception introuvable'; END IF;
  IF cash.recorded_by_account_id IS DISTINCT FROM NEW.created_by THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Seul le declarant reserve cette reception';
  END IF;
  -- Toute affectation historique compte, sans lecture d'un solde net de billing ou d'un statut d'obligation.
  SELECT COALESCE(sum(amount_minor),0) INTO historical FROM cash_allocations
    WHERE establishment_id=NEW.establishment_id AND reception_id=NEW.reception_id;
  SELECT COALESCE(sum(r.amount_minor),0) INTO reserved FROM cash_excess_requests r
    WHERE r.establishment_id=NEW.establishment_id AND r.reception_id=NEW.reception_id
      AND NOT EXISTS (SELECT 1 FROM cash_excess_decisions d WHERE d.request_id=r.id AND d.kind IN ('REJECTED','WITHDRAWN'));
  IF NEW.retained_minor IS DISTINCT FROM cash.amount_received_minor-cash.change_given_minor
      OR NEW.historical_allocated_before_minor IS DISTINCT FROM historical
      OR NEW.reserved_before_minor IS DISTINCT FROM reserved
      OR historical+reserved+NEW.amount_minor>cash.amount_received_minor-cash.change_given_minor THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Budget jamais affecte insuffisant ou preuve perimee';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER cash_excess_request_guard BEFORE INSERT ON cash_excess_requests FOR EACH ROW EXECUTE FUNCTION cash_excess_request_guard();

CREATE FUNCTION cash_excess_decision_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  request cash_excess_requests%ROWTYPE;
BEGIN
  IF current_setting('transaction_isolation')<>'read committed' THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Les decisions de reservation exigent READ COMMITTED';
  END IF;
  SELECT * INTO request FROM cash_excess_requests WHERE establishment_id=NEW.establishment_id AND id=NEW.request_id;
  IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Demande introuvable'; END IF;
  PERFORM 1 FROM cash_receptions WHERE establishment_id=NEW.establishment_id AND id=request.reception_id FOR UPDATE;
  IF EXISTS (SELECT 1 FROM cash_excess_decisions WHERE request_id=NEW.request_id) THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Cette demande a deja une decision';
  END IF;
  IF NEW.kind='WITHDRAWN' THEN
    IF NEW.decided_by IS DISTINCT FROM request.created_by THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Seul le demandeur retire sa reservation non approuvee';
    END IF;
  ELSIF NEW.kind IN ('APPROVED','REJECTED') THEN
    IF NEW.decided_by IS NOT DISTINCT FROM request.created_by THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une seconde personne doit examiner la reservation';
    END IF;
  ELSE
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='La remise physique et les autres decisions restent fermees';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER cash_excess_decision_guard BEFORE INSERT ON cash_excess_decisions FOR EACH ROW EXECUTE FUNCTION cash_excess_decision_guard();

-- Garde complémentaire : V9 reste intacte et garde toutes ses contraintes d'affectation/journal.
CREATE FUNCTION cash_allocation_reservations_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  retained numeric;
  historical numeric;
  reserved numeric;
BEGIN
  IF current_setting('transaction_isolation')<>'read committed' THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Les affectations exigent READ COMMITTED';
  END IF;
  SELECT amount_received_minor-change_given_minor INTO retained FROM cash_receptions
    WHERE establishment_id=NEW.establishment_id AND id=NEW.reception_id FOR UPDATE;
  SELECT COALESCE(sum(amount_minor),0) INTO historical FROM cash_allocations
    WHERE establishment_id=NEW.establishment_id AND reception_id=NEW.reception_id;
  SELECT COALESCE(sum(r.amount_minor),0) INTO reserved FROM cash_excess_requests r
    WHERE r.establishment_id=NEW.establishment_id AND r.reception_id=NEW.reception_id
      AND NOT EXISTS (SELECT 1 FROM cash_excess_decisions d WHERE d.request_id=r.id AND d.kind IN ('REJECTED','WITHDRAWN'));
  IF historical+reserved+NEW.amount_minor>retained THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Les fonds reserves ne peuvent pas etre affectes';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER cash_allocation_reservations_guard BEFORE INSERT ON cash_allocations
  FOR EACH ROW EXECUTE FUNCTION cash_allocation_reservations_guard();

CREATE INDEX audit_cash_excess_idx ON audit_events(establishment_id,target_type,target_id,actor_account_id)
  WHERE action IN ('payments.excess.requested','payments.excess.approved','payments.excess.rejected','payments.excess.withdrawn');
CREATE FUNCTION cash_excess_complete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_TABLE_NAME='cash_excess_requests' THEN
    IF NOT EXISTS (SELECT 1 FROM audit_events WHERE establishment_id=NEW.establishment_id AND target_id=NEW.id
        AND actor_account_id=NEW.created_by AND action='payments.excess.requested' AND target_type='cash_excess_request'
        AND details->>'reference'=NEW.reference AND details->>'receptionId'=NEW.reception_id::text) THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une demande exige son audit lie';
    END IF;
  ELSE
    IF NOT EXISTS (SELECT 1 FROM audit_events WHERE establishment_id=NEW.establishment_id AND target_id=NEW.id
        AND actor_account_id=NEW.decided_by AND action='payments.excess.'||lower(NEW.kind) AND target_type='cash_excess_decision'
        AND details->>'reference'=NEW.reference AND details->>'requestId'=NEW.request_id::text) THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une decision exige son audit lie';
    END IF;
  END IF;
  RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER cash_excess_request_complete AFTER INSERT ON cash_excess_requests
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cash_excess_complete();
CREATE CONSTRAINT TRIGGER cash_excess_decision_complete AFTER INSERT ON cash_excess_decisions
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cash_excess_complete();
