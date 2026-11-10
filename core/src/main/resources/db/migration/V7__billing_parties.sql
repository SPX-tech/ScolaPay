-- Les personnes liées à une obligation. Rien ici ne se déduit d'un âge ou d'un lien de parenté : chaque
-- rôle est une désignation explicite, tracée, et ne s'efface jamais.

CREATE TABLE billing_parties (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL REFERENCES establishments (id),
  kind             text NOT NULL CHECK (kind IN ('PERSON', 'ORGANIZATION')),
  display_name     text NOT NULL CHECK (length(display_name) BETWEEN 2 AND 200),
  identifier       text CHECK (length(identifier) BETWEEN 1 AND 100),
  account_id       uuid REFERENCES accounts (id),
  created_at       timestamptz NOT NULL,
  created_by       uuid NOT NULL REFERENCES accounts (id),
  revision         integer NOT NULL DEFAULT 0 CHECK (revision >= 0),
  UNIQUE (establishment_id, id)
);
CREATE UNIQUE INDEX billing_parties_one_per_account ON billing_parties (establishment_id, account_id) WHERE account_id IS NOT NULL;
CREATE INDEX billing_parties_account_idx ON billing_parties (account_id) WHERE account_id IS NOT NULL;
CREATE TRIGGER billing_parties_immutable BEFORE DELETE ON billing_parties
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER billing_parties_no_truncate BEFORE TRUNCATE ON billing_parties
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

-- Le compte et le type restent immuables : corriger un texte ne transfère aucun droit financier.
CREATE TABLE billing_party_corrections (
  id                  uuid PRIMARY KEY,
  establishment_id    uuid NOT NULL,
  party_id            uuid NOT NULL,
  previous_revision   integer NOT NULL CHECK (previous_revision >= 0),
  revision            integer NOT NULL CHECK (revision = previous_revision + 1),
  previous_name       text NOT NULL CHECK (length(previous_name) BETWEEN 2 AND 200),
  previous_identifier text CHECK (length(previous_identifier) BETWEEN 1 AND 100),
  new_name            text NOT NULL CHECK (length(btrim(new_name)) BETWEEN 2 AND 200),
  new_identifier      text CHECK (length(btrim(new_identifier)) BETWEEN 1 AND 100),
  reason              text NOT NULL CHECK (length(btrim(reason)) BETWEEN 3 AND 500),
  corrected_at        timestamptz NOT NULL,
  corrected_by        uuid NOT NULL REFERENCES accounts(id),
  UNIQUE (establishment_id, id),
  UNIQUE (establishment_id, party_id, revision),
  FOREIGN KEY (establishment_id, party_id) REFERENCES billing_parties(establishment_id, id),
  CHECK ((previous_name, previous_identifier) IS DISTINCT FROM (new_name, new_identifier))
);

CREATE FUNCTION billing_party_correction_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE person billing_parties%ROWTYPE;
BEGIN
  SELECT * INTO person FROM billing_parties
    WHERE establishment_id=NEW.establishment_id AND id=NEW.party_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Personne introuvable dans cet etablissement';
  END IF;
  IF NEW.previous_revision <> person.revision OR NEW.revision <> person.revision + 1
     OR (NEW.previous_name, NEW.previous_identifier) IS DISTINCT FROM (person.display_name, person.identifier) THEN
    RAISE EXCEPTION 'La correction doit correspondre a la version courante';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER billing_party_correction_guard BEFORE INSERT ON billing_party_corrections
  FOR EACH ROW EXECUTE FUNCTION billing_party_correction_guard();

CREATE FUNCTION billing_parties_update_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.id, NEW.establishment_id, NEW.kind, NEW.account_id, NEW.created_at, NEW.created_by)
     IS DISTINCT FROM (OLD.id, OLD.establishment_id, OLD.kind, OLD.account_id, OLD.created_at, OLD.created_by) THEN
    RAISE EXCEPTION 'L''identite et le compte d''une personne sont immuables';
  END IF;
  IF NEW.revision <> OLD.revision + 1 OR
     NOT EXISTS (SELECT 1 FROM billing_party_corrections c
       WHERE c.establishment_id=OLD.establishment_id AND c.party_id=OLD.id
         AND c.previous_revision=OLD.revision AND c.revision=NEW.revision
         AND (c.previous_name,c.previous_identifier) IS NOT DISTINCT FROM (OLD.display_name,OLD.identifier)
         AND (c.new_name,c.new_identifier) IS NOT DISTINCT FROM (NEW.display_name,NEW.identifier)) THEN
    RAISE EXCEPTION 'Une personne est immuable hors correction tracee';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER billing_parties_update_guard BEFORE UPDATE ON billing_parties
  FOR EACH ROW EXECUTE FUNCTION billing_parties_update_guard();

-- Une trace insérée seule ne doit pas prétendre qu'une correction a eu lieu.
CREATE FUNCTION billing_party_correction_applied() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM billing_parties
      WHERE establishment_id=NEW.establishment_id AND id=NEW.party_id AND revision>=NEW.revision) THEN
    RAISE EXCEPTION 'La correction doit etre appliquee dans la meme transaction';
  END IF;
  RETURN NEW;
