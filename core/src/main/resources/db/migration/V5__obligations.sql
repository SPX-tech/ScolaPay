-- Obligations financières. Une obligation lie un dossier à une charge : c'est ce qui empêche, en base,
-- de facturer deux fois la même charge. Ce n'est pas une facture, voir la décision 0019.
-- Le montant dû et le statut ne sont stockés nulle part : ils se déduisent du journal.

-- Ces clés permettent aux références composites ci-dessous de garantir la cohérence d'ensemble :
-- la ligne de tarif, sa charge, sa version et son année ; l'inscription, son dossier et son année.
ALTER TABLE fee_items ADD UNIQUE (establishment_id, academic_year_id, catalog_version_id, id, fee_definition_id);
ALTER TABLE enrollments ADD UNIQUE (establishment_id, id, student_record_id, academic_year_id);

CREATE TABLE issuance_runs (
  id                  uuid PRIMARY KEY,
  establishment_id    uuid NOT NULL,
  academic_year_id    uuid NOT NULL,
  academic_unit_id    uuid,
  catalog_version_id  uuid NOT NULL,
  obligations_created int NOT NULL CHECK (obligations_created >= 0),
  total_minor         bigint NOT NULL CHECK (total_minor >= 0),
  confirmed_at        timestamptz NOT NULL,
  confirmed_by        uuid NOT NULL REFERENCES accounts (id),
  UNIQUE (establishment_id, id),
  FOREIGN KEY (establishment_id, academic_year_id, catalog_version_id)
    REFERENCES fee_catalog_versions (establishment_id, academic_year_id, id),
  FOREIGN KEY (establishment_id, academic_unit_id) REFERENCES academic_units (establishment_id, id)
);

