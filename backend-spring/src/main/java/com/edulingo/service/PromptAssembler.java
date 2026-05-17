package com.edulingo.service;

import com.edulingo.dto.ChatMessage;
import com.edulingo.dto.ChatRequest;
import com.edulingo.dto.PersonaCard;
import com.edulingo.dto.SuggestPolicy;
import com.edulingo.dto.TopicDto;
import com.edulingo.dto.TopicType;
import com.edulingo.dto.TutorStyle;
import com.edulingo.entity.LearnerProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the system prompt and multi-turn message array sent to an
 * {@link AiService}. Per the chat-persona-cards, chat-multi-turn-context,
 * and chat-session-scratchpad specs.
 *
 * v1: system-prompt rendering with persona card + scenario seed + learner
 * profile + output-format block. The optional SCENARIO STATE SO FAR block
 * (scratchpad) is rendered when present but extraction is wired in Phase 2.
 */
@Component
public class PromptAssembler {

    /**
     * Assemble the full system prompt for a chat turn.
     *
     * @param topic       The topic being roleplayed.
     * @param profile     The learner's profile (CEFR level, etc.).
     * @param topErrors   The personalization summary string (may be blank).
     * @param scratchpad  Optional structured scratchpad rendered as
     *                    "SCENARIO STATE SO FAR". Pass {@code null} on
     *                    first turn (or when scratchpad is unavailable).
     */
    public String assembleSystemPrompt(TopicDto.Topic topic,
                                       LearnerProfile profile,
                                       String topErrors,
                                       String scratchpad) {
        PersonaCard p = topic.persona();
        StringBuilder sb = new StringBuilder();

        // ── ROLE ─────────────────────────────────────────────────────────────
        sb.append("ROLE\n");
        sb.append("You are ").append(p.name()).append(", ")
                .append(p.article()).append(' ').append(p.roleContext()).append(".\n");
        sb.append("You are NOT a teacher.\n\n");

        // ── VOICE ────────────────────────────────────────────────────────────
        sb.append("VOICE\n").append(p.voice()).append("\n\n");

        // ── DOES ─────────────────────────────────────────────────────────────
        sb.append("DOES\n");
        for (String item : p.does()) {
            sb.append("- ").append(item).append('\n');
        }
        sb.append('\n');

        // ── DOESN'T ──────────────────────────────────────────────────────────
        sb.append("DOESN'T\n");
        for (String item : p.doesnt()) {
            sb.append("- ").append(item).append('\n');
        }
        sb.append('\n');

        // ── TUTOR STYLE ──────────────────────────────────────────────────────
        sb.append("TUTOR STYLE\n");
        sb.append(renderTutorStyle(p.tutorStyle())).append("\n\n");

        // ── VOCABULARY ANCHORS ───────────────────────────────────────────────
        sb.append("VOCABULARY ANCHORS (").append(profile.getCefrLevel()).append(")\n");
        List<String> anchors = pickVocabBand(p, profile.getCefrLevel());
        sb.append(String.join(", ", anchors)).append("\n\n");

        sb.append("─────────────────────────────────────────────\n\n");

        // ── SCENARIO ─────────────────────────────────────────────────────────
        sb.append("SCENARIO\n").append(p.scenarioSeed()).append("\n\n");

        // ── LEARNER ──────────────────────────────────────────────────────────
        sb.append("LEARNER\n");
        sb.append("Level: ").append(profile.getCefrLevel()).append(".\n");
        sb.append("Recurring mistakes to gently recast (do NOT lecture):\n");
        sb.append(topErrors == null || topErrors.isBlank() ? "(none recorded yet)" : topErrors)
                .append("\n\n");

        // ── SCENARIO STATE SO FAR ────────────────────────────────────────────
        if (scratchpad != null && !scratchpad.isBlank()) {
            sb.append("SCENARIO STATE SO FAR\n").append(scratchpad).append("\n\n");
        }

        sb.append("─────────────────────────────────────────────\n\n");

        // ── OUTPUT FORMAT + RULES ────────────────────────────────────────────
        sb.append(renderOutputFormat(p));
        sb.append('\n');
        sb.append(renderRules(p));

        return sb.toString();
    }

