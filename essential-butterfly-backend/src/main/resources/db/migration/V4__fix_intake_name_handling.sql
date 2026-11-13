-- =====================================================================
-- V4  Intake submissions: names, ownership and history
--
-- Problems fixed
--   * "Who the intake is about" (client_full_name / parent_guardian_name)
--     and "who submitted it" (the account holder) were not separated, and
--     the submitter was only reachable through client_user_id. Changing
--     or deleting the account therefore changed or blocked the history.
--   * blank strings were accepted for names and required text
--   * the legacy status NEW duplicated SUBMITTED
--
-- Result
--   * the submission is an immutable record: the text the family wrote
--     can never be edited afterwards, only the review outcome can change
--   * the submitter's name, e-mail and phone are copied onto the
--     submission at the moment it is made (filled automatically by a
--     trigger, so no caller can forget it)
--   * deleting an account keeps its submissions (ON DELETE SET NULL)
-- =====================================================================

-- 1. clean legacy data ------------------------------------------------
UPDATE intake_submissions SET status = 'SUBMITTED' WHERE status = 'NEW';

UPDATE intake_submissions
   SET client_full_name         = btrim(client_full_name),
       parent_guardian_name     = NULLIF(btrim(parent_guardian_name), ''),
       contact_info             = btrim(contact_info),
       preferred_contact_method = btrim(preferred_contact_method);

UPDATE intake_submissions
   SET rejection_reason = 'No reason was recorded.'
 WHERE status = 'REJECTED'
   AND (rejection_reason IS NULL OR btrim(rejection_reason) = '');

UPDATE intake_submissions
   SET rejection_reason = NULL
 WHERE status <> 'REJECTED' AND rejection_reason IS NOT NULL;

-- 2. submitter snapshot -----------------------------------------------
ALTER TABLE intake_submissions
    ADD COLUMN submitted_by_name  varchar(255),
    ADD COLUMN submitted_by_email varchar(254),
    ADD COLUMN submitted_by_phone varchar(20);

UPDATE intake_submissions i
   SET submitted_by_name  = c.full_name,
       submitted_by_email = e.email,
       submitted_by_phone = c.phone_number
  FROM client_users c
  LEFT JOIN account_emails e
         ON e.client_user_id = c.id AND e.is_primary
 WHERE c.id = i.client_user_id;

-- anything still empty (cannot happen with the old NOT NULL FK, but be safe)
UPDATE intake_submissions
   SET submitted_by_name  = COALESCE(submitted_by_name,  'Unknown'),
       submitted_by_email = COALESCE(submitted_by_email, 'unknown@invalid.local');

ALTER TABLE intake_submissions
    ALTER COLUMN submitted_by_name  SET NOT NULL,
    ALTER COLUMN submitted_by_email SET NOT NULL;

-- 3. keep submissions when the account goes away ----------------------
ALTER TABLE intake_submissions
    ALTER COLUMN client_user_id DROP NOT NULL;

DO $fk$
DECLARE
    fk_name text;
BEGIN
    SELECT conname INTO fk_name
      FROM pg_constraint
     WHERE conrelid = 'public.intake_submissions'::regclass
       AND contype = 'f'
       AND confrelid = 'public.client_users'::regclass;

    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE intake_submissions DROP CONSTRAINT %I', fk_name);
    END IF;
END
$fk$;

ALTER TABLE intake_submissions
    ADD CONSTRAINT fk_intake_submissions_client_user
        FOREIGN KEY (client_user_id) REFERENCES client_users (id)
        ON DELETE SET NULL;

-- 4. constraints ------------------------------------------------------
ALTER TABLE intake_submissions
    ADD CONSTRAINT ck_intake_status
        CHECK (status IN ('SUBMITTED', 'IN_PROGRESS', 'ACCEPTED', 'REJECTED')),
    ADD CONSTRAINT ck_intake_client_full_name_not_blank
        CHECK (btrim(client_full_name) <> ''),
    ADD CONSTRAINT ck_intake_parent_guardian_name_not_blank
        CHECK (parent_guardian_name IS NULL OR btrim(parent_guardian_name) <> ''),
    ADD CONSTRAINT ck_intake_contact_info_not_blank
        CHECK (btrim(contact_info) <> ''),
    ADD CONSTRAINT ck_intake_contact_method_not_blank
        CHECK (btrim(preferred_contact_method) <> ''),
    ADD CONSTRAINT ck_intake_overview_not_blank
        CHECK (btrim(overview) <> ''),
    ADD CONSTRAINT ck_intake_support_needs_not_blank
        CHECK (btrim(support_needs) <> ''),
    -- a rejection always has a reason; nothing else carries one
    ADD CONSTRAINT ck_intake_rejection_reason
        CHECK ((status = 'REJECTED')
               = (rejection_reason IS NOT NULL AND btrim(rejection_reason) <> ''));

-- 5. triggers ---------------------------------------------------------

-- 5a. fill the submitter snapshot from the account when it is not given
CREATE FUNCTION fn_intake_fill_submitter() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.client_user_id IS NOT NULL THEN
        SELECT COALESCE(NEW.submitted_by_name,  c.full_name),
               COALESCE(NEW.submitted_by_email, e.email),
               COALESCE(NEW.submitted_by_phone, c.phone_number)
          INTO NEW.submitted_by_name, NEW.submitted_by_email, NEW.submitted_by_phone
          FROM client_users c
          LEFT JOIN account_emails e
                 ON e.client_user_id = c.id AND e.is_primary
         WHERE c.id = NEW.client_user_id;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_intake_fill_submitter
    BEFORE INSERT ON intake_submissions
    FOR EACH ROW EXECUTE FUNCTION fn_intake_fill_submitter();

-- 5b. the original submission is immutable; only the review outcome moves
CREATE FUNCTION fn_intake_protect_original() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.client_full_name         IS DISTINCT FROM OLD.client_full_name
    OR NEW.parent_guardian_name     IS DISTINCT FROM OLD.parent_guardian_name
    OR NEW.contact_info             IS DISTINCT FROM OLD.contact_info
    OR NEW.preferred_contact_method IS DISTINCT FROM OLD.preferred_contact_method
    OR NEW.overview                 IS DISTINCT FROM OLD.overview
    OR NEW.support_needs            IS DISTINCT FROM OLD.support_needs
    OR NEW.respite_goals            IS DISTINCT FROM OLD.respite_goals
    OR NEW.subject_focus            IS DISTINCT FROM OLD.subject_focus
    OR NEW.additional_notes         IS DISTINCT FROM OLD.additional_notes
    OR NEW.submitted_by_name        IS DISTINCT FROM OLD.submitted_by_name
    OR NEW.submitted_by_email       IS DISTINCT FROM OLD.submitted_by_email
    OR NEW.submitted_by_phone       IS DISTINCT FROM OLD.submitted_by_phone
    OR NEW.created_at               IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'An intake submission cannot be edited after it has been submitted (id %).', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    -- a decision is final
    IF OLD.status IN ('ACCEPTED', 'REJECTED') AND NEW.status IS DISTINCT FROM OLD.status THEN
        RAISE EXCEPTION 'Intake submission % has already been decided (%).', OLD.id, OLD.status
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NEW;
END
$$;

CREATE TRIGGER trg_intake_protect_original
    BEFORE UPDATE ON intake_submissions
    FOR EACH ROW EXECUTE FUNCTION fn_intake_protect_original();
