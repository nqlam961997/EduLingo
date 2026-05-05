package com.edulingo.service;

import com.edulingo.dto.ChatRequest;
import com.edulingo.dto.ErrorItem;
import com.edulingo.dto.ScenarioResponse;
import com.edulingo.dto.TopicDto;
import com.edulingo.entity.LearnerProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final AiService gemini;
    private final PersonalizationService personalization;
    private final ObjectMapper mapper;

    public ChatService(AiService gemini, PersonalizationService personalization, ObjectMapper mapper) {
        this.gemini = gemini;
        this.personalization = personalization;
        this.mapper = mapper;
    }

    public Mono<ScenarioResponse> startScenario(String email, String topicId) {
        return Mono.fromCallable(() -> personalization.getOrCreate(email))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(profile -> {
                    TopicDto.Topic topic = findTopic(topicId);
                    String prompt = buildScenarioPrompt(profile, topic);
                    return gemini.generate(prompt,
                                    "Create a scenario for topic: " + topic.name())
                            .map(json -> parseScenario(json, topic));
                });
    }

    public Flux<String> reply(String email, ChatRequest req) {
        return Mono.fromCallable(() -> personalization.getOrCreate(email))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(profile -> {
                    TopicDto.Topic topic = findTopic(req.topicId());
                    String systemPrompt = buildRoleplayPrompt(profile, topic, req.scenario());
                    String conversation = buildConversation(req.history(), req.message());
                    StringBuilder accumulated = new StringBuilder();
                    return gemini.streamGenerate(systemPrompt, conversation)
                            .doOnNext(accumulated::append)
                            .doOnComplete(() -> saveErrorsAsync(profile.getId(), accumulated.toString()));
                });
    }

    private String buildScenarioPrompt(LearnerProfile p, TopicDto.Topic topic) {
        return """
                You are an English tutor for a %s learner.
                Create a realistic roleplay scenario for the topic "%s" (%s).
                The AI character is named "%s" who is a %s.

                Return JSON only:
                {"scenario":"2-3 sentence description of the situation","openingMessage":"your first line as %s greeting the learner"}

                Keep language at %s level. Stay in character as %s.
                """.formatted(p.getCefrLevel(), topic.name(), topic.description(),
                topic.characterName(), topic.characterRole(),
                topic.characterName(), p.getCefrLevel(), topic.characterName());
    }

    private String buildRoleplayPrompt(LearnerProfile p, TopicDto.Topic topic, String scenario) {
        String topErrors = personalization.topErrorsSummary(p.getId());
        return """
                You are %s, a %s. You are roleplaying in this scenario: %s
                Topic: %s

                The learner is at %s level. Stay in character as %s and keep your language at that level.

                Their recurring mistakes (gently correct when they appear):
                %s

                IMPORTANT OUTPUT FORMAT:
                You MUST reply with ONLY a valid JSON object, nothing else. No markdown, no code fences, no extra text.
                {
                  "reply": "your in-character response as %s (2-4 sentences, with gentle corrections in parentheses if needed)",
                  "suggestions": ["option 1","option 2","option 3"],
                  "errors": []
                }

                Rules:
                1. Stay in character as %s. Respond naturally with personality fitting your role.
                2. If the learner makes a real English mistake (grammar, vocabulary, spelling, article, tense, or structure), include a gentle correction in parentheses inside "reply" AND add it to "errors".
                3. Keep responses short (2-4 sentences).
                4. "suggestions" must contain exactly 3 possible responses the learner could say next.
                5. "errors" must ONLY contain mistakes from the learner's current message. If no mistakes, keep "errors" as [].
                   Each error object: {"type":"Grammar|Vocabulary|Spelling|Article|Tense|Structure","original":"exact wrong phrase","fixed":"corrected phrase","explain_vi":"brief explanation in Vietnamese"}
                """.formatted(topic.characterName(), topic.characterRole(), scenario, topic.name(),
                p.getCefrLevel(), topic.characterName(),
                topErrors.isBlank() ? "(none yet)" : topErrors,
                topic.characterName(), topic.characterName());
    }

    private void saveErrorsAsync(UUID learnerId, String fullText) {
        try {
            String text = fullText.trim()
                    .replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            int start = text.indexOf('{');
            int end   = text.lastIndexOf('}');
            if (start < 0 || end <= start) return;
            text = text.substring(start, end + 1);

            var node = mapper.readTree(text);
            var errorsNode = node.path("errors");
            if (!errorsNode.isArray() || errorsNode.isEmpty()) return;

            List<ErrorItem> errors = new ArrayList<>();
            for (var e : errorsNode) {
                String type     = e.path("type").asText("").trim();
                String original = e.path("original").asText("").trim();
                String fixed    = e.path("fixed").asText("").trim();
                String explain  = e.path("explain_vi").asText("").trim();
                if (!type.isBlank() && !original.isBlank()) {
                    errors.add(new ErrorItem(type, original, fixed, explain));
                }
            }
            if (errors.isEmpty()) return;

            Mono.fromRunnable(() -> personalization.recordErrors(learnerId, errors))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            null,
                            err -> log.warn("Failed to save chat errors: {}", err.getMessage())
                    );
        } catch (Exception e) {
            log.warn("Failed to parse errors from chat reply: {}", e.getMessage());
        }
    }

    private String buildConversation(List<ChatRequest.MessageItem> history, String newMessage) {
        StringBuilder sb = new StringBuilder();
        if (history != null) {
            for (ChatRequest.MessageItem m : history) {
                sb.append(m.role().equals("user") ? "Learner: " : "You: ");
                sb.append(m.content()).append("\n");
            }
        }
        sb.append("Learner: ").append(newMessage);
        return sb.toString();
    }

    private ScenarioResponse parseScenario(String json, TopicDto.Topic topic) {
        try {
            String cleaned = json.trim().replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
            var node = mapper.readTree(cleaned);
            String scenario = node.path("scenario").asText("A casual English conversation.");
            String opening = node.path("openingMessage").asText(json);
            return new ScenarioResponse(scenario, opening,
                    topic.characterName(), topic.characterRole(), topic.characterAvatar());
        } catch (Exception e) {
            return new ScenarioResponse("A casual English conversation.", json,
                    topic.characterName(), topic.characterRole(), topic.characterAvatar());
        }
    }

    private TopicDto.Topic findTopic(String topicId) {
        return TopicDto.TOPICS.stream()
                .filter(t -> t.id().equals(topicId))
                .findFirst()
                .orElse(new TopicDto.Topic(topicId, topicId, "", "", "Tutor", "English Tutor", "🧑‍🏫"));
    }
}