    /**
     * Assemble the messages array sent to the AI provider. Maps the request's
     * history (a list of {role, content} items) to {@link ChatMessage}s in
     * chronological order and appends the newest learner message as a final
     * user turn. Per chat-multi-turn-context spec.
     */
    public List<ChatMessage> assembleMessages(List<ChatRequest.MessageItem> history, String newestMessage) {
        List<ChatMessage> out = new ArrayList<>(history == null ? 1 : history.size() + 1);
        if (history != null) {
            for (ChatRequest.MessageItem m : history) {
                ChatMessage.MessageRole role = "assistant".equals(m.role())
                        ? ChatMessage.MessageRole.ASSISTANT
                        : ChatMessage.MessageRole.USER;
                out.add(new ChatMessage(role, m.content()));
            }
        }
        out.add(ChatMessage.user(newestMessage));
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<String> pickVocabBand(PersonaCard p, String cefr) {
        return switch (cefr == null ? "A2" : cefr.toUpperCase()) {
            case "A1", "A2" -> p.vocabA2();
            case "B1" -> p.vocabB1() != null ? p.vocabB1() : p.vocabA2();
            case "B2", "C1", "C2" -> p.vocabB2() != null ? p.vocabB2()
                    : (p.vocabB1() != null ? p.vocabB1() : p.vocabA2());
            default -> p.vocabA2();
        };
    }

    private String renderTutorStyle(TutorStyle style) {
        return switch (style) {
            case SUBTLE_RECAST -> "SUBTLE_RECAST. If the learner makes a mistake, naturally repeat the "
                    + "correct phrasing inside your normal reply. Never use parentheses or labels.";
            case PAREN -> "PAREN. If the learner makes a mistake, include the corrected phrase in "
                    + "parentheses inside your reply, e.g. \"(you mean: ...)\".";
            case OFF -> "OFF. Do NOT correct the learner's English in your reply. Errors are tracked "
                    + "silently for the post-session review.";
        };
    }

    private String renderOutputFormat(PersonaCard p) {
        StringBuilder sb = new StringBuilder("OUTPUT FORMAT\n");
        sb.append("Reply with one JSON object only — no markdown, no prose around it:\n{\n");
        sb.append("  \"reply\": \"your in-character line, 1-3 short sentences\"");

        SuggestPolicy s = p.suggestPolicy();
        if (s == SuggestPolicy.ON) {
            sb.append(",\n  \"suggestions\": [\"≤6-word option\", \"≤6-word option\", \"≤6-word option\"]");
        } else if (s == SuggestPolicy.HINT) {
            sb.append(",\n  \"hint\": \"")
                    .append(p.hintTemplate() == null
                            ? "a single short coaching nudge for the learner"
                            : p.hintTemplate())
                    .append("\" // optional, omit if not needed");
        }
        // SUGGEST=OFF: no suggestions field at all

        sb.append(",\n  \"errors\": []  // or [ { \"type\": \"Grammar|Vocabulary|Spelling|Article|Tense|Structure\","
                + " \"original\": \"...\", \"fixed\": \"...\", \"explain_vi\": \"...\" } ]");
        sb.append("\n}\n");
        return sb.toString();
    }

    private String renderRules(PersonaCard p) {
        StringBuilder sb = new StringBuilder("RULES\n");
        int n = 1;
        sb.append(n++).append(". Stay as ").append(p.name())
                .append(". Never address the learner as a student.\n");

        switch (p.tutorStyle()) {
            case SUBTLE_RECAST -> sb.append(n++).append(". SUBTLE_RECAST applies — never use parentheticals in \"reply\".\n");
            case PAREN -> sb.append(n++).append(". PAREN — include gentle corrections in parentheses inside \"reply\".\n");
            case OFF -> sb.append(n++).append(". Do NOT correct grammar in the reply. Errors go to \"errors\" only.\n");
        }

        switch (p.suggestPolicy()) {
            case ON -> sb.append(n++).append(". Suggestions: 3 concrete next things the learner could say, ≤6 words each, not leading.\n");
            case OFF -> sb.append(n++).append(". Do NOT include a \"suggestions\" field.\n");
            case HINT -> sb.append(n++).append(". \"hint\" is optional — only include when the learner appears stuck.\n");
        }

        sb.append(n++).append(". errors[] contains only mistakes from the learner's NEWEST turn.\n");

        if (p.neverSays() != null && !p.neverSays().isEmpty()) {
            sb.append(n++).append(". You MUST NOT say any of these phrases: ");
            sb.append(String.join("; ", p.neverSays())).append('.');
            sb.append('\n');
        }

        // Type-flavored guidance
        // (kept short — the persona card already covers the bulk)
        return sb.toString();
    }
}
