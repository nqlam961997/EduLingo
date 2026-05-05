package com.edulingo.repository;

import com.edulingo.entity.UserWordProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserWordProgressRepository extends JpaRepository<UserWordProgress, UUID> {

    Optional<UserWordProgress> findByUserIdAndVocabularyId(UUID userId, UUID vocabularyId);

    @Query("SELECT p.vocabulary.id FROM UserWordProgress p WHERE p.user.id = :userId AND p.mastered = true AND p.vocabulary.id IN :vocabIds")
    Set<UUID> findMasteredVocabIds(UUID userId, List<UUID> vocabIds);

    @Query("SELECT COUNT(p) FROM UserWordProgress p WHERE p.user.id = :userId AND p.vocabulary.wordSet.id = :setId AND p.mastered = true")
    long countMasteredInSet(UUID userId, UUID setId);

    @Query("SELECT COUNT(p) FROM UserWordProgress p WHERE p.user.id = :userId AND p.vocabulary.wordSet.cefrLevel = :level AND p.mastered = true")
    long countMasteredInLevel(UUID userId, String level);
}
