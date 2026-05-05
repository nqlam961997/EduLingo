package com.edulingo.service;

import com.edulingo.dto.ErrorItem;
import com.edulingo.entity.ErrorPattern;
import com.edulingo.entity.LearnerProfile;
import com.edulingo.mapper.ErrorPatternMapper;
import com.edulingo.repository.ErrorPatternRepository;
import com.edulingo.repository.LearnerProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PersonalizationService {

    private final LearnerProfileRepository profiles;
    private final ErrorPatternRepository patterns;
    private final ErrorPatternMapper mapper;

    public PersonalizationService(LearnerProfileRepository profiles,
                                  ErrorPatternRepository patterns,
                                  ErrorPatternMapper mapper) {
        this.profiles = profiles;
        this.patterns = patterns;
        this.mapper = mapper;
    }

    @Transactional
    public LearnerProfile getOrCreate(String email) {
        return profiles.findByEmail(email).orElseGet(() -> {
            LearnerProfile p = new LearnerProfile();
            p.setEmail(email);
            return profiles.save(p);
        });
    }

    public String chatSystemPrompt(LearnerProfile p) {
        String topErrors = topErrorsAsBullets(p.getId(), 5);
        return """
                You are an English tutor for a %s learner.
                Goal: %s.

                Their recurring mistakes (avoid letting them slip):
                %s

                Rules:
                1. Keep replies short and natural at level %s.
                2. Gently flag any of their recurring mistakes when they appear.
                3. After your reply, return JSON only:
                   {"reply":"...","suggestions":["...","...","..."]}
                """.formatted(
                p.getCefrLevel(),
                p.getLearningGoal() == null ? "general English" : p.getLearningGoal(),
                topErrors.isBlank() ? "(none recorded yet)" : topErrors,
                p.getCefrLevel()
        );
    }

    public String pictureSystemPrompt(LearnerProfile p) {
        String topErrors = topErrorsAsBullets(p.getId(), 5);
        return """
                You are an English writing tutor for a %s learner.
                The learner will send an image and an English description.

                Recurring mistakes to watch for:
                %s

                Return JSON only:
                {
                  "corrected": "natural rewrite at level %s + 1 small step up",
                  "errors": [{"type":"...", "original":"...", "fixed":"...", "explain_vi":"..."}],
                  "score": 0-100,
                  "tips": ["1-2 short tips"]
                }
                """.formatted(
                p.getCefrLevel(),
                topErrors.isBlank() ? "(none recorded yet)" : topErrors,
                p.getCefrLevel()
        );
    }

    public String pictureDescribeSystemPrompt(LearnerProfile p,
                                               com.edulingo.dto.TopicDto.Topic topic,
                                               String imageDescription) {
        String topErrors = topErrorsAsBullets(p.getId(), 5);
        String imageContext = imageDescription.isBlank()
                ? "Topic: \"%s\" — %s.".formatted(topic.name(), topic.description())
                : "Topic: \"%s\". The picture shows: %s".formatted(topic.name(), imageDescription);

        return """
                You are an English writing tutor for a %s-level learner.
                %s

                The learner wrote a short English description of what they see in the picture.
                Evaluate their description in context of the picture above.

                Recurring mistakes to watch for:
                %s

                IMPORTANT: Reply with ONLY valid JSON — no markdown, no code fences, no extra text before or after.
                {
                  "corrected": "a natural rewrite slightly above %s level",
                  "errors": [{"type":"Grammar|Vocabulary|Spelling|Structure","original":"...","fixed":"...","explain_vi":"..."}],
                  "score": <integer 0-100>,
                  "tips": ["tip in Vietnamese", "optional second tip"]
                }
                """.formatted(
                p.getCefrLevel(),
                imageContext,
                topErrors.isBlank() ? "(none recorded yet)" : topErrors,
                p.getCefrLevel()
        );
    }

    public String questionAnswerSystemPrompt(LearnerProfile p,
                                              com.edulingo.dto.TopicDto.Topic topic,
                                              String imageScene,
                                              String question) {
        return """
                You are an English speaking tutor for a %s-level learner.
                Topic: "%s". The picture shows: %s

                The tutor asked: "%s"
                The learner replied with their answer below.

                Evaluate the learner's English answer. Be encouraging and concise.

                IMPORTANT: Reply with ONLY valid JSON — no markdown, no code fences, no extra text.
                {
                  "feedback": "short encouraging feedback in Vietnamese (1-2 sentences)",
                  "corrected": "a better version of their answer in English (empty string if already correct)",
                  "score": <integer 0-100>,
                  "isCorrect": <true if answer is relevant and reasonable, false otherwise>
                }
                """.formatted(
                p.getCefrLevel(),
                topic.name(),
                imageScene.isBlank() ? topic.description() : imageScene,
                question
        );
    }

    @Transactional
    public void recordErrors(UUID learnerId, List<ErrorItem> errors) {
        for (ErrorItem item : errors) {
            patterns.findByLearnerIdAndErrorType(learnerId, item.type()).ifPresentOrElse(
                    existing -> mapper.incrementWith(existing, item),
                    () -> patterns.save(mapper.toEntity(learnerId, item))
            );
        }
    }

    public String topErrorsSummary(UUID learnerId) {
        return topErrorsAsBullets(learnerId, 5);
    }

    @Transactional
    public void updateLevel(String email, String newLevel) {
        profiles.findByEmail(email).ifPresent(p -> {
            p.setCefrLevel(newLevel);
            profiles.save(p);
        });
    }

    private String topErrorsAsBullets(UUID learnerId, int n) {
        List<ErrorPattern> top = patterns.findTopByLearner(learnerId, PageRequest.of(0, n));
        return top.stream()
                .map(e -> "- %s (x%d): %s".formatted(e.getErrorType(), e.getCount(),
                        e.getExample() == null ? "" : e.getExample()))
                .collect(Collectors.joining("\n"));
    }
}
