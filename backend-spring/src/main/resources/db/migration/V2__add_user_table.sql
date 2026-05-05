CREATE TABLE app_user (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL UNIQUE,
    full_name     TEXT,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL DEFAULT 'STUDENT',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE learner_profile ADD COLUMN user_id UUID REFERENCES app_user(id) ON DELETE CASCADE;
CREATE INDEX idx_learner_user ON learner_profile (user_id);
