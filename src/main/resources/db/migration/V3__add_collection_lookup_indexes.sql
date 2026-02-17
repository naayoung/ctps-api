-- Tag-based filtering support (e.g., problem list tag filters)
CREATE INDEX idx_problem_tags_tag ON problem_tags(tag);

-- Solved-date based analytics support (e.g., heatmap / yearly activity)
CREATE INDEX idx_problem_solved_dates_solved_date ON problem_solved_dates(solved_date);

-- Review-history timeline queries
CREATE INDEX idx_problem_review_history_reviewed_date ON problem_review_history(reviewed_date);

-- Reverse lookup helpers (from problem -> containing study sets)
CREATE INDEX idx_study_set_problem_ids_problem_id ON study_set_problem_ids(problem_id);
CREATE INDEX idx_study_set_completed_problem_ids_problem_id ON study_set_completed_problem_ids(problem_id);
