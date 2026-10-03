-- A user's own library of venues, kept apart from any scene: what stays true of a place whatever is shot there
-- (where it is, its picture, who to call, how hard it is to book), plus the user's tags and notes. Fit scores,
-- statuses and crew notes belong to a scene and are not kept here. source_location_id remembers which scouted
-- venue it was saved from, so saving it twice finds the first copy.
CREATE TABLE library_venues (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id           UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_location_id UUID          REFERENCES locations (id) ON DELETE SET NULL,
    name               TEXT          NOT NULL CHECK (char_length(name) BETWEEN 1 AND 200),
    address            TEXT,
    latitude           NUMERIC(9, 6),
    longitude          NUMERIC(9, 6),
    source_url         TEXT,
    image_url          TEXT,
    booking_friction   TEXT          CHECK (booking_friction IN ('PUBLIC', 'COMMERCIAL', 'PRIVATE')),
    friction_note      TEXT,
    footprint_warnings JSONB         NOT NULL DEFAULT '[]'::jsonb,
    contact_name       TEXT,
    contact_email      TEXT,
    contact_phone      TEXT,
    tags               JSONB         NOT NULL DEFAULT '[]'::jsonb,
    notes              TEXT          CHECK (notes IS NULL OR char_length(notes) <= 4000),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_library_venues_owner ON library_venues (owner_id, created_at DESC);
CREATE UNIQUE INDEX uq_library_venues_source ON library_venues (owner_id, source_location_id) WHERE source_location_id IS NOT NULL;

CREATE TRIGGER trg_library_venues_updated_at BEFORE UPDATE ON library_venues
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
