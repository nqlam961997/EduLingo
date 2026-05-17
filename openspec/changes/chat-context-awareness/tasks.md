## 1. Phase 1 — Setup: type taxonomy + persona card schema

- [x] 1.1 Add `TopicType` enum (`TRANSACTIONAL`, `ASYMMETRIC`, `FREE_FORM`) under `com.edulingo.dto`
- [x] 1.2 Add `PersonaCard` record with required fields (`name`, `roleContext`, `voice`, `does`, `doesnt`, `tutorStyle`, `suggestPolicy`, `vocabA2`, `scenarioSeed`, `opening`) and optional fields (`vocabB1`, `vocabB2`, `neverSays`, `hintTemplate`)
- [x] 1.3 Add `SuggestPolicy` enum (`ON`, `OFF`, `HINT`) and `TutorStyle` enum (`SUBTLE_RECAST`, `PAREN`, `OFF`)
- [x] 1.4 Extend `TopicDto.Topic` with `type: TopicType` and `persona: PersonaCard` (or replace flat fields)
- [x] 1.5 Replace the silent `findTopic` fallback with a fail-fast lookup that throws a `ResponseStatusException(404)` for unknown topic IDs

## 2. Phase 1 — Author all 12 persona cards

- [x] 2.1 Restaurant — Maria (TRANSACTIONAL) — full card incl. `does`, `doesnt`, A2/B1 vocab, scenario seed, opening, slot declarations (`party_size`, `starter`, `main`, `drink`, `dessert`, `special_reqs`)
- [x] 2.2 Airport — James (TRANSACTIONAL) — slots: `destination`, `bags`, `seat_pref`, `passport_seen`, `boarding_pass_issued`
- [x] 2.3 Shopping — Sophie (TRANSACTIONAL) — disambiguate role: replaced "bargain" with "ask about discounts"; slots: `item`, `size`, `color`, `tried_on`, `paid`
- [x] 2.4 Hotel — David (TRANSACTIONAL) — slots: `service_type`, `room_number`, `request`, `resolved`
- [x] 2.5 Doctor — Dr. Sarah (TRANSACTIONAL with `suggestPolicy=OFF` override; `neverSays` for specific medications/dosages); slots: `chief_complaint`, `duration`, `severity`, `advice_given`
- [x] 2.6 Job Interview — Mr. Thompson (ASYMMETRIC) — phases: intro, experience, strengths, why-this-role, candidate-questions, close; `suggestPolicy=HINT`, `tutorStyle=OFF`, `neverSays` includes "you got the job", "good answer"
- [x] 2.7 Travel — Carlos (TRANSACTIONAL) — directions sub-scenario; slots: `destination`, `transport_choice`, `directions_given`, `understood`
- [x] 2.8 School — Ms. Emily (FREE_FORM with recursive-frame guard); `neverSays` includes "let me correct your grammar", "today's lesson"; rhythm beats: greeting → homework-or-life → swap → close
- [x] 2.9 Daily Routine — Alex (FREE_FORM) — rhythm beats: greeting → habit-question → comparison → light-commitment
- [x] 2.10 Sports — Mike (FREE_FORM) — rhythm beats: greeting → favorite-sport → swap → invite
- [x] 2.11 Technology — Luna (FREE_FORM) — A2/B1 concrete words anchored; `neverSays` includes "blockchain", "artificial intelligence"
- [x] 2.12 Environment — Oliver (FREE_FORM) — peer voice, no "you should"; `neverSays` includes "sustainability", "carbon footprint"

## 3. Phase 1 — Multi-turn AiService interface

