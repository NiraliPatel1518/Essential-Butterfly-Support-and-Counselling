-- =====================================================================
-- V10  A verified phone number can belong to only one client account
--
-- client_users.phone_number holds only verified numbers (see V3). This
-- makes the rule "one number, one account" a database guarantee, so it
-- holds even if two requests race each other.
--
-- If older data already has the same verified number on several accounts,
-- the account that has had it longest (lowest id) keeps it and the others
-- have it cleared, so they can verify a number of their own. Nothing else
-- on those accounts is touched.
-- =====================================================================

DO $dedupe$
DECLARE
    cleared integer;
BEGIN
    UPDATE client_users c
       SET phone_number = NULL
     WHERE c.phone_number IS NOT NULL
       AND EXISTS (SELECT 1
                     FROM client_users other
                    WHERE other.phone_number = c.phone_number
                      AND other.id < c.id);

    GET DIAGNOSTICS cleared = ROW_COUNT;

    IF cleared > 0 THEN
        RAISE NOTICE 'V10: cleared a duplicate phone number from % account(s).', cleared;
    END IF;
END
$dedupe$;

CREATE UNIQUE INDEX ux_client_users_phone_number
    ON client_users (phone_number)
 WHERE phone_number IS NOT NULL;