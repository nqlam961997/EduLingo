package com.edulingo.service;

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
    public Mono<String> generate(String systemPrompt, String userText) {
        return webClient.post()
                .uri("/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("system_prompt", systemPrompt, "user_text", userText))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> node.path("text").asText(""))
                .doOnError(e -> log.error("Local AI generate error: {}", e.getMessage()));
    }

    @Override
    public Flux<String> streamGenerate(String systemPrompt, String userText) {
        return webClient.post()
                .uri("/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("system_prompt", systemPrompt, "user_text", userText))
                .retrieve()
                .bodyToFlux(String.class)
                .filter(chunk -> !chunk.isBlank())
                .doOnError(e -> log.error("Local AI stream error: {}", e.getMessage()));
    }
}
