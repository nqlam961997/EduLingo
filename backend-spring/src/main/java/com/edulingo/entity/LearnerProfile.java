package com.edulingo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learner_profile")
@Getter
@Setter
public class LearnerProfile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "cefr_level", nullable = false)
    private String cefrLevel = "A2";

    @Column(name = "learning_goal")
    private String learningGoal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
