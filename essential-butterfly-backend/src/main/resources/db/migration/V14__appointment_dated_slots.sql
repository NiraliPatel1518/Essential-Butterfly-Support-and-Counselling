-- =====================================================================
-- V14  UC-11 one-date availability
--
-- An availability row is now EITHER a weekly slot (weekday set, repeats every
-- week) OR a one-date slot (slot_date set, happens once). Exactly one of the
-- two is always filled. Existing weekly rows are unchanged.
-- =====================================================================

ALTER TABLE appointment_availability
    DROP CONSTRAINT uq_appointment_availability_start,
    DROP CONSTRAINT ex_appointment_availability_overlap,
    DROP CONSTRAINT ck_appointment_availability_weekday;

ALTER TABLE appointment_availability
    ALTER COLUMN weekday DROP NOT NULL,
    ADD COLUMN slot_date date;

ALTER TABLE appointment_availability
    ADD CONSTRAINT ck_appointment_availability_weekday
        CHECK (weekday IS NULL OR weekday BETWEEN 1 AND 7),
    ADD CONSTRAINT ck_appointment_availability_kind
        CHECK ((weekday IS NOT NULL AND slot_date IS NULL)
            OR (weekday IS NULL AND slot_date IS NOT NULL));

-- no duplicate start time, and no overlap, among weekly slots
CREATE UNIQUE INDEX ux_appointment_availability_weekly
    ON appointment_availability (weekday, start_time)
    WHERE weekday IS NOT NULL;

ALTER TABLE appointment_availability
    ADD CONSTRAINT ex_appointment_availability_weekly_overlap
        EXCLUDE USING gist (
            int4range(
                weekday * 1440 + (extract(epoch FROM start_time)::int / 60),
                weekday * 1440 + (extract(epoch FROM start_time)::int / 60) + slot_minutes
            ) WITH &&
        ) WHERE (weekday IS NOT NULL);

-- the same for one-date slots
CREATE UNIQUE INDEX ux_appointment_availability_dated
    ON appointment_availability (slot_date, start_time)
    WHERE slot_date IS NOT NULL;

ALTER TABLE appointment_availability
    ADD CONSTRAINT ex_appointment_availability_dated_overlap
        EXCLUDE USING gist (
            tsrange(
                slot_date + start_time,
                slot_date + start_time + slot_minutes * interval '1 minute'
            ) WITH &&
        ) WHERE (slot_date IS NOT NULL);

CREATE OR REPLACE FUNCTION fn_audit_appointment_availability() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM fn_audit('AVAILABILITY_ADDED', 'appointment_availability', NEW.id,
                         jsonb_build_object('weekday', NEW.weekday,
                                            'slot_date', NEW.slot_date,
                                            'start_time', NEW.start_time,
                                            'slot_minutes', NEW.slot_minutes));
    ELSIF TG_OP = 'UPDATE' THEN
        PERFORM fn_audit('AVAILABILITY_CHANGED', 'appointment_availability', NEW.id,
                         jsonb_build_object('weekday', NEW.weekday,
                                            'slot_date', NEW.slot_date,
                                            'start_time', NEW.start_time,
                                            'slot_minutes', NEW.slot_minutes));
    ELSE
        PERFORM fn_audit('AVAILABILITY_REMOVED', 'appointment_availability', OLD.id,
                         jsonb_build_object('weekday', OLD.weekday,
                                            'slot_date', OLD.slot_date,
                                            'start_time', OLD.start_time,
                                            'slot_minutes', OLD.slot_minutes));
    END IF;
    RETURN NULL;
END
$$;
