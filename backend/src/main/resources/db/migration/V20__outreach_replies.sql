-- Replies to outreach emails. Each draft gets a secret reply token, which makes its own reply address
-- (scout+<token>@<reply domain>); an inbound-mail webhook matches a reply by it. Replies can also be pasted in by
-- hand. Only the plain text of a reply is kept, at most 20 KB of it.
ALTER TABLE outreach_drafts
    ADD COLUMN reply_token TEXT;

UPDATE outreach_drafts SET reply_token = replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');

ALTER TABLE outreach_drafts
    ALTER COLUMN reply_token SET NOT NULL;

CREATE UNIQUE INDEX uq_outreach_drafts_reply_token ON outreach_drafts (reply_token);

CREATE TABLE outreach_replies (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id     UUID        NOT NULL REFERENCES outreach_drafts (id) ON DELETE CASCADE,
    source       TEXT        NOT NULL CHECK (source IN ('INBOUND', 'MANUAL')),
    from_address TEXT,
    from_name    TEXT,
    subject      TEXT,
    body_text    TEXT        CHECK (body_text IS NULL OR char_length(body_text) <= 20480),
    received_at  TIMESTAMPTZ NOT NULL,
    recorded_by  UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outreach_replies_draft ON outreach_replies (draft_id, received_at DESC);
