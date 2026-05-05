package com.edulingo.controller;

import com.edulingo.dto.*;
import com.edulingo.entity.LearnerProfile;
import com.edulingo.entity.LearningSession;
import com.edulingo.repository.LearningSessionRepository;
import com.edulingo.security.SecurityUtils;
import com.edulingo.service.PersonalizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/assessment")
public class AssessmentController {

    private static final Logger log = LoggerFactory.getLogger(AssessmentController.class);
    private static final List<String> CEFR = List.of("A1", "A2", "B1", "B2", "C1", "C2");

    private final PersonalizationService personalization;
    private final LearningSessionRepository sessions;
    private final SecurityUtils security;
    private final ObjectMapper mapper;

    /** level → bank data loaded from assessment-questions.json */
    private final Map<String, JsonNode> questionBank = new HashMap<>();

    public AssessmentController(PersonalizationService personalization,
                                 LearningSessionRepository sessions,
                                 SecurityUtils security,
                                 ObjectMapper mapper) {
        this.personalization = personalization;
        this.sessions        = sessions;
        this.security        = security;
        this.mapper          = mapper;
    }

    @PostConstruct
    void loadBank() {
        try {
            ClassPathResource res = new ClassPathResource("assessment-questions.json");
            JsonNode root = mapper.readTree(res.getInputStream());
            root.fields().forEachRemaining(e -> questionBank.put(e.getKey(), e.getValue()));
            log.info("Loaded assessment questions for {} levels", questionBank.size());
        } catch (Exception e) {
            log.error("Failed to load assessment questions: {}", e.getMessage());
        }
    }

    /** GET /api/assessment/start — trả về bộ câu hỏi cho level hiện tại */
    @GetMapping("/start")
    public Mono<AssessmentStartResponse> start() {
        return security.currentEmail()
                .flatMap(email -> Mono.fromCallable(() -> buildStart(email))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /** POST /api/assessment/submit — chấm điểm + cập nhật level nếu đủ điều kiện */
    @PostMapping("/submit")
    public Mono<AssessmentResultResponse> submit(@Valid @RequestBody AssessmentSubmitRequest req) {
        return security.currentEmail()
                .flatMap(email -> Mono.fromCallable(() -> evaluate(email, req.answers()))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private AssessmentStartResponse buildStart(String email) {
        LearnerProfile profile = personalization.getOrCreate(email);
        String level = profile.getCefrLevel();
        JsonNode bank = questionBank.get(level);

        if (bank == null) {
            // C2 — đã đạt cấp cao nhất
            return new AssessmentStartResponse(level, level,
                    "Bạn đã đạt trình độ C2 — cấp độ cao nhất!", List.of());
        }

        String targetLevel  = bank.path("targetLevel").asText();
        String description  = bank.path("description").asText();
        List<AssessmentQuestion> questions = new ArrayList<>();
        bank.path("questions").forEach(q -> questions.add(new AssessmentQuestion(
                q.path("id").asInt(),
                q.path("question").asText(),
                toList(q.path("options")),
                q.path("correct").asInt(),
                q.path("explanation").asText()
        )));

        return new AssessmentStartResponse(level, targetLevel, description, questions);
    }

    private AssessmentResultResponse evaluate(String email, List<Integer> answers) {
        LearnerProfile profile = personalization.getOrCreate(email);
        String prevLevel = profile.getCefrLevel();
        JsonNode bank    = questionBank.get(prevLevel);

        if (bank == null) {
            return new AssessmentResultResponse(0, 0, 100, prevLevel, prevLevel,
                    false, "Bạn đã đạt C2 — trình độ cao nhất!", List.of());
        }

        List<JsonNode> qs = new ArrayList<>();
        bank.path("questions").forEach(qs::add);

        int correct = 0;
        List<AssessmentResultResponse.QuestionResult> results = new ArrayList<>();

        for (int i = 0; i < qs.size(); i++) {
            JsonNode q      = qs.get(i);
            int rightAnswer = q.path("correct").asInt();
            int chosen      = (i < answers.size()) ? answers.get(i) : -1;
            boolean ok      = chosen == rightAnswer;
            if (ok) correct++;
            results.add(new AssessmentResultResponse.QuestionResult(
                    q.path("id").asInt(),
                    q.path("question").asText(),
                    chosen, rightAnswer, ok,
                    q.path("explanation").asText()
            ));
        }

        int total = qs.size();
        int score = total > 0 ? (correct * 100 / total) : 0;

        // Level up nếu đạt ≥ 80%
        String newLevel = prevLevel;
        boolean leveledUp = false;
        int idx = CEFR.indexOf(prevLevel);
        if (score >= 80 && idx < CEFR.size() - 1) {
            newLevel = CEFR.get(idx + 1);
            leveledUp = true;
            personalization.updateLevel(email, newLevel);
        }

        // Record session
        LearningSession s = new LearningSession();
        s.setLearnerId(profile.getId());
        s.setSessionType("ASSESSMENT");
        s.setTopicId("assessment_" + prevLevel);
        s.setTopicName("Kiểm tra " + prevLevel + " → " + bank.path("targetLevel").asText());
        s.setScore(score);
        sessions.save(s);

        String message = leveledUp
                ? "🎉 Xuất sắc! Bạn đã lên cấp từ %s lên %s!".formatted(prevLevel, newLevel)
                : score >= 60
                ? "👍 Tốt lắm! Hãy ôn luyện thêm để đạt %s.".formatted(bank.path("targetLevel").asText())
                : "💪 Tiếp tục cố gắng! Luyện tập thêm để củng cố nền tảng %s.".formatted(prevLevel);

        return new AssessmentResultResponse(correct, total, score,
                prevLevel, newLevel, leveledUp, message, results);
    }

    private List<String> toList(JsonNode node) {
        List<String> list = new ArrayList<>();
        node.forEach(n -> list.add(n.asText()));
        return list;
    }
}
