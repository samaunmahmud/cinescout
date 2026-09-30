-- A link to a project's call sheet that works without an account, for the crew. The token is the whole
-- credential: random, unguessable, one per project at a time, and cleared when the owner stops sharing.
ALTER TABLE projects
    ADD COLUMN call_sheet_token TEXT;

CREATE UNIQUE INDEX uq_projects_call_sheet_token ON projects (call_sheet_token) WHERE call_sheet_token IS NOT NULL;
