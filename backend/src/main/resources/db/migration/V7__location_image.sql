-- A picture of the venue: the image its own web page offers for sharing (og:image), looked up once. image_url is
-- null both before the look-up and when the page offers none; image_checked_at tells the two apart.
ALTER TABLE locations
    ADD COLUMN image_url        TEXT,
    ADD COLUMN image_checked_at TIMESTAMPTZ;
