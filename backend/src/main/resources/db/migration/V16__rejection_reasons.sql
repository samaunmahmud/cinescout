-- Why the crew passed on a venue, when they say: preset words ("Too small") or their own. Kept while the venue is
-- REJECTED; the scene's latest few steer its next scouting runs away from the same problems.
ALTER TABLE locations
    ADD COLUMN rejection_reason TEXT CHECK (rejection_reason IS NULL OR char_length(rejection_reason) <= 300);
