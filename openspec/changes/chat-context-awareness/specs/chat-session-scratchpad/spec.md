## ADDED Requirements

### Requirement: ChatSession entity
The system SHALL persist a `chat_session` row per chat. The entity SHALL include: `id` (UUID, PK), `learnerId` (UUID, FK to `learner_profile`), `topicId` (String), `scenarioSeedIndex` (int, default 0 in v1), `scratchpadJson` (jsonb, nullable), `startedAt` (Instant), `lastTurnAt` (Instant, nullable). The scratchpad JSON shape SHALL conform to the topic-type's schema.

#### Scenario: Session created on chat start
- **WHEN** `POST /api/chat/start` succeeds for an authenticated learner
- **THEN** a new `chat_session` row is inserted with `learnerId`, `topicId`, `startedAt`, `scratchpadJson = null`
- **AND** the row's `id` is returned to the client as `sessionId`

#### Scenario: lastTurnAt updated each turn
- **WHEN** `POST /api/chat/reply` completes successfully
- **THEN** the session's `lastTurnAt` is set to the current Instant

### Requirement: ChatRequest carries sessionId
`ChatRequest` SHALL be extended with a `sessionId` field (UUID, required after the first turn). `POST /api/chat/reply` SHALL reject requests without a valid `sessionId`.

#### Scenario: Missing sessionId
- **WHEN** `POST /api/chat/reply` is called without a `sessionId`
- **THEN** the response is HTTP 400 with an error identifying the missing field

#### Scenario: sessionId from another learner
- **WHEN** the authenticated learner sends a `sessionId` that does not belong to them
- **THEN** the response is HTTP 403

### Requirement: ScenarioResponse returns sessionId
`POST /api/chat/start` SHALL include the new session's `sessionId` in the response, in addition to the existing fields (scenario, openingMessage, characterName, characterRole, characterAvatar).

#### Scenario: sessionId in start response
- **WHEN** `POST /api/chat/start` succeeds
- **THEN** the JSON response contains `sessionId` as a UUID string

### Requirement: Per-topic-type scratchpad schema
The system SHALL define three scratchpad schemas, one per topic type:
- `TRANSACTIONAL`: a slot map keyed by topic-defined slot names (e.g. `party_size`, `main`, `drink`) — slots are declared on the persona card.
- `ASYMMETRIC`: `phase` (current phase name), `phasesDone` (ordered list), `phasesLeft` (ordered list), `facts` (key:value bag), `silentErrors` (list).
- `FREE_FORM`: `rhythmBeat` (int), `nextBeat` (string), `topicsTouched` (list), `learnerHabits` (key:value bag), `aiSharedFacts` (key:value bag), `openLoops` (list).

The active schema for a session SHALL be determined by the topic's type.

#### Scenario: Restaurant scratchpad uses slot schema
- **WHEN** a session's topic is `restaurant` (TRANSACTIONAL)
- **THEN** the persisted `scratchpadJson` matches the slot schema (party_size, main, drink, etc.)

#### Scenario: Job interview scratchpad uses phase schema
- **WHEN** a session's topic is `job_interview` (ASYMMETRIC)
- **THEN** the persisted `scratchpadJson` matches the phase schema

#### Scenario: Environment scratchpad uses rhythm schema
- **WHEN** a session's topic is `environment` (FREE_FORM)
- **THEN** the persisted `scratchpadJson` matches the rhythm schema

### Requirement: Scratchpad injected into system prompt
The `PromptAssembler` SHALL inject a rendered `SCENARIO STATE SO FAR` block into the system prompt on every turn where a non-null scratchpad exists for the session. The rendering SHALL be deterministic and human-readable (key: value pairs for transactional/free-form; sectioned blocks for asymmetric).

#### Scenario: Scratchpad rendered at turn N+1
- **WHEN** a turn N has been processed and produced a scratchpad
- **THEN** at turn N+1 the assembled system prompt contains a `SCENARIO STATE SO FAR` section with the same key:value lines as the persisted scratchpad

#### Scenario: First turn omits the section
- **WHEN** the first learner turn is processed and no scratchpad exists yet
- **THEN** the assembled system prompt contains no `SCENARIO STATE SO FAR` section (the heading is omitted, not left empty)

### Requirement: Second-pass scratchpad extraction
After each chat reply completes (stream end), the system SHALL invoke a `ScratchpadExtractor` to produce the next scratchpad value. Extraction SHALL run asynchronously with respect to the user-visible response (it MUST NOT delay the stream-end signal to the client). The extractor SHALL call a small AI model (provider-configured, e.g., `qwen2.5:1.5b` on Local) with a focused per-topic-type extraction prompt.

#### Scenario: Extraction triggered after reply
- **WHEN** a chat reply finishes streaming
- **THEN** a `ScratchpadExtractor.extract(sessionId, topic, messages, previousScratchpad)` call is dispatched
- **AND** the call runs on a separate scheduler (e.g., Reactor `boundedElastic`)
- **AND** the HTTP stream to the client is not blocked by the extraction call

#### Scenario: Extraction result persisted
- **WHEN** extraction returns a valid JSON object matching the topic type's schema
- **THEN** the session's `scratchpadJson` is updated to the new value
- **AND** the next turn's prompt assembly reads this new value

### Requirement: Stale-extraction fallback
If turn N+1 arrives before extraction for turn N has persisted, the system SHALL proceed using the previous scratchpad (the value from turn N-1 or earlier). The event SHALL be logged with a `stale_extraction` marker including session id and turn number.

#### Scenario: Fast-typing learner
- **WHEN** the learner sends turn N+1 before extraction for turn N has completed
- **THEN** turn N+1's prompt assembly uses the previous scratchpad
- **AND** a log entry `stale_extraction sessionId=<...> turn=<N+1>` is written
- **AND** the chat continues without error

### Requirement: Extraction failure tolerance
If the extraction call fails (timeout, parse error, model error), the session's scratchpad SHALL be left unchanged. The failure SHALL be logged at WARN level. The next chat turn SHALL proceed using the previous scratchpad.

#### Scenario: Extractor returns invalid JSON
- **WHEN** the extraction model returns non-JSON or JSON that fails schema validation
- **THEN** the previous scratchpad value is retained
- **AND** a `scratchpad_extraction_failed` WARN log entry is written
- **AND** the next user turn proceeds normally

#### Scenario: Extractor times out
- **WHEN** the extraction call exceeds a configured timeout (e.g., 5s)
- **THEN** the previous scratchpad is retained
- **AND** a `scratchpad_extraction_timeout` WARN log entry is written

### Requirement: First-turn extraction policy
On turn 1 (the learner's very first message), extraction SHALL run after the reply completes, just like any other turn. There is no special "skip first turn" carve-out.

#### Scenario: T1 extraction produces initial scratchpad
- **WHEN** a session reaches the end of turn 1 (the assistant's reply to the learner's first message)
- **THEN** extraction is invoked with `previousScratchpad = null`
- **AND** on success, the session's first non-null scratchpad is persisted

### Requirement: Scratchpad schema validation
The system SHALL validate the extracted JSON against the topic-type's declared schema before persisting. Validation SHALL reject extra unknown top-level keys and SHALL enforce required keys for the type.

#### Scenario: TRANSACTIONAL schema requires slot map
- **WHEN** the extractor returns a JSON object for a TRANSACTIONAL session that omits the topic's declared slots
- **THEN** validation fails and the previous scratchpad is retained
- **AND** the failure is logged with the specific missing-keys list
