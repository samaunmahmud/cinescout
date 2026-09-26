-- YouTube videos of a venue, cached like its logistics: the search API's free quota is about a hundred
-- searches a day, so a location is searched once and the result kept. videos_query is what was searched for;
-- when the venue's name or address changes, the query changes and the cache no longer applies.
ALTER TABLE locations
    ADD COLUMN videos_json       JSONB,
    ADD COLUMN videos_query      TEXT,
    ADD COLUMN videos_fetched_at TIMESTAMPTZ;
