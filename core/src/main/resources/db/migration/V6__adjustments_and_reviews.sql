-- Remises, corrections et annulations : des écritures distinctes, car elles n'ont pas le même sens même
-- si elles aboutissent au même solde. Aucune n'est un encaissement : SETTLEMENT reste absent du journal.

ALTER TABLE obligation_entries
  DROP CONSTRAINT obligation_entries_kind_check,
  DROP CONSTRAINT obligation_entries_check,
  ADD COLUMN reverses_entry_id uuid,
  ADD COLUMN approved_by uuid REFERENCES accounts (id),
  ADD CONSTRAINT obligation_entries_kind_check
    CHECK (kind IN ('ISSUED', 'DISCOUNT', 'CORRECTION', 'CANCELLATION', 'REVERSAL')),
  ADD CONSTRAINT obligation_entries_shape CHECK (
    (kind = 'ISSUED' AND amount_minor > 0 AND reason IS NULL AND reverses_entry_id IS NULL)
    OR (kind = 'DISCOUNT' AND amount_minor < 0 AND reason IS NOT NULL AND reverses_entry_id IS NULL)
    OR (kind = 'CORRECTION' AND amount_minor <> 0 AND reason IS NOT NULL AND reverses_entry_id IS NULL)
    OR (kind = 'CANCELLATION' AND amount_minor < 0 AND reason IS NOT NULL AND reverses_entry_id IS NULL)
    OR (kind = 'REVERSAL' AND amount_minor <> 0 AND reason IS NOT NULL AND reverses_entry_id IS NOT NULL)),
  ADD CONSTRAINT obligation_entries_second_person CHECK (approved_by IS NULL OR approved_by <> recorded_by),
  ADD CONSTRAINT obligation_entries_entry_key UNIQUE (establishment_id, obligation_id, id);

-- La référence composite garantit que l'écriture contrepassée appartient à la même obligation.
ALTER TABLE obligation_entries ADD CONSTRAINT obligation_entries_reverses_fk
  FOREIGN KEY (establishment_id, obligation_id, reverses_entry_id)
  REFERENCES obligation_entries (establishment_id, obligation_id, id);
CREATE UNIQUE INDEX obligation_entries_one_reversal ON obligation_entries (reverses_entry_id) WHERE reverses_entry_id IS NOT NULL;

-- Ces règles tiennent même si le code applicatif se trompe : amener le dû à zéro ou annuler exige
-- toujours une seconde personne, et une contrepassée est l'exact opposé de son écriture.
CREATE OR REPLACE FUNCTION obligation_entries_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  due bigint;
  original_kind text;
  original_amount bigint;
BEGIN
  PERFORM 1 FROM obligations WHERE id = NEW.obligation_id FOR UPDATE;
  SELECT COALESCE(sum(amount_minor), 0) INTO due FROM obligation_entries WHERE obligation_id = NEW.obligation_id;
  IF due + NEW.amount_minor < 0 THEN
    RAISE EXCEPTION 'Le montant dû d''une obligation ne peut pas devenir négatif';
  END IF;
  IF NEW.kind <> 'ISSUED' AND due + NEW.amount_minor = 0 AND NEW.approved_by IS NULL THEN
    RAISE EXCEPTION 'Amener le dû à zéro exige la confirmation d''une seconde personne';
  END IF;
  IF NEW.kind = 'CANCELLATION' AND due + NEW.amount_minor <> 0 THEN
    RAISE EXCEPTION 'Une annulation porte sur tout le montant restant dû';
  END IF;
  IF NEW.kind = 'REVERSAL' THEN
    SELECT kind, amount_minor INTO original_kind, original_amount FROM obligation_entries WHERE id = NEW.reverses_entry_id;
    IF original_kind IN ('ISSUED', 'REVERSAL') THEN
      RAISE EXCEPTION 'Cette écriture ne se contrepasse pas';
    END IF;
    IF original_amount <> -NEW.amount_minor THEN
      RAISE EXCEPTION 'Une contrepassée est l''exact opposé de son écriture';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

-- Le directeur confirme les opérations sensibles sans pouvoir les passer lui-même.
ALTER TABLE membership_roles
  DROP CONSTRAINT membership_roles_role_check,
  ADD CONSTRAINT membership_roles_role_check
    CHECK (role IN ('ESTABLISHMENT_ADMIN', 'REGISTRAR', 'CASHIER', 'ACCOUNTANT', 'AUDITOR', 'DIRECTOR'));

