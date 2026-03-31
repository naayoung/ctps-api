ALTER TABLE study_sets
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_study_sets_user'
          AND table_name = 'study_sets'
    ) THEN
        ALTER TABLE study_sets
            ADD CONSTRAINT fk_study_sets_user
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_study_sets_user_id ON study_sets(user_id);
