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

CREATE TABLE cash_remitter_reviews (
  id uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  identification_id uuid NOT NULL,
  original_receipt_matched_confirmed boolean NOT NULL CHECK (original_receipt_matched_confirmed),
  signed_statement_checked_confirmed boolean NOT NULL CHECK (signed_statement_checked_confirmed),
  identity_checked_confirmed boolean NOT NULL CHECK (identity_checked_confirmed),
  idempotency_key uuid NOT NULL,
  reference text NOT NULL CHECK (reference='CIR-'||id::text),
  reviewed_at timestamptz NOT NULL CHECK (isfinite(reviewed_at)),
  reviewed_by uuid NOT NULL,
  UNIQUE (establishment_id,id),
  UNIQUE (establishment_id,identification_id),
  UNIQUE (establishment_id,idempotency_key),
  FOREIGN KEY (establishment_id,identification_id) REFERENCES cash_remitter_identifications(establishment_id,id),
  FOREIGN KEY (establishment_id,reviewed_by) REFERENCES memberships(establishment_id,account_id)
);
CREATE TABLE cash_beneficiary_verifications (
  id uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  request_id uuid NOT NULL,
  identification_id uuid NOT NULL,
  beneficiary_kind text NOT NULL CHECK (beneficiary_kind='REMITTER_ONLY'),
  identity_checked_confirmed boolean NOT NULL CHECK (identity_checked_confirmed),
  encrypted_payload text NOT NULL CHECK (length(encrypted_payload) BETWEEN 43 AND 4096 AND encrypted_payload LIKE 'v1:%'),
  idempotency_key uuid NOT NULL,
  reference text NOT NULL CHECK (reference='CBV-'||id::text),
  verified_at timestamptz NOT NULL CHECK (isfinite(verified_at)),
  verified_by uuid NOT NULL,
  UNIQUE (establishment_id,id),
  UNIQUE (establishment_id,request_id),
  UNIQUE (establishment_id,idempotency_key),
  FOREIGN KEY (establishment_id,request_id) REFERENCES cash_excess_requests(establishment_id,id),
  FOREIGN KEY (establishment_id,identification_id) REFERENCES cash_remitter_identifications(establishment_id,id),
  FOREIGN KEY (establishment_id,verified_by) REFERENCES memberships(establishment_id,account_id)
);
CREATE TABLE cash_restitution_authorizations (
  id uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  request_id uuid NOT NULL,
  beneficiary_verification_id uuid NOT NULL,
  policy text NOT NULL CHECK (policy='NAMED_REMITTER_V1'),
  beneficiary_reviewed_confirmed boolean NOT NULL CHECK (beneficiary_reviewed_confirmed),
  idempotency_key uuid NOT NULL,
  reference text NOT NULL CHECK (reference='CRA-'||id::text),
  authorized_at timestamptz NOT NULL CHECK (isfinite(authorized_at)),
  authorized_by uuid NOT NULL,
  UNIQUE (establishment_id,id),
  UNIQUE (establishment_id,request_id),
  UNIQUE (establishment_id,idempotency_key),
  FOREIGN KEY (establishment_id,request_id) REFERENCES cash_excess_requests(establishment_id,id),
  FOREIGN KEY (establishment_id,beneficiary_verification_id) REFERENCES cash_beneficiary_verifications(establishment_id,id),
  FOREIGN KEY (establishment_id,authorized_by) REFERENCES memberships(establishment_id,account_id)
);
CREATE TRIGGER cash_remitter_reviews_immutable BEFORE UPDATE OR DELETE ON cash_remitter_reviews FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_remitter_reviews_no_truncate BEFORE TRUNCATE ON cash_remitter_reviews FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_beneficiary_verifications_immutable BEFORE UPDATE OR DELETE ON cash_beneficiary_verifications FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_beneficiary_verifications_no_truncate BEFORE TRUNCATE ON cash_beneficiary_verifications FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_restitution_authorizations_immutable BEFORE UPDATE OR DELETE ON cash_restitution_authorizations FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER cash_restitution_authorizations_no_truncate BEFORE TRUNCATE ON cash_restitution_authorizations FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE FUNCTION cash_restitution_preparation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  identity cash_remitter_identifications%ROWTYPE;
  request cash_excess_requests%ROWTYPE;
  verification cash_beneficiary_verifications%ROWTYPE;
