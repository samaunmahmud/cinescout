-- Drives between two points (company moves), kept so the public routing server is asked once per pair of places.
-- Coordinates are rounded to five decimal places (about a metre). found = false records "no road between them".
CREATE TABLE route_cache (
    from_lat         NUMERIC(8, 5) NOT NULL,
    from_lng         NUMERIC(8, 5) NOT NULL,
    to_lat           NUMERIC(8, 5) NOT NULL,
    to_lng           NUMERIC(8, 5) NOT NULL,
    found            BOOLEAN       NOT NULL,
    distance_m       DOUBLE PRECISION,
    duration_s       DOUBLE PRECISION,
    fetched_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (from_lat, from_lng, to_lat, to_lng)
);
