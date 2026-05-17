package com.edulingo.service;

import com.edulingo.dto.ChatMessage;
import com.edulingo.dto.GeneratedImageData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String imageModel;

    public GeminiService(WebClient geminiWebClient,
                         ObjectMapper objectMapper,
                         @Value("${gemini.api-key}") String apiKey,
                         @Value("${gemini.model}") String model,
                         @Value("${gemini.image-model}") String imageModel) {
        this.webClient = geminiWebClient;
        this.mapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.imageModel = imageModel;
    }

    @Override
    public Mono<String> generate(String systemPrompt, List<ChatMessage> messages) {
        return webClient.post()
                .uri(uri -> uri.path("/models/{m}:generateContent").queryParam("key", apiKey).build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(systemPrompt, messages, true))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body -> geminiError(resp.statusCode(), body)))
                .bodyToMono(JsonNode.class)
                .map(this::extractText)
                .retryWhen(retryOn503());
    }

    @Override
    public Flux<String> streamGenerate(String systemPrompt, List<ChatMessage> messages) {
        return webClient.post()
                .uri(uri -> uri.path("/models/{m}:streamGenerateContent")
                        .queryParam("alt", "sse")
                        .queryParam("key", apiKey).build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(systemPrompt, messages, false))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body -> geminiError(resp.statusCode(), body)))
                .bodyToFlux(String.class)
                .mapNotNull(this::extractTextOrNull)
                .filter(s -> !s.isEmpty())
                .retryWhen(retryOn503());
    }

    @Override
    public Mono<GeneratedImageData> generateImage(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of("responseModalities", List.of("IMAGE", "TEXT"))
        );
        return webClient.post()
                .uri(uri -> uri.path("/models/{m}:generateContent")
                        .queryParam("key", apiKey).build(imageModel))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body2 -> geminiError(resp.statusCode(), body2)))
                .bodyToMono(JsonNode.class)
                .map(this::extractImage)
                .retryWhen(retryOn503());
    }

    @Override
    public Mono<String> generateWithBase64Image(String systemPrompt, String userText,
                                                String base64Data, String mimeType) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(
                                Map.of("text", userText),
                                Map.of("inlineData", Map.of("mimeType", mimeType, "data", base64Data))
                        )
                )),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );
        return webClient.post()
                .uri(uri -> uri.path("/models/{m}:generateContent").queryParam("key", apiKey).build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body2 -> geminiError(resp.statusCode(), body2)))
                .bodyToMono(JsonNode.class)
                .map(this::extractText)
                .retryWhen(retryOn503());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildBody(String systemPrompt, List<ChatMessage> messages, boolean jsonMode) {
        List<Map<String, Object>> contents = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            String role = (m.role() == ChatMessage.MessageRole.ASSISTANT) ? "model" : "user";
            contents.add(Map.of(
                    "role", role,
                    "parts", List.of(Map.of("text", m.content()))
            ));
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", contents);
        if (jsonMode) {
            body.put("generationConfig", Map.of("responseMimeType", "application/json"));
        }
        return body;
    }

    private GeneratedImageData extractImage(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        for (JsonNode part : parts) {
            JsonNode inline = part.path("inlineData");
            if (!inline.isMissingNode()) {
                return new GeneratedImageData(
                        inline.path("data").asText(),
                        inline.path("mimeType").asText("image/png")
                );
            }
        }
        throw new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "Gemini did not return an image");
    }

    private String extractText(JsonNode node) {
        JsonNode text = node.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        return text.isMissingNode() ? "" : text.asText();
    }

    private String extractTextOrNull(String sseChunk) {
        try {
            return extractText(mapper.readTree(sseChunk));
        } catch (Exception e) {
            return null;
        }
    }

    private Retry retryOn503() {
        return Retry.backoff(2, Duration.ofSeconds(2))
                .filter(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 503)
                .doBeforeRetry(signal -> log.warn("Gemini 503, retrying ({}/2)...", signal.totalRetries() + 1));
    }

    private <T> Mono<T> geminiError(HttpStatusCode status, String body) {
        log.error("Gemini API error {}: {}", status.value(), body);
        if (status.value() == 503 || status.value() == 429) {
            return Mono.error(new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "AI service is temporarily busy, please try again"));
        }
        return Mono.error(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "AI service error: " + status.value()));
    }
}
