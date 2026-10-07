-- Cover sets: backup venues for a scene, each one of the scene's own candidates, with a free-text trigger for when
-- to switch to it ("if rain > 60%"). A venue is a cover for a scene at most once; deleting the scene or the venue
-- deletes the cover. The schedule and call sheet list them under the scene, and the weather watch suggests them.
CREATE TABLE scene_covers (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    scene_id     UUID        NOT NULL REFERENCES scenes (id) ON DELETE CASCADE,
    location_id  UUID        NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    trigger_text TEXT        CHECK (trigger_text IS NULL OR char_length(trigger_text) <= 200),
    added_by     UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_scene_covers_venue UNIQUE (scene_id, location_id)
);

CREATE INDEX idx_scene_covers_location ON scene_covers (location_id);

CREATE TRIGGER trg_scene_covers_updated_at BEFORE UPDATE ON scene_covers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE activity DROP CONSTRAINT activity_verb_check;
ALTER TABLE activity ADD CONSTRAINT activity_verb_check CHECK (verb IN ('VENUE_STATUS_CHANGED', 'DIRECTOR_CALLED', 'SCOUTED',
    'OUTREACH_STATUS_CHANGED', 'MEMBER_JOINED', 'MEMBER_LEFT', 'MEMBER_REMOVED', 'ROLE_CHANGED', 'OWNERSHIP_TRANSFERRED',
    'SHOOT_DATES_CHANGED', 'COMMENTED', 'AVAILABILITY_CHANGED', 'COVER_SET_CHANGED'));
