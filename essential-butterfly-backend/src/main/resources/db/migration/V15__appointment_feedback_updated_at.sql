-- =====================================================================
-- V15  UC-11 follow-up: remember when the feedback was last changed
--
-- The client sees "updated on ..." next to the feedback. It is kept apart
-- from updated_at (which also moves when only the private notes change) so
-- editing a private note never makes the feedback look edited.
-- =====================================================================

ALTER TABLE appointment_feedback
    ADD COLUMN feedback_updated_at timestamp(6);

UPDATE appointment_feedback
   SET feedback_updated_at = updated_at
 WHERE feedback IS NOT NULL;

ALTER TABLE appointment_feedback
    ADD CONSTRAINT ck_appointment_feedback_updated_at
        CHECK ((feedback IS NULL) = (feedback_updated_at IS NULL));
