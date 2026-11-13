--
-- PostgreSQL database dump
--


-- Dumped from database version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: fn_account_email_link_subscriber(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_account_email_link_subscriber() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_append_only(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_append_only() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is not allowed.', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END
$$;


--
-- Name: fn_audit(text, text, bigint, jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_audit(p_action text, p_type text, p_id bigint, p_details jsonb) RETURNS void
    LANGUAGE sql
    AS $$
    INSERT INTO audit_log (action, entity_type, entity_id, actor, details)
    VALUES (p_action, p_type, p_id,
            NULLIF(current_setting('app.actor', true), ''),
            COALESCE(p_details, '{}'::jsonb));
$$;


--
-- Name: fn_audit_account_emails(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_audit_account_emails() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_audit_admin_users(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_audit_admin_users() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_audit_campaign(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_audit_campaign() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        PERFORM fn_audit('NEWSLETTER_STATUS_CHANGED', 'newsletter_campaigns', NEW.id,
                         jsonb_build_object('from', OLD.status, 'to', NEW.status));
    END IF;
    RETURN NULL;
END
$$;


--
-- Name: fn_audit_client_users(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_audit_client_users() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_audit_feature_visibility(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_audit_feature_visibility() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.enabled IS DISTINCT FROM OLD.enabled THEN
        PERFORM fn_audit('FEATURE_TOGGLED', 'public_feature_visibility', NEW.id,
                         jsonb_build_object('feature_key', NEW.feature_key,
                                            'enabled', NEW.enabled));
    END IF;
    RETURN NULL;
END
$$;


--
-- Name: fn_audit_intake(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_audit_intake() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        PERFORM fn_audit('INTAKE_STATUS_CHANGED', 'intake_submissions', NEW.id,
                         jsonb_build_object('from', OLD.status, 'to', NEW.status));
    END IF;
    RETURN NULL;
END
$$;


--
-- Name: fn_audit_password_reset(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_audit_password_reset() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_audit_testimonial(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_audit_testimonial() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_contact_message_protect_original(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_contact_message_protect_original() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_intake_fill_submitter(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_intake_fill_submitter() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.client_user_id IS NOT NULL THEN
        SELECT COALESCE(NEW.submitted_by_name,  c.full_name),
               COALESCE(NEW.submitted_by_email, e.email),
               COALESCE(NEW.submitted_by_phone, c.phone_number)
          INTO NEW.submitted_by_name, NEW.submitted_by_email, NEW.submitted_by_phone
          FROM client_users c
          LEFT JOIN account_emails e
                 ON e.client_user_id = c.id AND e.is_primary
         WHERE c.id = NEW.client_user_id;
    END IF;
    RETURN NEW;
END
$$;


--
-- Name: fn_intake_protect_original(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_intake_protect_original() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.client_full_name         IS DISTINCT FROM OLD.client_full_name
    OR NEW.parent_guardian_name     IS DISTINCT FROM OLD.parent_guardian_name
    OR NEW.contact_info             IS DISTINCT FROM OLD.contact_info
    OR NEW.preferred_contact_method IS DISTINCT FROM OLD.preferred_contact_method
    OR NEW.overview                 IS DISTINCT FROM OLD.overview
    OR NEW.support_needs            IS DISTINCT FROM OLD.support_needs
    OR NEW.respite_goals            IS DISTINCT FROM OLD.respite_goals
    OR NEW.subject_focus            IS DISTINCT FROM OLD.subject_focus
    OR NEW.additional_notes         IS DISTINCT FROM OLD.additional_notes
    OR NEW.submitted_by_name        IS DISTINCT FROM OLD.submitted_by_name
    OR NEW.submitted_by_email       IS DISTINCT FROM OLD.submitted_by_email
    OR NEW.submitted_by_phone       IS DISTINCT FROM OLD.submitted_by_phone
    OR NEW.created_at               IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'An intake submission cannot be edited after it has been submitted (id %).', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    -- a decision is final
    IF OLD.status IN ('ACCEPTED', 'REJECTED') AND NEW.status IS DISTINCT FROM OLD.status THEN
        RAISE EXCEPTION 'Intake submission % has already been decided (%).', OLD.id, OLD.status
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NEW;
END
$$;


--
-- Name: fn_newsletter_log_subscription(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_newsletter_log_subscription() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_password_reset_token_protect(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_password_reset_token_protect() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.token_hash     IS DISTINCT FROM OLD.token_hash
    OR NEW.admin_user_id  IS DISTINCT FROM OLD.admin_user_id
    OR NEW.client_user_id IS DISTINCT FROM OLD.client_user_id
    OR NEW.expires_at     IS DISTINCT FROM OLD.expires_at
    OR NEW.created_at     IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'A password reset token can only be marked as used.'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    IF OLD.used_at IS NOT NULL AND NEW.used_at IS DISTINCT FROM OLD.used_at THEN
        RAISE EXCEPTION 'A used password reset token cannot be changed.'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NEW;
END
$$;


--
-- Name: fn_subscriber_link_client(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_subscriber_link_client() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_testimonial_fill_submitter(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_testimonial_fill_submitter() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_testimonial_limit_featured(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_testimonial_limit_featured() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: fn_testimonial_protect_original(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_testimonial_protect_original() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: account_emails; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.account_emails (
    id bigint NOT NULL,
    admin_user_id bigint,
    client_user_id bigint,
    email character varying(254) NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    created_at timestamp(6) without time zone DEFAULT now() NOT NULL,
    verified_at timestamp(6) without time zone,
    code_hash character varying(100),
    code_expires_at timestamp(6) without time zone,
    code_last_sent_at timestamp(6) without time zone,
    code_attempts integer DEFAULT 0 NOT NULL,
    code_resend_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_account_emails_admin_primary_verified CHECK (((admin_user_id IS NULL) OR (NOT is_primary) OR (verified_at IS NOT NULL))),
    CONSTRAINT ck_account_emails_canonical CHECK ((((email)::text = lower(btrim((email)::text))) AND (POSITION(('@'::text) IN (email)) > 1) AND ((email)::text !~ '\s'::text))),
    CONSTRAINT ck_account_emails_counters_non_negative CHECK (((code_attempts >= 0) AND (code_resend_count >= 0))),
    CONSTRAINT ck_account_emails_pending_unverified CHECK ((is_primary OR (verified_at IS NULL))),
    CONSTRAINT ck_account_emails_single_owner CHECK (((((admin_user_id IS NOT NULL))::integer + ((client_user_id IS NOT NULL))::integer) = 1)),
    CONSTRAINT ck_account_emails_verified_has_no_code CHECK (((verified_at IS NULL) OR (code_hash IS NULL)))
);


--
-- Name: account_emails_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.account_emails ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.account_emails_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: admin_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_users (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    failed_login_attempts integer DEFAULT 0 NOT NULL,
    full_name character varying(255) NOT NULL,
    locked_until timestamp(6) without time zone,
    password character varying(255) NOT NULL,
    login_challenge_token_hash character varying(100),
    login_otp_attempts integer DEFAULT 0 NOT NULL,
    login_otp_expiry timestamp(6) without time zone,
    login_otp_hash character varying(100),
    login_otp_last_sent_at timestamp(6) without time zone,
    login_otp_resend_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_admin_users_failed_attempts_non_negative CHECK ((failed_login_attempts >= 0)),
    CONSTRAINT ck_admin_users_full_name_not_blank CHECK ((btrim((full_name)::text) <> ''::text)),
    CONSTRAINT ck_admin_users_login_challenge_complete CHECK ((((login_challenge_token_hash IS NULL) AND (login_otp_hash IS NULL) AND (login_otp_expiry IS NULL)) OR ((login_challenge_token_hash IS NOT NULL) AND (login_otp_hash IS NOT NULL) AND (login_otp_expiry IS NOT NULL)))),
    CONSTRAINT ck_admin_users_login_otp_counters_non_negative CHECK (((login_otp_attempts >= 0) AND (login_otp_resend_count >= 0)))
);


--
-- Name: admin_users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.admin_users ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.admin_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    id bigint NOT NULL,
    occurred_at timestamp(6) without time zone DEFAULT now() NOT NULL,
    action character varying(60) NOT NULL,
    entity_type character varying(60) NOT NULL,
    entity_id bigint,
    actor character varying(80),
    details jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT ck_audit_log_action_not_blank CHECK ((btrim((action)::text) <> ''::text)),
    CONSTRAINT ck_audit_log_details_object CHECK ((jsonb_typeof(details) = 'object'::text))
);


--
-- Name: audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.audit_log ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: client_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.client_users (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    full_name character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    phone_number character varying(20),
    CONSTRAINT ck_client_users_full_name_not_blank CHECK ((btrim((full_name)::text) <> ''::text)),
    CONSTRAINT ck_client_users_phone_e164 CHECK (((phone_number IS NULL) OR ((phone_number)::text ~ '^\+[1-9][0-9]{7,14}$'::text)))
);


--
-- Name: client_users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.client_users ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.client_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: contact_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contact_messages (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    email character varying(255) NOT NULL,
    full_name character varying(255) NOT NULL,
    message text NOT NULL,
    status character varying(255) NOT NULL,
    subject character varying(255) NOT NULL,
    client_user_id bigint,
    CONSTRAINT ck_contact_messages_body_not_blank CHECK ((btrim(message) <> ''::text)),
    CONSTRAINT ck_contact_messages_sender_email CHECK ((((email)::text = lower(btrim((email)::text))) AND (POSITION(('@'::text) IN (email)) > 1))),
    CONSTRAINT ck_contact_messages_sender_name_not_blank CHECK ((btrim((full_name)::text) <> ''::text)),
    CONSTRAINT ck_contact_messages_status CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'VIEWED'::character varying])::text[]))),
    CONSTRAINT ck_contact_messages_subject_not_blank CHECK ((btrim((subject)::text) <> ''::text))
);


--
-- Name: contact_messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.contact_messages ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.contact_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: customer_testimonials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_testimonials (
    id bigint NOT NULL,
    content character varying(5000) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    display_name character varying(80) NOT NULL,
    display_on_public boolean NOT NULL,
    featured_on_home boolean NOT NULL,
    publication_consent boolean NOT NULL,
    reviewed_at timestamp(6) without time zone,
    status character varying(20) NOT NULL,
    client_user_id bigint,
    submitted_by_email character varying(254),
    CONSTRAINT ck_testimonials_alias_not_blank CHECK ((btrim((display_name)::text) <> ''::text)),
    CONSTRAINT ck_testimonials_content_not_blank CHECK ((btrim((content)::text) <> ''::text)),
    CONSTRAINT ck_testimonials_featured_requires_public CHECK (((NOT featured_on_home) OR display_on_public)),
    CONSTRAINT ck_testimonials_public_requires_approval CHECK (((NOT display_on_public) OR (((status)::text = 'APPROVED'::text) AND publication_consent))),
    CONSTRAINT ck_testimonials_reviewed_when_decided CHECK ((((status)::text = 'PENDING'::text) OR (reviewed_at IS NOT NULL))),
    CONSTRAINT ck_testimonials_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'REMOVED'::character varying])::text[])))
);


--
-- Name: customer_testimonials_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.customer_testimonials ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.customer_testimonials_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: intake_submissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intake_submissions (
    id bigint NOT NULL,
    additional_notes text,
    client_full_name character varying(255) NOT NULL,
    contact_info character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    overview text NOT NULL,
    parent_guardian_name character varying(255),
    preferred_contact_method character varying(255) NOT NULL,
    rejection_reason text,
    respite_goals text,
    status character varying(255) NOT NULL,
    subject_focus text,
    support_needs text NOT NULL,
    client_user_id bigint,
    submitted_by_name character varying(255) NOT NULL,
    submitted_by_email character varying(254) NOT NULL,
    submitted_by_phone character varying(20),
    CONSTRAINT ck_intake_client_full_name_not_blank CHECK ((btrim((client_full_name)::text) <> ''::text)),
    CONSTRAINT ck_intake_contact_info_not_blank CHECK ((btrim((contact_info)::text) <> ''::text)),
    CONSTRAINT ck_intake_contact_method_not_blank CHECK ((btrim((preferred_contact_method)::text) <> ''::text)),
    CONSTRAINT ck_intake_overview_not_blank CHECK ((btrim(overview) <> ''::text)),
    CONSTRAINT ck_intake_parent_guardian_name_not_blank CHECK (((parent_guardian_name IS NULL) OR (btrim((parent_guardian_name)::text) <> ''::text))),
    CONSTRAINT ck_intake_rejection_reason CHECK ((((status)::text = 'REJECTED'::text) = ((rejection_reason IS NOT NULL) AND (btrim(rejection_reason) <> ''::text)))),
    CONSTRAINT ck_intake_status CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'IN_PROGRESS'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_intake_support_needs_not_blank CHECK ((btrim(support_needs) <> ''::text))
);


--
-- Name: intake_submissions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.intake_submissions ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.intake_submissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: newsletter_campaigns; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.newsletter_campaigns (
    id bigint NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    sent_at timestamp(6) without time zone,
    status character varying(30) NOT NULL,
    subject character varying(200) NOT NULL,
    CONSTRAINT ck_newsletter_campaigns_content_not_blank CHECK ((btrim(content) <> ''::text)),
    CONSTRAINT ck_newsletter_campaigns_sent_at CHECK ((((status)::text = 'DRAFT'::text) = (sent_at IS NULL))),
    CONSTRAINT ck_newsletter_campaigns_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SENT'::character varying, 'PARTIAL'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_newsletter_campaigns_subject_not_blank CHECK ((btrim((subject)::text) <> ''::text))
);


--
-- Name: newsletter_deliveries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.newsletter_deliveries (
    id bigint NOT NULL,
    error_message character varying(1000),
    sent_at timestamp(6) without time zone,
    status character varying(20) NOT NULL,
    campaign_id bigint NOT NULL,
    subscriber_id bigint NOT NULL,
    CONSTRAINT ck_newsletter_deliveries_error_only_when_failed CHECK (((error_message IS NULL) OR ((status)::text = 'FAILED'::text))),
    CONSTRAINT ck_newsletter_deliveries_sent_at CHECK ((((status)::text <> 'SENT'::text) OR (sent_at IS NOT NULL))),
    CONSTRAINT ck_newsletter_deliveries_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: newsletter_campaign_stats; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.newsletter_campaign_stats AS
 SELECT c.id AS campaign_id,
    count(d.id) AS recipient_count,
    count(d.id) FILTER (WHERE ((d.status)::text = 'SENT'::text)) AS success_count,
    count(d.id) FILTER (WHERE ((d.status)::text = 'FAILED'::text)) AS failed_count,
    count(d.id) FILTER (WHERE ((d.status)::text = 'PENDING'::text)) AS pending_count
   FROM (public.newsletter_campaigns c
     LEFT JOIN public.newsletter_deliveries d ON ((d.campaign_id = c.id)))
  GROUP BY c.id;


--
-- Name: newsletter_campaigns_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.newsletter_campaigns ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.newsletter_campaigns_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: newsletter_deliveries_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.newsletter_deliveries ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.newsletter_deliveries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: newsletter_subscribers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.newsletter_subscribers (
    id bigint NOT NULL,
    active boolean NOT NULL,
    consent_at timestamp(6) without time zone,
    consent_given boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    email character varying(254) NOT NULL,
    unsubscribe_token character varying(36) NOT NULL,
    unsubscribed_at timestamp(6) without time zone,
    client_user_id bigint,
    CONSTRAINT ck_newsletter_subscribers_active_needs_consent CHECK (((NOT active) OR consent_given)),
    CONSTRAINT ck_newsletter_subscribers_active_state CHECK ((active = (unsubscribed_at IS NULL))),
    CONSTRAINT ck_newsletter_subscribers_consent CHECK ((consent_given = (consent_at IS NOT NULL))),
    CONSTRAINT ck_newsletter_subscribers_email_format CHECK ((((email)::text = lower((email)::text)) AND (POSITION(('@'::text) IN (email)) > 1))),
    CONSTRAINT ck_newsletter_subscribers_token_length CHECK ((char_length((unsubscribe_token)::text) = 36))
);


--
-- Name: newsletter_subscribers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.newsletter_subscribers ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.newsletter_subscribers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: newsletter_subscription_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.newsletter_subscription_events (
    id bigint NOT NULL,
    subscriber_id bigint NOT NULL,
    event_type character varying(20) NOT NULL,
    occurred_at timestamp(6) without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_newsletter_events_type CHECK (((event_type)::text = ANY ((ARRAY['SUBSCRIBED'::character varying, 'UNSUBSCRIBED'::character varying, 'RESUBSCRIBED'::character varying])::text[])))
);


--
-- Name: newsletter_subscription_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.newsletter_subscription_events ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.newsletter_subscription_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    id bigint NOT NULL,
    admin_user_id bigint,
    client_user_id bigint,
    token_hash character varying(64) NOT NULL,
    created_at timestamp(6) without time zone DEFAULT now() NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    used_at timestamp(6) without time zone,
    CONSTRAINT ck_password_reset_tokens_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT ck_password_reset_tokens_hash_format CHECK (((token_hash)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_password_reset_tokens_single_owner CHECK (((((admin_user_id IS NOT NULL))::integer + ((client_user_id IS NOT NULL))::integer) = 1)),
    CONSTRAINT ck_password_reset_tokens_used_after_created CHECK (((used_at IS NULL) OR (used_at >= created_at)))
);


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.password_reset_tokens ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.password_reset_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: phone_verifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.phone_verifications (
    client_user_id bigint NOT NULL,
    phone_number character varying(20) NOT NULL,
    code_hash character varying(100),
    code_expires_at timestamp(6) without time zone,
    code_last_sent_at timestamp(6) without time zone,
    code_attempts integer DEFAULT 0 NOT NULL,
    code_resend_count integer DEFAULT 0 NOT NULL,
    created_at timestamp(6) without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_phone_verifications_counters_non_negative CHECK (((code_attempts >= 0) AND (code_resend_count >= 0))),
    CONSTRAINT ck_phone_verifications_e164 CHECK (((phone_number)::text ~ '^\+[1-9][0-9]{7,14}$'::text))
);


--
-- Name: public_feature_visibility; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.public_feature_visibility (
    id bigint NOT NULL,
    display_order integer NOT NULL,
    enabled boolean NOT NULL,
    feature_key character varying(40) NOT NULL,
    label character varying(100) NOT NULL,
    CONSTRAINT ck_feature_visibility_key_format CHECK (((feature_key)::text ~ '^[A-Z][A-Z0-9_]*$'::text)),
    CONSTRAINT ck_feature_visibility_label_not_blank CHECK ((btrim((label)::text) <> ''::text)),
    CONSTRAINT ck_feature_visibility_order_non_negative CHECK ((display_order >= 0))
);


--
-- Name: public_feature_visibility_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.public_feature_visibility ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.public_feature_visibility_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: testimonial_photos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.testimonial_photos (
    testimonial_id bigint NOT NULL,
    photo_data text NOT NULL,
    photo_order integer NOT NULL,
    CONSTRAINT ck_testimonial_photos_data_not_blank CHECK ((btrim(photo_data) <> ''::text)),
    CONSTRAINT testimonial_photos_photo_order_check CHECK ((photo_order >= 0))
);


--
-- Name: website_update_photos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.website_update_photos (
    update_id bigint NOT NULL,
    photo_data text NOT NULL,
    photo_order integer NOT NULL,
    CONSTRAINT ck_website_update_photos_data_not_blank CHECK ((btrim(photo_data) <> ''::text)),
    CONSTRAINT website_update_photos_photo_order_check CHECK ((photo_order >= 0))
);


--
-- Name: website_update_social_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.website_update_social_links (
    update_id bigint NOT NULL,
    social_link character varying(1000) NOT NULL,
    link_order integer NOT NULL,
    CONSTRAINT ck_website_update_social_links_not_blank CHECK ((btrim((social_link)::text) <> ''::text)),
    CONSTRAINT website_update_social_links_link_order_check CHECK ((link_order >= 0))
);


--
-- Name: website_updates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.website_updates (
    id bigint NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    published boolean NOT NULL,
    title character varying(180) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_website_updates_content_not_blank CHECK ((btrim(content) <> ''::text)),
    CONSTRAINT ck_website_updates_timestamps CHECK ((updated_at >= created_at)),
    CONSTRAINT ck_website_updates_title_not_blank CHECK ((btrim((title)::text) <> ''::text))
);


--
-- Name: website_updates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.website_updates ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.website_updates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: account_emails account_emails_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account_emails
    ADD CONSTRAINT account_emails_pkey PRIMARY KEY (id);


--
-- Name: admin_users admin_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT admin_users_pkey PRIMARY KEY (id);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: client_users client_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.client_users
    ADD CONSTRAINT client_users_pkey PRIMARY KEY (id);


--
-- Name: contact_messages contact_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_messages
    ADD CONSTRAINT contact_messages_pkey PRIMARY KEY (id);


--
-- Name: customer_testimonials customer_testimonials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_testimonials
    ADD CONSTRAINT customer_testimonials_pkey PRIMARY KEY (id);


--
-- Name: intake_submissions intake_submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_submissions
    ADD CONSTRAINT intake_submissions_pkey PRIMARY KEY (id);


--
-- Name: newsletter_campaigns newsletter_campaigns_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_campaigns
    ADD CONSTRAINT newsletter_campaigns_pkey PRIMARY KEY (id);


--
-- Name: newsletter_deliveries newsletter_deliveries_campaign_id_subscriber_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_deliveries
    ADD CONSTRAINT newsletter_deliveries_campaign_id_subscriber_id_key UNIQUE (campaign_id, subscriber_id);


--
-- Name: newsletter_deliveries newsletter_deliveries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_deliveries
    ADD CONSTRAINT newsletter_deliveries_pkey PRIMARY KEY (id);


--
-- Name: newsletter_subscribers newsletter_subscribers_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_subscribers
    ADD CONSTRAINT newsletter_subscribers_email_key UNIQUE (email);


--
-- Name: newsletter_subscribers newsletter_subscribers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_subscribers
    ADD CONSTRAINT newsletter_subscribers_pkey PRIMARY KEY (id);


--
-- Name: newsletter_subscribers newsletter_subscribers_unsubscribe_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_subscribers
    ADD CONSTRAINT newsletter_subscribers_unsubscribe_token_key UNIQUE (unsubscribe_token);


--
-- Name: newsletter_subscription_events newsletter_subscription_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_subscription_events
    ADD CONSTRAINT newsletter_subscription_events_pkey PRIMARY KEY (id);


--
-- Name: phone_verifications phone_verifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.phone_verifications
    ADD CONSTRAINT phone_verifications_pkey PRIMARY KEY (client_user_id);


--
-- Name: password_reset_tokens pk_password_reset_tokens; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id);


--
-- Name: public_feature_visibility public_feature_visibility_feature_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.public_feature_visibility
    ADD CONSTRAINT public_feature_visibility_feature_key_key UNIQUE (feature_key);


--
-- Name: public_feature_visibility public_feature_visibility_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.public_feature_visibility
    ADD CONSTRAINT public_feature_visibility_pkey PRIMARY KEY (id);


--
-- Name: testimonial_photos testimonial_photos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.testimonial_photos
    ADD CONSTRAINT testimonial_photos_pkey PRIMARY KEY (testimonial_id, photo_order);


--
-- Name: account_emails uq_account_emails_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account_emails
    ADD CONSTRAINT uq_account_emails_email UNIQUE (email);


--
-- Name: password_reset_tokens uq_password_reset_tokens_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash);


--
-- Name: website_update_photos website_update_photos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_update_photos
    ADD CONSTRAINT website_update_photos_pkey PRIMARY KEY (update_id, photo_order);


--
-- Name: website_update_social_links website_update_social_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_update_social_links
    ADD CONSTRAINT website_update_social_links_pkey PRIMARY KEY (update_id, link_order);


--
-- Name: website_updates website_updates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_updates
    ADD CONSTRAINT website_updates_pkey PRIMARY KEY (id);


--
-- Name: ix_account_emails_admin_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_account_emails_admin_user ON public.account_emails USING btree (admin_user_id) WHERE (admin_user_id IS NOT NULL);


--
-- Name: ix_account_emails_client_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_account_emails_client_user ON public.account_emails USING btree (client_user_id) WHERE (client_user_id IS NOT NULL);


--
-- Name: ix_audit_log_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_log_action ON public.audit_log USING btree (action, occurred_at DESC);


--
-- Name: ix_audit_log_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_log_entity ON public.audit_log USING btree (entity_type, entity_id, occurred_at DESC);


--
-- Name: ix_audit_log_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_log_occurred ON public.audit_log USING btree (occurred_at DESC);


--
-- Name: ix_contact_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_contact_client ON public.contact_messages USING btree (client_user_id) WHERE (client_user_id IS NOT NULL);


--
-- Name: ix_contact_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_contact_created ON public.contact_messages USING btree (created_at DESC);


--
-- Name: ix_contact_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_contact_status_created ON public.contact_messages USING btree (status, created_at DESC);


--
-- Name: ix_intake_client_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_intake_client_created ON public.intake_submissions USING btree (client_user_id, created_at DESC) WHERE (client_user_id IS NOT NULL);


--
-- Name: ix_intake_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_intake_status_created ON public.intake_submissions USING btree (status, created_at DESC);


--
-- Name: ix_newsletter_deliveries_campaign_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_newsletter_deliveries_campaign_status ON public.newsletter_deliveries USING btree (campaign_id, status);


--
-- Name: ix_newsletter_deliveries_subscriber; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_newsletter_deliveries_subscriber ON public.newsletter_deliveries USING btree (subscriber_id);


--
-- Name: ix_newsletter_events_subscriber; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_newsletter_events_subscriber ON public.newsletter_subscription_events USING btree (subscriber_id, occurred_at);


--
-- Name: ix_newsletter_subscribers_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_newsletter_subscribers_active ON public.newsletter_subscribers USING btree (created_at DESC) WHERE active;


--
-- Name: ix_password_reset_tokens_admin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_password_reset_tokens_admin ON public.password_reset_tokens USING btree (admin_user_id) WHERE (admin_user_id IS NOT NULL);


--
-- Name: ix_password_reset_tokens_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_password_reset_tokens_client ON public.password_reset_tokens USING btree (client_user_id) WHERE (client_user_id IS NOT NULL);


--
-- Name: ix_password_reset_tokens_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_password_reset_tokens_expires ON public.password_reset_tokens USING btree (expires_at);


--
-- Name: ix_testimonials_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_testimonials_client ON public.customer_testimonials USING btree (client_user_id) WHERE (client_user_id IS NOT NULL);


--
-- Name: ix_testimonials_featured; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_testimonials_featured ON public.customer_testimonials USING btree (reviewed_at DESC) WHERE (((status)::text = 'APPROVED'::text) AND featured_on_home);


--
-- Name: ix_testimonials_public; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_testimonials_public ON public.customer_testimonials USING btree (reviewed_at DESC) WHERE (((status)::text = 'APPROVED'::text) AND display_on_public);


--
-- Name: ix_testimonials_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_testimonials_status_created ON public.customer_testimonials USING btree (status, created_at);


--
-- Name: ix_website_updates_published; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_website_updates_published ON public.website_updates USING btree (updated_at DESC) WHERE published;


--
-- Name: ix_website_updates_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_website_updates_updated ON public.website_updates USING btree (updated_at DESC);


--
-- Name: uq_account_emails_pending_per_admin; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_account_emails_pending_per_admin ON public.account_emails USING btree (admin_user_id) WHERE ((NOT is_primary) AND (admin_user_id IS NOT NULL));


--
-- Name: uq_account_emails_pending_per_client; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_account_emails_pending_per_client ON public.account_emails USING btree (client_user_id) WHERE ((NOT is_primary) AND (client_user_id IS NOT NULL));


--
-- Name: uq_account_emails_primary_per_admin; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_account_emails_primary_per_admin ON public.account_emails USING btree (admin_user_id) WHERE (is_primary AND (admin_user_id IS NOT NULL));


--
-- Name: uq_account_emails_primary_per_client; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_account_emails_primary_per_client ON public.account_emails USING btree (client_user_id) WHERE (is_primary AND (client_user_id IS NOT NULL));


--
-- Name: uq_newsletter_subscribers_client_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_newsletter_subscribers_client_user ON public.newsletter_subscribers USING btree (client_user_id) WHERE (client_user_id IS NOT NULL);


--
-- Name: uq_password_reset_tokens_active_admin; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_password_reset_tokens_active_admin ON public.password_reset_tokens USING btree (admin_user_id) WHERE ((used_at IS NULL) AND (admin_user_id IS NOT NULL));


--
-- Name: uq_password_reset_tokens_active_client; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_password_reset_tokens_active_client ON public.password_reset_tokens USING btree (client_user_id) WHERE ((used_at IS NULL) AND (client_user_id IS NOT NULL));


--
-- Name: account_emails trg_account_email_link_subscriber; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_account_email_link_subscriber AFTER INSERT OR UPDATE OF verified_at, is_primary ON public.account_emails FOR EACH ROW EXECUTE FUNCTION public.fn_account_email_link_subscriber();


--
-- Name: account_emails trg_audit_account_emails; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_account_emails AFTER INSERT OR UPDATE ON public.account_emails FOR EACH ROW EXECUTE FUNCTION public.fn_audit_account_emails();


--
-- Name: admin_users trg_audit_admin_users; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_admin_users AFTER UPDATE ON public.admin_users FOR EACH ROW EXECUTE FUNCTION public.fn_audit_admin_users();


--
-- Name: newsletter_campaigns trg_audit_campaign; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_campaign AFTER UPDATE ON public.newsletter_campaigns FOR EACH ROW EXECUTE FUNCTION public.fn_audit_campaign();


--
-- Name: client_users trg_audit_client_users; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_client_users AFTER UPDATE ON public.client_users FOR EACH ROW EXECUTE FUNCTION public.fn_audit_client_users();


--
-- Name: public_feature_visibility trg_audit_feature_visibility; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_feature_visibility AFTER UPDATE ON public.public_feature_visibility FOR EACH ROW EXECUTE FUNCTION public.fn_audit_feature_visibility();


--
-- Name: intake_submissions trg_audit_intake; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_intake AFTER UPDATE ON public.intake_submissions FOR EACH ROW EXECUTE FUNCTION public.fn_audit_intake();


--
-- Name: audit_log trg_audit_log_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_log_append_only BEFORE DELETE OR UPDATE ON public.audit_log FOR EACH ROW EXECUTE FUNCTION public.fn_append_only();


--
-- Name: password_reset_tokens trg_audit_password_reset; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_password_reset AFTER INSERT OR UPDATE ON public.password_reset_tokens FOR EACH ROW EXECUTE FUNCTION public.fn_audit_password_reset();


--
-- Name: customer_testimonials trg_audit_testimonial; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_testimonial AFTER UPDATE ON public.customer_testimonials FOR EACH ROW EXECUTE FUNCTION public.fn_audit_testimonial();


--
-- Name: contact_messages trg_contact_message_protect_original; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_contact_message_protect_original BEFORE UPDATE ON public.contact_messages FOR EACH ROW EXECUTE FUNCTION public.fn_contact_message_protect_original();


--
-- Name: intake_submissions trg_intake_fill_submitter; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_intake_fill_submitter BEFORE INSERT ON public.intake_submissions FOR EACH ROW EXECUTE FUNCTION public.fn_intake_fill_submitter();


--
-- Name: intake_submissions trg_intake_protect_original; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_intake_protect_original BEFORE UPDATE ON public.intake_submissions FOR EACH ROW EXECUTE FUNCTION public.fn_intake_protect_original();


--
-- Name: newsletter_subscription_events trg_newsletter_events_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_newsletter_events_append_only BEFORE DELETE OR UPDATE ON public.newsletter_subscription_events FOR EACH ROW EXECUTE FUNCTION public.fn_append_only();


--
-- Name: newsletter_subscribers trg_newsletter_log_subscription; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_newsletter_log_subscription AFTER INSERT OR UPDATE OF active ON public.newsletter_subscribers FOR EACH ROW EXECUTE FUNCTION public.fn_newsletter_log_subscription();


--
-- Name: password_reset_tokens trg_password_reset_token_protect; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_password_reset_token_protect BEFORE UPDATE ON public.password_reset_tokens FOR EACH ROW EXECUTE FUNCTION public.fn_password_reset_token_protect();


--
-- Name: newsletter_subscribers trg_subscriber_link_client; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_subscriber_link_client BEFORE INSERT ON public.newsletter_subscribers FOR EACH ROW EXECUTE FUNCTION public.fn_subscriber_link_client();


--
-- Name: customer_testimonials trg_testimonial_fill_submitter; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_testimonial_fill_submitter BEFORE INSERT ON public.customer_testimonials FOR EACH ROW EXECUTE FUNCTION public.fn_testimonial_fill_submitter();


--
-- Name: customer_testimonials trg_testimonial_limit_featured; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_testimonial_limit_featured BEFORE INSERT OR UPDATE OF featured_on_home ON public.customer_testimonials FOR EACH ROW EXECUTE FUNCTION public.fn_testimonial_limit_featured();


--
-- Name: customer_testimonials trg_testimonial_protect_original; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_testimonial_protect_original BEFORE UPDATE ON public.customer_testimonials FOR EACH ROW EXECUTE FUNCTION public.fn_testimonial_protect_original();


--
-- Name: account_emails account_emails_admin_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account_emails
    ADD CONSTRAINT account_emails_admin_user_id_fkey FOREIGN KEY (admin_user_id) REFERENCES public.admin_users(id) ON DELETE CASCADE;


--
-- Name: account_emails account_emails_client_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account_emails
    ADD CONSTRAINT account_emails_client_user_id_fkey FOREIGN KEY (client_user_id) REFERENCES public.client_users(id) ON DELETE CASCADE;


--
-- Name: contact_messages fk_contact_messages_client_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_messages
    ADD CONSTRAINT fk_contact_messages_client_user FOREIGN KEY (client_user_id) REFERENCES public.client_users(id) ON DELETE SET NULL;


--
-- Name: customer_testimonials fk_customer_testimonials_client_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_testimonials
    ADD CONSTRAINT fk_customer_testimonials_client_user FOREIGN KEY (client_user_id) REFERENCES public.client_users(id) ON DELETE SET NULL;


--
-- Name: intake_submissions fk_intake_submissions_client_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_submissions
    ADD CONSTRAINT fk_intake_submissions_client_user FOREIGN KEY (client_user_id) REFERENCES public.client_users(id) ON DELETE SET NULL;


--
-- Name: newsletter_deliveries fk_newsletter_deliveries_campaign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_deliveries
    ADD CONSTRAINT fk_newsletter_deliveries_campaign FOREIGN KEY (campaign_id) REFERENCES public.newsletter_campaigns(id) ON DELETE RESTRICT;


--
-- Name: newsletter_deliveries fk_newsletter_deliveries_subscriber; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_deliveries
    ADD CONSTRAINT fk_newsletter_deliveries_subscriber FOREIGN KEY (subscriber_id) REFERENCES public.newsletter_subscribers(id) ON DELETE RESTRICT;


--
-- Name: newsletter_subscribers fk_newsletter_subscribers_client_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_subscribers
    ADD CONSTRAINT fk_newsletter_subscribers_client_user FOREIGN KEY (client_user_id) REFERENCES public.client_users(id) ON DELETE SET NULL;


--
-- Name: testimonial_photos fk_testimonial_photos_testimonial; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.testimonial_photos
    ADD CONSTRAINT fk_testimonial_photos_testimonial FOREIGN KEY (testimonial_id) REFERENCES public.customer_testimonials(id) ON DELETE CASCADE;


--
-- Name: website_update_photos fk_website_update_photos_update; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_update_photos
    ADD CONSTRAINT fk_website_update_photos_update FOREIGN KEY (update_id) REFERENCES public.website_updates(id) ON DELETE CASCADE;


--
-- Name: website_update_social_links fk_website_update_social_links_update; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.website_update_social_links
    ADD CONSTRAINT fk_website_update_social_links_update FOREIGN KEY (update_id) REFERENCES public.website_updates(id) ON DELETE CASCADE;


--
-- Name: newsletter_subscription_events newsletter_subscription_events_subscriber_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_subscription_events
    ADD CONSTRAINT newsletter_subscription_events_subscriber_id_fkey FOREIGN KEY (subscriber_id) REFERENCES public.newsletter_subscribers(id) ON DELETE RESTRICT;


--
-- Name: password_reset_tokens password_reset_tokens_admin_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_admin_user_id_fkey FOREIGN KEY (admin_user_id) REFERENCES public.admin_users(id) ON DELETE CASCADE;


--
-- Name: password_reset_tokens password_reset_tokens_client_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_client_user_id_fkey FOREIGN KEY (client_user_id) REFERENCES public.client_users(id) ON DELETE CASCADE;


--
-- Name: phone_verifications phone_verifications_client_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.phone_verifications
    ADD CONSTRAINT phone_verifications_client_user_id_fkey FOREIGN KEY (client_user_id) REFERENCES public.client_users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--


