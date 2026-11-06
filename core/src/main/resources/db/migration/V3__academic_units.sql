-- Catalogue académique. Une école n'a que des niveaux ; une université les regroupe sous des
-- programmes. Une nouvelle nature (semestre, filière) s'ajoute en élargissant le CHECK.

CREATE TABLE academic_units (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL REFERENCES establishments (id),
  parent_id        uuid,
  kind             text NOT NULL CHECK (kind IN ('PROGRAM', 'LEVEL')),
  code             text NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9-]{0,30}$'),
  name             text NOT NULL CHECK (length(name) BETWEEN 1 AND 100),
  status           text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  created_at       timestamptz NOT NULL,
  updated_at       timestamptz NOT NULL,
  UNIQUE (establishment_id, id),
  UNIQUE (establishment_id, code),
  FOREIGN KEY (establishment_id, parent_id) REFERENCES academic_units (establishment_id, id)
);
CREATE INDEX academic_units_parent_idx ON academic_units (establishment_id, parent_id) WHERE parent_id IS NOT NULL;

-- La tarification s'appuiera sur le code et la position d'une unité : ils ne changent jamais.
-- Le parent étant figé et antérieur à l'unité, aucun cycle n'est possible.
CREATE FUNCTION academic_units_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE'
     AND (NEW.establishment_id, NEW.code, NEW.kind, NEW.parent_id)
         IS DISTINCT FROM (OLD.establishment_id, OLD.code, OLD.kind, OLD.parent_id) THEN
    RAISE EXCEPTION 'L''identité d''une unité académique est immuable';
  END IF;
  IF TG_OP = 'INSERT' AND NEW.parent_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM academic_units
       WHERE establishment_id = NEW.establishment_id AND id = NEW.parent_id AND kind = 'PROGRAM') THEN
    RAISE EXCEPTION 'Seul un programme peut regrouper des unités académiques';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER academic_units_guard BEFORE INSERT OR UPDATE ON academic_units
  FOR EACH ROW EXECUTE FUNCTION academic_units_guard();
CREATE TRIGGER academic_units_no_delete BEFORE DELETE ON academic_units
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Sans reprise de données : rien n'est déployé. Une inscription existante ferait échouer cette
-- migration, ce qui vaut mieux que d'inventer son rattachement. La colonne level reste le libellé
-- tel qu'il était à l'inscription, l'historique ne change pas si l'unité est renommée.
ALTER TABLE enrollments ADD COLUMN academic_unit_id uuid NOT NULL;
ALTER TABLE enrollments ADD FOREIGN KEY (establishment_id, academic_unit_id) REFERENCES academic_units (establishment_id, id);
CREATE INDEX enrollments_academic_unit_idx ON enrollments (establishment_id, academic_unit_id);
