-- Première étape de payments : déclaration immuable, sans affectation, crédit ou SETTLEMENT.
-- La devise et l'auteur restent attachés à leur établissement même en SQL direct.
ALTER TABLE establishments ADD UNIQUE (id, currency);

CREATE TABLE cash_receptions (
  id                      uuid PRIMARY KEY,
  establishment_id        uuid NOT NULL,
  reference               text NOT NULL CHECK (reference = 'CR-' || id::text),
  idempotency_key          uuid NOT NULL,
  currency                text NOT NULL,
  amount_received_minor   bigint NOT NULL CHECK (amount_received_minor BETWEEN 1 AND 999999999999999),
  change_given_minor      bigint NOT NULL CHECK (change_given_minor >= 0 AND change_given_minor < amount_received_minor),
  cash_received_confirmed boolean NOT NULL CHECK (cash_received_confirmed),
  recorded_at             timestamptz NOT NULL CHECK (isfinite(recorded_at)),
  recorded_by_account_id  uuid NOT NULL,
  business_date           date NOT NULL,
  timezone                text NOT NULL,
  CHECK (business_date = (recorded_at AT TIME ZONE timezone)::date),
  UNIQUE (establishment_id, id),
  UNIQUE (establishment_id, reference),
  UNIQUE (establishment_id, idempotency_key),
  FOREIGN KEY (establishment_id, currency) REFERENCES establishments (id, currency),
  FOREIGN KEY (establishment_id, recorded_by_account_id) REFERENCES memberships (establishment_id, account_id)
);

CREATE INDEX cash_receptions_cashier_day_idx
  ON cash_receptions (establishment_id, recorded_by_account_id, business_date, recorded_at, id);

CREATE TRIGGER cash_receptions_immutable BEFORE UPDATE OR DELETE ON cash_receptions
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_receptions_no_truncate BEFORE TRUNCATE ON cash_receptions
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

-- Intégration SQL avec audit, sans donner au dépôt payments accès aux tables de ce module.
CREATE INDEX audit_cash_receptions_idx ON audit_events (establishment_id, target_id, actor_account_id)
  WHERE action = 'payments.cash.received' AND target_type = 'cash_reception';

CREATE FUNCTION cash_reception_complete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM audit_events
    WHERE establishment_id = NEW.establishment_id AND target_id = NEW.id
      AND actor_account_id = NEW.recorded_by_account_id
      AND action = 'payments.cash.received' AND target_type = 'cash_reception'
      AND details->>'reference' = NEW.reference
  ) THEN
    RAISE EXCEPTION 'Une declaration d''especes exige son audit lie';
  END IF;
  RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER cash_reception_complete AFTER INSERT ON cash_receptions
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cash_reception_complete();

-- Les contraintes V6 du journal ne sont pas modifiées : tout SETTLEMENT reste refusé.
