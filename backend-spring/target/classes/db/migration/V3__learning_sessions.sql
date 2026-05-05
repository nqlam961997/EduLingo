ALTER TABLE learner_profile ADD COLUMN IF NOT EXISTS user_id UUID;

CREATE TABLE IF NOT EXISTS learning_session (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id   UUID        NOT NULL REFERENCES learner_profile(id) ON DELETE CASCADE,
    session_type TEXT        NOT NULL,   -- 'CHAT' | 'PICTURE'
    topic_id     TEXT        NOT NULL,
    topic_name   TEXT,
    score        INT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_session_learner_time
    ON learning_session (learner_id, created_at DESC);
