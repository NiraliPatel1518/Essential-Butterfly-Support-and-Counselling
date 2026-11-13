-- =====================================================================
-- V3  Move account e-mail / phone handling into normalised structures
--
-- Part A  e-mail   admin_users.email / pending_email and
--                  client_users.email / email_verified + OTP columns
--                  -> account_emails
-- Part B  phone    client_users keeps ONLY a verified phone_number.
--                  A number that is still being verified lives in
--                  phone_verifications (no "pending/alternate" column,
--                  no redundant phone_verified flag).
--
-- Nothing is dropped until it has been copied and the copy has been
-- checked. If the legacy data already contains the same address on two
-- accounts (for example an admin and a client) the migration stops with
-- a message that names the addresses, instead of silently picking one.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Part A: e-mail
-- ---------------------------------------------------------------------

-- A1. refuse to continue if an address is not globally unique
DO $check$
DECLARE
    clash text;
BEGIN
    SELECT string_agg(e, ', ' ORDER BY e)
      INTO clash
      FROM (
            SELECT lower(btrim(email)) AS e
              FROM (SELECT email FROM admin_users
                    UNION ALL
                    SELECT email FROM client_users) all_primary
             GROUP BY lower(btrim(email))
            HAVING count(*) > 1
           ) dup;

    IF clash IS NOT NULL THEN
        RAISE EXCEPTION
          'V3 stopped: these e-mail addresses are used by more than one account: %. '
          'Change or remove the duplicates (admin vs client, or differing only by case) and run the migration again.',
          clash;
    END IF;
END
$check$;

-- A2. administrators: primary (sign-in) address. Existing admins have
--     been signing in with it, so it is treated as verified.
INSERT INTO account_emails (admin_user_id, email, is_primary, created_at, verified_at)
SELECT id, lower(btrim(email)), true, created_at, created_at
  FROM admin_users;

-- A3. clients: primary address. Verified accounts keep verified_at;
--     unverified ones keep their in-flight code (hash only).
INSERT INTO account_emails (
        client_user_id, email, is_primary, created_at, verified_at,
        code_hash, code_expires_at, code_last_sent_at,
        code_attempts, code_resend_count)
SELECT c.id,
       lower(btrim(c.email)),
       true,
       c.created_at,
       CASE WHEN c.email_verified THEN c.created_at END,
       CASE WHEN c.email_verified THEN NULL ELSE c.email_verification_code_hash END,
       CASE WHEN c.email_verified THEN NULL ELSE c.email_verification_code_expiry END,
       CASE WHEN c.email_verified THEN NULL ELSE c.email_verification_last_sent_at END,
       CASE WHEN c.email_verified THEN 0    ELSE c.email_verification_attempts END,
       CASE WHEN c.email_verified THEN 0    ELSE c.email_verification_resend_count END
  FROM client_users c;

