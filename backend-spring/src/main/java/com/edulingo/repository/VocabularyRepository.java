package com.edulingo.repository;

import com.edulingo.entity.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VocabularyRepository extends JpaRepository<Vocabulary, UUID> {

    List<Vocabulary> findByWordSetIdOrderByDisplayOrderAsc(UUID wordSetId);

    @Query("SELECT COUNT(v) FROM Vocabulary v WHERE v.wordSet.id = :setId")
    long countByWordSetId(UUID setId);

    @Query("SELECT COUNT(v) FROM Vocabulary v WHERE v.wordSet.cefrLevel = :level")
    long countByLevel(String level);
}
