## Why

The AI roleplay chat feels disconnected: it forgets earlier turns, drifts off persona, misreads intent, and produces canned-sounding replies. The architecture flattens the full transcript into a single user-message string before sending it to gemma2:9b (Local Ollama), so the model loses the alternating multi-turn shape it was trained on. The persona definition is two adjectives and a noun, so every reply re-invents the character. There is no structured scenario state, no per-topic differentiation (a job interview shares the same "gentle correction" rule as a friendly waitress), and no eval harness — meaning any prompt change today ships on vibes.

## What Changes

- Replace flattened `"Learner: ... \n You: ..."` user-message with a proper multi-turn `messages[]` array sent to all AI providers (Local/Ollama, Gemini, OpenAI).
- Introduce **persona cards** per topic: name, role-context, voice, does/doesn't, tutor-overlay style, suggest policy, CEFR-banded vocabulary anchors, curated scenario seed, curated opening line.
- Introduce a **3-type topic taxonomy** — `TRANSACTIONAL`, `ASYMMETRIC`, `FREE-FORM` — driving per-type chat-prompt templates, scratchpad schemas, and output-format defaults. Per-topic overrides handle edge cases (doctor's `SUGGEST=OFF` override, school's recursive-frame guard).
- Replace the model-generated scenario text with **curated scenario seeds** so the same topic produces deterministic chat state across runs.
- Add a **session scratchpad** that tracks structured "facts so far" per session (slot-machine for transactional, phase-machine for asymmetric, rhythm-beat for free-form). Sourcing uses a second-pass extraction call to a small model (e.g. `qwen2.5:1.5b` or `phi3:mini`) so the main chat reply doesn't carry extra constraints.
- Add `chat_session` persistence so the scratchpad survives turns (and, optionally later, page refresh).
- Move suggestions and tutor-overlay behavior into the persona card: `SUGGEST=ON|OFF|HINT`, `TUTOR=SUBTLE_RECAST|PAREN|OFF`. The frontend respects `SUGGEST=OFF` by hiding the chip strip.
- Add a **3-layer eval harness**:
  - Layer 1: static prompt-assembly tests in JUnit (every topic, every type invariant).
  - Layer 2: expanded mock-model behavior tests in JUnit (scratchpad update, stale-fallback, asymmetric output shape).
  - Layer 3: live-model golden-path rubric in Python under `eval/` — three goldens (restaurant, job-interview, environment), per-turn deterministic + probabilistic checks, scratchpad assertions. Calls a dev-only `POST /api/internal/debug/assemble-prompt` to avoid duplicating assembly code.
- **BREAKING** (internal contract only): `AiService.streamGenerate(systemPrompt, userText)` evolves to a multi-turn shape (`streamGenerate(systemPrompt, messages[])`). Existing OpenAI/Gemini/LocalAI implementations are updated in lockstep; no external API change.
- Fix incidental defects surfaced by the audit: article correctness in role rendering (`"a Airport Staff"` → `"an Airport Staff"`); the silent `findTopic` fallback that produces `"Tutor / English Tutor"`; the orphan `PersonalizationService.chatSystemPrompt()` that diverges from the actually-used `ChatService.buildRoleplayPrompt`.

## Capabilities

### New Capabilities
- `chat-persona-cards`: Structured per-topic persona definition (name, role, voice, does/doesn't, tutor style, suggest policy, CEFR vocab anchors, scenario seed, opening line) and the 3-type topic taxonomy (transactional, asymmetric, free-form) with per-topic overrides.
- `chat-multi-turn-context`: Proper alternating `messages[]` array sent to AI providers; history reconstruction uses parsed `reply` text (not raw JSON); first-turn handling; updated `AiService` interface adopted by all three providers.
- `chat-session-scratchpad`: Structured conversation-state object scoped to a chat session; per-topic-type schema; second-pass extraction by a small model after each reply; stale-fallback, first-turn, and race-condition policies; `chat_session` persistence row.
- `chat-eval-harness`: Three-layer eval — Layer 1 static prompt-assembly tests (JUnit), Layer 2 mock-model behavior tests (JUnit), Layer 3 live-model golden-path rubric (Python under `eval/`); dev-only `POST /api/internal/debug/assemble-prompt` endpoint to expose assembled prompts without duplication.

### Modified Capabilities
<!-- None. openspec/specs/ is empty; this change introduces the first specs in this area. -->

## Impact

**Backend (Spring):**
- `ChatService` substantially refactored — replaces `buildScenarioPrompt`/`buildRoleplayPrompt`/`buildConversation` with a `PromptAssembler` collaborator parameterized by topic type.
- `TopicDto` extended into a richer record family: `Topic`, `PersonaCard`, `ScenarioSeed`, `TopicType` enum, scratchpad schemas per type.
- `AiService` interface change: `streamGenerate(String systemPrompt, String userText)` → `streamGenerate(String systemPrompt, List<ChatMessage> messages)`. All three implementations (`GeminiService`, `OpenAiService`, `LocalAiService`) updated in lockstep.
- New `ChatSessionService` + `ChatSession` JPA entity + `ChatSessionRepository` for scratchpad persistence.
- New `ScratchpadExtractor` collaborator (calls small Ollama model with focused per-type extraction prompt).
- New dev-only `DebugController` exposing `POST /api/internal/debug/assemble-prompt` (guarded by profile).
- `ChatRequest` gains an optional `sessionId` (server-issued on `startScenario`).

**Backend (Python):**
- `main.py` already handles multi-turn (Ollama `/api/chat` accepts `messages[]`); no contract changes required.
- New small model pulled in `ollama/entrypoint.sh` (extraction model). Choice of model is a v1 decision (likely `qwen2.5:1.5b`).

**Frontend (Vue):**
- `ChatView.vue`: history reconstruction stays as today (parsed reply only). UI respects `personaCard.suggestPolicy === 'OFF'` to hide the suggestion chip strip and (for asymmetric topics) renders the `hint` field separately. `startScenario` response now also carries a `sessionId` echoed back on every `/api/chat/reply` call. Streaming-UX is left for a follow-up (acknowledged limitation).
- `api/edulingo.ts`: `streamReply` signature gains `sessionId`.

**Tests:**
- `ChatServiceTest` expanded by ~8 cases (Layer 2). New `PromptAssemblerTest`, `PersonaCardTest`, `ChatSessionServiceTest`, `ScratchpadExtractorTest`.
- New `eval/` directory with Python harness + three golden conversations + rubric runner.

**Operations:**
- `docker-compose*.yml`: Ollama service must pull the extraction model on startup (or the entrypoint must lazy-pull). Coordinated with the in-flight `docker-deployment` change.
- Eval suite runs nightly (or pre-merge on chat-touching branches), not on every commit — Layer 1 + Layer 2 run on every commit.
