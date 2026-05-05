package com.edulingo.controller;

import com.edulingo.entity.User;
import com.edulingo.entity.UserWordProgress;
import com.edulingo.entity.Vocabulary;
import com.edulingo.entity.WordSet;
import com.edulingo.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/vocabulary")
public class VocabularyController {

    private final WordSetRepository         wordSetRepo;
    private final VocabularyRepository      vocabRepo;
    private final UserWordProgressRepository progressRepo;
    private final UserRepository            userRepo;

    public VocabularyController(WordSetRepository wordSetRepo,
                                VocabularyRepository vocabRepo,
                                UserWordProgressRepository progressRepo,
                                UserRepository userRepo) {
        this.wordSetRepo  = wordSetRepo;
        this.vocabRepo    = vocabRepo;
        this.progressRepo = progressRepo;
        this.userRepo     = userRepo;
    }

    // ── GET /api/vocabulary/levels ───────────────────────────────────────────
    @GetMapping("/levels")
    public List<Map<String, Object>> getLevels(@AuthenticationPrincipal UserDetails principal) {
        User user = resolveUser(principal);
        List<String> levels = wordSetRepo.findDistinctLevels();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String level : levels) {
            long setCount    = wordSetRepo.countByLevel(level);
            long totalWords  = vocabRepo.countByLevel(level);
            long mastered    = user != null ? progressRepo.countMasteredInLevel(user.getId(), level) : 0;
            int  difficulty  = levelDifficulty(level);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("level",      level);
            item.put("setCount",   setCount);
            item.put("totalWords", totalWords);
            item.put("mastered",   mastered);
            item.put("difficulty", difficulty);
            result.add(item);
        }
        return result;
    }

    // ── GET /api/vocabulary/levels/{level}/sets ──────────────────────────────
    @GetMapping("/levels/{level}/sets")
    public List<Map<String, Object>> getSets(@PathVariable String level,
                                             @AuthenticationPrincipal UserDetails principal) {
        User user = resolveUser(principal);
        List<WordSet> sets = wordSetRepo.findByCefrLevelOrderByDisplayOrderAsc(level.toUpperCase());

        List<Map<String, Object>> result = new ArrayList<>();
        for (WordSet ws : sets) {
            long total    = vocabRepo.countByWordSetId(ws.getId());
            long mastered = user != null ? progressRepo.countMasteredInSet(user.getId(), ws.getId()) : 0;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",          ws.getId());
            item.put("name",        ws.getName());
            item.put("description", ws.getDescription());
            item.put("cefrLevel",   ws.getCefrLevel());
            item.put("difficulty",  ws.getDifficulty());
            item.put("totalWords",  total);
            item.put("mastered",    mastered);
            result.add(item);
        }
        return result;
    }

    // ── GET /api/vocabulary/sets/{setId} ─────────────────────────────────────
    @GetMapping("/sets/{setId}")
    public ResponseEntity<Map<String, Object>> getSet(@PathVariable UUID setId,
                                                      @AuthenticationPrincipal UserDetails principal) {
        WordSet ws = wordSetRepo.findById(setId).orElse(null);
        if (ws == null) return ResponseEntity.notFound().build();

        User user = resolveUser(principal);
        List<Vocabulary> words = vocabRepo.findByWordSetIdOrderByDisplayOrderAsc(setId);

        Set<UUID> masteredIds = Collections.emptySet();
        if (user != null && !words.isEmpty()) {
            masteredIds = progressRepo.findMasteredVocabIds(user.getId(),
                    words.stream().map(Vocabulary::getId).toList());
        }

        List<Map<String, Object>> wordList = new ArrayList<>();
        for (Vocabulary v : words) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("id",             v.getId());
            w.put("word",           v.getWord());
            w.put("pronunciation",  v.getPronunciation());
            w.put("meaning",        v.getMeaning());
            w.put("wordType",       v.getWordType());
            w.put("example",        v.getExample());
            w.put("exampleMeaning", v.getExampleMeaning());
            w.put("mastered",       masteredIds.contains(v.getId()));
            wordList.add(w);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id",          ws.getId());
        resp.put("name",        ws.getName());
        resp.put("description", ws.getDescription());
        resp.put("cefrLevel",   ws.getCefrLevel());
        resp.put("difficulty",  ws.getDifficulty());
        resp.put("words",       wordList);
        return ResponseEntity.ok(resp);
    }

    // ── POST /api/vocabulary/words/{wordId}/toggle ───────────────────────────
    @PostMapping("/words/{wordId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleMastered(
            @PathVariable UUID wordId,
            @AuthenticationPrincipal UserDetails principal) {

        User user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).build();

        Vocabulary vocab = vocabRepo.findById(wordId).orElse(null);
        if (vocab == null) return ResponseEntity.notFound().build();

        UserWordProgress prog = progressRepo
                .findByUserIdAndVocabularyId(user.getId(), wordId)
                .orElseGet(() -> {
                    UserWordProgress p = new UserWordProgress();
                    p.setUser(user);
                    p.setVocabulary(vocab);
                    return p;
                });

        prog.setMastered(!prog.isMastered());
        prog.setUpdatedAt(Instant.now());
        progressRepo.save(prog);

        Map<String, Object> resp = Map.of(
                "wordId",   wordId,
                "mastered", prog.isMastered()
        );
        return ResponseEntity.ok(resp);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User resolveUser(UserDetails principal) {
        if (principal == null) return null;
        return userRepo.findByEmail(principal.getUsername()).orElse(null);
    }

    private int levelDifficulty(String level) {
        return switch (level) {
            case "A1" -> 1;
            case "A2" -> 2;
            case "B1" -> 3;
            case "B2" -> 4;
            case "C1" -> 5;
            default   -> 5;
        };
    }
}
