-- =====================================================================
-- V12  Resource Library: several links and an optional photo per resource
--
--   * A resource may now have up to five web links AND/OR one uploaded
--     document or image (the image is shown as the picture beside the
--     resource title). It must have at least a link or a file.
--   * link_url keeps the first link (still unique among live resources);
--     extra_links keeps links two to five, one per line.
--   * The audit trigger also notices a change to the extra links.
--
-- V11 is not edited: it may already be applied, and Flyway checks it.
-- =====================================================================

ALTER TABLE library_resources
    ADD COLUMN extra_links text;

ALTER TABLE library_resources
    DROP CONSTRAINT ck_library_resources_source;

ALTER TABLE library_resources
    ADD CONSTRAINT ck_library_resources_source
    CHECK (
        -- the three file columns are all present or all absent
        ((file_name IS NULL AND file_content_type IS NULL AND file_size IS NULL)
         OR (file_name IS NOT NULL AND file_content_type IS NOT NULL
             AND file_size IS NOT NULL AND file_size > 0))
        -- the type says whether there is a file
        AND ((resource_type = 'FILE') = (file_name IS NOT NULL))
        -- never empty: at least a link or a file
        AND (link_url IS NOT NULL OR file_name IS NOT NULL)
        -- extra links only make sense after a first link
        AND (extra_links IS NULL OR link_url IS NOT NULL)
    );

CREATE OR REPLACE FUNCTION fn_audit_library_resources() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM fn_audit('RESOURCE_CREATED', 'library_resources', NEW.id,
                         jsonb_build_object('title', NEW.title,
                                            'type', NEW.resource_type,
                                            'status', NEW.status));
        RETURN NULL;
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status THEN
        IF NEW.status = 'DELETED' THEN
            PERFORM fn_audit('RESOURCE_REMOVED', 'library_resources', NEW.id,
                             jsonb_build_object('title', NEW.title));
        ELSIF NEW.status = 'PUBLISHED' THEN
            PERFORM fn_audit('RESOURCE_PUBLISHED', 'library_resources', NEW.id,
                             jsonb_build_object('title', NEW.title));
        ELSE
            PERFORM fn_audit('RESOURCE_UNPUBLISHED', 'library_resources', NEW.id,
                             jsonb_build_object('title', NEW.title));
        END IF;
    END IF;

    IF NEW.status <> 'DELETED'
       AND (NEW.title IS DISTINCT FROM OLD.title
            OR NEW.description IS DISTINCT FROM OLD.description
            OR NEW.category IS DISTINCT FROM OLD.category
            OR NEW.link_url IS DISTINCT FROM OLD.link_url
            OR NEW.extra_links IS DISTINCT FROM OLD.extra_links
            OR NEW.file_name IS DISTINCT FROM OLD.file_name
            OR NEW.file_size IS DISTINCT FROM OLD.file_size
            OR NEW.alt_text IS DISTINCT FROM OLD.alt_text) THEN
        PERFORM fn_audit('RESOURCE_UPDATED', 'library_resources', NEW.id,
                         jsonb_build_object('title', NEW.title));
    END IF;

    RETURN NULL;
END
$$;

-- ---------------------------------------------------------------------
-- Page text is now stored as plain-text values for named slots of the
-- page's fixed design (JSON), never as free-form markup. Anything saved
-- before that in the older format is cleared, so the website goes on
-- showing its designed built-in pages until an administrator publishes
-- again.
-- ---------------------------------------------------------------------
UPDATE website_content_pages
   SET published_title = NULL,
       published_body  = NULL,
       published_at    = NULL,
       draft_title     = NULL,
       draft_body      = NULL
 WHERE (published_body IS NOT NULL AND published_body NOT LIKE '{%')
    OR (draft_body IS NOT NULL AND draft_body NOT LIKE '{%');
