-- Calendar feed: a per-person secret token for an ICS feed of the shoot days across their projects. Null while the
-- feed is off; a new token replaces the old one, so a link that went too far can be cut off.
ALTER TABLE users ADD COLUMN calendar_token TEXT CHECK (calendar_token IS NULL OR char_length(calendar_token) = 43);

CREATE UNIQUE INDEX uq_users_calendar_token ON users (calendar_token) WHERE calendar_token IS NOT NULL;
