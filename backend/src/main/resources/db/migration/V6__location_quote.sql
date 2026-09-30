-- What the venue asks for the shoot, in the user's own words ("$425 an hour, four hour minimum"). Free text
-- on purpose: quotes come with conditions, currencies and units that a number would lose.
ALTER TABLE locations
    ADD COLUMN quote TEXT;
