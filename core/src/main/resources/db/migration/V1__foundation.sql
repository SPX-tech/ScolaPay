-- Fondation ScolaPay 2.0 : identités, établissements, personnel, dossiers scolaires.
--
-- Règle d'isolation : toute table rattachée à un établissement porte establishment_id,
-- expose UNIQUE (establishment_id, id) et référence ses voisines par clé composite.
-- Une ligne ne peut donc pas pointer vers une ressource d'un autre établissement,
-- même si le code applicatif se trompe.

CREATE FUNCTION forbid_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'La table % est immuable', TG_TABLE_NAME USING ERRCODE = '55000';
END;
$$;

-- Identité globale : une personne, indépendamment de tout établissement.

CREATE TABLE accounts (
  id                    uuid PRIMARY KEY,
  full_name             text NOT NULL CHECK (length(full_name) BETWEEN 2 AND 200),
  password_hash         text NOT NULL,
  status                text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
  -- Autorise uniquement l'administration de la plateforme, jamais la lecture de dossiers.
  platform_operator     boolean NOT NULL DEFAULT false,
  token_version         integer NOT NULL DEFAULT 0,
  failed_login_attempts integer NOT NULL DEFAULT 0,
  locked_until          timestamptz,
  created_at            timestamptz NOT NULL,
  updated_at            timestamptz NOT NULL
);

-- Séparé du compte : beaucoup de familles n'ont qu'un téléphone.
CREATE TABLE account_identifiers (
  kind       text NOT NULL CHECK (kind IN ('EMAIL', 'PHONE')),
  value      text NOT NULL,
  account_id uuid NOT NULL REFERENCES accounts (id),
  created_at timestamptz NOT NULL,
  PRIMARY KEY (kind, value)
);
CREATE INDEX account_identifiers_account_idx ON account_identifiers (account_id);

-- Règle juridique par pays. Elle ne se configure pas par établissement et ne change que
-- par migration, donc par une décision relue. legally_confirmed reste faux tant que la règle
-- n'a pas été validée juridiquement : c'est une condition d'ouverture du portail des familles.
CREATE TABLE legal_majority_rules (
  country_code      text PRIMARY KEY CHECK (country_code ~ '^[A-Z]{2}$'),
  age_of_majority   smallint NOT NULL CHECK (age_of_majority BETWEEN 15 AND 25),
  legally_confirmed boolean NOT NULL DEFAULT false
);
INSERT INTO legal_majority_rules (country_code, age_of_majority) VALUES ('TD', 18);

-- Établissements et personnel.

CREATE TABLE establishments (
  id              uuid PRIMARY KEY,
  code            text NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9][A-Z0-9-]{1,30}[A-Z0-9]$'),
  name            text NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
  kind            text NOT NULL CHECK (kind IN ('SCHOOL', 'UNIVERSITY', 'TRAINING_CENTER', 'OTHER')),
  timezone        text NOT NULL,
  currency        text NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  country_code    text NOT NULL REFERENCES legal_majority_rules (country_code),
  status          text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL
);

CREATE TABLE memberships (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL REFERENCES establishments (id),
  account_id       uuid NOT NULL REFERENCES accounts (id),
  status           text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
  created_at       timestamptz NOT NULL,
  updated_at       timestamptz NOT NULL,
  UNIQUE (establishment_id, account_id),
  UNIQUE (establishment_id, id)
);
CREATE INDEX memberships_account_idx ON memberships (account_id);

CREATE TABLE membership_roles (
  establishment_id uuid NOT NULL,
  membership_id    uuid NOT NULL,
  role             text NOT NULL CHECK (role IN ('ESTABLISHMENT_ADMIN', 'REGISTRAR', 'CASHIER', 'ACCOUNTANT', 'AUDITOR')),
  granted_at       timestamptz NOT NULL,
  PRIMARY KEY (membership_id, role),
  FOREIGN KEY (establishment_id, membership_id) REFERENCES memberships (establishment_id, id)
);

-- Invitations : le jeton n'est stocké que sous forme d'empreinte.

CREATE TABLE invitations (
  id                  uuid PRIMARY KEY,
  token_hash          text NOT NULL UNIQUE,
  contact_kind        text NOT NULL CHECK (contact_kind IN ('EMAIL', 'PHONE')),
  contact_value       text NOT NULL,
  purpose             text NOT NULL,
  establishment_id    uuid NOT NULL REFERENCES establishments (id),
  subject_id          uuid,
  attributes          jsonb NOT NULL DEFAULT '{}',
  issued_by           uuid REFERENCES accounts (id),
  expires_at          timestamptz NOT NULL,
  accepted_at         timestamptz,
  accepted_account_id uuid REFERENCES accounts (id),
  revoked_at          timestamptz,
  created_at          timestamptz NOT NULL
);
CREATE INDEX invitations_establishment_idx ON invitations (establishment_id);

-- Scolarité.

