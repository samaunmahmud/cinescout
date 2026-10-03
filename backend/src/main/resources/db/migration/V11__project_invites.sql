-- An invitation to join a project, for someone who has no account yet. CineScout sends no email: the owner copies
-- the link. Only a SHA-256 hash of the link's token is kept, so a copy of the database holds no usable link. An
-- invite is for one email address and one use, and lapses after seven days.
CREATE TABLE project_invites (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    email       TEXT        NOT NULL,
    role        TEXT        NOT NULL CHECK (role IN ('EDITOR', 'VIEWER')),
    token_hash  TEXT        NOT NULL,
    invited_by  UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    accepted_by UUID        REFERENCES users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_project_invites_token ON project_invites (token_hash);
CREATE INDEX idx_project_invites_project ON project_invites (project_id) WHERE accepted_at IS NULL;
