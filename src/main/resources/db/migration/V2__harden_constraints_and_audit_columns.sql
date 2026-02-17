-- 1) Audit columns
ALTER TABLE problems
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE study_sets
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE reviews
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

-- 2) Additional check constraints (non-breaking but stricter)
ALTER TABLE problems
    ADD CONSTRAINT chk_problem_platform_not_blank CHECK (LENGTH(BTRIM(platform)) > 0),
    ADD CONSTRAINT chk_problem_number_not_blank CHECK (LENGTH(BTRIM(number)) > 0),
    ADD CONSTRAINT chk_problem_link_not_blank CHECK (LENGTH(BTRIM(link)) > 0),
    ADD CONSTRAINT chk_problem_memo_not_blank CHECK (LENGTH(BTRIM(memo)) > 0);

ALTER TABLE study_sets
    ADD CONSTRAINT chk_study_set_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0);

ALTER TABLE reviews
    ADD CONSTRAINT chk_reviews_review_count_non_negative CHECK (review_count >= 0),
    ADD CONSTRAINT chk_reviews_date_order CHECK (next_review_date >= last_reviewed_date);

-- 3) Useful indexes for expected query patterns
CREATE INDEX idx_problems_created_at ON problems(created_at DESC);
CREATE INDEX idx_problems_platform ON problems(platform);
CREATE INDEX idx_problems_needs_review ON problems(needs_review);
CREATE INDEX idx_problems_bookmarked ON problems(bookmarked);
CREATE INDEX idx_study_sets_created_at ON study_sets(created_at DESC);

-- 4) Generic trigger for updated_at auto-maintenance
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_problems_updated_at
BEFORE UPDATE ON problems
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_study_sets_updated_at
BEFORE UPDATE ON study_sets
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_reviews_updated_at
BEFORE UPDATE ON reviews
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
