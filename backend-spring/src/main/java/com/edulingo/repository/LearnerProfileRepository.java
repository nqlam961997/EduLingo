package com.edulingo.repository;

import com.edulingo.entity.LearnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, UUID> {
    Optional<LearnerProfile> findByEmail(String email);
}
