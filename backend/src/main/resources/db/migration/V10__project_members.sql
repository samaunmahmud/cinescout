-- The crew of a project: who can see it and what each may do. OWNER can do everything (one per project, the same
-- user as projects.owner_id); EDITOR everything but deleting or archiving the project and managing the crew;
-- VIEWER reads only. Authorization joins here instead of comparing projects.owner_id.
CREATE TABLE project_members (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       TEXT        NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    invited_by UUID        REFERENCES users (id) ON DELETE SET NULL,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_members UNIQUE (project_id, user_id)
);

CREATE INDEX idx_project_members_user ON project_members (user_id);
-- At most one owner per project.
CREATE UNIQUE INDEX uq_project_members_owner ON project_members (project_id) WHERE role = 'OWNER';

-- Every existing project's creator becomes its owner, as a member joined when the project was made.
INSERT INTO project_members (project_id, user_id, role, joined_at)
SELECT id, owner_id, 'OWNER', created_at FROM projects;
