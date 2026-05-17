## ADDED Requirements

### Requirement: Topic type taxonomy
The system SHALL classify every chat topic into exactly one of three topic types: `TRANSACTIONAL`, `ASYMMETRIC`, or `FREE_FORM`. The topic type SHALL determine the default suggest policy, default tutor overlay, scratchpad schema, and the chat-prompt template branch used at assembly time.

#### Scenario: Restaurant is transactional
- **WHEN** a topic represents a goal-oriented encounter with named slots (e.g., restaurant, hotel, airport, shopping, travel)
- **THEN** the topic's type is `TRANSACTIONAL`
- **AND** its persona card's default `suggestPolicy` is `ON`
- **AND** its default `tutorStyle` is `SUBTLE_RECAST`

#### Scenario: Job interview is asymmetric
- **WHEN** a topic represents a one-way exchange where the AI character drives and the learner responds (e.g., job_interview)
- **THEN** the topic's type is `ASYMMETRIC`
- **AND** its persona card's default `suggestPolicy` is `OFF`
- **AND** its default `tutorStyle` is `OFF`

#### Scenario: Environment is free-form
- **WHEN** a topic represents a peer chat without a clear transaction (e.g., environment, technology, daily_routine, sports, school)
- **THEN** the topic's type is `FREE_FORM`
- **AND** its persona card's default `suggestPolicy` is `ON`
- **AND** its default `tutorStyle` is `SUBTLE_RECAST`

### Requirement: PersonaCard data structure
Every topic SHALL carry a `PersonaCard` record with the following non-optional fields: `name`, `roleContext`, `voice`, `does` (list, length ≥ 3), `doesnt` (list, length ≥ 2), `tutorStyle`, `suggestPolicy`, `vocabA2` (list, length ≥ 5), `scenarioSeed`, `opening`. Optional fields: `vocabB1`, `vocabB2`, `neverSays` (regex-able forbidden phrases for L1/L3 enforcement), `hintTemplate` (only when `suggestPolicy == HINT`).

#### Scenario: Required field missing
- **WHEN** any topic's persona card is missing a required field at startup
- **THEN** the application fails fast at boot with a clear error identifying the topic and field

#### Scenario: DOES list too short
- **WHEN** a topic's persona card has fewer than 3 `does` entries
- **THEN** Layer 1 prompt-assembly tests fail with an explicit assertion

### Requirement: Per-topic persona authored
The 12 topics defined today (`restaurant`, `airport`, `shopping`, `hotel`, `doctor`, `job_interview`, `travel`, `school`, `daily_routine`, `sports`, `technology`, `environment`) SHALL each have a fully-populated persona card matching the schema above. The cards are authored as part of this change, not generated.

#### Scenario: All 12 cards populated
- **WHEN** the application starts
- **THEN** all 12 topic IDs resolve to a `PersonaCard` with every required field present

### Requirement: Suggest policy values
`PersonaCard.suggestPolicy` SHALL take exactly one of three values: `ON`, `OFF`, `HINT`. The value SHALL affect the assembled output-format block in the system prompt and the structure of the model's expected output.

#### Scenario: SUGGEST=ON
- **WHEN** `suggestPolicy == ON`
- **THEN** the system prompt's output-format block instructs the model to produce a `suggestions` array of exactly 3 items, each ≤ 6 words
- **AND** the frontend renders the suggestion chip strip

#### Scenario: SUGGEST=OFF
- **WHEN** `suggestPolicy == OFF`
- **THEN** the system prompt does NOT request a `suggestions` field
- **AND** the frontend hides the suggestion chip strip for this topic

#### Scenario: SUGGEST=HINT
- **WHEN** `suggestPolicy == HINT`
- **THEN** the system prompt instructs the model to produce at most one `hint` string (NOT three options)
- **AND** the frontend renders the hint in a distinct UI affordance from the reply (e.g., a coach-tip toast)

