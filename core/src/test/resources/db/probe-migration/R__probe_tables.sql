-- Tables de la sonde d'OutboxReliabilityTest. L'effet porte une contrainte d'unicité, comme le
-- feront les lignes d'obligation : une seconde exécution du listener ne doit rien changer.
CREATE TABLE probe_behaviors (student_id uuid PRIMARY KEY, behavior text NOT NULL);
CREATE TABLE probe_attempts (id bigserial PRIMARY KEY, student_id uuid NOT NULL, instance text NOT NULL);
CREATE TABLE probe_effects (student_id uuid PRIMARY KEY);

-- Fait échouer la validation elle-même, après la publication de l'événement : c'est le pire moment.
CREATE FUNCTION probe_fail_at_commit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM probe_behaviors WHERE student_id = NEW.student_record_id AND behavior = 'FAIL_AT_COMMIT') THEN
    RAISE EXCEPTION 'échec provoqué à la validation';
  END IF;
  RETURN NEW;
END;
$$;
CREATE CONSTRAINT TRIGGER probe_fail_at_commit AFTER INSERT ON enrollments
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION probe_fail_at_commit();
