package com.edulingo.controller;

import com.edulingo.dto.DashboardResponse;
import com.edulingo.dto.RecordSessionRequest;
import com.edulingo.entity.ErrorPattern;
import com.edulingo.entity.LearnerProfile;
import com.edulingo.entity.LearningSession;
import com.edulingo.repository.ErrorPatternRepository;
import com.edulingo.repository.LearningSessionRepository;
import com.edulingo.security.SecurityUtils;
import com.edulingo.service.PersonalizationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final List<String> CEFR_ORDER = List.of("A1", "A2", "B1", "B2", "C1", "C2");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Map<String, String> FOCUS_TIPS = Map.of(
            "Grammar",    "Luyện ngữ pháp: viết 3 câu mô tả ảnh mỗi ngày.",
            "Vocabulary", "Mở rộng từ vựng: học 5 từ mới theo chủ đề yêu thích.",
            "Spelling",   "Cải thiện chính tả: đọc to và viết lại câu vừa sửa.",
            "Article",    "Ôn mạo từ a/an/the: chú ý khi nhắc đến vật thể lần đầu và lần sau.",
            "Tense",      "Luyện thì: kể lại ngày hôm qua chỉ dùng quá khứ đơn.",
            "Structure",  "Cải thiện cấu trúc câu: thử dùng because, although, so."
    );

    private final PersonalizationService personalization;
    private final ErrorPatternRepository errorPatterns;
    private final LearningSessionRepository sessions;
    private final SecurityUtils security;

    public DashboardController(PersonalizationService personalization,
                                ErrorPatternRepository errorPatterns,
                                LearningSessionRepository sessions,
                                SecurityUtils security) {
        this.personalization = personalization;
        this.errorPatterns   = errorPatterns;
        this.sessions        = sessions;
        this.security        = security;
    }

    @GetMapping
    public Mono<DashboardResponse> getDashboard() {
        return security.currentEmail()
                .flatMap(email -> Mono.fromCallable(() -> buildDashboard(email))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Mono<Void> recordSession(@Valid @RequestBody RecordSessionRequest req) {
        return security.currentEmail()
                .flatMap(email -> Mono.fromRunnable(() -> {
                    LearnerProfile profile = personalization.getOrCreate(email);
                    LearningSession s = new LearningSession();
                    s.setLearnerId(profile.getId());
                    s.setSessionType(req.sessionType());
                    s.setTopicId(req.topicId());
                    s.setTopicName(req.topicName());
                    s.setScore(req.score());
                    sessions.save(s);
                }).subscribeOn(Schedulers.boundedElastic()).then());
    }

    private DashboardResponse buildDashboard(String email) {
        LearnerProfile profile = personalization.getOrCreate(email);
        List<ErrorPattern> errors = errorPatterns.findTopByLearner(
                profile.getId(), PageRequest.of(0, 6));

        String level  = profile.getCefrLevel();
        int cefrIdx   = Math.max(0, CEFR_ORDER.indexOf(level));

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(VN);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(VN);

        String since = dayFmt.format(profile.getCreatedAt());

        List<DashboardResponse.ErrorSummary> topErrors = errors.stream()
                .map(e -> new DashboardResponse.ErrorSummary(
                        e.getErrorType(), e.getCount(),
                        e.getExample() == null ? "" : e.getExample()))
                .toList();

        String focusTip = topErrors.isEmpty()
                ? "Bắt đầu luyện tập để nhận gợi ý cá nhân hóa!"
                : FOCUS_TIPS.getOrDefault(topErrors.get(0).type(),
                  "Tiếp tục luyện tập đều đặn để cải thiện!");

        // Session stats
        long total   = sessions.countByLearnerId(profile.getId());
        long chat    = sessions.countByLearnerIdAndSessionType(profile.getId(), "CHAT");
        long picture = sessions.countByLearnerIdAndSessionType(profile.getId(), "PICTURE");
        int avgScore = (int) Math.round(sessions.avgScoreByLearnerId(profile.getId()));
        int topicsCount = sessions.distinctTopicsByLearnerId(profile.getId()).size();

        List<DashboardResponse.SessionSummary> recent = sessions
                .findByLearnerIdOrderByCreatedAtDesc(profile.getId(), PageRequest.of(0, 7))
                .stream()
                .map(s -> new DashboardResponse.SessionSummary(
                        s.getSessionType(),
                        s.getTopicName() == null ? s.getTopicId() : s.getTopicName(),
                        s.getScore(),
                        timeFmt.format(s.getCreatedAt())))
                .toList();

        return new DashboardResponse(
                level, cefrIdx,
                profile.getLearningGoal() == null ? "General English" : profile.getLearningGoal(),
                since, topErrors, errors.size(), focusTip,
                total, chat, picture, avgScore, topicsCount, recent
        );
    }
}
