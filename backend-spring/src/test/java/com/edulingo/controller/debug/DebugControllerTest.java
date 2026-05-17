package com.edulingo.controller.debug;

import com.edulingo.dto.ChatRequest;
import com.edulingo.service.PromptAssembler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DebugController — chat-eval-harness / Layer 1 (dev-only endpoint)")
class DebugControllerTest {

    @Test
    @DisplayName("assemble() returns the system prompt + messages for a valid topic")
    void assembleHappyPath() {
        DebugController controller = new DebugController(new PromptAssembler());
        var resp = controller.assemble(new DebugController.AssembleRequest(
                "restaurant",
                "A2",
                List.of(new ChatRequest.MessageItem("assistant", "Welcome!")),
                "two please",
                null));

        assertThat(resp.systemPrompt()).contains("Maria");
        assertThat(resp.systemPrompt()).contains("trattoria");
        assertThat(resp.messages()).hasSize(2);
        assertThat(resp.messages().get(1).content()).isEqualTo("two please");
    }

    @Test
    @DisplayName("Scratchpad is injected when provided")
    void scratchpadInjected() {
        DebugController controller = new DebugController(new PromptAssembler());
        var resp = controller.assemble(new DebugController.AssembleRequest(
                "restaurant",
                "A2",
                List.of(),
                "hi",
                "- main: pasta\n- drink: water"));

        assertThat(resp.systemPrompt())
                .contains("SCENARIO STATE SO FAR")
                .contains("main: pasta");
    }

    /**
     * The @Profile("dev") guard is verified structurally: the class is
     * annotated with {@code @Profile("dev")} so Spring excludes the bean
     * when "dev" is not active. Runtime profile-loading is covered by an
     * integration test in the prod-profile context.
     */
    @Test
    @DisplayName("DebugController class is annotated @Profile(\"dev\")")
    void classCarriesDevProfileAnnotation() {
        org.springframework.context.annotation.Profile prof =
                DebugController.class.getAnnotation(org.springframework.context.annotation.Profile.class);
        assertThat(prof).isNotNull();
        assertThat(prof.value()).containsExactly("dev");
    }
}