BEGIN
  IF current_setting('transaction_isolation')<>'read committed' THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='La preparation de restitution exige READ COMMITTED';
  END IF;
  IF TG_TABLE_NAME='cash_remitter_reviews' THEN
    SELECT * INTO identity FROM cash_remitter_identifications WHERE establishment_id=NEW.establishment_id AND id=NEW.identification_id;
    IF NOT FOUND OR identity.origin<>'LEGACY_DECLARATION' THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Declaration historique signee requise';
    END IF;
    PERFORM 1 FROM cash_receptions WHERE establishment_id=NEW.establishment_id AND id=identity.id FOR UPDATE;
    IF NEW.reviewed_by IS NOT DISTINCT FROM identity.recorded_by THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une seconde personne doit examiner la declaration';
    END IF;
  ELSE
    SELECT * INTO request FROM cash_excess_requests WHERE establishment_id=NEW.establishment_id AND id=NEW.request_id;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Reservation introuvable'; END IF;
    PERFORM 1 FROM cash_receptions WHERE establishment_id=NEW.establishment_id AND id=request.reception_id FOR UPDATE;
    IF NOT EXISTS (SELECT 1 FROM cash_excess_decisions WHERE establishment_id=NEW.establishment_id AND request_id=request.id AND kind='APPROVED') THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une reservation techniquement approuvee est requise';
    END IF;
    IF TG_TABLE_NAME='cash_beneficiary_verifications' THEN
      SELECT * INTO identity FROM cash_remitter_identifications WHERE establishment_id=NEW.establishment_id AND id=NEW.identification_id;
      IF NOT FOUND OR identity.id IS DISTINCT FROM request.reception_id OR NEW.verified_by IS DISTINCT FROM request.created_by THEN
        RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Source ou auteur de rapprochement incompatible';
      END IF;
      IF identity.origin='LEGACY_DECLARATION' AND NOT EXISTS (SELECT 1 FROM cash_remitter_reviews WHERE establishment_id=NEW.establishment_id AND identification_id=identity.id) THEN
        RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='La declaration historique exige son examen independant';
      END IF;
    ELSE
      SELECT * INTO verification FROM cash_beneficiary_verifications WHERE establishment_id=NEW.establishment_id AND id=NEW.beneficiary_verification_id;
      IF NOT FOUND OR verification.request_id IS DISTINCT FROM request.id THEN
        RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Le beneficiaire doit appartenir a cette reservation';
      END IF;
      IF NEW.authorized_by IS NOT DISTINCT FROM request.created_by THEN
        RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='Une seconde personne doit autoriser la restitution';
      END IF;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER cash_remitter_review_guard BEFORE INSERT ON cash_remitter_reviews FOR EACH ROW EXECUTE FUNCTION cash_restitution_preparation_guard();
CREATE TRIGGER cash_beneficiary_verification_guard BEFORE INSERT ON cash_beneficiary_verifications FOR EACH ROW EXECUTE FUNCTION cash_restitution_preparation_guard();
CREATE TRIGGER cash_restitution_authorization_guard BEFORE INSERT ON cash_restitution_authorizations FOR EACH ROW EXECUTE FUNCTION cash_restitution_preparation_guard();

CREATE INDEX audit_cash_restitution_preparation_idx ON audit_events(establishment_id,target_type,target_id,actor_account_id)
  WHERE action IN ('payments.remitter.reviewed','payments.beneficiary.verified','payments.restitution.authorized');
CREATE FUNCTION cash_restitution_preparation_complete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE expected_action text; expected_target text; actor uuid; source uuid; linked_request uuid;
BEGIN
  IF TG_TABLE_NAME='cash_remitter_reviews' THEN
    expected_action:='payments.remitter.reviewed';expected_target:='cash_remitter_review';actor:=NEW.reviewed_by;source:=NEW.identification_id;
  ELSIF TG_TABLE_NAME='cash_beneficiary_verifications' THEN
    expected_action:='payments.beneficiary.verified';expected_target:='cash_beneficiary_verification';actor:=NEW.verified_by;source:=NEW.identification_id;linked_request:=NEW.request_id;
  ELSE
    expected_action:='payments.restitution.authorized';expected_target:='cash_restitution_authorization';actor:=NEW.authorized_by;linked_request:=NEW.request_id;
    SELECT reception_id INTO source FROM cash_excess_requests WHERE establishment_id=NEW.establishment_id AND id=NEW.request_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM audit_events WHERE establishment_id=NEW.establishment_id AND target_id=NEW.id
      AND actor_account_id=actor AND action=expected_action AND target_type=expected_target
      AND details->>'reference'=NEW.reference AND details->>'receptionId'=source::text
      AND (linked_request IS NULL OR details->>'requestId'=linked_request::text)) THEN
    RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='La preparation de restitution exige son audit lie';
  END IF;
  RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER cash_remitter_review_complete AFTER INSERT ON cash_remitter_reviews
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cash_restitution_preparation_complete();
CREATE CONSTRAINT TRIGGER cash_beneficiary_verification_complete AFTER INSERT ON cash_beneficiary_verifications
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cash_restitution_preparation_complete();
CREATE CONSTRAINT TRIGGER cash_restitution_authorization_complete AFTER INSERT ON cash_restitution_authorizations
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION cash_restitution_preparation_complete();
