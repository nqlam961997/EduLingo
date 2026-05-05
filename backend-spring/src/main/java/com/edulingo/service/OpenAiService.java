package com.edulingo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Primary
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String visionModel;

    public OpenAiService(@Qualifier("openAiWebClient") WebClient webClient,
                         ObjectMapper mapper,
                         @Value("${openai.api-key}") String apiKey,
                         @Value("${openai.model:gpt-4o-mini}") String model,
                         @Value("${openai.vision-model:gpt-4o-mini}") String visionModel) {
        this.webClient    = webClient;
        this.mapper       = mapper;
        this.apiKey       = apiKey;
        this.model        = model;
        this.visionModel  = visionModel;
    }

    // ── generate (non-streaming) ─────────────────────────────────────────────
    @Override
    public Mono<String> generate(String systemPrompt, String userText) {
        Map<String, Object> body = chatBody(model, systemPrompt, userText, false);
        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(b -> openAiError(resp.statusCode(), b)))
                .bodyToMono(JsonNode.class)
                .map(this::extractContent)
                .retryWhen(retryOn429())
                .doOnError(e -> log.error("OpenAI generate error: {}", e.getMessage()));
    }

    // ── streamGenerate (SSE) ─────────────────────────────────────────────────
    @Override
    public Flux<String> streamGenerate(String systemPrompt, String userText) {
        Map<String, Object> body = chatBody(model, systemPrompt, userText, true);
        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(b -> openAiError(resp.statusCode(), b)))
                .bodyToFlux(String.class)
                .mapNotNull(this::extractDeltaOrNull)
                .filter(s -> !s.isEmpty())
                .retryWhen(retryOn429())
                .doOnError(e -> log.error("OpenAI stream error: {}", e.getMessage()));
    }

    // ── generateWithBase64Image (vision) ────────────────────────────────────
    @Override
    public Mono<String> generateWithBase64Image(String systemPrompt, String userText,
                                                 String base64Data, String mimeType) {
        // GPT-4o-mini supports vision via image_url with data URI
        List<Map<String, Object>> userContent = List.of(
                Map.of("type", "text", "text", userText),
                Map.of("type", "image_url",
                       "image_url", Map.of("url", "data:" + mimeType + ";base64," + base64Data,
                                           "detail", "low"))
        );
        Map<String, Object> body = Map.of(
                "model",    visionModel,
                "messages", List.of(
                        Map.of("role", "system",  "content", systemPrompt),
                        Map.of("role", "user",    "content", userContent)
                )
        );
        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(b -> openAiError(resp.statusCode(), b)))
                .bodyToMono(JsonNode.class)
                .map(this::extractContent)
                .doOnError(e -> log.error("OpenAI vision error: {}", e.getMessage()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> chatBody(String mdl, String system, String user, boolean stream) {
        return Map.of(
                "model",    mdl,
                "stream",   stream,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user",   "content", user)
                )
        );
    }

    /** Extract content from a non-streaming response. */
    private String extractContent(JsonNode root) {
        return root.path("choices").path(0)
                   .path("message").path("content").asText("");
    }

    /**
     * Parse an SSE line from OpenAI streaming.
     * Lines look like: "data: {...}" or "data: [DONE]"
     */
    private String extractDeltaOrNull(String line) {
        if (!line.startsWith("data:")) return null;
        String json = line.substring(5).trim();
        if ("[DONE]".equals(json)) return null;
        try {
            JsonNode node = mapper.readTree(json);
            String delta = node.path("choices").path(0)
                               .path("delta").path("content").asText("");
            return delta;
        } catch (Exception e) {
            log.debug("Could not parse SSE line: {}", line);
            return null;
        }
    }

    private Mono<Throwable> openAiError(HttpStatusCode status, String body) {
        log.error("OpenAI API error {}: {}", status.value(), body);
        if (status.value() == 429) {
            return Mono.error(new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI rate limit exceeded, please retry shortly"));
        }
        if (status.value() == 401) {
            return Mono.error(new ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid OpenAI API key"));
        }
        return Mono.error(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "OpenAI error " + status.value()));
    }

    private Retry retryOn429() {
        return Retry.backoff(2, Duration.ofSeconds(3))
                .filter(e -> e instanceof ResponseStatusException rex
                             && rex.getStatusCode().value() == 503)
                .doBeforeRetry(s -> log.warn("OpenAI 429, retrying ({}/{})...",
                        s.totalRetries() + 1, 2));
    }
}
