CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE learner_profile (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL UNIQUE,
    cefr_level    TEXT        NOT NULL DEFAULT 'A2',
    learning_goal TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE error_pattern (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id  UUID        NOT NULL REFERENCES learner_profile(id) ON DELETE CASCADE,
    error_type  TEXT        NOT NULL,
    example     VARCHAR(1000),
    count       INT         NOT NULL DEFAULT 1,
    last_seen   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (learner_id, error_type)
);

CREATE INDEX idx_error_user_count ON error_pattern (learner_id, count DESC);
