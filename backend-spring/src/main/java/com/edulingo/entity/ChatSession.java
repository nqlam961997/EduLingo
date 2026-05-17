package com.edulingo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted state of a single chat session. Created on POST /api/chat/start
 * and updated after each turn with the extracted scratchpad.
 *
 * @see com.edulingo.service.ChatSessionService
 */
@Entity
@Table(name = "chat_session")
@Getter
@Setter
public class ChatSession {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;

    @Column(name = "topic_id", nullable = false, length = 64)
    private String topicId;

    @Column(name = "scenario_seed_index", nullable = false)
    private int scenarioSeedIndex = 0;

    /**
     * Stored as a JSON text whose shape matches the topic-type's scratchpad
     * schema (transactional/asymmetric/free-form). The DB column type is
     * {@code jsonb} for indexing/validation; Hibernate treats it as String.
     */
    @Column(name = "scratchpad_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String scratchpadJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "last_turn_at")
    private Instant lastTurnAt;
}
