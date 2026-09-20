-- Where scouting searches for venues, as free text (e.g. 'Brooklyn, New York').
-- A film usually shoots in one region, so it lives on the project and every scene
-- inherits it. Nullable: existing projects have none, and scouting asks for one
-- instead of guessing.
ALTER TABLE projects
    ADD COLUMN location_area TEXT,
    ADD CONSTRAINT ck_projects_location_area_not_blank
        CHECK (location_area IS NULL OR btrim(location_area) <> '');
