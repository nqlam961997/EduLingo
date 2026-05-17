package com.edulingo.service;

import com.edulingo.dto.ChatMessage;
import com.edulingo.dto.ChatRequest;
import com.edulingo.dto.ErrorItem;
import com.edulingo.dto.ScenarioResponse;
import com.edulingo.dto.TopicDto;
import com.edulingo.entity.ChatSession;
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

    private final AiService aiService;
    private final PersonalizationService personalization;
    private final PromptAssembler promptAssembler;
    private final ChatSessionService sessions;
    private final ScratchpadExtractor extractor;
    private final ObjectMapper mapper;

    public ChatService(AiService aiService,
                       PersonalizationService personalization,
                       PromptAssembler promptAssembler,
                       ChatSessionService sessions,
                       ScratchpadExtractor extractor,
                       ObjectMapper mapper) {
        this.aiService = aiService;
        this.personalization = personalization;
        this.promptAssembler = promptAssembler;
        this.sessions = sessions;
        this.extractor = extractor;
        this.mapper = mapper;
    }

    /**
     * Start a chat session. Creates a chat_session row, returns the topic's
     * curated scenario seed and opening line — NO AI call.
     */
    public Mono<ScenarioResponse> startScenario(String email, String topicId) {
        return Mono.fromCallable(() -> {
                    var profile = personalization.getOrCreate(email);
                    TopicDto.Topic topic = TopicDto.requireTopic(topicId);
                    ChatSession session = sessions.create(profile.getId(), topicId);
                    return new ScenarioResponse(
                            topic.persona().scenarioSeed(),
                            topic.persona().opening(),
                            topic.characterName(),
                            topic.characterRole(),
                            topic.characterAvatar(),
                            topic.persona().suggestPolicy().name(),
                            session.getId().toString());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Stream a reply for a chat turn. Loads the session's scratchpad (if any),
     * builds the system prompt via PromptAssembler, sends a proper multi-turn
     * messages array, then triggers async scratchpad extraction on completion.
     */
    public Flux<String> reply(String email, ChatRequest req) {
        return Mono.fromCallable(() -> personalization.getOrCreate(email))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(profile -> {
                    TopicDto.Topic topic = TopicDto.requireTopic(req.topicId());

                    final ChatSession session;
                    if (req.sessionId() != null && !req.sessionId().isBlank()) {
                        session = sessions.requireOwned(
                                UUID.fromString(req.sessionId()), profile.getId());
                    } else {
                        // Backward-compat: pre-Phase-2 clients without sessionId.
                        session = null;
                        log.debug("Chat reply with no sessionId — stateless turn");
                    }

                    String scratchpadRendered = session != null
                            ? renderScratchpadForPrompt(session.getScratchpadJson(), topic.type())
                            : null;
                    String topErrors = personalization.topErrorsSummary(profile.getId());
                    String systemPrompt = promptAssembler.assembleSystemPrompt(
                            topic, profile, topErrors, scratchpadRendered);
                    List<ChatMessage> messages = promptAssembler.assembleMessages(
                            req.history(), req.message());

                    StringBuilder accumulated = new StringBuilder();
                    return aiService.streamGenerate(systemPrompt, messages)
                            .doOnNext(accumulated::append)
                            .doOnComplete(() -> {
                                saveErrorsAsync(profile.getId(), accumulated.toString());
                                if (session != null) {
                                    sessions.touch(session.getId());
                                    triggerExtractionAsync(session, topic, messages, accumulated.toString());
                                }
                            });
                });
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Render the scratchpad JSON into the human-readable block the
     * PromptAssembler expects. Returns null if no scratchpad or parse fails.
     */
    private String renderScratchpadForPrompt(String scratchpadJson, com.edulingo.dto.TopicType type) {
        if (scratchpadJson == null || scratchpadJson.isBlank()) return null;
        try {
            var node = mapper.readTree(scratchpadJson);
            StringBuilder sb = new StringBuilder();
            node.fields().forEachRemaining(e ->
                    sb.append("- ").append(e.getKey()).append(": ")
                      .append(e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString())
                      .append('\n'));
            String out = sb.toString().trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            log.warn("Failed to render scratchpad JSON: {}", e.getMessage());
            return null;
        }
    }

    private void triggerExtractionAsync(ChatSession session,
                                        TopicDto.Topic topic,
                                        List<ChatMessage> messages,
                                        String fullReply) {
        // Reconstruct the post-reply messages including the assistant's reply
        List<ChatMessage> postTurn = new ArrayList<>(messages.size() + 1);
        postTurn.addAll(messages);
        postTurn.add(ChatMessage.assistant(extractReplyTextOrEmpty(fullReply)));

        extractor.extract(session.getId(), topic, postTurn, session.getScratchpadJson())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        json -> {
                            if (json != null && !json.isBlank()) {
                                sessions.updateScratchpad(session.getId(), json);
                            }
                        },
                        err -> log.warn("scratchpad_extraction_failed session={} {}",
                                session.getId(), err.getMessage())
                );
    }

    private String extractReplyTextOrEmpty(String fullText) {
        try {
            String text = fullText.trim()
                    .replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            int start = text.indexOf('{');
            int end   = text.lastIndexOf('}');
            if (start < 0 || end <= start) return fullText;
            var node = mapper.readTree(text.substring(start, end + 1));
            return node.path("reply").asText(fullText);
        } catch (Exception e) {
            return fullText;
        }
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
}
