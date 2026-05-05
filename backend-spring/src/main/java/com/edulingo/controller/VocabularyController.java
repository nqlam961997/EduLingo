package com.edulingo.controller;

import com.edulingo.entity.User;
import com.edulingo.entity.UserWordProgress;
import com.edulingo.entity.Vocabulary;
import com.edulingo.entity.WordSet;
import com.edulingo.repository.*;
import com.edulingo.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/vocabulary")
public class VocabularyController {

    private final WordSetRepository          wordSetRepo;
    private final VocabularyRepository       vocabRepo;
    private final UserWordProgressRepository progressRepo;
    private final UserRepository             userRepo;
    private final SecurityUtils              security;

    public VocabularyController(WordSetRepository wordSetRepo,
                                VocabularyRepository vocabRepo,
                                UserWordProgressRepository progressRepo,
                                UserRepository userRepo,
                                SecurityUtils security) {
        this.wordSetRepo  = wordSetRepo;
        this.vocabRepo    = vocabRepo;
        this.progressRepo = progressRepo;
        this.userRepo     = userRepo;
        this.security     = security;
    }

    // ── GET /api/vocabulary/levels ───────────────────────────────────────────
    @GetMapping("/levels")
    public Mono<List<Map<String, Object>>> getLevels() {
        return withOptionalUser(user ->
            Mono.fromCallable(() -> {
                List<String> levels = wordSetRepo.findDistinctLevels();
                List<Map<String, Object>> result = new ArrayList<>();
                for (String level : levels) {
                    long mastered = user != null ? progressRepo.countMasteredInLevel(user.getId(), level) : 0;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("level",      level);
                    item.put("setCount",   wordSetRepo.countByLevel(level));
                    item.put("totalWords", vocabRepo.countByLevel(level));
                    item.put("mastered",   mastered);
                    item.put("difficulty", levelDifficulty(level));
                    result.add(item);
                }
                return result;
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    // ── GET /api/vocabulary/levels/{level}/sets ──────────────────────────────
    @GetMapping("/levels/{level}/sets")
    public Mono<List<Map<String, Object>>> getSets(@PathVariable String level) {
        return withOptionalUser(user ->
            Mono.fromCallable(() -> {
                List<WordSet> sets = wordSetRepo.findByCefrLevelOrderByDisplayOrderAsc(level.toUpperCase());
                List<Map<String, Object>> result = new ArrayList<>();
                for (WordSet ws : sets) {
                    long mastered = user != null ? progressRepo.countMasteredInSet(user.getId(), ws.getId()) : 0;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id",          ws.getId());
                    item.put("name",        ws.getName());
                    item.put("description", ws.getDescription());
                    item.put("cefrLevel",   ws.getCefrLevel());
                    item.put("difficulty",  ws.getDifficulty());
                    item.put("totalWords",  vocabRepo.countByWordSetId(ws.getId()));
                    item.put("mastered",    mastered);
                    result.add(item);
                }
                return result;
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    // ── GET /api/vocabulary/sets/{setId} ─────────────────────────────────────
    @GetMapping("/sets/{setId}")
    public Mono<ResponseEntity<Map<String, Object>>> getSet(@PathVariable UUID setId) {
        return this.<ResponseEntity<Map<String, Object>>>withOptionalUser(user ->
            Mono.fromCallable(() -> buildSetResponse(setId, user))
                .subscribeOn(Schedulers.boundedElastic())
        );
    }

    private ResponseEntity<Map<String, Object>> buildSetResponse(UUID setId, User user) {
        WordSet ws = wordSetRepo.findById(setId).orElse(null);
        if (ws == null) return ResponseEntity.notFound().build();

        List<Vocabulary> words = vocabRepo.findByWordSetIdOrderByDisplayOrderAsc(setId);
        Set<UUID> masteredIds = Collections.emptySet();
        if (user != null && !words.isEmpty()) {
            masteredIds = progressRepo.findMasteredVocabIds(
                    user.getId(), words.stream().map(Vocabulary::getId).toList());
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
    public Mono<ResponseEntity<Map<String, Object>>> toggleMastered(@PathVariable UUID wordId) {
        return security.currentUserId()
                .flatMap(uid -> Mono.fromCallable(() -> doToggle(uid, wordId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .switchIfEmpty(Mono.just(ResponseEntity.status(401).<Map<String, Object>>build()))
                .onErrorReturn(ResponseEntity.status(401).<Map<String, Object>>build());
    }

    private ResponseEntity<Map<String, Object>> doToggle(UUID userId, UUID wordId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();

        Vocabulary vocab = vocabRepo.findById(wordId).orElse(null);
        if (vocab == null) return ResponseEntity.notFound().build();

        UserWordProgress prog = progressRepo
                .findByUserIdAndVocabularyId(userId, wordId)
                .orElseGet(() -> {
                    UserWordProgress p = new UserWordProgress();
                    p.setUser(user);
                    p.setVocabulary(vocab);
                    return p;
                });

        prog.setMastered(!prog.isMastered());
        prog.setUpdatedAt(Instant.now());
        progressRepo.save(prog);

        return ResponseEntity.ok(Map.of(
                "wordId",   wordId,
                "mastered", prog.isMastered()
        ));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Lấy user hiện tại (nullable) cho các endpoint đọc.
     * Nếu không có token hoặc token lỗi → user = null (guest view).
     */
    private <T> Mono<T> withOptionalUser(java.util.function.Function<User, Mono<T>> fn) {
        return security.currentUserId()
                .onErrorResume(e -> Mono.empty())
                .flatMap(uid -> Mono.fromCallable(() -> userRepo.findById(uid).orElse(null))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(fn)
                .switchIfEmpty(Mono.defer(() -> fn.apply(null)));
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