CREATE TABLE academic_years (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL REFERENCES establishments (id),
  label            text NOT NULL CHECK (length(label) BETWEEN 2 AND 50),
  starts_on        date NOT NULL,
  ends_on          date NOT NULL,
  status           text NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'OPEN', 'CLOSED')),
  created_at       timestamptz NOT NULL,
  updated_at       timestamptz NOT NULL,
  CHECK (starts_on < ends_on),
  UNIQUE (establishment_id, label),
  UNIQUE (establishment_id, id)
);

-- Un dossier appartient à un établissement. Une personne scolarisée dans deux
-- établissements a deux dossiers, éventuellement reliés au même compte.
CREATE TABLE student_records (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL REFERENCES establishments (id),
  student_number   text NOT NULL CHECK (length(student_number) BETWEEN 1 AND 64),
  full_name        text NOT NULL CHECK (length(full_name) BETWEEN 2 AND 200),
  birth_date       date,
  status           text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  created_at       timestamptz NOT NULL,
  updated_at       timestamptz NOT NULL,
  UNIQUE (establishment_id, student_number),
  UNIQUE (establishment_id, id)
);

CREATE TABLE enrollments (
  id                uuid PRIMARY KEY,
  establishment_id  uuid NOT NULL,
  student_record_id uuid NOT NULL,
  academic_year_id  uuid NOT NULL,
  level             text NOT NULL CHECK (length(level) BETWEEN 1 AND 100),
  status            text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'WITHDRAWN')),
  enrolled_at       timestamptz NOT NULL,
  ended_at          timestamptz,
  UNIQUE (establishment_id, id),
  FOREIGN KEY (establishment_id, student_record_id) REFERENCES student_records (establishment_id, id),
  FOREIGN KEY (establishment_id, academic_year_id) REFERENCES academic_years (establishment_id, id)
);
CREATE UNIQUE INDEX enrollments_one_active_per_year
  ON enrollments (establishment_id, student_record_id, academic_year_id)
  WHERE status = 'ACTIVE';

CREATE TABLE enrollment_history (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  enrollment_id    uuid NOT NULL,
  from_status      text,
  to_status        text NOT NULL,
  reason           text,
  actor_account_id uuid NOT NULL REFERENCES accounts (id),
  occurred_at      timestamptz NOT NULL,
  FOREIGN KEY (establishment_id, enrollment_id) REFERENCES enrollments (establishment_id, id)
);
CREATE INDEX enrollment_history_enrollment_idx ON enrollment_history (establishment_id, enrollment_id, occurred_at);
CREATE TRIGGER enrollment_history_immutable BEFORE UPDATE OR DELETE ON enrollment_history
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER enrollment_history_no_truncate BEFORE TRUNCATE ON enrollment_history
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

-- Le seul lien entre un compte et un dossier, hors personnel. Aucune donnée n'est
-- accessible aux familles sans une ligne active ici. Les lignes révoquées restent.
-- academic_access vaut pour un élève mineur. Pour un élève majeur, seul son consentement
-- (academic_consent_at) ouvre l'accès académique : voir AcademicAccess.
CREATE TABLE record_access_grants (
  id                    uuid PRIMARY KEY,
  establishment_id      uuid NOT NULL,
  student_record_id     uuid NOT NULL,
  account_id            uuid NOT NULL REFERENCES accounts (id),
  kind                  text NOT NULL CHECK (kind IN ('THIRD_PARTY', 'SELF')),
  relationship          text CHECK (relationship IN ('MOTHER', 'FATHER', 'GUARDIAN', 'OTHER')),
  academic_access       boolean NOT NULL DEFAULT false,
  financial_contributor boolean NOT NULL DEFAULT false,
  academic_consent_at   timestamptz,
  academic_consent_by   uuid REFERENCES accounts (id),
  granted_at            timestamptz NOT NULL,
  granted_by            uuid REFERENCES accounts (id),
  revoked_at            timestamptz,
  revoked_by            uuid REFERENCES accounts (id),
  UNIQUE (establishment_id, id),
  FOREIGN KEY (establishment_id, student_record_id) REFERENCES student_records (establishment_id, id),
  CHECK ((kind = 'THIRD_PARTY') = (relationship IS NOT NULL)),
  CHECK (kind = 'SELF' OR academic_access OR financial_contributor)
);
CREATE UNIQUE INDEX record_access_one_active_per_account
  ON record_access_grants (student_record_id, account_id) WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX record_access_one_active_self
  ON record_access_grants (student_record_id) WHERE kind = 'SELF' AND revoked_at IS NULL;
CREATE INDEX record_access_account_idx
  ON record_access_grants (account_id) WHERE revoked_at IS NULL;

-- Journal d'audit, écrit dans la transaction du changement qu'il documente.

CREATE TABLE audit_events (
  id               uuid PRIMARY KEY,
  occurred_at      timestamptz NOT NULL,
  establishment_id uuid REFERENCES establishments (id),
  actor_account_id uuid REFERENCES accounts (id),
  action           text NOT NULL,
  target_type      text NOT NULL,
  target_id        uuid,
  details          jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX audit_events_establishment_idx ON audit_events (establishment_id, occurred_at DESC);
CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER audit_events_no_truncate BEFORE TRUNCATE ON audit_events
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
