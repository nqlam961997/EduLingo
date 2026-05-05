package com.edulingo.service;

import com.edulingo.dto.GeneratedImageData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AiService {
    Mono<String> generate(String systemPrompt, String userText);
    Flux<String> streamGenerate(String systemPrompt, String userText);

    default Mono<GeneratedImageData> generateImage(String prompt) {
        return Mono.error(new UnsupportedOperationException("Image generation not supported by this AI provider"));
    }

    default Mono<String> generateWithBase64Image(String systemPrompt, String userText,
                                                  String base64Data, String mimeType) {
        return generate(systemPrompt, userText);
    }
}
