package com.edulingo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "error_pattern", indexes = {
        @Index(name = "idx_error_user_count", columnList = "learner_id, count DESC")
})
@Getter
@Setter
public class ErrorPattern {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;

    @Column(name = "error_type", nullable = false)
    private String errorType;

    @Column(name = "example", length = 1000)
    private String example;

    @Column(nullable = false)
    private int count = 1;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen = Instant.now();
}
