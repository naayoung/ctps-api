ALTER TABLE problems
    ALTER COLUMN difficulty DROP NOT NULL;

ALTER TABLE problem_interaction_events
    ALTER COLUMN difficulty DROP NOT NULL;
