-- Who to talk to at a venue: the person the user found, asked for or was answered by. Kept on the location so
-- it is entered once and every outreach email to the venue can start from it. All optional, all user-entered.
ALTER TABLE locations
    ADD COLUMN contact_name  TEXT,
    ADD COLUMN contact_email TEXT,
    ADD COLUMN contact_phone TEXT;