END;
$$;
CREATE CONSTRAINT TRIGGER billing_party_correction_applied AFTER INSERT ON billing_party_corrections
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION billing_party_correction_applied();
CREATE TRIGGER billing_party_corrections_immutable BEFORE UPDATE OR DELETE ON billing_party_corrections
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER billing_party_corrections_no_truncate BEFORE TRUNCATE ON billing_party_corrections
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE TABLE obligation_parties (
  id                   uuid PRIMARY KEY,
  establishment_id     uuid NOT NULL,
  obligation_id        uuid NOT NULL,
  party_id             uuid NOT NULL,
  role                 text NOT NULL CHECK (role IN ('CONTRACTUAL_DEBTOR', 'FINANCIAL_RESPONSIBLE', 'VOLUNTARY_CONTRIBUTOR')),
  debtor_status        text CHECK (debtor_status IN ('UNCONFIRMED', 'CONFIRMED')),
  commitment_reference text CHECK (length(btrim(commitment_reference)) BETWEEN 1 AND 200),
  designated_at        timestamptz NOT NULL,
  designated_by        uuid NOT NULL REFERENCES accounts (id),
  confirmed_at         timestamptz,
  confirmed_by         uuid REFERENCES accounts (id),
  ended_at             timestamptz,
  ended_by             uuid REFERENCES accounts (id),
  end_reason           text CHECK (length(end_reason) BETWEEN 1 AND 500),
  CONSTRAINT obligation_parties_debtor_status CHECK ((role = 'CONTRACTUAL_DEBTOR') = (debtor_status IS NOT NULL)),
  CONSTRAINT obligation_parties_commitment CHECK (
    (debtor_status IS NOT DISTINCT FROM 'CONFIRMED' AND commitment_reference IS NOT NULL
      AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL)
    OR (debtor_status IS DISTINCT FROM 'CONFIRMED' AND commitment_reference IS NULL
      AND confirmed_at IS NULL AND confirmed_by IS NULL)),
  CHECK ((confirmed_at IS NULL) = (confirmed_by IS NULL)),
  CHECK ((commitment_reference IS NULL) = (confirmed_at IS NULL)),
  CHECK ((ended_at IS NULL) = (ended_by IS NULL)),
  CHECK ((ended_at IS NULL) = (end_reason IS NULL)),
  UNIQUE (establishment_id, id),
  FOREIGN KEY (establishment_id, obligation_id) REFERENCES obligations (establishment_id, id),
  FOREIGN KEY (establishment_id, party_id) REFERENCES billing_parties (establishment_id, id)
);
CREATE UNIQUE INDEX obligation_parties_one_debtor ON obligation_parties (obligation_id)
  WHERE role = 'CONTRACTUAL_DEBTOR' AND ended_at IS NULL;
CREATE UNIQUE INDEX obligation_parties_one_responsible ON obligation_parties (obligation_id)
  WHERE role = 'FINANCIAL_RESPONSIBLE' AND ended_at IS NULL;
CREATE UNIQUE INDEX obligation_parties_one_active_role ON obligation_parties (obligation_id, party_id, role)
  WHERE ended_at IS NULL;
CREATE INDEX obligation_parties_party_idx ON obligation_parties (party_id) WHERE ended_at IS NULL;

-- Un contributeur règle lui-même, ce qui suppose un compte. Un redevable ou un responsable peut n'en avoir aucun.
CREATE FUNCTION obligation_parties_insert_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.role = 'VOLUNTARY_CONTRIBUTOR'
     AND NOT EXISTS (SELECT 1 FROM billing_parties WHERE id = NEW.party_id AND account_id IS NOT NULL) THEN
    RAISE EXCEPTION 'Un contributeur volontaire doit disposer d''un compte';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER obligation_parties_insert_guard BEFORE INSERT ON obligation_parties
  FOR EACH ROW EXECUTE FUNCTION obligation_parties_insert_guard();

CREATE FUNCTION obligation_parties_update_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.ended_at IS NOT NULL THEN
    RAISE EXCEPTION 'Une désignation terminée est immuable';
  END IF;
  IF (NEW.id, NEW.establishment_id, NEW.obligation_id, NEW.party_id, NEW.role, NEW.designated_at, NEW.designated_by)
     IS DISTINCT FROM (OLD.id, OLD.establishment_id, OLD.obligation_id, OLD.party_id, OLD.role, OLD.designated_at, OLD.designated_by) THEN
    RAISE EXCEPTION 'L''identité d''une désignation est immuable';
  END IF;
  IF OLD.debtor_status = 'CONFIRMED'
     AND (NEW.debtor_status, NEW.commitment_reference, NEW.confirmed_at, NEW.confirmed_by)
         IS DISTINCT FROM (OLD.debtor_status, OLD.commitment_reference, OLD.confirmed_at, OLD.confirmed_by) THEN
    RAISE EXCEPTION 'Une confirmation est immuable : terminez cette désignation pour en créer une autre';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER obligation_parties_update_guard BEFORE UPDATE ON obligation_parties
  FOR EACH ROW EXECUTE FUNCTION obligation_parties_update_guard();
CREATE TRIGGER obligation_parties_no_delete BEFORE DELETE ON obligation_parties
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER obligation_parties_no_truncate BEFORE TRUNCATE ON obligation_parties
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

-- financial_contributor disparaît : un lien dit seulement « ce compte est lié à ce dossier », et
-- obligation_parties dit quel rôle il tient sur quelle obligation. Aucune instance déployée n'en
-- contient : plutôt que de transformer des données à l'aveugle, la migration s'arrête si c'était faux.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM record_access_grants WHERE financial_contributor AND revoked_at IS NULL) THEN
    RAISE EXCEPTION 'Des liens de contribution actifs existent : à désigner explicitement avant cette migration';
  END IF;
END;
$$;
ALTER TABLE record_access_grants DROP COLUMN financial_contributor;
