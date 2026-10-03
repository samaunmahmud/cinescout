-- The crew's conversation about a venue, apart from its private notes. A comment is either by a member (author_id)
-- or by a guest through a director link (guest_name, tied to their call by director_response_id and kept in step
-- with it). Replies hang off a top-level comment, one level deep. mentions holds the ids of the members it @mentions.
CREATE TABLE venue_comments (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id          UUID        NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    parent_id            UUID        REFERENCES venue_comments (id) ON DELETE CASCADE,
    author_id            UUID        REFERENCES users (id) ON DELETE SET NULL,
    guest_name           TEXT        CHECK (guest_name IS NULL OR char_length(guest_name) BETWEEN 1 AND 60),
    director_response_id UUID        UNIQUE REFERENCES director_responses (id) ON DELETE CASCADE,
    body                 TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 4000),
    mentions             JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_venue_comments_one_author CHECK (author_id IS NULL OR guest_name IS NULL)
);

CREATE INDEX idx_venue_comments_location ON venue_comments (location_id, created_at) WHERE parent_id IS NULL;
CREATE INDEX idx_venue_comments_parent ON venue_comments (parent_id) WHERE parent_id IS NOT NULL;

CREATE TRIGGER trg_venue_comments_updated_at BEFORE UPDATE ON venue_comments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
