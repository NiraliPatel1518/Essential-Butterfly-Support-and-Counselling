-- =====================================================================
-- V5  Contact messages: keep the sender, protect the message
--
-- A contact message is what a person sent at a point in time. The
-- sender's name and address are therefore stored ON the message (a
-- deliberate historical copy, not a duplicate of the account), and the
-- link to a registered account is optional:
--
--   * public / guest messages have no account (client_user_id IS NULL)
--   * messages from a registered client keep working if that account is
--     later deleted (ON DELETE SET NULL) - the sender details remain
--   * a message is never "linked" to an account just because the typed
--     e-mail address happens to match: the address on a public form is
--     unverified, so matching it would misattribute messages.
--   * sender, subject, body and time cannot be edited afterwards; only
--     the review status (NEW -> VIEWED) changes
-- =====================================================================

-- 1. clean legacy data ------------------------------------------------
UPDATE contact_messages
   SET full_name = btrim(full_name),
       email     = lower(btrim(email)),
       subject   = btrim(subject),
       status    = upper(btrim(status));

UPDATE contact_messages
   SET status = 'NEW'
 WHERE status NOT IN ('NEW', 'VIEWED');

-- 2. keep messages when an account is deleted -------------------------
DO $fk$
DECLARE
    fk_name text;
BEGIN
    SELECT conname INTO fk_name
      FROM pg_constraint
     WHERE conrelid = 'public.contact_messages'::regclass
       AND contype = 'f'
       AND confrelid = 'public.client_users'::regclass;

    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE contact_messages DROP CONSTRAINT %I', fk_name);
    END IF;
END
$fk$;

ALTER TABLE contact_messages
    ADD CONSTRAINT fk_contact_messages_client_user
        FOREIGN KEY (client_user_id) REFERENCES client_users (id)
        ON DELETE SET NULL;

-- 3. constraints ------------------------------------------------------
ALTER TABLE contact_messages
    ADD CONSTRAINT ck_contact_messages_status
        CHECK (status IN ('NEW', 'VIEWED')),
    ADD CONSTRAINT ck_contact_messages_sender_name_not_blank
        CHECK (btrim(full_name) <> ''),
    ADD CONSTRAINT ck_contact_messages_sender_email
        CHECK (email = lower(btrim(email)) AND position('@' IN email) > 1),
    ADD CONSTRAINT ck_contact_messages_subject_not_blank
        CHECK (btrim(subject) <> ''),
    ADD CONSTRAINT ck_contact_messages_body_not_blank
        CHECK (btrim(message) <> '');

-- 4. the message itself is immutable ---------------------------------
CREATE FUNCTION fn_contact_message_protect_original() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.full_name  IS DISTINCT FROM OLD.full_name
    OR NEW.email      IS DISTINCT FROM OLD.email
    OR NEW.subject    IS DISTINCT FROM OLD.subject
    OR NEW.message    IS DISTINCT FROM OLD.message
    OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'A contact message cannot be edited after it has been sent (id %).', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    -- VIEWED is final: a viewed message never goes back to NEW
    IF OLD.status = 'VIEWED' AND NEW.status = 'NEW' THEN
        RAISE EXCEPTION 'Contact message % has already been viewed.', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NEW;
END
$$;

CREATE TRIGGER trg_contact_message_protect_original
    BEFORE UPDATE ON contact_messages
    FOR EACH ROW EXECUTE FUNCTION fn_contact_message_protect_original();
