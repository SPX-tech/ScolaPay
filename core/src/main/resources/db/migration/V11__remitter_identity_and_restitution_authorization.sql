-- Les faits V8 à V10 restent immuables. Seules les nouvelles réceptions exigent A05.
ALTER TABLE cash_receptions ADD COLUMN identification_policy text NOT NULL DEFAULT 'LEGACY_UNIDENTIFIED'
  CHECK (identification_policy IN ('LEGACY_UNIDENTIFIED','IDENTIFIED_V1'));
ALTER TABLE cash_receptions ALTER COLUMN identification_policy SET DEFAULT 'IDENTIFIED_V1';
CREATE FUNCTION cash_reception_identity_policy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.identification_policy IS DISTINCT FROM 'IDENTIFIED_V1' THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une nouvelle reception exige une identite de remettant';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER cash_reception_identity_policy BEFORE INSERT ON cash_receptions
  FOR EACH ROW EXECUTE FUNCTION cash_reception_identity_policy();

CREATE TABLE cash_remitter_identifications (
  id uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  origin text NOT NULL CHECK (origin IN ('AT_RECEPTION','LEGACY_DECLARATION')),
  encrypted_payload text NOT NULL CHECK (length(encrypted_payload) BETWEEN 43 AND 4096 AND encrypted_payload LIKE 'v1:%'),
  idempotency_key uuid NOT NULL,
  reference text NOT NULL CHECK (reference='CI-'||id::text),
  statement_reference text CHECK (statement_reference ~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,79}$'),
  statement_signed_confirmed boolean NOT NULL,
  recorded_at timestamptz NOT NULL CHECK (isfinite(recorded_at)),
  recorded_by uuid NOT NULL,
  CHECK ((origin='AT_RECEPTION' AND statement_reference IS NULL AND NOT statement_signed_confirmed)
      OR (origin='LEGACY_DECLARATION' AND statement_reference IS NOT NULL AND statement_signed_confirmed)),
  UNIQUE (establishment_id,id),
  UNIQUE (establishment_id,idempotency_key),
  UNIQUE (establishment_id,statement_reference),
  FOREIGN KEY (establishment_id,id) REFERENCES cash_receptions(establishment_id,id),
  FOREIGN KEY (establishment_id,recorded_by) REFERENCES memberships(establishment_id,account_id)
);
CREATE TRIGGER cash_remitter_identifications_immutable BEFORE UPDATE OR DELETE ON cash_remitter_identifications
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_remitter_identifications_no_truncate BEFORE TRUNCATE ON cash_remitter_identifications
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE FUNCTION cash_remitter_identification_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source cash_receptions%ROWTYPE;
BEGIN
  IF current_setting('transaction_isolation')<>'read committed' THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Les identifications exigent READ COMMITTED';
  END IF;
  SELECT * INTO source FROM cash_receptions WHERE establishment_id=NEW.establishment_id AND id=NEW.id FOR UPDATE;
  IF NOT FOUND OR source.recorded_by_account_id IS DISTINCT FROM NEW.recorded_by THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Source ou declarant incompatibles';
  END IF;
  IF (NEW.origin='AT_RECEPTION' AND (source.identification_policy<>'IDENTIFIED_V1' OR source.recorded_at IS DISTINCT FROM NEW.recorded_at
        OR source.idempotency_key IS DISTINCT FROM NEW.idempotency_key))
      OR (NEW.origin='LEGACY_DECLARATION' AND source.identification_policy<>'LEGACY_UNIDENTIFIED') THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Procedure incompatible avec cette reception';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER cash_remitter_identification_guard BEFORE INSERT ON cash_remitter_identifications
  FOR EACH ROW EXECUTE FUNCTION cash_remitter_identification_guard();

CREATE INDEX audit_cash_identities_idx ON audit_events(establishment_id,target_type,target_id,actor_account_id)
  WHERE action='payments.remitter.recorded';
CREATE FUNCTION cash_remitter_complete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_TABLE_NAME='cash_receptions' THEN
    IF NOT EXISTS (SELECT 1 FROM cash_remitter_identifications WHERE establishment_id=NEW.establishment_id AND id=NEW.id AND origin='AT_RECEPTION') THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une nouvelle reception exige son identification liee';
    END IF;
  ELSE
    IF NOT EXISTS (SELECT 1 FROM audit_events WHERE establishment_id=NEW.establishment_id AND target_id=NEW.id
        AND actor_account_id=NEW.recorded_by AND target_type='cash_remitter_identification' AND action='payments.remitter.recorded'
        AND details->>'reference'=NEW.reference AND details->>'receptionId'=NEW.id::text) THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une identification exige son audit lie';
    END IF;
  END IF;
  RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER cash_reception_identification_complete AFTER INSERT ON cash_receptions
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cash_remitter_complete();
CREATE CONSTRAINT TRIGGER cash_remitter_identification_complete AFTER INSERT ON cash_remitter_identifications
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cash_remitter_complete();
