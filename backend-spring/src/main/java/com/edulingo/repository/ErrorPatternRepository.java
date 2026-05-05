package com.edulingo.repository;

import com.edulingo.entity.ErrorPattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ErrorPatternRepository extends JpaRepository<ErrorPattern, UUID> {

    Optional<ErrorPattern> findByLearnerIdAndErrorType(UUID learnerId, String errorType);

    @Query("select e from ErrorPattern e where e.learnerId = :learnerId order by e.count desc")
    List<ErrorPattern> findTopByLearner(UUID learnerId, Pageable pageable);
}
