package com.edulingo.dto;

import java.util.List;

/**
 * Per-topic persona definition. Drives prompt assembly, output-format defaults,
 * and frontend UI affordances (e.g., suggestion chip visibility).
 *
 * Required fields must be non-null and non-empty per the chat-persona-cards spec.
 * Type-specific fields (slots / phases / rhythmBeats) MUST be populated according
 * to the topic's {@link TopicType}; PersonaCardTest enforces this at boot.
 */
public record PersonaCard(
        // ── Identity ─────────────────────────────────────────────────────────
        String name,
        String roleContext,
        String article,              // "a" or "an" — explicit so silent-H cases ("an HR") work
        String voice,

        // ── Behavior constraints ─────────────────────────────────────────────
        List<String> does,
        List<String> doesnt,

        // ── Output policy ────────────────────────────────────────────────────
        TutorStyle tutorStyle,
        SuggestPolicy suggestPolicy,

        // ── Vocabulary anchors (per CEFR band) ───────────────────────────────
        List<String> vocabA2,
        List<String> vocabB1,        // optional, nullable
        List<String> vocabB2,        // optional, nullable

        // ── Scenario + opening ───────────────────────────────────────────────
        String scenarioSeed,
        String opening,

        // ── Eval / safety ────────────────────────────────────────────────────
        List<String> neverSays,      // optional, nullable

        // ── HINT-mode template (only when suggestPolicy == HINT) ─────────────
        String hintTemplate,         // optional, nullable

        // ── Type-specific structural fields ──────────────────────────────────
        // TRANSACTIONAL: ordered slot keys for the scratchpad
        List<String> slots,
        // ASYMMETRIC: ordered phase names for the scratchpad
        List<String> phases,
        // FREE_FORM: ordered rhythm beats for the scratchpad
        List<String> rhythmBeats
) {
}
