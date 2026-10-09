-- A saved copy of a shared demo account's productions and library (cinescout.security.locked-accounts), written by the
-- demo-snapshot job and put back each night by the demo-reset job, so whatever visitors change or delete returns.
-- data: {"tables": {"<table>": [<row as JSON>, ...], ...}}, one entry per table, in the order the rows go back.
CREATE TABLE demo_snapshots (
    user_id  UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    data     JSONB       NOT NULL,
    taken_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
