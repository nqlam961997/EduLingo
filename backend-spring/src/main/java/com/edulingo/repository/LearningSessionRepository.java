package com.edulingo.repository;

import com.edulingo.entity.LearningSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LearningSessionRepository extends JpaRepository<LearningSession, UUID> {

    long countByLearnerId(UUID learnerId);

    List<LearningSession> findByLearnerIdOrderByCreatedAtDesc(UUID learnerId, Pageable pageable);

    @Query("SELECT COALESCE(AVG(s.score), 0) FROM LearningSession s WHERE s.learnerId = :learnerId AND s.score IS NOT NULL")
    double avgScoreByLearnerId(UUID learnerId);

    @Query("SELECT DISTINCT s.topicId FROM LearningSession s WHERE s.learnerId = :learnerId")
    List<String> distinctTopicsByLearnerId(UUID learnerId);

    @Query("SELECT COUNT(s) FROM LearningSession s WHERE s.learnerId = :learnerId AND s.sessionType = :type")
    long countByLearnerIdAndSessionType(UUID learnerId, String type);
}
