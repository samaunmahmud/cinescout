-- The local authority area a venue's position falls in (an AdminArea as JSON: name, ISO 3166-2 codes, country and
-- the position it was looked up for), cached so the permit guide and the schedule need not ask the geocoder again
-- until the venue moves. Null until first looked up.
ALTER TABLE locations
    ADD COLUMN admin_area JSONB;
