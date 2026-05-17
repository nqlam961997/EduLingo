package com.edulingo.controller.debug;

import com.edulingo.dto.ChatMessage;
import com.edulingo.dto.ChatRequest;
import com.edulingo.dto.TopicDto;
import com.edulingo.entity.LearnerProfile;
import com.edulingo.service.PromptAssembler;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dev-only endpoint exposing the assembled prompt + messages for a given
 * input. Used by the Python eval harness (Layer 3) to avoid duplicating the
 * prompt-assembly logic. Per chat-eval-harness spec.
 *
 * Guarded by {@code @Profile("dev")} — never registered in prod.
 */
@RestController
@RequestMapping("/api/internal/debug")
@Profile("dev")
public class DebugController {

    private final PromptAssembler assembler;

    public DebugController(PromptAssembler assembler) {
        this.assembler = assembler;
    }

    public record AssembleRequest(
            String topicId,
            String learnerCefr,
            List<ChatRequest.MessageItem> history,
            String newestMessage,
            String scratchpad) {}

    public record AssembleResponse(String systemPrompt, List<ChatMessage> messages) {}

    @PostMapping("/assemble-prompt")
    public AssembleResponse assemble(@RequestBody AssembleRequest req) {
        TopicDto.Topic topic = TopicDto.requireTopic(req.topicId());
        LearnerProfile profile = new LearnerProfile();
        profile.setCefrLevel(req.learnerCefr() == null ? "A2" : req.learnerCefr());

        String systemPrompt = assembler.assembleSystemPrompt(topic, profile, "", req.scratchpad());
        List<ChatMessage> messages = assembler.assembleMessages(
                req.history(),
                req.newestMessage() == null ? "" : req.newestMessage());
        return new AssembleResponse(systemPrompt, messages);
    }
}