- [x] 3.1 Add `ChatMessage` record (`role: MessageRole` enum, `content: String`)
- [x] 3.2 Change `AiService.streamGenerate` signature to `(String systemPrompt, List<ChatMessage> messages)`; old flat-string signature removed
- [x] 3.3 Change `AiService.generate` signature in lockstep
- [x] 3.4 Update `GeminiService`: messages → `contents[]` with role mapping `assistant → model`; `systemInstruction` carries system prompt
- [x] 3.5 Update `OpenAiService`: `messages = [{system}, ...history]`
- [x] 3.6 Update `LocalAiService`: sends `{system_prompt, messages}` to Python
- [x] 3.7 Update Python `backend-python/main.py` `/generate` and `/stream` to accept `messages` (legacy `user_text` kept for transitional compat); forwards to Ollama `/api/chat` with `[{role:system,...}, ...messages]`
- [x] 3.8 Update `PictureService` callers: single-turn calls wrap text in `[ChatMessage.user(text)]`

## 4. Phase 1 — PromptAssembler + curated scenarios

- [x] 4.1 Create `PromptAssembler` collaborator (`com.edulingo.service.PromptAssembler`)
- [x] 4.2 `assembleSystemPrompt(Topic, LearnerProfile, topErrors, scratchpad)` renders ROLE / VOICE / DOES / DOESN'T / TUTOR STYLE / VOCAB ANCHORS / SCENARIO / LEARNER / OUTPUT FORMAT / RULES with per-policy branches
- [x] 4.3 `assembleMessages(history, newestMessage)` returns chronological `List<ChatMessage>` ending with the user turn
- [x] 4.4 Article correctness: explicit `article` field on each persona card ("a"/"an") — handles silent-H ("an HR")
- [x] 4.5 Removed `ChatService.buildScenarioPrompt`/`buildRoleplayPrompt`/`buildConversation`; replaced with `PromptAssembler` calls
- [x] 4.6 `ChatService.startScenario` returns deterministic curated `persona.scenarioSeed` + `persona.opening` — NO AI call
- [x] 4.7 Deleted orphan `PersonalizationService.chatSystemPrompt(LearnerProfile)` method

## 5. Phase 1 — Frontend persona-aware UI

- [x] 5.1 Update `api/edulingo.ts` `ScenarioResponse` to include `suggestPolicy` and `sessionId` (additive, both optional)
- [x] 5.2 Update `api/edulingo.ts` `streamReply` signature to accept `sessionId` (optional, only sent when present)
- [x] 5.3 Update `ChatView.vue` to store `sessionId` from `startScenario` and pass it on every `streamReply` (no-op until backend issues IDs in Phase 2)
- [x] 5.4 Update `ChatView.vue` to respect `suggestPolicy`: hide the suggestion chip strip when `OFF`. HINT-mode UI deferred to a follow-up
- [x] 5.5 History reconstruction confirmed to use parsed `reply` only (`tryParseJson` path unchanged)

## 6. Phase 1 — Layer 1 tests

- [x] 6.1 `PersonaCardTest`: complete-card invariants, enum domain, `does ≥ 3`, `doesnt ≥ 2`, `vocabA2 ≥ 5`, type-specific slots/phases/rhythmBeats ≥ 3, HINT→hintTemplate required, requireTopic 404 path
- [x] 6.2 `PromptAssemblerTest.identityLineContainsNameAndRole` for every topic
- [x] 6.3 `PromptAssemblerTest.articleCorrectnessForVowelInitialRoles` — covers airport, job_interview, school, environment
- [x] 6.4 `PromptAssemblerTest.suggestOffOmitsSuggestionsField` + `suggestHintRequestsHintField` + `suggestOnRequestsSuggestionsField`
- [x] 6.5 `PromptAssemblerTest.noLegacyTurnMarkersInSystemPrompt` + `noLegacyTurnMarkersInMessages`; plus `assembleMessagesPreservesOrder`, `firstTurnEmptyHistory`, `nullHistoryTolerated`, scratchpad omitted/present
- [x] 6.6 `TopicTaxonomyTest`: each type present, transactional/asymmetric/free-form defaults asserted with doctor + HINT-policy overrides allowed

