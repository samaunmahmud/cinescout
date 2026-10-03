-- What happened on a project, newest last: an append-only log. actor_name keeps the name as it was (or a guest's
-- typed name), so the line still reads right after an account is renamed or deleted. kind groups verbs for the
-- filter; payload holds the few facts a line needs (venue name, old and new status, counts), never script text or
-- private notes. Rows are never changed (but for forgetting a deleted account's id); they go only with their project.
CREATE TABLE activity (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    actor_id    UUID        REFERENCES users (id) ON DELETE SET NULL,
    actor_name  TEXT,
    kind        TEXT        NOT NULL CHECK (kind IN ('VENUE', 'SCOUTING', 'OUTREACH', 'CREW', 'SCHEDULE', 'COMMENT')),
    verb        TEXT        NOT NULL CHECK (verb IN ('VENUE_STATUS_CHANGED', 'DIRECTOR_CALLED', 'SCOUTED',
                                                      'OUTREACH_STATUS_CHANGED', 'MEMBER_JOINED', 'MEMBER_LEFT',
                                                      'MEMBER_REMOVED', 'ROLE_CHANGED', 'OWNERSHIP_TRANSFERRED',
                                                      'SHOOT_DATES_CHANGED', 'COMMENTED')),
    target_type TEXT        NOT NULL CHECK (target_type IN ('PROJECT', 'SCENE', 'LOCATION', 'OUTREACH_DRAFT', 'MEMBER', 'COMMENT')),
    target_id   UUID,
    payload     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_activity_project ON activity (project_id, created_at DESC, id DESC);
CREATE INDEX idx_activity_project_kind ON activity (project_id, kind, created_at DESC, id DESC);

-- The one change allowed: an actor's account is deleted (ON DELETE SET NULL); actor_name keeps who it was.
CREATE FUNCTION refuse_activity_update() RETURNS trigger AS $$
BEGIN
    IF OLD.actor_id IS NOT NULL AND NEW.actor_id IS NULL
       AND (NEW.id, NEW.project_id, NEW.actor_name, NEW.kind, NEW.verb, NEW.target_type, NEW.target_id, NEW.payload, NEW.created_at)
           IS NOT DISTINCT FROM
           (OLD.id, OLD.project_id, OLD.actor_name, OLD.kind, OLD.verb, OLD.target_type, OLD.target_id, OLD.payload, OLD.created_at) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'activity is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_activity_append_only BEFORE UPDATE ON activity
    FOR EACH ROW EXECUTE FUNCTION refuse_activity_update();
