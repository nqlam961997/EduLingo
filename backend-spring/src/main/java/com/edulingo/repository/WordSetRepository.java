package com.edulingo.repository;

import com.edulingo.entity.WordSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WordSetRepository extends JpaRepository<WordSet, UUID> {

    List<WordSet> findByCefrLevelOrderByDisplayOrderAsc(String cefrLevel);

    @Query("SELECT DISTINCT ws.cefrLevel FROM WordSet ws ORDER BY ws.cefrLevel")
    List<String> findDistinctLevels();

    @Query("SELECT COUNT(ws) FROM WordSet ws WHERE ws.cefrLevel = :level")
    long countByLevel(String level);
}