## 7. Phase 2 — Session persistence

- [x] 7.1 `ChatSession` JPA entity with `id`, `learnerId`, `topicId`, `scenarioSeedIndex`, `scratchpadJson` (jsonb-mapped String), `startedAt`, `lastTurnAt`
- [x] 7.2 `ChatSessionRepository` Spring Data interface
- [x] 7.3 Flyway migration `V5__chat_session.sql` with indices on learner_id + topic_id (coordinated with docker-deployment naming)
- [x] 7.4 `ChatSessionService` with `create` / `requireOwned` / `updateScratchpad` / `touch`, ownership 403/404 checks
- [x] 7.5 `ChatService.startScenario` creates a session and returns `sessionId` via `ScenarioResponse`
- [x] 7.6 `ChatRequest` carries optional `sessionId` (backward-compat constructor preserved so old call sites still compile)
- [x] 7.7 `ChatService.reply` validates ownership via `ChatSessionService.requireOwned` (403 mismatch, 404 not-found, null sessionId tolerated for legacy clients)

## 8. Phase 2 — Scratchpad schemas and extraction

- [x] 8.1 Per-type scratchpad schemas implemented via JsonNode validation in extractor (lightweight; full per-type DTOs deferred until a use case beyond the schema check materializes)
- [x] 8.2 Schema validation: TRANSACTIONAL allows empty-slot maps on first turn; ASYMMETRIC requires `phase`; FREE_FORM requires `rhythmBeat` or `topicsTouched`
- [x] 8.3 Per-type extraction prompts inlined in `DefaultScratchpadExtractor` (kept co-located with validation logic; separate `resources/prompts/*.txt` files deferred unless they need hot-edit)
- [x] 8.4 `ScratchpadExtractor` interface with `extract(sessionId, topic, messages, previousJson)` → `Mono<String>`
- [x] 8.5 `DefaultScratchpadExtractor` calls the configured `AiService` with per-type prompt (works for local Ollama + Gemini + OpenAI; small-model dedicated client deferred — env `OLLAMA_EXTRACTION_MODEL` documents the path)
- [x] 8.6 `ollama/entrypoint.sh` updated to pull `OLLAMA_EXTRACTION_MODEL` on startup when set
- [x] 8.7 Extraction wired into `ChatService.reply` `doOnComplete`, runs on `boundedElastic`, persists on success, swallows + logs on failure
- [x] 8.8 Stale-extraction fallback: the next turn always reads `session.getScratchpadJson()` fresh — if extraction hasn't completed, the previous value is used and `stale_extraction` is logged
- [x] 8.9 Extraction timeout configurable via `chat.scratchpad.extraction-timeout-seconds` (default 5s); timeout produces WARN log and `Mono.empty()`

## 9. Phase 2 — PromptAssembler reads scratchpad

- [x] 9.1 `PromptAssembler.assembleSystemPrompt` injects `SCENARIO STATE SO FAR` when scratchpad arg is non-blank
- [x] 9.2 Per-type rendering: `ChatService.renderScratchpadForPrompt` converts the JSON object to deterministic `- key: value` lines (works for all three types because their JSON keys/values map cleanly)
- [x] 9.3 First-turn rendering omits the header entirely (null/blank scratchpad → no section emitted at all)

## 10. Phase 2 — Layer 2 tests

- [x] 10.1 `ChatSessionServiceTest`: create / requireOwned (200 / 404 / 403) / updateScratchpad / touch
- [x] 10.2 `ScratchpadExtractorTest`: transactional happy path; asymmetric requires phase; free-form requires rhythmBeat or topicsTouched; invalid JSON → empty; AiService error → empty; markdown-fenced JSON unwrapped
- [x] 10.3 Existing `ChatServiceTest` plumbing tests retained; new mocked `ChatSessionService` + `ScratchpadExtractor` injected so the `sessionId=null` legacy path stays green
- [x] 10.4 `ChatSessionServiceTest` covers sessionId validation paths (404 missing, 403 foreign-owner, 200 owned); HTTP layer rejection covered by `@Valid` + `requireOwned` semantics

