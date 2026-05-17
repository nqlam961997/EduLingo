-- chat-context-awareness Phase 2: persist per-chat session state.
-- The scratchpad_json column holds the structured scenario-state extracted
-- after each turn (slot machine / phase machine / rhythm beats — schema is
-- determined by the topic's TopicType at the application layer).

CREATE TABLE chat_session (
    id                   UUID PRIMARY KEY,
    learner_id           UUID NOT NULL REFERENCES learner_profile(id) ON DELETE CASCADE,
    topic_id             VARCHAR(64) NOT NULL,
    scenario_seed_index  INTEGER NOT NULL DEFAULT 0,
    scratchpad_json      JSONB,
    started_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_turn_at         TIMESTAMP
);

CREATE INDEX idx_chat_session_learner ON chat_session(learner_id);
CREATE INDEX idx_chat_session_topic ON chat_session(topic_id);
