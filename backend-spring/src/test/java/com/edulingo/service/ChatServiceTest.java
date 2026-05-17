package com.edulingo.service;

import com.edulingo.dto.ChatRequest;
import com.edulingo.dto.ErrorItem;
import com.edulingo.entity.LearnerProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService")
class ChatServiceTest {

    @Mock AiService              gemini;
    @Mock PersonalizationService personalization;
    @Mock ChatSessionService     chatSessions;
    @Mock ScratchpadExtractor    extractor;

    // Use real PromptAssembler + ObjectMapper because they're pure-function collaborators.
    private ChatService service;

    @BeforeEach
    void setUp() {
        service = new ChatService(gemini, personalization, new PromptAssembler(),
                chatSessions, extractor, new ObjectMapper());
    }

    // ── reply() — streaming ──────────────────────────────────────────────────

    @Test
    @DisplayName("reply() phải stream đúng các chunk từ AiService")
    void reply_streamsChunksFromAiService() {
        setupProfile();
        when(gemini.streamGenerate(anyString(), anyList()))
                .thenReturn(Flux.just("{\"reply\":\"Hello!\",", "\"suggestions\":[],", "\"errors\":[]}"));

        ChatRequest req = makeChatRequest("Hi there");

        StepVerifier.create(service.reply("user@test.com", req))
                .expectNext("{\"reply\":\"Hello!\",")
                .expectNext("\"suggestions\":[],")
                .expectNext("\"errors\":[]}")
                .verifyComplete();
    }

    @Test
    @DisplayName("reply() phải lưu lỗi vào DB khi AI trả về errors có nội dung")
    void reply_savesErrorsWhenAiReturnsErrors() throws InterruptedException {
        UUID learnerId = setupProfile();
        String aiJson = "{\"reply\":\"...\",\"suggestions\":[],\"errors\":[" +
                "{\"type\":\"Grammar\",\"original\":\"I go yesterday\"," +
                "\"fixed\":\"I went yesterday\",\"explain_vi\":\"quá khứ đơn\"}]}";

        when(gemini.streamGenerate(anyString(), anyList()))
                .thenReturn(Flux.just(aiJson));

        ChatRequest req = makeChatRequest("I go yesterday to school");

        StepVerifier.create(service.reply("user@test.com", req))
                .expectNext(aiJson)
                .verifyComplete();

        // doOnComplete chạy async → đợi một chút để xác nhận
        Thread.sleep(200);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ErrorItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(personalization, timeout(500)).recordErrors(eq(learnerId), captor.capture());

        List<ErrorItem> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).type()).isEqualTo("Grammar");
        assertThat(saved.get(0).original()).isEqualTo("I go yesterday");
    }

    @Test
    @DisplayName("reply() không gọi recordErrors khi errors rỗng")
    void reply_doesNotSaveWhenNoErrors() throws InterruptedException {
        setupProfile();
        String aiJson = "{\"reply\":\"Good job!\",\"suggestions\":[],\"errors\":[]}";

        when(gemini.streamGenerate(anyString(), anyList()))
                .thenReturn(Flux.just(aiJson));

        StepVerifier.create(service.reply("user@test.com", makeChatRequest("Hello")))
                .expectNextCount(1)
                .verifyComplete();

        Thread.sleep(200);
        verify(personalization, never()).recordErrors(any(), any());
    }

    @Test
    @DisplayName("reply() không crash khi AI trả về JSON không hợp lệ")
    void reply_doesNotCrashOnMalformedJson() {
        setupProfile();
        when(gemini.streamGenerate(anyString(), anyList()))
                .thenReturn(Flux.just("not valid json at all"));

        StepVerifier.create(service.reply("user@test.com", makeChatRequest("Hello")))
                .expectNext("not valid json at all")
                .verifyComplete();
    }

    @Test
    @DisplayName("reply() phải propagate lỗi khi AiService ném exception")
    void reply_propagatesAiServiceError() {
        setupProfile();
        when(gemini.streamGenerate(anyString(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("AI service down")));

        StepVerifier.create(service.reply("user@test.com", makeChatRequest("Hi")))
                .expectErrorMessage("AI service down")
                .verify();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID setupProfile() {
        UUID learnerId = UUID.randomUUID();
        LearnerProfile profile = new LearnerProfile();
        profile.setId(learnerId);
        profile.setEmail("user@test.com");
        profile.setCefrLevel("A2");

        when(personalization.getOrCreate("user@test.com")).thenReturn(profile);
        when(personalization.topErrorsSummary(learnerId)).thenReturn("");
        return learnerId;
    }

    private ChatRequest makeChatRequest(String message) {
        // ChatRequest(topicId, scenario, history, message)
        return new ChatRequest("airport", "You are at an airport.", List.of(), message);
    }
}
