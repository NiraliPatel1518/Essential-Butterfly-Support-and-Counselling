-- =====================================================================
-- V17  UC-12 follow-up: the Memory Wall is the fifth feature toggle
--
-- Same table and rules as the other website features. When the toggle is
-- off the public site shows nothing about the Memory Wall (no menu link, no
-- page, no photos); the administrator can still manage the photos.
-- =====================================================================
INSERT INTO public_feature_visibility (feature_key, label, enabled, display_order)
VALUES ('MEMORY_WALL', 'Memory Wall', true, 5)
ON CONFLICT (feature_key) DO NOTHING;
