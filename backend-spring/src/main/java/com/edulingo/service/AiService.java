package com.edulingo.service;

import com.edulingo.dto.ChatMessage;
import com.edulingo.dto.GeneratedImageData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AiService {

    /**
     * Generate a single non-streamed reply given a system prompt and
     * an alternating list of chat messages (user/assistant turns).
     */
    Mono<String> generate(String systemPrompt, List<ChatMessage> messages);

    /**
     * Stream a reply token-by-token given a system prompt and an
     * alternating list of chat messages.
     */
    Flux<String> streamGenerate(String systemPrompt, List<ChatMessage> messages);

    default Mono<GeneratedImageData> generateImage(String prompt) {
        return Mono.error(new UnsupportedOperationException("Image generation not supported by this AI provider"));
    }

    /**
     * Vision call: not converted to multi-turn because picture flows are
     * single-turn by design (one image + one text prompt).
     */
    default Mono<String> generateWithBase64Image(String systemPrompt, String userText,
                                                  String base64Data, String mimeType) {
        return generate(systemPrompt, List.of(ChatMessage.user(userText)));
    }
}
