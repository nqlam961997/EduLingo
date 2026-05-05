package com.edulingo.service;

import com.edulingo.dto.AnswerFeedbackResponse;
import com.edulingo.dto.CorrectionResponse;
import com.edulingo.dto.GeneratedImageResponse;
import com.edulingo.dto.QuestionListResponse;
import com.edulingo.dto.TopicDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PictureService {

    private static final Logger log = LoggerFactory.getLogger(PictureService.class);

    private final GeminiService gemini;
    private final AiService aiService;
    private final PersonalizationService personalization;
    private final ImageCacheService imageCache;
    private final ObjectMapper mapper;

    /** topicId → scene description loaded from descriptions.json */
    private final Map<String, String> imageDescriptions;

    /** topicId → ordered list of questions loaded from descriptions.json */
    private final Map<String, List<String>> imageQuestions;

    public PictureService(GeminiService gemini,
                          AiService aiService,
                          PersonalizationService personalization,
                          ImageCacheService imageCache,
                          ObjectMapper mapper) {
        this.gemini = gemini;
        this.aiService = aiService;
        this.personalization = personalization;
        this.imageCache = imageCache;
        this.mapper = mapper;
        this.imageDescriptions = new HashMap<>();
        this.imageQuestions = new HashMap<>();
        loadDescriptionsAndQuestions(mapper, this.imageDescriptions, this.imageQuestions);
    }

    private static void loadDescriptionsAndQuestions(ObjectMapper mapper,
                                                      Map<String, String> descriptions,
                                                      Map<String, List<String>> questions) {
        try {
            ClassPathResource res = new ClassPathResource("static/topic-images/descriptions.json");
            if (res.exists()) {
                JsonNode root = mapper.readTree(res.getInputStream());
                root.fields().forEachRemaining(entry -> {
                    String topicId = entry.getKey();
                    JsonNode node = entry.getValue();
                    descriptions.put(topicId, node.path("scene").asText(""));
                    List<String> qs = new java.util.ArrayList<>();
                    node.path("questions").forEach(q -> qs.add(q.asText()));
                    questions.put(topicId, qs);
                });
                log.info("Loaded descriptions and questions for {} topics", descriptions.size());
            }
        } catch (Exception e) {
            log.warn("Could not load image descriptions: {}", e.getMessage());
        }
    }

    public Mono<GeneratedImageResponse> generateImage(String topicId) {
        return Mono.fromCallable(() -> loadStaticImage(topicId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> {
                    if (result != null) {
                        // Dùng ảnh static đã lưu sẵn
                        String id = imageCache.store(result[0], result[1], topicId);
                        return Mono.just(new GeneratedImageResponse(id, result[0], result[1], topicId));
                    }
                    // Fallback: gọi Gemini nếu không có ảnh static
                    log.warn("No static image for topic '{}', falling back to Gemini", topicId);
                    TopicDto.Topic topic = findTopic(topicId);
                    String prompt = "Generate a clear, colorful illustration for English learning. " +
                            "Topic: %s — %s. Realistic scene, no text in image.".formatted(topic.name(), topic.description());
                    return gemini.generateImage(prompt)
                            .map(img -> {
                                String id = imageCache.store(img.base64Data(), img.mimeType(), topicId);
                                return new GeneratedImageResponse(id, img.base64Data(), img.mimeType(), topicId);
                            });
                });
    }

    /**
     * Đọc ảnh từ classpath: static/topic-images/{topicId}.jpg (hoặc .png, .webp)
     * Trả về [base64Data, mimeType] hoặc null nếu không tìm thấy.
     */
    private String[] loadStaticImage(String topicId) {
        String[][] candidates = {
            {"static/topic-images/" + topicId + ".jpg",  "image/jpeg"},
            {"static/topic-images/" + topicId + ".jpeg", "image/jpeg"},
            {"static/topic-images/" + topicId + ".png",  "image/png"},
            {"static/topic-images/" + topicId + ".webp", "image/webp"},
        };
        for (String[] candidate : candidates) {
            try {
                ClassPathResource resource = new ClassPathResource(candidate[0]);
                if (resource.exists()) {
                    byte[] bytes = resource.getContentAsByteArray();
                    return new String[]{Base64.getEncoder().encodeToString(bytes), candidate[1]};
                }
            } catch (Exception e) {
                log.debug("Could not load {}: {}", candidate[0], e.getMessage());
            }
        }
        return null;
    }

    public Mono<CorrectionResponse> correct(String email, String imageId, String userDescription) {
        ImageCacheService.CachedImage cached = imageCache.get(imageId);
        TopicDto.Topic topic = findTopic(cached.topicId());
        String imageDescription = imageDescriptions.getOrDefault(cached.topicId(), "");

        return Mono.fromCallable(() -> personalization.getOrCreate(email))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(profile -> {
                    String systemPrompt = personalization.pictureDescribeSystemPrompt(
                            profile, topic, imageDescription);
                    String userText = "Learner description: " + userDescription;
                    return aiService.generate(systemPrompt, userText)
                            .map(this::parse)
                            .doOnNext(resp -> persistErrors(profile.getId(), resp));
                });
    }

    private CorrectionResponse parse(String raw) {
        try {
            // Strip markdown code fences (```json ... ```)
            String text = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            // Extract only the outermost JSON object { ... }
            int start = text.indexOf('{');
            int end   = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }

            return mapper.readValue(text, CorrectionResponse.class);
        } catch (Exception e) {
            log.warn("Failed to parse AI response as JSON, wrapping as corrected text: {}", e.getMessage());
            // Return a safe fallback so the UI never shows raw JSON
            return new CorrectionResponse(raw.trim(), List.of(), 0,
                    List.of("Không thể phân tích phản hồi từ AI. Vui lòng thử lại."));
        }
    }

    private void persistErrors(UUID learnerId, CorrectionResponse resp) {
        if (resp.errors() == null || resp.errors().isEmpty()) return;
        Mono.fromRunnable(() -> personalization.recordErrors(learnerId, resp.errors()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    public QuestionListResponse getQuestions(String imageId) {
        ImageCacheService.CachedImage cached = imageCache.get(imageId);
        String topicId = cached.topicId();
        List<String> qs = imageQuestions.getOrDefault(topicId, List.of(
                "What do you see in the picture?",
                "Describe the main objects.",
                "What is the atmosphere like?",
                "Who or what is the focus of the picture?",
                "How does this picture make you feel?"
        ));
        String scene = imageDescriptions.getOrDefault(topicId, "");
        return new QuestionListResponse(imageId, topicId, scene, qs);
    }

    public Mono<AnswerFeedbackResponse> evaluateAnswer(String email, String imageId,
                                                        int questionIndex, String answer) {
        ImageCacheService.CachedImage cached = imageCache.get(imageId);
        TopicDto.Topic topic = findTopic(cached.topicId());
        String scene = imageDescriptions.getOrDefault(cached.topicId(), "");
        List<String> qs = imageQuestions.getOrDefault(cached.topicId(), List.of());
        String question = questionIndex < qs.size() ? qs.get(questionIndex) : "Describe what you see.";

        return Mono.fromCallable(() -> personalization.getOrCreate(email))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(profile -> {
                    String systemPrompt = personalization.questionAnswerSystemPrompt(profile, topic, scene, question);
                    return aiService.generate(systemPrompt, answer)
                            .map(this::parseAnswerFeedback)
                            .doOnNext(fb -> {
                                if (fb.corrected() != null && !fb.corrected().isBlank()) {
                                    var errItem = new com.edulingo.dto.ErrorItem("Answer", answer, fb.corrected(), "");
                                    persistErrors(profile.getId(), new CorrectionResponse(
                                            fb.corrected(), List.of(errItem), fb.score(), List.of()));
                                }
                            });
                });
    }

    private AnswerFeedbackResponse parseAnswerFeedback(String raw) {
        try {
            String text = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();
            int start = text.indexOf('{');
            int end   = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
            JsonNode node = mapper.readTree(text);
            return new AnswerFeedbackResponse(
                    node.path("feedback").asText(""),
                    node.path("corrected").asText(""),
                    node.path("score").asInt(50),
                    node.path("isCorrect").asBoolean(true)
            );
        } catch (Exception e) {
            log.warn("Failed to parse answer feedback: {}", e.getMessage());
            return new AnswerFeedbackResponse(raw.trim(), "", 50, true);
        }
    }

    private TopicDto.Topic findTopic(String topicId) {
        return TopicDto.TOPICS.stream()
                .filter(t -> t.id().equals(topicId))
                .findFirst()
                .orElse(new TopicDto.Topic(topicId, topicId, "", "", "Tutor", "English Tutor", "🧑‍🏫"));
    }
}
