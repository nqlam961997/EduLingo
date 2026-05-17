package com.edulingo.service;

import com.edulingo.dto.ChatMessage;
import com.edulingo.dto.TopicDto;
import com.edulingo.dto.TopicType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Default extractor implementation: calls the configured {@link AiService}
 * with a focused per-topic-type prompt. Validates the returned JSON against
 * the topic-type's schema; on failure or timeout, completes empty so callers
 * retain the previous scratchpad.
 */
@Service
public class DefaultScratchpadExtractor implements ScratchpadExtractor {

    private static final Logger log = LoggerFactory.getLogger(DefaultScratchpadExtractor.class);

    private final AiService aiService;
    private final ObjectMapper mapper;
    private final Duration timeout;

    public DefaultScratchpadExtractor(AiService aiService,
                                      ObjectMapper mapper,
                                      @Value("${chat.scratchpad.extraction-timeout-seconds:5}") int timeoutSeconds) {
        this.aiService = aiService;
        this.mapper = mapper;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public Mono<String> extract(UUID sessionId,
                                TopicDto.Topic topic,
                                List<ChatMessage> messages,
                                String previousJson) {
        String systemPrompt = buildExtractionPrompt(topic, previousJson);
        String userText = buildExtractionUserText(messages);
        return aiService.generate(systemPrompt, List.of(ChatMessage.user(userText)))
                .timeout(timeout)
                .flatMap(raw -> validateAndNormalize(raw, topic))
                .onErrorResume(err -> {
                    log.warn("scratchpad_extraction_failed session={} {}",
                            sessionId, err.getMessage());
                    return Mono.empty();
                });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String buildExtractionPrompt(TopicDto.Topic topic, String previousJson) {
        String schemaHint = schemaHintFor(topic);
        String prev = (previousJson == null || previousJson.isBlank()) ? "(none)" : previousJson;
        return """
                You are a state-extraction assistant. Given the conversation so far
                between an AI character and an English learner, produce the updated
                scenario-state JSON for the next turn.

                Topic: %s (%s).
                Persona: %s.

                Schema:
                %s

                Previous state:
                %s

                Output a single JSON object only. No prose, no markdown, no code fences.
                If a field is unknown, omit it (do not invent values).
                """.formatted(topic.name(), topic.type(), topic.persona().name(),
                schemaHint, prev);
    }

    private String schemaHintFor(TopicDto.Topic topic) {
        return switch (topic.type()) {
            case TRANSACTIONAL -> {
                String slots = String.join(", ", topic.persona().slots());
                yield "Slots (omit unfilled): " + slots;
            }
            case ASYMMETRIC -> {
                String phases = String.join(" → ", topic.persona().phases());
                yield "Fields: phase (current), phasesDone (list), phasesLeft (list),\n"
                        + "facts (key:value bag of what the candidate revealed),\n"
                        + "silentErrors (list of {original, fixed, type}).\n"
                        + "Phase order: " + phases;
            }
            case FREE_FORM -> {
                String beats = String.join(" → ", topic.persona().rhythmBeats());
                yield "Fields: rhythmBeat (int 1..N), nextBeat (string),\n"
                        + "topicsTouched (list), learnerHabits (key:value bag),\n"
                        + "aiSharedFacts (key:value bag), openLoops (list).\n"
                        + "Beats: " + beats;
            }
        };
    }

    private String buildExtractionUserText(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder("Conversation:\n");
        for (ChatMessage m : messages) {
            sb.append(m.role() == ChatMessage.MessageRole.ASSISTANT ? "ASSISTANT: " : "USER: ");
            sb.append(m.content()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Validate the extracted JSON minimally — must be a JSON object with at
     * least one key. Topic-type-specific stricter validation can be layered
     * here as topics evolve.
     */
    private Mono<String> validateAndNormalize(String raw, TopicDto.Topic topic) {
        if (raw == null || raw.isBlank()) return Mono.empty();
        try {
            String text = raw.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();
            int start = text.indexOf('{');
            int end   = text.lastIndexOf('}');
            if (start < 0 || end <= start) return Mono.empty();
            String json = text.substring(start, end + 1);
            JsonNode node = mapper.readTree(json);
            if (!node.isObject() || node.size() == 0) return Mono.empty();

            // Stricter validation per type
            switch (topic.type()) {
                case ASYMMETRIC -> {
                    if (!node.has("phase")) return Mono.empty();
                }
                case FREE_FORM -> {
                    if (!node.has("rhythmBeat") && !node.has("topicsTouched")) {
                        return Mono.empty();
                    }
                }
                case TRANSACTIONAL -> {
                    // No required slots — extractor may produce empty slot maps
                    // on the first turn.
                }
            }
            return Mono.just(mapper.writeValueAsString(node));
        } catch (Exception e) {
            return Mono.empty();
        }
    }
}
