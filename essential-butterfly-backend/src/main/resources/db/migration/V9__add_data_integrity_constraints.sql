-- =====================================================================
-- V9  Final integrity layer
--
--   1. consistent delete rules for owned child rows (CASCADE)
--   2. remaining cross-field rules
--   3. indexes for the queries the application actually runs
--   4. audit_log: an append-only record of security- and
--      workflow-relevant changes, written by triggers
--
-- Delete-rule summary for the whole schema
--   owned parts  (photos, links)             -> ON DELETE CASCADE
--   account e-mails / phone checks / tokens  -> ON DELETE CASCADE
--   history that must outlive an account
--     (intakes, contact messages,
--      testimonials, newsletter subscriber)   -> ON DELETE SET NULL
--   history that must never disappear
--     (deliveries, subscription events)       -> ON DELETE RESTRICT
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. owned child rows are removed with their parent
-- ---------------------------------------------------------------------
DO $fk$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT con.conrelid::regclass::text AS tbl, con.conname
          FROM pg_constraint con
         WHERE con.contype = 'f'
           AND con.conrelid IN ('public.testimonial_photos'::regclass,
                                'public.website_update_photos'::regclass,
                                'public.website_update_social_links'::regclass)
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END
$fk$;

ALTER TABLE testimonial_photos
    ADD CONSTRAINT fk_testimonial_photos_testimonial
        FOREIGN KEY (testimonial_id) REFERENCES customer_testimonials (id)
        ON DELETE CASCADE;

ALTER TABLE website_update_photos
    ADD CONSTRAINT fk_website_update_photos_update
        FOREIGN KEY (update_id) REFERENCES website_updates (id)
        ON DELETE CASCADE;

ALTER TABLE website_update_social_links
    ADD CONSTRAINT fk_website_update_social_links_update
        FOREIGN KEY (update_id) REFERENCES website_updates (id)
        ON DELETE CASCADE;

-- ---------------------------------------------------------------------
-- 2. remaining cross-field rules
-- ---------------------------------------------------------------------
UPDATE website_updates
   SET updated_at = created_at
 WHERE updated_at < created_at;

ALTER TABLE website_updates
    ADD CONSTRAINT ck_website_updates_timestamps
        CHECK (updated_at >= created_at);

-- the admin two-factor challenge is all-or-nothing: either there is an
-- active challenge (token + code + expiry) or there is none
UPDATE admin_users
   SET login_challenge_token_hash = NULL,
       login_otp_hash = NULL,
       login_otp_expiry = NULL,
       login_otp_last_sent_at = NULL,
       login_otp_attempts = 0,
       login_otp_resend_count = 0
 WHERE NOT (login_challenge_token_hash IS NOT NULL
        AND login_otp_hash IS NOT NULL
        AND login_otp_expiry IS NOT NULL);

ALTER TABLE admin_users
    ADD CONSTRAINT ck_admin_users_login_challenge_complete
        CHECK ((login_challenge_token_hash IS NULL
                AND login_otp_hash IS NULL
                AND login_otp_expiry IS NULL)
            OR (login_challenge_token_hash IS NOT NULL
                AND login_otp_hash IS NOT NULL
                AND login_otp_expiry IS NOT NULL));

-- ---------------------------------------------------------------------
-- 3. indexes (unique keys already have their own index)
-- ---------------------------------------------------------------------

-- account e-mails: loading all addresses of one account
CREATE INDEX ix_account_emails_admin_user  ON account_emails (admin_user_id)  WHERE admin_user_id  IS NOT NULL;
CREATE INDEX ix_account_emails_client_user ON account_emails (client_user_id) WHERE client_user_id IS NOT NULL;

-- intake: "my latest submission" and the admin queue by status
CREATE INDEX ix_intake_client_created ON intake_submissions (client_user_id, created_at DESC)
    WHERE client_user_id IS NOT NULL;
CREATE INDEX ix_intake_status_created ON intake_submissions (status, created_at DESC);

-- contact messages: admin list, unread counter
CREATE INDEX ix_contact_status_created ON contact_messages (status, created_at DESC);
CREATE INDEX ix_contact_created        ON contact_messages (created_at DESC);
CREATE INDEX ix_contact_client         ON contact_messages (client_user_id)
    WHERE client_user_id IS NOT NULL;

