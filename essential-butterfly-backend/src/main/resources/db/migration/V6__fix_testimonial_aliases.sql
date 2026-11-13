-- =====================================================================
-- V6  Testimonials: preserve the alias and the submitted words
--
-- A testimonial is published under an ALIAS (display_name) that the
-- client chose, not under the account name, and the client consented to
-- publication of exactly the text they wrote. So:
--
--   * display_name, content and publication_consent are immutable once
--     the testimonial exists (review only moves status / visibility)
--   * the testimonial survives the deletion of the account
--     (client_user_id becomes NULL; alias and text are untouched)
--   * the submitter's e-mail at submission time is kept for the admin
--     (filled by a trigger)
--   * visibility rules become database rules instead of service-only
--     rules: only APPROVED testimonials with consent can be public, only
--     public ones can be on the home page, and at most two can be there.
-- =====================================================================

-- 1. repair legacy data so the new rules hold -------------------------
UPDATE customer_testimonials
   SET display_name = btrim(display_name),
       content      = btrim(content),
       status       = upper(btrim(status));

UPDATE customer_testimonials
   SET display_name = 'Anonymous'
 WHERE btrim(display_name) = '';

UPDATE customer_testimonials
   SET status = 'PENDING'
 WHERE status NOT IN ('PENDING', 'APPROVED', 'REJECTED', 'REMOVED');

UPDATE customer_testimonials
   SET display_on_public = false,
       featured_on_home  = false
 WHERE NOT (status = 'APPROVED' AND publication_consent);

UPDATE customer_testimonials
   SET featured_on_home = false
 WHERE NOT display_on_public;

-- keep only the two most recently reviewed home-page testimonials
UPDATE customer_testimonials t
   SET featured_on_home = false
 WHERE t.featured_on_home
   AND t.id NOT IN (SELECT id
                      FROM customer_testimonials
                     WHERE featured_on_home
                     ORDER BY reviewed_at DESC NULLS LAST, id DESC
                     LIMIT 2);

UPDATE customer_testimonials
   SET reviewed_at = created_at
 WHERE status <> 'PENDING' AND reviewed_at IS NULL;

-- 2. submitter e-mail snapshot ---------------------------------------
ALTER TABLE customer_testimonials
    ADD COLUMN submitted_by_email varchar(254);

UPDATE customer_testimonials t
   SET submitted_by_email = e.email
  FROM account_emails e
 WHERE e.client_user_id = t.client_user_id
   AND e.is_primary;

-- 3. keep testimonials when an account is deleted ---------------------
ALTER TABLE customer_testimonials
    ALTER COLUMN client_user_id DROP NOT NULL;

DO $fk$
DECLARE
    fk_name text;
BEGIN
    SELECT conname INTO fk_name
      FROM pg_constraint
     WHERE conrelid = 'public.customer_testimonials'::regclass
       AND contype = 'f'
       AND confrelid = 'public.client_users'::regclass;

    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE customer_testimonials DROP CONSTRAINT %I', fk_name);
    END IF;
END
$fk$;

ALTER TABLE customer_testimonials
    ADD CONSTRAINT fk_customer_testimonials_client_user
        FOREIGN KEY (client_user_id) REFERENCES client_users (id)
        ON DELETE SET NULL;

-- 4. constraints ------------------------------------------------------
ALTER TABLE customer_testimonials
    ADD CONSTRAINT ck_testimonials_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REMOVED')),
    ADD CONSTRAINT ck_testimonials_alias_not_blank
        CHECK (btrim(display_name) <> ''),
    ADD CONSTRAINT ck_testimonials_content_not_blank
        CHECK (btrim(content) <> ''),
    ADD CONSTRAINT ck_testimonials_reviewed_when_decided
        CHECK (status = 'PENDING' OR reviewed_at IS NOT NULL),
    ADD CONSTRAINT ck_testimonials_public_requires_approval
        CHECK (NOT display_on_public OR (status = 'APPROVED' AND publication_consent)),
    ADD CONSTRAINT ck_testimonials_featured_requires_public
        CHECK (NOT featured_on_home OR display_on_public);

-- 5. triggers ---------------------------------------------------------

-- 5a. snapshot of the submitter's address
CREATE FUNCTION fn_testimonial_fill_submitter() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.submitted_by_email IS NULL AND NEW.client_user_id IS NOT NULL THEN
        SELECT e.email
          INTO NEW.submitted_by_email
          FROM account_emails e
         WHERE e.client_user_id = NEW.client_user_id
           AND e.is_primary;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_testimonial_fill_submitter
    BEFORE INSERT ON customer_testimonials
    FOR EACH ROW EXECUTE FUNCTION fn_testimonial_fill_submitter();

-- 5b. alias and words are immutable
CREATE FUNCTION fn_testimonial_protect_original() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.display_name        IS DISTINCT FROM OLD.display_name
    OR NEW.content             IS DISTINCT FROM OLD.content
    OR NEW.publication_consent IS DISTINCT FROM OLD.publication_consent
    OR NEW.created_at          IS DISTINCT FROM OLD.created_at
    OR NEW.submitted_by_email  IS DISTINCT FROM OLD.submitted_by_email THEN
        RAISE EXCEPTION 'A submitted testimonial (alias, text, consent) cannot be edited (id %).', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_testimonial_protect_original
    BEFORE UPDATE ON customer_testimonials
    FOR EACH ROW EXECUTE FUNCTION fn_testimonial_protect_original();

-- 5c. at most two testimonials on the home page
CREATE FUNCTION fn_testimonial_limit_featured() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.featured_on_home THEN
        PERFORM pg_advisory_xact_lock(hashtext('customer_testimonials.featured_on_home'));

        IF (SELECT count(*)
              FROM customer_testimonials
             WHERE featured_on_home AND id <> COALESCE(NEW.id, -1)) >= 2 THEN
            RAISE EXCEPTION 'Only two testimonials can be featured on the home page.'
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_testimonial_limit_featured
    BEFORE INSERT OR UPDATE OF featured_on_home ON customer_testimonials
    FOR EACH ROW EXECUTE FUNCTION fn_testimonial_limit_featured();
