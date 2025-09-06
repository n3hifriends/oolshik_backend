--
-- PostgreSQL database dump
--

-- Dumped from database version 16.10 (Debian 16.10-1.pgdg13+1)
-- Dumped by pg_dump version 16.10 (Debian 16.10-1.pgdg13+1)

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
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user (
                                 id uuid NOT NULL,
                                 phone_number character varying(32) NOT NULL,
                                 email character varying(255),
                                 password_hash character varying(255),
                                 display_name character varying(255),
                                 roles text NOT NULL,
                                 languages text,
                                 created_at timestamp with time zone DEFAULT now() NOT NULL,
                                 updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: audio_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audio_files (
                                    id uuid NOT NULL,
                                    owner_user_id character varying(100) NOT NULL,
                                    filename character varying(255) NOT NULL,
                                    mime_type character varying(100) NOT NULL,
                                    size_bytes bigint NOT NULL,
                                    storage_key character varying(500) NOT NULL,
                                    created_at timestamp without time zone DEFAULT now() NOT NULL,
                                    duration_ms bigint,
                                    sample_rate integer,
                                    request_id character varying(100)
);

--
-- Name: help_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.help_request (
                                     id uuid NOT NULL,
                                     title character varying(255) NOT NULL,
                                     description text,
                                     latitude double precision NOT NULL,
                                     longitude double precision NOT NULL,
                                     radius_meters integer NOT NULL,
                                     status character varying(32) NOT NULL,
                                     requester_id uuid NOT NULL,
                                     helper_id uuid,
                                     created_at timestamp with time zone DEFAULT now() NOT NULL,
                                     updated_at timestamp with time zone DEFAULT now() NOT NULL,
                                     voice_url character varying(500),
                                     rating_value numeric(2,1),
                                     rated_by_user_id uuid,
                                     rated_at timestamp with time zone,
                                     CONSTRAINT help_request_rating_value_check CHECK (((rating_value >= (0)::numeric) AND (rating_value <= (5)::numeric)))
);


--
-- Name: otp_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.otp_code (
                                 id uuid NOT NULL,
                                 phone_number character varying(32) NOT NULL,
                                 code_hash character varying(255) NOT NULL,
                                 purpose character varying(32) NOT NULL,
                                 expires_at timestamp with time zone NOT NULL,
                                 consumed_at timestamp with time zone,
                                 attempt_count integer DEFAULT 0 NOT NULL,
                                 resend_count integer DEFAULT 0 NOT NULL,
                                 last_sent_at timestamp with time zone DEFAULT now() NOT NULL,
                                 created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: phone_reveal_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.phone_reveal_event (
                                           id uuid NOT NULL,
                                           requester_user_id uuid NOT NULL,
                                           target_user_id uuid NOT NULL,
                                           phone_number character varying(20) NOT NULL,
                                           revealed_at timestamp without time zone DEFAULT now(),
                                           reveal_count integer DEFAULT 0
);


--
-- Name: report_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.report_event (
                                     id uuid DEFAULT gen_random_uuid() NOT NULL,
                                     reporter_user_id uuid NOT NULL,
                                     target_user_id uuid,
                                     help_request_id uuid,
                                     reason text NOT NULL,
                                     details text,
                                     created_at timestamp with time zone DEFAULT now() NOT NULL,
                                     CONSTRAINT report_event_reason_check CHECK ((reason = ANY (ARRAY['SPAM'::text, 'INAPPROPRIATE'::text, 'UNSAFE'::text, 'OTHER'::text])))
);


--
-- Name: app_user app_user_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_email_key UNIQUE (email);


--
-- Name: app_user app_user_phone_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_phone_number_key UNIQUE (phone_number);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: audio_files audio_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audio_files
    ADD CONSTRAINT audio_files_pkey PRIMARY KEY (id);


--
-- Name: help_request help_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.help_request
    ADD CONSTRAINT help_request_pkey PRIMARY KEY (id);


--
-- Name: otp_code otp_code_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.otp_code
    ADD CONSTRAINT otp_code_pkey PRIMARY KEY (id);


--
-- Name: phone_reveal_event phone_reveal_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.phone_reveal_event
    ADD CONSTRAINT phone_reveal_event_pkey PRIMARY KEY (id);


--
-- Name: report_event report_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_event
    ADD CONSTRAINT report_event_pkey PRIMARY KEY (id);


--
-- Name: idx_help_request_geo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_help_request_geo ON public.help_request USING btree (latitude, longitude);


--
-- Name: idx_help_request_rating; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_help_request_rating ON public.help_request USING btree (rating_value);


--
-- Name: idx_help_request_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_help_request_status ON public.help_request USING btree (status);


--
-- Name: idx_hr_created_id_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hr_created_id_desc ON public.help_request USING btree (created_at DESC, id DESC);


--
-- Name: idx_hr_helper_rated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hr_helper_rated ON public.help_request USING btree (helper_id) WHERE (rating_value IS NOT NULL);


--
-- Name: idx_hr_lat_lon; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hr_lat_lon ON public.help_request USING btree (latitude, longitude);


--
-- Name: idx_otp_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_otp_phone ON public.otp_code USING btree (phone_number);


--
-- Name: idx_phone_reveal_event_requester; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_phone_reveal_event_requester ON public.phone_reveal_event USING btree (requester_user_id);


--
-- Name: idx_phone_reveal_event_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_phone_reveal_event_target ON public.phone_reveal_event USING btree (target_user_id);


--
-- Name: idx_report_event_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_event_created_at ON public.report_event USING btree (created_at DESC);


--
-- Name: idx_report_event_help_request_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_event_help_request_id ON public.report_event USING btree (help_request_id);


--
-- Name: idx_report_event_target_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_event_target_user_id ON public.report_event USING btree (target_user_id);


--
-- Name: help_request help_request_helper_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.help_request
    ADD CONSTRAINT help_request_helper_id_fkey FOREIGN KEY (helper_id) REFERENCES public.app_user(id);


--
-- Name: help_request help_request_requester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.help_request
    ADD CONSTRAINT help_request_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.app_user(id);


--
-- Name: report_event report_event_help_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_event
    ADD CONSTRAINT report_event_help_request_id_fkey FOREIGN KEY (help_request_id) REFERENCES public.help_request(id);


--
-- Name: report_event report_event_reporter_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_event
    ADD CONSTRAINT report_event_reporter_user_id_fkey FOREIGN KEY (reporter_user_id) REFERENCES public.app_user(id);


--
-- Name: report_event report_event_target_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_event
    ADD CONSTRAINT report_event_target_user_id_fkey FOREIGN KEY (target_user_id) REFERENCES public.app_user(id);


--
-- PostgreSQL database dump complete
--
