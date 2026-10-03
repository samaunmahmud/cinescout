-- Photos the crew took on a recce, kept in the file store (storage_key, thumb_key) after being re-encoded without
-- their metadata. The GPS position is read before that and kept here, to offer as the venue's pin. A venue's
-- cover_photo_id picks the photo its polaroid shows, over the picture from its web page.
CREATE TABLE location_photos (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id   UUID          NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    uploaded_by   UUID          REFERENCES users (id) ON DELETE SET NULL,
    storage_key   TEXT          NOT NULL,
    thumb_key     TEXT          NOT NULL,
    content_type  TEXT          NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    width         INTEGER       NOT NULL CHECK (width > 0),
    height        INTEGER       NOT NULL CHECK (height > 0),
    size_bytes    INTEGER       NOT NULL CHECK (size_bytes > 0),
    gps_latitude  NUMERIC(9, 6) CHECK (gps_latitude BETWEEN -90 AND 90),
    gps_longitude NUMERIC(9, 6) CHECK (gps_longitude BETWEEN -180 AND 180),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_location_photos_location ON location_photos (location_id, created_at);

ALTER TABLE locations
    ADD COLUMN cover_photo_id UUID REFERENCES location_photos (id) ON DELETE SET NULL;
