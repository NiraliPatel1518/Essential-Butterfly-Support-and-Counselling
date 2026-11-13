-- =====================================================================
-- V7  Newsletter: subscribers, campaigns and deliveries
--
-- Three separate things, linked only by keys:
--
--   newsletter_subscribers   an ADDRESS that agreed to receive mail
--                            (guest, or linked to a registered client)
--   newsletter_campaigns     the CONTENT that was written / sent
--   newsletter_deliveries    one row per (campaign, subscriber): what
--                            actually happened
--
-- Changes
--   * recipient_count / success_count / failed_count are removed from
--     campaigns. They were copies of facts that already exist in
--     newsletter_deliveries and could drift. Statistics are now always
--     calculated from the delivery rows (view newsletter_campaign_stats).
--   * guest AND registered subscriptions: client_user_id is optional
--   * subscription history is kept in an append-only event log that is
--     written by triggers, so it cannot be skipped or edited
--   * delivery rows can never be orphaned or silently removed
-- =====================================================================

-- 1. warn (do not fail) if a legacy counter has no delivery rows behind it
DO $audit$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.id, c.recipient_count, count(d.id) AS deliveries
          FROM newsletter_campaigns c
          LEFT JOIN newsletter_deliveries d ON d.campaign_id = c.id
         GROUP BY c.id, c.recipient_count
        HAVING c.recipient_count <> count(d.id)
    LOOP
        RAISE NOTICE 'Campaign % had recipient_count=% but % delivery rows; the delivery rows are the source of truth.',
                     r.id, r.recipient_count, r.deliveries;
    END LOOP;
END
$audit$;

-- 2. campaigns --------------------------------------------------------
ALTER TABLE newsletter_campaigns
    DROP COLUMN recipient_count,
    DROP COLUMN success_count,
    DROP COLUMN failed_count;

UPDATE newsletter_campaigns
   SET subject = btrim(subject)
 WHERE subject <> btrim(subject);

UPDATE newsletter_campaigns
   SET status = 'DRAFT'
 WHERE status NOT IN ('DRAFT', 'SENT', 'PARTIAL', 'FAILED');

UPDATE newsletter_campaigns
   SET sent_at = COALESCE(sent_at, created_at)
 WHERE status <> 'DRAFT';

UPDATE newsletter_campaigns
   SET sent_at = NULL
 WHERE status = 'DRAFT' AND sent_at IS NOT NULL;

ALTER TABLE newsletter_campaigns
    ADD CONSTRAINT ck_newsletter_campaigns_status
        CHECK (status IN ('DRAFT', 'SENT', 'PARTIAL', 'FAILED')),
    ADD CONSTRAINT ck_newsletter_campaigns_subject_not_blank
        CHECK (btrim(subject) <> ''),
    ADD CONSTRAINT ck_newsletter_campaigns_content_not_blank
        CHECK (btrim(content) <> ''),
    ADD CONSTRAINT ck_newsletter_campaigns_sent_at
        CHECK ((status = 'DRAFT') = (sent_at IS NULL));

-- 3. deliveries -------------------------------------------------------
UPDATE newsletter_deliveries
   SET status = 'PENDING'
 WHERE status NOT IN ('PENDING', 'SENT', 'FAILED');

UPDATE newsletter_deliveries
   SET sent_at = now()
 WHERE status = 'SENT' AND sent_at IS NULL;

UPDATE newsletter_deliveries
   SET error_message = NULL
 WHERE status <> 'FAILED' AND error_message IS NOT NULL;

ALTER TABLE newsletter_deliveries
    ADD CONSTRAINT ck_newsletter_deliveries_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    ADD CONSTRAINT ck_newsletter_deliveries_sent_at
        CHECK (status <> 'SENT' OR sent_at IS NOT NULL),
    ADD CONSTRAINT ck_newsletter_deliveries_error_only_when_failed
        CHECK (error_message IS NULL OR status = 'FAILED');

-- history must never be deleted by cascade
DO $fk$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT conname
          FROM pg_constraint
         WHERE conrelid = 'public.newsletter_deliveries'::regclass
           AND contype = 'f'
    LOOP
        EXECUTE format('ALTER TABLE newsletter_deliveries DROP CONSTRAINT %I', r.conname);
    END LOOP;
END
$fk$;

