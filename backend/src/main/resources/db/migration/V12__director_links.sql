-- A link that shows a director (or anyone else without an account) a project's or one scene's shortlisted venues
-- and lets them answer each with Approve, Maybe or No. Like the call sheet link, the token is the whole credential:
-- random, one link per project and one per scene at a time, deleted when sharing stops. show_private also shows
-- the private notes, quotes and why the fit score is what it is.
CREATE TABLE director_links (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    scene_id     UUID        REFERENCES scenes (id) ON DELETE CASCADE,
    token        TEXT        NOT NULL,
    show_private BOOLEAN     NOT NULL DEFAULT false,
    created_by   UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_director_links_token ON director_links (token);
CREATE UNIQUE INDEX uq_director_links_project ON director_links (project_id) WHERE scene_id IS NULL;
CREATE UNIQUE INDEX uq_director_links_scene ON director_links (scene_id) WHERE scene_id IS NOT NULL;

-- A guest's call on a venue, under the name they typed. One per name per venue: answering again replaces it.
-- Kept when the link is withdrawn, as the decision still stands.
CREATE TABLE director_responses (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID        NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    guest_name  TEXT        NOT NULL CHECK (char_length(guest_name) BETWEEN 1 AND 60),
    verdict     TEXT        NOT NULL CHECK (verdict IN ('APPROVE', 'MAYBE', 'NO')),
    comment     TEXT        CHECK (comment IS NULL OR char_length(comment) <= 2000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_director_responses_guest ON director_responses (location_id, lower(guest_name));

CREATE TRIGGER trg_director_responses_updated_at BEFORE UPDATE ON director_responses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