-- Une opération sensible attend ici la décision d'une seconde personne. Tant qu'elle attend, elle
-- n'existe pas dans le journal et ne change donc pas le dû. La demande fige ce qui est présenté : son
-- montant, le dû et la taille du journal. Toute nouvelle écriture la rend périmée, car l'approbateur
-- ne doit jamais confirmer autre chose que ce qu'on lui a montré.
CREATE TABLE adjustment_requests (
  id                uuid PRIMARY KEY,
  establishment_id  uuid NOT NULL,
  obligation_id     uuid NOT NULL,
  kind              text NOT NULL CHECK (kind IN ('DISCOUNT', 'CORRECTION', 'CANCELLATION', 'REVERSAL')),
  amount_minor      bigint NOT NULL CHECK (amount_minor <> 0),
  due_at_request_minor bigint NOT NULL CHECK (due_at_request_minor >= 0),
  journal_size_at_request integer NOT NULL CHECK (journal_size_at_request >= 1),
  reverses_entry_id uuid,
  reason            text NOT NULL CHECK (length(reason) BETWEEN 1 AND 500),
  idempotency_key   text NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
  status            text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
  requested_at      timestamptz NOT NULL,
  requested_by      uuid NOT NULL REFERENCES accounts (id),
  decided_at        timestamptz,
  decided_by        uuid REFERENCES accounts (id),
  decision_note     text CHECK (length(decision_note) BETWEEN 1 AND 500),
  entry_id          uuid,
  CONSTRAINT adjustment_requests_cancels_all CHECK (kind <> 'CANCELLATION' OR amount_minor = -due_at_request_minor),
  CHECK ((kind = 'REVERSAL') = (reverses_entry_id IS NOT NULL)),
  CHECK ((status = 'PENDING') = (decided_at IS NULL)),
  CHECK ((status = 'PENDING') = (decided_by IS NULL)),
  CONSTRAINT adjustment_requests_note_required CHECK (status NOT IN ('REJECTED', 'WITHDRAWN') OR decision_note IS NOT NULL),
  CHECK ((status = 'APPROVED') = (entry_id IS NOT NULL)),
  CONSTRAINT adjustment_requests_second_person CHECK (decided_by IS NULL OR status = 'WITHDRAWN' OR decided_by <> requested_by),
  CONSTRAINT adjustment_requests_withdrawn_by_author CHECK (status <> 'WITHDRAWN' OR decided_by = requested_by),
  UNIQUE (establishment_id, id),
  UNIQUE (establishment_id, idempotency_key),
  FOREIGN KEY (establishment_id, obligation_id) REFERENCES obligations (establishment_id, id),
  FOREIGN KEY (establishment_id, obligation_id, reverses_entry_id) REFERENCES obligation_entries (establishment_id, obligation_id, id),
  FOREIGN KEY (establishment_id, obligation_id, entry_id) REFERENCES obligation_entries (establishment_id, obligation_id, id)
);
CREATE UNIQUE INDEX adjustment_requests_one_pending ON adjustment_requests (obligation_id) WHERE status = 'PENDING';
CREATE INDEX adjustment_requests_status_idx ON adjustment_requests (establishment_id, status, requested_at);

CREATE FUNCTION adjustment_requests_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  journal_size integer;
BEGIN
  IF OLD.status <> 'PENDING' THEN
    RAISE EXCEPTION 'Une demande décidée est immuable';
  END IF;
  IF (NEW.id, NEW.establishment_id, NEW.obligation_id, NEW.kind, NEW.amount_minor, NEW.due_at_request_minor, NEW.journal_size_at_request,
      NEW.reverses_entry_id, NEW.reason, NEW.idempotency_key, NEW.requested_at, NEW.requested_by)
     IS DISTINCT FROM (OLD.id, OLD.establishment_id, OLD.obligation_id, OLD.kind, OLD.amount_minor, OLD.due_at_request_minor, OLD.journal_size_at_request,
      OLD.reverses_entry_id, OLD.reason, OLD.idempotency_key, OLD.requested_at, OLD.requested_by) THEN
    RAISE EXCEPTION 'Le contenu d''une demande est immuable';
  END IF;
  IF NEW.status = 'APPROVED' THEN
    IF NOT EXISTS (
      SELECT 1 FROM obligation_entries e
      WHERE e.id = NEW.entry_id AND e.kind = NEW.kind AND e.amount_minor = NEW.amount_minor
        AND e.reverses_entry_id IS NOT DISTINCT FROM NEW.reverses_entry_id
        AND e.recorded_by = NEW.requested_by AND e.approved_by = NEW.decided_by) THEN
      RAISE EXCEPTION 'L''écriture ne correspond pas à la demande confirmée';
    END IF;
    SELECT count(*) INTO journal_size FROM obligation_entries WHERE obligation_id = NEW.obligation_id;
    IF journal_size <> NEW.journal_size_at_request + 1 THEN
      RAISE EXCEPTION 'Le journal a changé depuis cette demande : une nouvelle confirmation est nécessaire';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER adjustment_requests_guard BEFORE UPDATE ON adjustment_requests
  FOR EACH ROW EXECUTE FUNCTION adjustment_requests_guard();
CREATE TRIGGER adjustment_requests_no_delete BEFORE DELETE ON adjustment_requests
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Une décision de réexamen. Les signaux eux-mêmes se déduisent de la réalité et ne sont pas stockés.
-- L'empreinte décrit la situation décidée : une situation nouvelle redevient donc visible.
CREATE TABLE obligation_review_decisions (
  id               uuid PRIMARY KEY,
  establishment_id uuid NOT NULL,
  obligation_id    uuid NOT NULL,
  signal           text NOT NULL CHECK (signal IN ('TARIFF_DIFFERENCE', 'WITHDRAWAL')),
  fingerprint      text NOT NULL CHECK (length(fingerprint) BETWEEN 1 AND 200),
  decision         text NOT NULL CHECK (decision IN ('NO_CHANGE', 'ADJUSTED')),
  reason           text NOT NULL CHECK (length(reason) BETWEEN 1 AND 500),
  entry_id         uuid,
  decided_at       timestamptz NOT NULL,
  decided_by       uuid NOT NULL REFERENCES accounts (id),
  CHECK ((decision = 'ADJUSTED') = (entry_id IS NOT NULL)),
  UNIQUE (establishment_id, obligation_id, signal, fingerprint),
  FOREIGN KEY (establishment_id, obligation_id) REFERENCES obligations (establishment_id, id),
  FOREIGN KEY (establishment_id, obligation_id, entry_id) REFERENCES obligation_entries (establishment_id, obligation_id, id)
);
CREATE TRIGGER obligation_review_decisions_immutable BEFORE UPDATE OR DELETE ON obligation_review_decisions
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
