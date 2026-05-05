package com.edulingo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_session")
@Getter
@Setter
public class LearningSession {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;

    @Column(name = "session_type", nullable = false)
    private String sessionType;   // CHAT | PICTURE

    @Column(name = "topic_id", nullable = false)
    private String topicId;

    @Column(name = "topic_name")
    private String topicName;

    @Column
    private Integer score;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
