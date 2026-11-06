-- Périodes de facturation : indépendantes de l'inscription, qui reste annuelle. Une école peut n'en
-- avoir qu'une par an, une université en avoir une par semestre ou par session.

CREATE TABLE billing_periods (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  academic_year_id uuid NOT NULL,
  code             text NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9-]{0,30}$'),
  name             text NOT NULL CHECK (length(name) BETWEEN 1 AND 100),
  starts_on        date NOT NULL,
  ends_on          date NOT NULL,
  status           text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  created_at       timestamptz NOT NULL,
  updated_at       timestamptz NOT NULL,
  CHECK (starts_on <= ends_on),
  UNIQUE (establishment_id, academic_year_id, id),
  UNIQUE (establishment_id, academic_year_id, code),
  FOREIGN KEY (establishment_id, academic_year_id) REFERENCES academic_years (establishment_id, id)
);

CREATE FUNCTION billing_periods_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.establishment_id, NEW.academic_year_id, NEW.code, NEW.starts_on, NEW.ends_on)
     IS DISTINCT FROM (OLD.establishment_id, OLD.academic_year_id, OLD.code, OLD.starts_on, OLD.ends_on) THEN
    RAISE EXCEPTION 'L''identité et les dates d''une période de facturation sont immuables';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER billing_periods_guard BEFORE UPDATE ON billing_periods
  FOR EACH ROW EXECUTE FUNCTION billing_periods_guard();
CREATE TRIGGER billing_periods_no_delete BEFORE DELETE ON billing_periods
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Versions du catalogue d'une année. Le tarif applicable est la seule version PUBLISHED.

CREATE TABLE fee_catalog_versions (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  academic_year_id uuid NOT NULL,
  version          int NOT NULL CHECK (version > 0),
  status           text NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
  currency         text NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  created_at       timestamptz NOT NULL,
  created_by       uuid NOT NULL REFERENCES accounts (id),
  published_at     timestamptz,
  published_by     uuid REFERENCES accounts (id),
  superseded_at    timestamptz,
  CHECK ((status = 'DRAFT') = (published_at IS NULL)),
  CHECK ((status = 'DRAFT') = (published_by IS NULL)),
  CHECK ((status = 'SUPERSEDED') = (superseded_at IS NOT NULL)),
  UNIQUE (establishment_id, academic_year_id, id),
  UNIQUE (establishment_id, academic_year_id, version),
  FOREIGN KEY (establishment_id, academic_year_id) REFERENCES academic_years (establishment_id, id)
);
CREATE UNIQUE INDEX fee_catalog_versions_one_draft ON fee_catalog_versions (establishment_id, academic_year_id) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX fee_catalog_versions_one_published ON fee_catalog_versions (establishment_id, academic_year_id) WHERE status = 'PUBLISHED';

-- Une ligne par combinaison de frais et d'unité académique visée. Une unité nulle vise tout
-- l'établissement. fee_code est l'identité stable d'une charge : il ne change pas de période
-- d'une version à l'autre, ce qui permet de refuser une double facturation sur ce seul code.

CREATE TABLE fee_items (
  id                 uuid PRIMARY KEY,
  establishment_id   uuid NOT NULL,
  academic_year_id   uuid NOT NULL,
  catalog_version_id uuid NOT NULL,
  fee_code           text NOT NULL CHECK (fee_code ~ '^[A-Z0-9][A-Z0-9-]{0,30}$'),
  label              text NOT NULL CHECK (length(label) BETWEEN 1 AND 200),
  category           text NOT NULL CHECK (category IN ('REGISTRATION', 'TUITION', 'EXAM', 'TRANSPORT', 'CANTEEN', 'OTHER')),
  application        text NOT NULL CHECK (application IN ('AUTOMATIC', 'ON_REQUEST')),
  amount_minor       bigint NOT NULL CHECK (amount_minor BETWEEN 1 AND 999999999999999),
  billing_period_id  uuid NOT NULL,
  academic_unit_id   uuid,
  UNIQUE (establishment_id, catalog_version_id, id),
  UNIQUE NULLS NOT DISTINCT (catalog_version_id, fee_code, academic_unit_id),
  FOREIGN KEY (establishment_id, academic_year_id, catalog_version_id)
    REFERENCES fee_catalog_versions (establishment_id, academic_year_id, id),
  FOREIGN KEY (establishment_id, academic_year_id, billing_period_id)
    REFERENCES billing_periods (establishment_id, academic_year_id, id),
  FOREIGN KEY (establishment_id, academic_unit_id) REFERENCES academic_units (establishment_id, id)
);
CREATE INDEX fee_items_code_idx ON fee_items (establishment_id, academic_year_id, fee_code);