CREATE TABLE obligations (
  id                  uuid PRIMARY KEY,
  establishment_id    uuid NOT NULL,
  academic_year_id    uuid NOT NULL,
  student_record_id   uuid NOT NULL,
  enrollment_id       uuid NOT NULL,
  fee_definition_id   uuid NOT NULL,
  catalog_version_id  uuid NOT NULL,
  fee_item_id         uuid NOT NULL,
  reference           text NOT NULL CHECK (reference ~ '^OBL-[0-9A-HJKMNP-TV-Z]{10}$'),
  fee_code            text NOT NULL,
  label               text NOT NULL,
  category            text NOT NULL,
  currency            text NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  issued_amount_minor bigint NOT NULL CHECK (issued_amount_minor BETWEEN 1 AND 999999999999999),
  source              text NOT NULL CHECK (source IN ('ENROLLMENT', 'BATCH', 'MANUAL')),
  issuance_run_id     uuid,
  issued_at           timestamptz NOT NULL,
  issued_by           uuid NOT NULL REFERENCES accounts (id),
  CHECK ((source = 'BATCH') = (issuance_run_id IS NOT NULL)),
  UNIQUE (establishment_id, id),
  UNIQUE (establishment_id, reference),
  CONSTRAINT obligations_one_per_charge UNIQUE (establishment_id, student_record_id, fee_definition_id),
  FOREIGN KEY (establishment_id, enrollment_id, student_record_id, academic_year_id)
    REFERENCES enrollments (establishment_id, id, student_record_id, academic_year_id),
  FOREIGN KEY (establishment_id, academic_year_id, catalog_version_id, fee_item_id, fee_definition_id)
    REFERENCES fee_items (establishment_id, academic_year_id, catalog_version_id, id, fee_definition_id),
  -- Différée : le lot est enregistré une fois ses obligations créées, avec leur nombre réel.
  FOREIGN KEY (establishment_id, issuance_run_id) REFERENCES issuance_runs (establishment_id, id)
    DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX obligations_year_idx ON obligations (establishment_id, academic_year_id, issued_at DESC);

CREATE TABLE obligation_installments (
  obligation_id    uuid NOT NULL,
  seq              int NOT NULL CHECK (seq > 0),
  establishment_id uuid NOT NULL,
  due_on           date NOT NULL,
  amount_minor     bigint NOT NULL CHECK (amount_minor BETWEEN 1 AND 999999999999999),
  PRIMARY KEY (obligation_id, seq),
  FOREIGN KEY (establishment_id, obligation_id) REFERENCES obligations (establishment_id, id)
);

-- Le journal. SETTLEMENT n'y figure volontairement pas : aucun règlement ne peut être écrit tant que
-- le module de paiement n'existe pas, et c'est lui qui l'ajoutera avec une preuve d'encaissement.
CREATE TABLE obligation_entries (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  obligation_id    uuid NOT NULL,
  kind             text NOT NULL CHECK (kind IN ('ISSUED', 'DISCOUNT', 'ADJUSTMENT')),
  amount_minor     bigint NOT NULL,
  reason           text CHECK (length(reason) BETWEEN 1 AND 500),
  idempotency_key  text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
  recorded_at      timestamptz NOT NULL,
  recorded_by      uuid NOT NULL REFERENCES accounts (id),
  CHECK (
    (kind = 'ISSUED' AND amount_minor > 0 AND reason IS NULL)
    OR (kind = 'DISCOUNT' AND amount_minor < 0 AND reason IS NOT NULL)
    OR (kind = 'ADJUSTMENT' AND amount_minor <> 0 AND reason IS NOT NULL)),
  UNIQUE (establishment_id, idempotency_key),
  FOREIGN KEY (establishment_id, obligation_id) REFERENCES obligations (establishment_id, id)
);
CREATE UNIQUE INDEX obligation_entries_one_issued ON obligation_entries (obligation_id) WHERE kind = 'ISSUED';
CREATE INDEX obligation_entries_obligation_idx ON obligation_entries (obligation_id);

-- Le verrou sérialise les écritures d'une obligation : le dû reste juste en concurrence, et il ne
-- devient jamais négatif.
CREATE FUNCTION obligation_entries_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  due bigint;
BEGIN
  PERFORM 1 FROM obligations WHERE id = NEW.obligation_id FOR UPDATE;
  SELECT COALESCE(sum(amount_minor), 0) INTO due FROM obligation_entries WHERE obligation_id = NEW.obligation_id;
  IF due + NEW.amount_minor < 0 THEN
    RAISE EXCEPTION 'Le montant dû d''une obligation ne peut pas devenir négatif';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER obligation_entries_guard BEFORE INSERT ON obligation_entries
  FOR EACH ROW EXECUTE FUNCTION obligation_entries_guard();

-- Vérifié à la validation : une obligation naît complète, et son échéancier ne change plus ensuite.
CREATE FUNCTION obligations_complete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  target uuid;
  issued bigint;
BEGIN
  IF TG_TABLE_NAME = 'obligations' THEN
    target := NEW.id;
  ELSE
    target := NEW.obligation_id;
  END IF;
  SELECT issued_amount_minor INTO issued FROM obligations WHERE id = target;
  IF COALESCE((SELECT sum(amount_minor) FROM obligation_installments WHERE obligation_id = target), 0) <> issued THEN
    RAISE EXCEPTION 'La somme des échéances doit égaler le montant émis';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM obligation_entries WHERE obligation_id = target AND kind = 'ISSUED' AND amount_minor = issued) THEN
    RAISE EXCEPTION 'Une obligation naît avec son écriture d''émission';
  END IF;
  RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER obligations_complete AFTER INSERT ON obligations
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION obligations_complete();
CREATE CONSTRAINT TRIGGER obligation_installments_complete AFTER INSERT ON obligation_installments
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION obligations_complete();

CREATE TRIGGER issuance_runs_immutable BEFORE UPDATE OR DELETE ON issuance_runs
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER obligations_immutable BEFORE UPDATE OR DELETE ON obligations
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER obligations_no_truncate BEFORE TRUNCATE ON obligations
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER obligation_installments_immutable BEFORE UPDATE OR DELETE ON obligation_installments
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER obligation_entries_immutable BEFORE UPDATE OR DELETE ON obligation_entries
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER obligation_entries_no_truncate BEFORE TRUNCATE ON obligation_entries
  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
