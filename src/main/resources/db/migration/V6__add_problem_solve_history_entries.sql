CREATE TABLE problem_solve_history_entries (
    problem_id BIGINT NOT NULL,
    solved_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_problem_solve_history_entries_problem
        FOREIGN KEY (problem_id) REFERENCES problems(id) ON DELETE CASCADE
);

CREATE INDEX idx_problem_solve_history_entries_problem_solved_at
    ON problem_solve_history_entries(problem_id, solved_at DESC);
