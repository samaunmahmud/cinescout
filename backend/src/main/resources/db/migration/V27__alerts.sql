-- In-app alerts, one row per person: the weather watch (rain or wind over a project's thresholds on a shoot day at a
-- confirmed venue, with the scene's cover sets) and follow-up nudges (an outreach email waiting on a reply). A
-- dedupe key per person makes each alert once however often the jobs run. Projects gain the weather thresholds.
ALTER TABLE projects
    ADD COLUMN rain_alert_percent INTEGER NOT NULL DEFAULT 60 CHECK (rain_alert_percent BETWEEN 1 AND 100),
    ADD COLUMN wind_alert_kmh     INTEGER NOT NULL DEFAULT 40 CHECK (wind_alert_kmh BETWEEN 5 AND 200);

CREATE TABLE alerts (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id  UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    kind        TEXT        NOT NULL CHECK (kind IN ('WEATHER', 'FOLLOW_UP')),
    scene_id    UUID        REFERENCES scenes (id) ON DELETE CASCADE,
    location_id UUID        REFERENCES locations (id) ON DELETE CASCADE,
    draft_id    UUID        REFERENCES outreach_drafts (id) ON DELETE CASCADE,
    payload     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    dedupe_key  TEXT        NOT NULL CHECK (char_length(dedupe_key) <= 200),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_alerts_dedupe UNIQUE (user_id, dedupe_key)
);

CREATE INDEX idx_alerts_user ON alerts (user_id, created_at DESC, id DESC);
CREATE INDEX idx_alerts_user_unread ON alerts (user_id) WHERE read_at IS NULL;

CREATE TRIGGER trg_alerts_updated_at BEFORE UPDATE ON alerts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
