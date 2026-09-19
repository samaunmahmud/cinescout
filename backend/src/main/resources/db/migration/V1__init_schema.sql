-- CineScout: initial schema
--
-- Ownership chain:  users -> projects -> scenes -> locations -> outreach_drafts
--
-- Conventions
--   * UUID primary keys (gen_random_uuid() is built in from PostgreSQL 13).
--   * timestamptz everywhere; updated_at is maintained by a trigger.
--   * Enumerations are TEXT + CHECK rather than native ENUM types, so adding a
--     value later is a normal migration instead of ALTER TYPE.
--   * Semi-structured AI output is stored as JSONB next to the typed columns we
--     actually query on, so a prompt/schema change never forces a migration.

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    display_name  TEXT        NOT NULL,
    role          TEXT        NOT NULL DEFAULT 'USER'
                              CHECK (role IN ('USER', 'ADMIN')),
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness without needing the citext extension.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

-- ---------------------------------------------------------------------------
-- projects
-- ---------------------------------------------------------------------------
CREATE TABLE projects (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       TEXT        NOT NULL,
    description TEXT,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_owner ON projects (owner_id, status);

-- ---------------------------------------------------------------------------
-- scenes
-- The user's scene text plus the requirements the Scene Parser (Gemini)
-- extracted from it.
-- ---------------------------------------------------------------------------
CREATE TABLE scenes (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id             UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    scene_number           INTEGER,
    title                  TEXT        NOT NULL,
    source_text            TEXT        NOT NULL,

    -- Target shoot window; drives the solar / weather look-ups in Module B.
    shoot_date_start       DATE,
    shoot_date_end         DATE,

    -- Extracted requirements (all nullable until parsing has run).
    parse_status           TEXT        NOT NULL DEFAULT 'PENDING'
                                       CHECK (parse_status IN ('PENDING', 'PARSED', 'FAILED')),
    setting_type           TEXT,
    visual_mood            TEXT,
    lighting_needs         TEXT,
    time_of_day            TEXT,
    acoustic_sensitivity   TEXT        CHECK (acoustic_sensitivity IN ('LOW', 'MEDIUM', 'HIGH')),
    estimated_crew_size    INTEGER     CHECK (estimated_crew_size >= 0),
    requirements_json      JSONB,      -- full, unmodified parser output
    parsed_at              TIMESTAMPTZ,

    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_scenes_shoot_window
        CHECK (shoot_date_end IS NULL OR shoot_date_start IS NULL OR shoot_date_end >= shoot_date_start),
    CONSTRAINT uq_scenes_project_number UNIQUE (project_id, scene_number)
);

CREATE INDEX idx_scenes_project ON scenes (project_id);

-- ---------------------------------------------------------------------------
-- locations
-- A candidate venue for one scene. Rows are created by the search pipeline
-- (status SUGGESTED) and moved along by the user.
-- ---------------------------------------------------------------------------
CREATE TABLE locations (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    scene_id              UUID          NOT NULL REFERENCES scenes (id) ON DELETE CASCADE,

    name                  TEXT          NOT NULL,
    address               TEXT,
    latitude              NUMERIC(9, 6) CHECK (latitude  BETWEEN -90  AND 90),
    longitude             NUMERIC(9, 6) CHECK (longitude BETWEEN -180 AND 180),

    -- Provenance: where the venue came from, so results stay grounded.
    source_url            TEXT,
    source_provider       TEXT,         -- which search backend produced it
    source_excerpt        TEXT,

    -- Feasibility & risk assessment (Gemini).
    fit_reason            TEXT,
    fit_score             SMALLINT      CHECK (fit_score BETWEEN 0 AND 100),
    booking_friction      TEXT          CHECK (booking_friction IN ('PUBLIC', 'COMMERCIAL', 'PRIVATE')),
    friction_note         TEXT,
    footprint_warnings    JSONB         NOT NULL DEFAULT '[]'::jsonb,

    -- Module B output, cached so we don't re-hit weather / solar APIs on every view.
    logistics_json        JSONB,        -- solar windows, weather, noise risks, nearby services
    logistics_fetched_at  TIMESTAMPTZ,

    -- User workflow.
    status                TEXT          NOT NULL DEFAULT 'SUGGESTED'
                                        CHECK (status IN ('SUGGESTED', 'SHORTLISTED', 'REJECTED',
                                                          'CONTACTED', 'CONFIRMED')),
    notes                 TEXT,

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- The same page must not be saved twice for the same scene.
    CONSTRAINT uq_locations_scene_source UNIQUE (scene_id, source_url)
);

CREATE INDEX idx_locations_scene_status ON locations (scene_id, status);

-- ---------------------------------------------------------------------------
-- outreach_drafts
-- Emails to venue owners. Several drafts per location are allowed (tone
-- variations, follow-ups).
-- ---------------------------------------------------------------------------
CREATE TABLE outreach_drafts (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id       UUID        NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    created_by        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    recipient_name    TEXT,
    recipient_email   TEXT,
    subject           TEXT        NOT NULL,
    body              TEXT        NOT NULL,
    tone              TEXT        NOT NULL DEFAULT 'PROFESSIONAL'
                                  CHECK (tone IN ('PROFESSIONAL', 'FRIENDLY', 'CONCISE')),
    generated_by      TEXT,       -- model id that wrote the first draft

    status            TEXT        NOT NULL DEFAULT 'DRAFT'
                                  CHECK (status IN ('DRAFT', 'SENT', 'REPLIED')),
    sent_at           TIMESTAMPTZ,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outreach_location ON outreach_drafts (location_id);
CREATE INDEX idx_outreach_created_by ON outreach_drafts (created_by);

-- ---------------------------------------------------------------------------
-- updated_at triggers
-- ---------------------------------------------------------------------------
CREATE TRIGGER trg_users_updated_at    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_projects_updated_at BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_scenes_updated_at   BEFORE UPDATE ON scenes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_locations_updated_at BEFORE UPDATE ON locations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_outreach_updated_at BEFORE UPDATE ON outreach_drafts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