ALTER TABLE newsletter_deliveries
    ADD CONSTRAINT fk_newsletter_deliveries_campaign
        FOREIGN KEY (campaign_id) REFERENCES newsletter_campaigns (id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_newsletter_deliveries_subscriber
        FOREIGN KEY (subscriber_id) REFERENCES newsletter_subscribers (id)
        ON DELETE RESTRICT;

-- 4. subscribers: guest or registered --------------------------------
UPDATE newsletter_subscribers
   SET consent_at = COALESCE(consent_at, created_at)
 WHERE consent_given AND consent_at IS NULL;

UPDATE newsletter_subscribers
   SET consent_at = NULL
 WHERE NOT consent_given AND consent_at IS NOT NULL;

-- active and unsubscribed_at must agree; when they disagree the safe
-- reading is "unsubscribed"
UPDATE newsletter_subscribers
   SET active = false,
       unsubscribed_at = COALESCE(unsubscribed_at, created_at)
 WHERE (NOT active AND unsubscribed_at IS NULL)
    OR (active AND unsubscribed_at IS NOT NULL)
    OR (active AND NOT consent_given);

ALTER TABLE newsletter_subscribers
    ADD COLUMN client_user_id bigint
        CONSTRAINT fk_newsletter_subscribers_client_user
        REFERENCES client_users (id) ON DELETE SET NULL;

-- one subscription per registered client
CREATE UNIQUE INDEX uq_newsletter_subscribers_client_user
    ON newsletter_subscribers (client_user_id)
    WHERE client_user_id IS NOT NULL;

-- link existing subscriptions whose address belongs to a VERIFIED account
UPDATE newsletter_subscribers s
   SET client_user_id = e.client_user_id
  FROM account_emails e
 WHERE e.client_user_id IS NOT NULL
   AND e.is_primary
   AND e.verified_at IS NOT NULL
   AND e.email = s.email;

ALTER TABLE newsletter_subscribers
    ADD CONSTRAINT ck_newsletter_subscribers_consent
        CHECK (consent_given = (consent_at IS NOT NULL)),
    ADD CONSTRAINT ck_newsletter_subscribers_active_state
        CHECK (active = (unsubscribed_at IS NULL)),
    ADD CONSTRAINT ck_newsletter_subscribers_active_needs_consent
        CHECK (NOT active OR consent_given),
    ADD CONSTRAINT ck_newsletter_subscribers_token_length
        CHECK (char_length(unsubscribe_token) = 36);

-- 4b. keep the link correct automatically ------------------------------
-- (a) a new subscription for an address that a verified client owns
CREATE FUNCTION fn_subscriber_link_client() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.client_user_id IS NULL THEN
        SELECT e.client_user_id
          INTO NEW.client_user_id
          FROM account_emails e
         WHERE e.client_user_id IS NOT NULL
           AND e.is_primary
           AND e.verified_at IS NOT NULL
           AND e.email = NEW.email
           AND NOT EXISTS (SELECT 1 FROM newsletter_subscribers s
                            WHERE s.client_user_id = e.client_user_id);
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_subscriber_link_client
    BEFORE INSERT ON newsletter_subscribers
    FOR EACH ROW EXECUTE FUNCTION fn_subscriber_link_client();

-- (b) a client who verifies an address that is already subscribed
CREATE FUNCTION fn_account_email_link_subscriber() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.client_user_id IS NOT NULL
       AND NEW.is_primary
       AND NEW.verified_at IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM newsletter_subscribers s
                        WHERE s.client_user_id = NEW.client_user_id) THEN
        UPDATE newsletter_subscribers
           SET client_user_id = NEW.client_user_id
         WHERE email = NEW.email
           AND client_user_id IS NULL;
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_account_email_link_subscriber
    AFTER INSERT OR UPDATE OF verified_at, is_primary ON account_emails
    FOR EACH ROW EXECUTE FUNCTION fn_account_email_link_subscriber();

-- 5. subscription history (append-only, written by triggers) ----------
CREATE TABLE newsletter_subscription_events (
    id            bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    subscriber_id bigint       NOT NULL
                  REFERENCES newsletter_subscribers (id) ON DELETE RESTRICT,
    event_type    varchar(20)  NOT NULL,
    occurred_at   timestamp(6) NOT NULL DEFAULT now(),

    CONSTRAINT ck_newsletter_events_type
        CHECK (event_type IN ('SUBSCRIBED', 'UNSUBSCRIBED', 'RESUBSCRIBED'))
);

CREATE INDEX ix_newsletter_events_subscriber
    ON newsletter_subscription_events (subscriber_id, occurred_at);

-- history of what already happened
INSERT INTO newsletter_subscription_events (subscriber_id, event_type, occurred_at)
SELECT id, 'SUBSCRIBED', COALESCE(consent_at, created_at)
  FROM newsletter_subscribers;

INSERT INTO newsletter_subscription_events (subscriber_id, event_type, occurred_at)
SELECT id, 'UNSUBSCRIBED', unsubscribed_at
  FROM newsletter_subscribers
 WHERE unsubscribed_at IS NOT NULL;

CREATE FUNCTION fn_newsletter_log_subscription() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO newsletter_subscription_events (subscriber_id, event_type)
        VALUES (NEW.id, 'SUBSCRIBED');
        IF NOT NEW.active THEN
            INSERT INTO newsletter_subscription_events (subscriber_id, event_type)
            VALUES (NEW.id, 'UNSUBSCRIBED');
        END IF;
    ELSIF OLD.active AND NOT NEW.active THEN
        INSERT INTO newsletter_subscription_events (subscriber_id, event_type)
        VALUES (NEW.id, 'UNSUBSCRIBED');
    ELSIF NOT OLD.active AND NEW.active THEN
        INSERT INTO newsletter_subscription_events (subscriber_id, event_type)
        VALUES (NEW.id, 'RESUBSCRIBED');
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_newsletter_log_subscription
    AFTER INSERT OR UPDATE OF active ON newsletter_subscribers
    FOR EACH ROW EXECUTE FUNCTION fn_newsletter_log_subscription();

CREATE FUNCTION fn_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is not allowed.', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END
$$;

CREATE TRIGGER trg_newsletter_events_append_only
    BEFORE UPDATE OR DELETE ON newsletter_subscription_events
    FOR EACH ROW EXECUTE FUNCTION fn_append_only();

-- 6. statistics are always calculated, never stored -------------------
CREATE VIEW newsletter_campaign_stats AS
SELECT c.id                                              AS campaign_id,
       count(d.id)                                       AS recipient_count,
       count(d.id) FILTER (WHERE d.status = 'SENT')      AS success_count,
       count(d.id) FILTER (WHERE d.status = 'FAILED')    AS failed_count,
       count(d.id) FILTER (WHERE d.status = 'PENDING')   AS pending_count
  FROM newsletter_campaigns c
  LEFT JOIN newsletter_deliveries d ON d.campaign_id = c.id
 GROUP BY c.id;
