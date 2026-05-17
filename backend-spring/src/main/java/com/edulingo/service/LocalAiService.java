package com.edulingo.service;

import com.edulingo.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "local")
@org.springframework.context.annotation.Primary
public class LocalAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(LocalAiService.class);

    private final WebClient webClient;

    public LocalAiService(@Qualifier("localAiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<String> generate(String systemPrompt, List<ChatMessage> messages) {
        return webClient.post()
                .uri("/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("system_prompt", systemPrompt, "messages", toWire(messages)))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> node.path("text").asText(""))
                .doOnError(e -> log.error("Local AI generate error: {}", e.getMessage()));
    }

    @Override
    public Flux<String> streamGenerate(String systemPrompt, List<ChatMessage> messages) {
        return webClient.post()
                .uri("/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("system_prompt", systemPrompt, "messages", toWire(messages)))
                .retrieve()
                .bodyToFlux(String.class)
                .filter(chunk -> !chunk.isBlank())
                .doOnError(e -> log.error("Local AI stream error: {}", e.getMessage()));
    }

    private List<Map<String, String>> toWire(List<ChatMessage> messages) {
        List<Map<String, String>> wire = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            String role = (m.role() == ChatMessage.MessageRole.ASSISTANT) ? "assistant" : "user";
            wire.add(Map.of("role", role, "content", m.content()));
        }
        return wire;
    }
}
