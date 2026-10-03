-- Holds and availability: for a venue and a day, how far booking it has got (pencilled, held, confirmed) or that it
-- cannot be had (unavailable), with an optional date a hold lapses on and a note. One row per venue per day.
-- Scenes gain an optional time window on their shoot days (call to wrap; a wrap at or before the call is the next
-- morning), so two scenes at one venue on one day can be told apart from a clash.
ALTER TABLE scenes
    ADD COLUMN call_time TIME,
    ADD COLUMN wrap_time TIME;

CREATE TABLE venue_availability (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id     UUID        NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    day             DATE        NOT NULL,
    state           TEXT        NOT NULL CHECK (state IN ('PENCILLED', 'HELD', 'CONFIRMED', 'UNAVAILABLE')),
    hold_expires_on DATE,
    note            TEXT        CHECK (note IS NULL OR char_length(note) <= 500),
    set_by          UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_venue_availability_day UNIQUE (location_id, day),
    CONSTRAINT ck_venue_availability_expiry CHECK (hold_expires_on IS NULL OR state IN ('PENCILLED', 'HELD'))
);

CREATE TRIGGER trg_venue_availability_updated_at BEFORE UPDATE ON venue_availability
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE activity DROP CONSTRAINT activity_verb_check;
ALTER TABLE activity ADD CONSTRAINT activity_verb_check CHECK (verb IN ('VENUE_STATUS_CHANGED', 'DIRECTOR_CALLED', 'SCOUTED',
    'OUTREACH_STATUS_CHANGED', 'MEMBER_JOINED', 'MEMBER_LEFT', 'MEMBER_REMOVED', 'ROLE_CHANGED', 'OWNERSHIP_TRANSFERRED',
    'SHOOT_DATES_CHANGED', 'COMMENTED', 'AVAILABILITY_CHANGED'));
