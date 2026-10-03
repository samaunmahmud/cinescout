-- Location agreements: a PDF location release filled in from the production, the venue, its contact and quote, the
-- scene's dates, access times and crew size. Each generation is a new version; the file lives in the file store.
CREATE TABLE location_agreements (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID        NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    version     INTEGER     NOT NULL CHECK (version > 0),
    storage_key TEXT        NOT NULL,
    size_bytes  INTEGER     NOT NULL CHECK (size_bytes > 0),
    created_by  UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_location_agreements_version UNIQUE (location_id, version)
);