CREATE TABLE fee_item_installments (
  fee_item_id        uuid NOT NULL,
  seq                int NOT NULL CHECK (seq > 0),
  establishment_id   uuid NOT NULL,
  catalog_version_id uuid NOT NULL,
  due_on             date NOT NULL,
  amount_minor       bigint NOT NULL CHECK (amount_minor BETWEEN 1 AND 999999999999999),
  PRIMARY KEY (fee_item_id, seq),
  FOREIGN KEY (establishment_id, catalog_version_id, fee_item_id)
    REFERENCES fee_items (establishment_id, catalog_version_id, id) ON DELETE CASCADE
);

-- Un tarif publié ne se réécrit pas. FOR SHARE attend une publication en cours : une ligne ne peut
-- pas se glisser dans une version au moment même où elle se fige.
CREATE FUNCTION fee_items_require_draft() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  target uuid;
  current_status text;
BEGIN
  IF TG_OP = 'DELETE' THEN
    target := OLD.catalog_version_id;
  ELSE
    target := NEW.catalog_version_id;
    IF TG_OP = 'UPDATE' AND NEW.catalog_version_id <> OLD.catalog_version_id THEN
      RAISE EXCEPTION 'Une ligne de tarif ne change pas de version';
    END IF;
  END IF;
  SELECT status INTO current_status FROM fee_catalog_versions WHERE id = target FOR SHARE;
  IF current_status IS DISTINCT FROM 'DRAFT' THEN
    RAISE EXCEPTION 'Un catalogue publié est immuable';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER fee_items_require_draft BEFORE INSERT OR UPDATE OR DELETE ON fee_items
  FOR EACH ROW EXECUTE FUNCTION fee_items_require_draft();
CREATE TRIGGER fee_item_installments_require_draft BEFORE INSERT OR UPDATE OR DELETE ON fee_item_installments
  FOR EACH ROW EXECUTE FUNCTION fee_items_require_draft();

-- Le cycle de vie et les conditions de publication tiennent même si le code applicatif se trompe.
CREATE FUNCTION fee_catalog_versions_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.status <> 'DRAFT' THEN
      RAISE EXCEPTION 'Un catalogue publié ne se supprime pas';
    END IF;
    RETURN OLD;
  END IF;
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'DRAFT' THEN
      RAISE EXCEPTION 'Un catalogue naît en brouillon';
    END IF;
    RETURN NEW;
  END IF;

  IF (NEW.id, NEW.establishment_id, NEW.academic_year_id, NEW.version, NEW.currency, NEW.created_at, NEW.created_by)
     IS DISTINCT FROM (OLD.id, OLD.establishment_id, OLD.academic_year_id, OLD.version, OLD.currency, OLD.created_at, OLD.created_by) THEN
    RAISE EXCEPTION 'L''identité d''une version de catalogue est immuable';
  END IF;
  IF NOT ((OLD.status = 'DRAFT' AND NEW.status = 'PUBLISHED') OR (OLD.status = 'PUBLISHED' AND NEW.status = 'SUPERSEDED')) THEN
    RAISE EXCEPTION 'Transition de catalogue interdite : % vers %', OLD.status, NEW.status;
  END IF;
  IF OLD.status = 'PUBLISHED' AND (NEW.published_at, NEW.published_by) IS DISTINCT FROM (OLD.published_at, OLD.published_by) THEN
    RAISE EXCEPTION 'La publication d''un catalogue est immuable';
  END IF;

  IF NEW.status = 'PUBLISHED' THEN
    IF NOT EXISTS (SELECT 1 FROM fee_items WHERE catalog_version_id = NEW.id) THEN
      RAISE EXCEPTION 'Un catalogue vide ne se publie pas';
    END IF;
    IF EXISTS (
         SELECT 1 FROM fee_items i
         LEFT JOIN fee_item_installments s ON s.fee_item_id = i.id
         WHERE i.catalog_version_id = NEW.id
         GROUP BY i.id, i.amount_minor
         HAVING COALESCE(sum(s.amount_minor), 0) <> i.amount_minor) THEN
      RAISE EXCEPTION 'La somme des échéances doit égaler le montant du frais';
    END IF;
    IF EXISTS (
         SELECT 1 FROM fee_items i
         JOIN fee_items other ON other.establishment_id = i.establishment_id
           AND other.academic_year_id = i.academic_year_id
           AND other.fee_code = i.fee_code
           AND other.billing_period_id <> i.billing_period_id
         JOIN fee_catalog_versions v ON v.id = other.catalog_version_id
         WHERE i.catalog_version_id = NEW.id
           AND (other.catalog_version_id = NEW.id OR v.status IN ('PUBLISHED', 'SUPERSEDED'))) THEN
      RAISE EXCEPTION 'Un code de frais reste attaché à une seule période de facturation';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER fee_catalog_versions_guard BEFORE INSERT OR UPDATE OR DELETE ON fee_catalog_versions
  FOR EACH ROW EXECUTE FUNCTION fee_catalog_versions_guard();
