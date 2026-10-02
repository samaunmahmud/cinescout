-- The picture finder now follows redirects, takes http pictures as https and passes over logos, so venues it found
-- nothing for may have a picture after all: mark them unchecked, and the web app looks them up again when they are
-- next shown. Not a change anyone made to the venue, so updated_at stays as it was.
ALTER TABLE locations DISABLE TRIGGER trg_locations_updated_at;
UPDATE locations SET image_checked_at = NULL WHERE image_url IS NULL AND image_checked_at IS NOT NULL;
ALTER TABLE locations ENABLE TRIGGER trg_locations_updated_at;