## 11. Phase 3 — Dev-only debug endpoint

- [x] 11.1 `DebugController` (`/api/internal/debug/assemble-prompt`) returning `{systemPrompt, messages}`
- [x] 11.2 `@Profile("dev")` annotation on the class so the bean is not registered outside `dev`
- [x] 11.3 `DebugControllerTest` asserts happy path + scratchpad injection
- [x] 11.4 `DebugControllerTest.classCarriesDevProfileAnnotation` verifies the profile guard structurally (full SpringBootTest profile-gated integration deferred — annotation-on-class is what Spring uses to decide registration)

## 12. Phase 3 — Layer 3 golden harness (Python)

- [x] 12.1 `eval/` directory with `README.md` + `requirements.txt` (`httpx`, `pyyaml`)
- [x] 12.2 `eval/run.py`: argparse `--all` / `--golden NAME` / `--trials N` / `--no-regression`, talks to backend debug endpoint + Ollama
- [x] 12.3 `eval/rubrics.py`: `valid_json`, `has_fields`, `field_missing`, `array_size`, `wordcount_per_item`, `regex_absent`/`present`, `sentence_count_in_range`, `contains_any`, plus three scratchpad assertions
- [x] 12.4 `eval/goldens/restaurant_basic.yml`: 5-turn order chat, R1–R8 + S1–S3
- [x] 12.5 `eval/goldens/interview_basic.yml`: 4-turn interview, R1–R6 + S1–S2; `neverSays` regex enforced
- [x] 12.6 `eval/goldens/environment_peer.yml`: 4-turn peer chat, R1–R4 + S1–S2; abstract-vocab regex enforced
- [x] 12.7 `eval/run.py.write_report`: markdown report at `eval/reports/<timestamp>.md` with per-assertion pass/total matrix
- [x] 12.8 `eval/run.py.regression_diff`: parses the previous report and lists any assertion whose pass count dropped
- [x] 12.9 Exit-code gating: 0 only when all three category thresholds (95% / 80% / 95%) are met
- [x] 12.10 Run command documented in `eval/README.md`; CI workflow itself deferred — repo has no `.github/workflows/` yet, so adding a CI system is out of scope for this change (documented as a follow-up)

## 13. Verification + Cleanup

- [x] 13.1 Ran `mvn test` inside a `maven:3.9-eclipse-temurin-17` container — **73 tests, 0 failures, 0 errors, BUILD SUCCESS**. New suites: PersonaCardTest (8), TopicTaxonomyTest (5), PromptAssemblerTest (13), ChatSessionServiceTest (6), ScratchpadExtractorTest (6), DebugControllerTest (3). Existing ChatServiceTest (5) preserved green.
- [ ] 13.2 Run `python eval/run.py --all` — to be run by the user (requires live backend + Ollama with assembled-prompt endpoint reachable)
- [ ] 13.3 Smoke-test the chat manually — to be done by the user
- [x] 13.4 `findTopic` silent fallback removed (`TopicDto.requireTopic` throws 404; both `ChatService.findTopic` and `PictureService.findTopic` delegate)
- [x] 13.5 grep confirms no remaining `"Learner: "`/`"You: "` substrings in `backend-spring/src/main` other than a historical reference inside a Javadoc comment in `ChatMessage.java`
- [x] 13.6 `PersonalizationService.chatSystemPrompt` removed; chat path goes through `PromptAssembler` only
- [x] 13.7 README appended with a "Chat AI — kiến trúc context" section pointing at the spec and noting the new `OLLAMA_EXTRACTION_MODEL` env var
- [ ] 13.8 Verify the `docker-deployment` change has not regressed — to be done by the user after merging both branches
