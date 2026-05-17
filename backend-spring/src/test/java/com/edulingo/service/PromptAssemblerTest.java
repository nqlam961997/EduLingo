package com.edulingo.service;

import com.edulingo.dto.ChatMessage;
import com.edulingo.dto.ChatRequest;
import com.edulingo.dto.SuggestPolicy;
import com.edulingo.dto.TopicDto;
import com.edulingo.entity.LearnerProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptAssembler — chat-multi-turn-context + chat-persona-cards / Layer 1")
class PromptAssemblerTest {

    private final PromptAssembler assembler = new PromptAssembler();

    private LearnerProfile a2Profile() {
        LearnerProfile p = new LearnerProfile();
        p.setEmail("learner@test.com");
        p.setCefrLevel("A2");
        return p;
    }

    // ── identity line ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Identity line contains persona name and role for every topic")
    void identityLineContainsNameAndRole() {
        LearnerProfile profile = a2Profile();
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            String prompt = assembler.assembleSystemPrompt(t, profile, "", null);
            assertThat(prompt)
                    .as("system prompt for %s", t.id())
                    .contains("You are " + t.persona().name());
            assertThat(prompt)
                    .as("roleContext for %s", t.id())
                    .contains(t.persona().roleContext());
        }
    }

    // ── article correctness ──────────────────────────────────────────────────

    @Test
    @DisplayName("Vowel-initial roles use 'an' in identity line")
    void articleCorrectnessForVowelInitialRoles() {
        LearnerProfile profile = a2Profile();
        // From the spec: Airport Staff, HR Manager, English Teacher, Eco Volunteer
        List<String> expectAnRoles = List.of("airport", "job_interview", "school", "environment");
        for (String topicId : expectAnRoles) {
            TopicDto.Topic t = TopicDto.requireTopic(topicId);
            String prompt = assembler.assembleSystemPrompt(t, profile, "", null);
            String expectedIdentity =
                    "You are " + t.persona().name() + ", an " + t.persona().roleContext();
            assertThat(prompt)
                    .as("identity line for %s should use 'an'", topicId)
                    .contains(expectedIdentity);
        }
    }

    // ── output-format policy ─────────────────────────────────────────────────

    @Test
    @DisplayName("SUGGEST=OFF topics produce no 'suggestions' field requirement")
    void suggestOffOmitsSuggestionsField() {
        LearnerProfile profile = a2Profile();
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            if (t.persona().suggestPolicy() != SuggestPolicy.OFF) continue;
            String prompt = assembler.assembleSystemPrompt(t, profile, "", null);
            assertThat(prompt)
                    .as("topic %s with SUGGEST=OFF should not request 'suggestions'", t.id())
                    .doesNotContain("\"suggestions\":");
        }
    }

    @Test
    @DisplayName("SUGGEST=HINT topics request a 'hint' field instead of suggestions")
    void suggestHintRequestsHintField() {
        LearnerProfile profile = a2Profile();
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            if (t.persona().suggestPolicy() != SuggestPolicy.HINT) continue;
            String prompt = assembler.assembleSystemPrompt(t, profile, "", null);
            assertThat(prompt)
                    .as("topic %s with SUGGEST=HINT should request 'hint'", t.id())
                    .contains("\"hint\":");
            assertThat(prompt)
                    .as("topic %s with SUGGEST=HINT should not request 'suggestions'", t.id())
                    .doesNotContain("\"suggestions\":");
        }
    }

    @Test
    @DisplayName("SUGGEST=ON topics request a 'suggestions' field")
    void suggestOnRequestsSuggestionsField() {
        LearnerProfile profile = a2Profile();
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            if (t.persona().suggestPolicy() != SuggestPolicy.ON) continue;
            String prompt = assembler.assembleSystemPrompt(t, profile, "", null);
            assertThat(prompt)
                    .as("topic %s with SUGGEST=ON should request 'suggestions'", t.id())
                    .contains("\"suggestions\":");
        }
    }

    // ── no legacy turn markers ───────────────────────────────────────────────

    @Test
    @DisplayName("Assembled system prompt for any topic contains no 'Learner:' or 'You:' markers")
    void noLegacyTurnMarkersInSystemPrompt() {
        LearnerProfile profile = a2Profile();
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            String prompt = assembler.assembleSystemPrompt(t, profile, "", null);
            assertThat(prompt)
                    .as("system prompt for %s", t.id())
                    .doesNotContain("Learner: ")
                    .doesNotContain("You: ");
        }
    }

    @Test
    @DisplayName("Assembled messages array contains no 'Learner:' or 'You:' markers")
    void noLegacyTurnMarkersInMessages() {
        List<ChatRequest.MessageItem> history = List.of(
                new ChatRequest.MessageItem("assistant", "Welcome! Just for one?"),
                new ChatRequest.MessageItem("user", "two please"),
                new ChatRequest.MessageItem("assistant", "Of course, right this way.")
        );
        List<ChatMessage> assembled = assembler.assembleMessages(history, "i want pastas");
        for (ChatMessage m : assembled) {
            assertThat(m.content())
                    .doesNotStartWith("Learner: ")
                    .doesNotStartWith("You: ");
        }
    }

    // ── multi-turn shape ─────────────────────────────────────────────────────

    @Test
    @DisplayName("assembleMessages preserves chronological order and appends newest user message")
    void assembleMessagesPreservesOrder() {
        List<ChatRequest.MessageItem> history = List.of(
                new ChatRequest.MessageItem("assistant", "Welcome!"),
                new ChatRequest.MessageItem("user", "two please"),
                new ChatRequest.MessageItem("assistant", "Right this way.")
        );
        List<ChatMessage> out = assembler.assembleMessages(history, "I want pasta");

        assertThat(out).hasSize(4);
        assertThat(out.get(0).role()).isEqualTo(ChatMessage.MessageRole.ASSISTANT);
        assertThat(out.get(0).content()).isEqualTo("Welcome!");
        assertThat(out.get(1).role()).isEqualTo(ChatMessage.MessageRole.USER);
        assertThat(out.get(1).content()).isEqualTo("two please");
        assertThat(out.get(2).role()).isEqualTo(ChatMessage.MessageRole.ASSISTANT);
        assertThat(out.get(3).role()).isEqualTo(ChatMessage.MessageRole.USER);
        assertThat(out.get(3).content()).isEqualTo("I want pasta");
    }

    @Test
    @DisplayName("First-turn assembly: empty history → single user message")
    void firstTurnEmptyHistory() {
        List<ChatMessage> out = assembler.assembleMessages(List.of(), "hello");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).role()).isEqualTo(ChatMessage.MessageRole.USER);
        assertThat(out.get(0).content()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Null history is tolerated — newest message still added")
    void nullHistoryTolerated() {
        List<ChatMessage> out = assembler.assembleMessages(null, "hi");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).role()).isEqualTo(ChatMessage.MessageRole.USER);
    }

    // ── persona payload appears in prompt ────────────────────────────────────

    @Test
    @DisplayName("Scenario seed and learner CEFR level appear in the system prompt")
    void scenarioSeedAndCefrLevelPresent() {
        LearnerProfile profile = a2Profile();
        TopicDto.Topic restaurant = TopicDto.requireTopic("restaurant");
        String prompt = assembler.assembleSystemPrompt(restaurant, profile, "", null);
        assertThat(prompt).contains(restaurant.persona().scenarioSeed());
        assertThat(prompt).contains("A2");
    }

    @Test
    @DisplayName("Scratchpad block omitted when scratchpad is null or blank")
    void scratchpadOmittedWhenNull() {
        LearnerProfile profile = a2Profile();
        TopicDto.Topic restaurant = TopicDto.requireTopic("restaurant");
        String prompt = assembler.assembleSystemPrompt(restaurant, profile, "", null);
        assertThat(prompt).doesNotContain("SCENARIO STATE SO FAR");
    }

    @Test
    @DisplayName("Scratchpad block included when provided")
    void scratchpadIncludedWhenPresent() {
        LearnerProfile profile = a2Profile();
        TopicDto.Topic restaurant = TopicDto.requireTopic("restaurant");
        String scratchpad = "- party_size: 2\n- main: pasta\n- drink: water";
        String prompt = assembler.assembleSystemPrompt(restaurant, profile, "", scratchpad);
        assertThat(prompt)
                .contains("SCENARIO STATE SO FAR")
                .contains("party_size: 2")
                .contains("main: pasta");
    }
}
