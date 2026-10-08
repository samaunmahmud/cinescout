-- A production's budget: a total in one currency, and lines (a venue's day rate, a permit fee, a deposit...) each an
-- estimate, committed or paid. A line may name the venue it is for; deleting the venue keeps the line, unlinked.
ALTER TABLE projects
    ADD COLUMN budget_total    NUMERIC(12, 2) CHECK (budget_total IS NULL OR budget_total >= 0),
    ADD COLUMN budget_currency TEXT NOT NULL DEFAULT 'GBP' CHECK (budget_currency ~ '^[A-Z]{3}$');

CREATE TABLE budget_items (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID           NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    location_id UUID           REFERENCES locations (id) ON DELETE SET NULL,
    category    TEXT           NOT NULL CHECK (category IN ('VENUE', 'PERMIT', 'DEPOSIT', 'CREW', 'EQUIPMENT', 'TRAVEL', 'CATERING', 'OTHER')),
    label       TEXT           NOT NULL CHECK (char_length(label) BETWEEN 1 AND 200),
    amount      NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    status      TEXT           NOT NULL DEFAULT 'ESTIMATE' CHECK (status IN ('ESTIMATE', 'COMMITTED', 'PAID')),
    note        TEXT           CHECK (note IS NULL OR char_length(note) <= 1000),
    added_by    UUID           REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_budget_items_project ON budget_items (project_id, created_at);
CREATE INDEX idx_budget_items_location ON budget_items (location_id);

CREATE TRIGGER trg_budget_items_updated_at BEFORE UPDATE ON budget_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
