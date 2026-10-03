-- Follow-up nudges. A project says how many days a sent email may go unanswered (default 5); a scheduled job flags
-- the SENT drafts older than that which have no reply and no follow-up of their own yet. A follow-up is a draft of
-- its own that points at the email it chases, so the chaser has its own reply address and can itself be flagged.
ALTER TABLE projects
    ADD COLUMN follow_up_days INTEGER NOT NULL DEFAULT 5 CHECK (follow_up_days BETWEEN 1 AND 60);

ALTER TABLE outreach_drafts
    ADD COLUMN follow_up_flagged_at TIMESTAMPTZ,
    ADD COLUMN follow_up_of         UUID REFERENCES outreach_drafts (id) ON DELETE SET NULL;

CREATE INDEX idx_outreach_drafts_follow_up_of ON outreach_drafts (follow_up_of);
CREATE INDEX idx_outreach_drafts_unflagged_sent ON outreach_drafts (sent_at) WHERE status = 'SENT' AND follow_up_flagged_at IS NULL;