-- testimonials: "mine", admin list, public list, home page
CREATE INDEX ix_testimonials_client         ON customer_testimonials (client_user_id)
    WHERE client_user_id IS NOT NULL;
CREATE INDEX ix_testimonials_status_created ON customer_testimonials (status, created_at);
CREATE INDEX ix_testimonials_public         ON customer_testimonials (reviewed_at DESC)
    WHERE status = 'APPROVED' AND display_on_public;
CREATE INDEX ix_testimonials_featured       ON customer_testimonials (reviewed_at DESC)
    WHERE status = 'APPROVED' AND featured_on_home;

-- newsletter
CREATE INDEX ix_newsletter_subscribers_active ON newsletter_subscribers (created_at DESC)
    WHERE active;
CREATE INDEX ix_newsletter_deliveries_subscriber ON newsletter_deliveries (subscriber_id);
CREATE INDEX ix_newsletter_deliveries_campaign_status ON newsletter_deliveries (campaign_id, status);

-- website updates: public list
CREATE INDEX ix_website_updates_published ON website_updates (updated_at DESC)
    WHERE published;
CREATE INDEX ix_website_updates_updated   ON website_updates (updated_at DESC);

-- ---------------------------------------------------------------------
-- 4. audit log
--
-- Append-only. The triggers below record WHAT changed and WHEN, never a
-- secret (password hashes, codes and tokens are not copied).
-- The acting user is not known to the database; the application may
-- optionally set it per transaction with
--     SELECT set_config('app.actor', 'ADMIN:42', true);
-- and it is then stored in "actor".
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id          bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    occurred_at timestamp(6) NOT NULL DEFAULT now(),
    action      varchar(60)  NOT NULL,
    entity_type varchar(60)  NOT NULL,
    entity_id   bigint,
    actor       varchar(80),
    details     jsonb        NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT ck_audit_log_action_not_blank CHECK (btrim(action) <> ''),
    CONSTRAINT ck_audit_log_details_object   CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX ix_audit_log_entity   ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_log_occurred ON audit_log (occurred_at DESC);
CREATE INDEX ix_audit_log_action   ON audit_log (action, occurred_at DESC);

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION fn_append_only();

CREATE FUNCTION fn_audit(p_action text, p_type text, p_id bigint, p_details jsonb)
RETURNS void
LANGUAGE sql AS $$
    INSERT INTO audit_log (action, entity_type, entity_id, actor, details)
    VALUES (p_action, p_type, p_id,
            NULLIF(current_setting('app.actor', true), ''),
            COALESCE(p_details, '{}'::jsonb));
$$;

-- administrators -------------------------------------------------------
CREATE FUNCTION fn_audit_admin_users() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.password IS DISTINCT FROM OLD.password THEN
        PERFORM fn_audit('ADMIN_PASSWORD_CHANGED', 'admin_users', NEW.id, '{}');
    END IF;
    IF NEW.locked_until IS NOT NULL AND OLD.locked_until IS NULL THEN
        PERFORM fn_audit('ADMIN_ACCOUNT_LOCKED', 'admin_users', NEW.id,
                         jsonb_build_object('locked_until', NEW.locked_until));
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_audit_admin_users
    AFTER UPDATE ON admin_users
    FOR EACH ROW EXECUTE FUNCTION fn_audit_admin_users();

-- clients --------------------------------------------------------------
CREATE FUNCTION fn_audit_client_users() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.password IS DISTINCT FROM OLD.password THEN
        PERFORM fn_audit('CLIENT_PASSWORD_CHANGED', 'client_users', NEW.id, '{}');
    END IF;
    IF NEW.phone_number IS DISTINCT FROM OLD.phone_number THEN
        PERFORM fn_audit('CLIENT_PHONE_CHANGED', 'client_users', NEW.id,
                         jsonb_build_object('old', OLD.phone_number, 'new', NEW.phone_number));
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_audit_client_users
    AFTER UPDATE ON client_users
    FOR EACH ROW EXECUTE FUNCTION fn_audit_client_users();

-- e-mail addresses -----------------------------------------------------
CREATE FUNCTION fn_audit_account_emails() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    owner_type text;
    owner_id   bigint;
