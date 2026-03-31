ALTER TABLE problem_solve_attempt_entries
    ADD COLUMN activity_type VARCHAR(20) NOT NULL DEFAULT 'solve';

ALTER TABLE problem_solve_attempt_entries
    ADD CONSTRAINT chk_problem_solve_attempt_entries_activity_type
        CHECK (activity_type IN ('solve', 'review'));
