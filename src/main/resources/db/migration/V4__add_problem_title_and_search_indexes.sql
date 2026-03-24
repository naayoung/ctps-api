ALTER TABLE problems
    ADD COLUMN title VARCHAR(255);

UPDATE problems
SET title = CASE
    WHEN LENGTH(BTRIM(number)) > 0 THEN number
    ELSE platform
END
WHERE title IS NULL;

ALTER TABLE problems
    ALTER COLUMN title SET NOT NULL;

ALTER TABLE problems
    ADD CONSTRAINT chk_problem_title_not_blank CHECK (LENGTH(BTRIM(title)) > 0);

CREATE INDEX idx_problems_title ON problems(title);
CREATE INDEX idx_problems_last_solved_at ON problems(last_solved_at DESC);
CREATE INDEX idx_problems_result ON problems(result);