BEGIN
    owner_type := CASE WHEN COALESCE(NEW.admin_user_id, OLD.admin_user_id) IS NOT NULL
                       THEN 'admin_users' ELSE 'client_users' END;
    owner_id   := COALESCE(NEW.admin_user_id, NEW.client_user_id,
                           OLD.admin_user_id, OLD.client_user_id);

    IF TG_OP = 'INSERT' THEN
        IF NOT NEW.is_primary THEN
            PERFORM fn_audit('EMAIL_CHANGE_REQUESTED', owner_type, owner_id,
                             jsonb_build_object('new_email', NEW.email));
        END IF;
    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.is_primary AND NEW.email IS DISTINCT FROM OLD.email THEN
            PERFORM fn_audit('EMAIL_CHANGED', owner_type, owner_id,
                             jsonb_build_object('old_email', OLD.email, 'new_email', NEW.email));
        END IF;
        IF NEW.verified_at IS NOT NULL AND OLD.verified_at IS NULL THEN
            PERFORM fn_audit('EMAIL_VERIFIED', owner_type, owner_id,
                             jsonb_build_object('email', NEW.email));
        END IF;
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_audit_account_emails
    AFTER INSERT OR UPDATE ON account_emails
    FOR EACH ROW EXECUTE FUNCTION fn_audit_account_emails();

-- intake decisions -----------------------------------------------------
CREATE FUNCTION fn_audit_intake() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        PERFORM fn_audit('INTAKE_STATUS_CHANGED', 'intake_submissions', NEW.id,
                         jsonb_build_object('from', OLD.status, 'to', NEW.status));
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_audit_intake
    AFTER UPDATE ON intake_submissions
    FOR EACH ROW EXECUTE FUNCTION fn_audit_intake();

-- testimonial review / visibility --------------------------------------
CREATE FUNCTION fn_audit_testimonial() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        PERFORM fn_audit('TESTIMONIAL_STATUS_CHANGED', 'customer_testimonials', NEW.id,
                         jsonb_build_object('from', OLD.status, 'to', NEW.status));
    END IF;
    IF NEW.display_on_public IS DISTINCT FROM OLD.display_on_public
    OR NEW.featured_on_home  IS DISTINCT FROM OLD.featured_on_home THEN
        PERFORM fn_audit('TESTIMONIAL_VISIBILITY_CHANGED', 'customer_testimonials', NEW.id,
                         jsonb_build_object('display_on_public', NEW.display_on_public,
                                            'featured_on_home', NEW.featured_on_home));
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_audit_testimonial
    AFTER UPDATE ON customer_testimonials
    FOR EACH ROW EXECUTE FUNCTION fn_audit_testimonial();

-- feature switches -----------------------------------------------------
CREATE FUNCTION fn_audit_feature_visibility() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.enabled IS DISTINCT FROM OLD.enabled THEN
        PERFORM fn_audit('FEATURE_TOGGLED', 'public_feature_visibility', NEW.id,
                         jsonb_build_object('feature_key', NEW.feature_key,
                                            'enabled', NEW.enabled));
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_audit_feature_visibility
    AFTER UPDATE ON public_feature_visibility
    FOR EACH ROW EXECUTE FUNCTION fn_audit_feature_visibility();

-- newsletter sends -----------------------------------------------------
CREATE FUNCTION fn_audit_campaign() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        PERFORM fn_audit('NEWSLETTER_STATUS_CHANGED', 'newsletter_campaigns', NEW.id,
                         jsonb_build_object('from', OLD.status, 'to', NEW.status));
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_audit_campaign
    AFTER UPDATE ON newsletter_campaigns
    FOR EACH ROW EXECUTE FUNCTION fn_audit_campaign();

-- password resets ------------------------------------------------------
CREATE FUNCTION fn_audit_password_reset() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    owner_type text;
    owner_id   bigint;
BEGIN
    owner_type := CASE WHEN NEW.admin_user_id IS NOT NULL THEN 'admin_users' ELSE 'client_users' END;
    owner_id   := COALESCE(NEW.admin_user_id, NEW.client_user_id);

    IF TG_OP = 'INSERT' THEN
        PERFORM fn_audit('PASSWORD_RESET_REQUESTED', owner_type, owner_id, '{}');
    ELSIF NEW.used_at IS NOT NULL AND OLD.used_at IS NULL THEN
        PERFORM fn_audit('PASSWORD_RESET_TOKEN_CLOSED', owner_type, owner_id, '{}');
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_audit_password_reset
    AFTER INSERT OR UPDATE ON password_reset_tokens
    FOR EACH ROW EXECUTE FUNCTION fn_audit_password_reset();