### Requirement: Tutor overlay values
`PersonaCard.tutorStyle` SHALL take exactly one of three values: `SUBTLE_RECAST`, `PAREN`, `OFF`. The value SHALL determine how grammar corrections appear in the assistant's reply.

#### Scenario: TUTOR=SUBTLE_RECAST
- **WHEN** `tutorStyle == SUBTLE_RECAST` and the learner makes an error
- **THEN** the system prompt instructs the model to repeat the corrected phrasing naturally inside its reply (no parentheses, no labels)
- **AND** the error is still recorded into `errors[]` for personalization tracking

#### Scenario: TUTOR=OFF
- **WHEN** `tutorStyle == OFF` and the learner makes an error
- **THEN** the system prompt instructs the model NOT to include any correction in the reply
- **AND** the error is recorded silently for post-session review

### Requirement: CEFR-banded vocabulary anchors
Every persona card SHALL provide a list of A2 vocabulary anchors (≥ 5 words/phrases). The prompt SHALL include the anchors band matching or just below the learner's current CEFR level.

#### Scenario: A2 learner with A2 anchors only
- **WHEN** a learner with `cefrLevel == A2` chats with a topic whose persona has `vocabA2` populated
- **THEN** the assembled system prompt's VOCABULARY ANCHORS section contains the A2 list verbatim

#### Scenario: B1 learner with B1 anchors available
- **WHEN** a learner with `cefrLevel == B1` chats with a topic whose persona has both `vocabA2` and `vocabB1` populated
- **THEN** the assembled system prompt's VOCABULARY ANCHORS section contains the B1 list (with A2 optionally included as a floor)

### Requirement: Curated scenario seed
Every persona card SHALL include a curated `scenarioSeed` string. This scenario text SHALL be deterministic per topic — the same topic always yields the same seed in v1. The scenario text SHALL NOT be generated by the AI model.

#### Scenario: Same topic, same seed
- **WHEN** two different sessions start with the same `topicId`
- **THEN** both sessions receive identical scenario text
- **AND** no AI call is made to generate the scenario

### Requirement: Curated opening line
Every persona card SHALL include a curated `opening` string. The opening line SHALL be returned to the frontend by `POST /api/chat/start` as the assistant's first message. It SHALL NOT be generated by the AI model.

#### Scenario: Opening line is deterministic
- **WHEN** a session starts with `topicId == "restaurant"`
- **THEN** the response's `openingMessage` equals the curated `opening` from the restaurant persona card
- **AND** no AI call is made to generate it

### Requirement: Article correctness in role rendering
The assembled system prompt SHALL use grammatically correct articles when introducing the character's role (e.g., `"You are James, an Airport Staff agent"`, not `"a Airport Staff"`). This applies to every topic where the role starts with a vowel sound: `Airport Staff`, `HR Manager`, `English Teacher`, `Eco Volunteer`.

#### Scenario: Vowel-initial role uses "an"
- **WHEN** a topic's `roleContext` starts with a vowel sound
- **THEN** the assembled prompt's identity line uses "an" before the role
- **AND** Layer 1 tests assert this across all 12 topics

### Requirement: findTopic fallback safety
The current `findTopic` fallback that silently returns a generic `"Tutor / English Tutor"` persona for unknown topic IDs SHALL be removed. The system SHALL reject unknown topic IDs with a clear 400-class error rather than silently producing an off-vibe chat.

#### Scenario: Unknown topic rejected
- **WHEN** `POST /api/chat/start` is called with an unknown `topicId`
- **THEN** the response is HTTP 400 with an error message identifying the unknown topic
- **AND** no chat session is created

### Requirement: NeverSays enforcement at eval time
When a persona card defines `neverSays`, Layer 3 eval rubrics SHALL include regex assertions ensuring the model's replies never match any pattern in the list. This applies on top of any prompt-level instruction.

#### Scenario: Mr. Thompson never hires mid-interview
- **WHEN** the job_interview persona's `neverSays` list contains `"you got the job"`
- **THEN** the Layer 3 golden for job_interview asserts no model reply contains that phrase across all turns
- **AND** failure on any trial fails the golden
