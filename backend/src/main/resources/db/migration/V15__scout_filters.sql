-- A project's default scouting filters (a ScoutFilters record as JSON): a base point and radius, a maximum budget,
-- venue types to leave out, and whether private property is wanted. Null until set; a run may override them.
ALTER TABLE projects
    ADD COLUMN scout_filters JSONB;
