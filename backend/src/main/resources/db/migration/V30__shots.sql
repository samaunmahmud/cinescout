-- A scene's shot list, in shooting order: what each shot is, its size, which way the camera faces (a compass bearing)
-- and when it is planned, so the app can say where the sun will be. A shot may name the venue it is shot at;
-- otherwise the scene's confirmed venue is meant.
CREATE TABLE shots (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    scene_id       UUID        NOT NULL REFERENCES scenes (id) ON DELETE CASCADE,
    location_id    UUID        REFERENCES locations (id) ON DELETE SET NULL,
    position       INTEGER     NOT NULL CHECK (position >= 0),
    description    TEXT        NOT NULL CHECK (char_length(description) BETWEEN 1 AND 500),
    size           TEXT        CHECK (size IN ('WIDE', 'FULL', 'MEDIUM', 'CLOSE_UP', 'EXTREME_CLOSE_UP', 'INSERT', 'AERIAL', 'OTHER')),
    camera_bearing SMALLINT    CHECK (camera_bearing BETWEEN 0 AND 359),
    planned_time   TIME,
    done           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by     UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_shots_scene ON shots (scene_id, position);
CREATE INDEX idx_shots_location ON shots (location_id);

CREATE TRIGGER trg_shots_updated_at BEFORE UPDATE ON shots
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
