package com.edulingo.service;

import com.edulingo.dto.ChatMessage;
import com.edulingo.dto.TopicDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultScratchpadExtractor — chat-session-scratchpad / Layer 2")
class ScratchpadExtractorTest {

    @Mock AiService aiService;
    private DefaultScratchpadExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DefaultScratchpadExtractor(aiService, new ObjectMapper(), 5);
    }

    @Test
    @DisplayName("Transactional topic: valid slot JSON is normalized and returned")
    void transactionalExtractionHappyPath() {
        TopicDto.Topic restaurant = TopicDto.requireTopic("restaurant");
        when(aiService.generate(anyString(), anyList()))
                .thenReturn(Mono.just("{\"main\":\"pasta\",\"drink\":\"water\"}"));

        StepVerifier.create(extractor.extract(
                        UUID.randomUUID(),
                        restaurant,
                        List.of(ChatMessage.user("i want pasta")),
                        null))
                .expectNextMatches(json -> json.contains("pasta") && json.contains("water"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Asymmetric topic: extraction missing required 'phase' field is rejected")
    void asymmetricRequiresPhase() {
        TopicDto.Topic interview = TopicDto.requireTopic("job_interview");
        when(aiService.generate(anyString(), anyList()))
                .thenReturn(Mono.just("{\"facts\":{\"name\":\"Lan\"}}"));  // no phase

        StepVerifier.create(extractor.extract(
                        UUID.randomUUID(), interview,
                        List.of(ChatMessage.user("hi")), null))
                .verifyComplete();  // empty — rejected
    }

    @Test
    @DisplayName("Free-form topic: extraction with rhythmBeat or topicsTouched is accepted")
    void freeFormRequiresRhythmOrTopics() {
        TopicDto.Topic env = TopicDto.requireTopic("environment");
        when(aiService.generate(anyString(), anyList()))
                .thenReturn(Mono.just("{\"rhythmBeat\":2,\"topicsTouched\":[\"recycling\"]}"));

        StepVerifier.create(extractor.extract(
                        UUID.randomUUID(), env,
                        List.of(ChatMessage.user("yes i recycle")), null))
                .expectNextMatches(json -> json.contains("rhythmBeat"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Invalid (non-JSON) extraction output completes empty — caller retains previous")
    void invalidJsonCompletesEmpty() {
        TopicDto.Topic restaurant = TopicDto.requireTopic("restaurant");
        when(aiService.generate(anyString(), anyList()))
                .thenReturn(Mono.just("this is not json at all"));

        StepVerifier.create(extractor.extract(
                        UUID.randomUUID(), restaurant,
                        List.of(ChatMessage.user("hi")), null))
                .verifyComplete();
    }

    @Test
    @DisplayName("AiService error → extractor completes empty (caller retains previous scratchpad)")
    void aiErrorCompletesEmpty() {
        TopicDto.Topic restaurant = TopicDto.requireTopic("restaurant");
        when(aiService.generate(anyString(), anyList()))
                .thenReturn(Mono.error(new RuntimeException("ollama down")));

        StepVerifier.create(extractor.extract(
                        UUID.randomUUID(), restaurant,
                        List.of(ChatMessage.user("hi")), null))
                .verifyComplete();
    }

    @Test
    @DisplayName("Markdown-fenced JSON output is unwrapped before validation")
    void unwrapsCodeFencedJson() {
        TopicDto.Topic restaurant = TopicDto.requireTopic("restaurant");
        when(aiService.generate(anyString(), anyList()))
                .thenReturn(Mono.just("```json\n{\"main\":\"pasta\"}\n```"));

        StepVerifier.create(extractor.extract(
                        UUID.randomUUID(), restaurant,
                        List.of(ChatMessage.user("hi")), null))
                .expectNextMatches(json -> json.contains("pasta"))
                .verifyComplete();
    }
}