-- A4. administrators: a pending e-mail change becomes a non-primary row
--     that carries its own verification state. It runs AFTER every primary
--     address has been copied, so a pending address that is already used
--     by any account (or by another admin's pending change) is simply
--     discarded - it could never have been verified - instead of blocking
--     the migration.
INSERT INTO account_emails (
        admin_user_id, email, is_primary, created_at,
        code_hash, code_expires_at, code_last_sent_at,
        code_attempts, code_resend_count)
SELECT DISTINCT ON (lower(btrim(a.pending_email)))
       a.id,
       lower(btrim(a.pending_email)),
       false,
       COALESCE(a.email_change_otp_last_sent_at, now()),
       a.email_change_otp_hash,
       a.email_change_otp_expiry,
       a.email_change_otp_last_sent_at,
       a.email_change_otp_attempts,
       a.email_change_otp_resend_count
  FROM admin_users a
 WHERE a.pending_email IS NOT NULL
   AND btrim(a.pending_email) <> ''
   AND NOT EXISTS (SELECT 1
                     FROM account_emails x
                    WHERE x.email = lower(btrim(a.pending_email)))
 ORDER BY lower(btrim(a.pending_email)), a.id;

-- A5. every account must now have exactly one primary address
DO $verify$
DECLARE
    missing bigint;
BEGIN
    SELECT (SELECT count(*) FROM admin_users a
             WHERE NOT EXISTS (SELECT 1 FROM account_emails e
                                WHERE e.admin_user_id = a.id AND e.is_primary))
         + (SELECT count(*) FROM client_users c
             WHERE NOT EXISTS (SELECT 1 FROM account_emails e
                                WHERE e.client_user_id = c.id AND e.is_primary))
      INTO missing;

    IF missing > 0 THEN
        RAISE EXCEPTION 'V3 stopped: % account(s) did not receive a primary e-mail row.', missing;
    END IF;
END
$verify$;

-- A6. drop the legacy columns (their unique constraints go with them)
ALTER TABLE admin_users
    DROP COLUMN email,
    DROP COLUMN pending_email,
    DROP COLUMN email_change_otp_hash,
    DROP COLUMN email_change_otp_expiry,
    DROP COLUMN email_change_otp_last_sent_at,
    DROP COLUMN email_change_otp_attempts,
    DROP COLUMN email_change_otp_resend_count;

ALTER TABLE client_users
    DROP COLUMN email,
    DROP COLUMN email_verified,
    DROP COLUMN email_verification_code_hash,
    DROP COLUMN email_verification_code_expiry,
    DROP COLUMN email_verification_last_sent_at,
    DROP COLUMN email_verification_attempts,
    DROP COLUMN email_verification_resend_count;

-- ---------------------------------------------------------------------
-- Part B: phone numbers
-- ---------------------------------------------------------------------

CREATE TABLE phone_verifications (
    client_user_id    bigint PRIMARY KEY
                      REFERENCES client_users (id) ON DELETE CASCADE,
    phone_number      varchar(20) NOT NULL,
    code_hash         varchar(100),
    code_expires_at   timestamp(6),
    code_last_sent_at timestamp(6),
    code_attempts     integer NOT NULL DEFAULT 0,
    code_resend_count integer NOT NULL DEFAULT 0,
    created_at        timestamp(6) NOT NULL DEFAULT now(),

    CONSTRAINT ck_phone_verifications_e164
        CHECK (phone_number ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_phone_verifications_counters_non_negative
        CHECK (code_attempts >= 0 AND code_resend_count >= 0)
);

-- B1. a number that was never verified (or is not in international
--     format) is not stored on the account; if the client was in the
--     middle of verifying it, keep that attempt as a verification row.
INSERT INTO phone_verifications (
        client_user_id, phone_number, code_hash, code_expires_at,
        code_last_sent_at, code_attempts, code_resend_count)
SELECT c.id,
       COALESCE(c.pending_phone_number,
                CASE WHEN NOT c.phone_verified THEN c.phone_number END),
       c.phone_verification_code_hash,
       c.phone_verification_code_expiry,
       c.phone_verification_last_sent_at,
       c.phone_verification_attempts,
       c.phone_verification_resend_count
  FROM client_users c
 WHERE COALESCE(c.pending_phone_number,
                CASE WHEN NOT c.phone_verified THEN c.phone_number END) IS NOT NULL
   AND COALESCE(c.pending_phone_number,
                CASE WHEN NOT c.phone_verified THEN c.phone_number END)
       ~ '^\+[1-9][0-9]{7,14}$';

UPDATE client_users
   SET phone_number = NULL
 WHERE phone_number IS NOT NULL
   AND (NOT phone_verified OR phone_number !~ '^\+[1-9][0-9]{7,14}$');

ALTER TABLE client_users
    DROP COLUMN pending_phone_number,
    DROP COLUMN phone_verified,
    DROP COLUMN phone_verification_code_hash,
    DROP COLUMN phone_verification_code_expiry,
    DROP COLUMN phone_verification_last_sent_at,
    DROP COLUMN phone_verification_attempts,
    DROP COLUMN phone_verification_resend_count;

-- only a verified, internationally formatted number can be stored
ALTER TABLE client_users
    ADD CONSTRAINT ck_client_users_phone_e164
        CHECK (phone_number IS NULL OR phone_number ~ '^\+[1-9][0-9]{7,14}$');
