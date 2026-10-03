-- The tech recce checklist of a venue: one JSON object of field name -> { value, by, byName, at }, so each answer
-- says who gave it and when. Every field is optional; the application checks names and values.
ALTER TABLE locations
    ADD COLUMN recce JSONB NOT NULL DEFAULT '{}'::jsonb;
