## ADDED Requirements

### Requirement: Layer 1 prompt-assembly tests
The system SHALL include a JUnit test class `PromptAssemblerTest` under `backend-spring/src/test/java/com/edulingo/service/` that asserts structural invariants of the assembled system prompt for every topic and every topic type. These tests SHALL be deterministic, require no AI model, and run in the default `mvn test` phase.

#### Scenario: Every topic produces a non-empty prompt
- **WHEN** `PromptAssemblerTest` runs
- **THEN** for each of the 12 topic IDs, `PromptAssembler.assembleSystemPrompt(topic, scratchpad=null, learner=A2)` returns a non-empty string
- **AND** the string contains the persona's `name` and `roleContext` verbatim

#### Scenario: Suggest policy reflected in output-format block
- **WHEN** a topic's `suggestPolicy == OFF` is assembled
- **THEN** the prompt's output-format section contains no `suggestions` field requirement

#### Scenario: Article correctness across all topics
- **WHEN** the assembly runs for `airport`, `school`, `job_interview`, `environment` (vowel-initial roles)
- **THEN** the identity line uses `"an"` not `"a"` before the role

#### Scenario: No legacy turn markers
- **WHEN** any topic is assembled with a non-empty history
- **THEN** the assembled output (system + messages) contains neither `"Learner: "` nor `"You: "`

### Requirement: Layer 1 persona-card invariant tests
The system SHALL include a JUnit test class `PersonaCardTest` that asserts every persona card declared in `TopicDto` satisfies the schema (required fields present, lists non-empty, enum values valid).

#### Scenario: All persona cards complete
- **WHEN** `PersonaCardTest.allTopicsHaveCompleteCards()` runs
- **THEN** every topic ID resolves to a persona card with every required field populated
- **AND** the test fails immediately if any field is missing, identifying the offending topic

#### Scenario: tutorStyle / suggestPolicy enum domain
- **WHEN** the invariant test runs
- **THEN** every card's `tutorStyle` is one of `{SUBTLE_RECAST, PAREN, OFF}`
- **AND** every card's `suggestPolicy` is one of `{ON, OFF, HINT}`

### Requirement: Layer 2 mock-model behavior tests
The system SHALL extend `ChatServiceTest` (or add new sibling classes such as `ChatSessionServiceTest`, `ScratchpadExtractorTest`) with mock-model tests covering scratchpad behavior. These tests SHALL run in the default `mvn test` phase.

#### Scenario: Scratchpad applied to next-turn prompt
- **WHEN** a mocked extraction returns `{main: "pasta", drink: "water"}` after turn N
- **THEN** the assembled system prompt for turn N+1 contains a `SCENARIO STATE SO FAR` section with those exact key:value lines

#### Scenario: Stale fallback when extraction not done
- **WHEN** the learner sends turn N+1 before extraction for turn N completes
- **THEN** turn N+1's prompt uses the previous scratchpad
- **AND** a `stale_extraction` log entry is emitted

#### Scenario: Extraction failure preserves previous state
- **WHEN** the mocked extractor throws or returns invalid JSON
- **THEN** the session's scratchpad is unchanged
- **AND** the chat reply still streams successfully

### Requirement: Layer 3 live-model golden conversations
The system SHALL include three live-model golden conversations under `eval/` (Python): one per topic type — `restaurant_basic.yml` (TRANSACTIONAL), `interview_basic.yml` (ASYMMETRIC), `environment_peer.yml` (FREE_FORM). Each golden SHALL define: persona/topic, learner inputs as a fixed list, per-turn deterministic assertions (R1..Rn), per-turn probabilistic assertions, and end-of-chat scratchpad assertions (S1..Sn).

#### Scenario: Restaurant golden runs end-to-end
- **WHEN** `python eval/run.py --golden restaurant_basic` is invoked
- **THEN** the runner submits each learner input in sequence, captures the assistant replies, and evaluates the rubric
- **AND** a markdown report identifies pass/fail per turn per assertion

#### Scenario: Golden requires curated scenario
- **WHEN** any golden is run
- **THEN** the topic's curated scenario seed (not a model-generated one) is used
- **AND** the same golden produces the same scratchpad shape (within probabilistic tolerance) across runs

### Requirement: Layer 3 rubric assertion types
Each Layer 3 golden's per-turn assertions SHALL include at least these categories:
- Structural (deterministic): valid JSON, required fields present, output respects the topic's `suggestPolicy` (e.g., no `suggestions` field for `OFF`), no parenthetical correction when `tutorStyle == SUBTLE_RECAST`.
- Behavioral (probabilistic, ≥ 2/3 trials by default): topic-relevant memory checks (e.g., turn N reply references something said earlier), forbidden-phrase regex (`neverSays`), error-detection on intentional learner mistakes.
- Scratchpad (deterministic, after end-of-chat): key:value assertions matching the topic-type's expected final state.

#### Scenario: Restaurant memory assertion
- **WHEN** the restaurant golden runs and the learner has said "i want pastas" at turn 2 and "water please" at turn 3
- **THEN** at turn 5 the model's reply or suggestions reference "pasta" or "water" in at least 2 of 3 trials

#### Scenario: Job interview forbids "you got the job"
- **WHEN** the job_interview golden runs
- **THEN** no model reply across any turn or trial contains the phrase "you got the job"

#### Scenario: Restaurant final scratchpad
- **WHEN** the restaurant golden finishes
- **THEN** the final scratchpad has `main == "pasta"`, `drink == "water"`, `party_size == 2` in all trials

### Requirement: Layer 3 report format
The Layer 3 runner SHALL emit a markdown report at `eval/reports/<timestamp>.md` showing per-golden, per-trial, per-assertion pass/fail along with overall pass rate and a regression diff against the previous run.

#### Scenario: Report identifies regression
- **WHEN** the current run has a lower pass rate than the previous run for any assertion
- **THEN** the report's `REGRESSION FROM PREVIOUS RUN` section lists the specific assertion and the delta (e.g., R6 dropped from 3/3 to 2/3)

### Requirement: Dev-only debug endpoint for prompt assembly
The Spring backend SHALL expose `POST /api/internal/debug/assemble-prompt` accepting `{topicId, learnerCefr, history, scratchpad}` and returning the assembled `{systemPrompt, messages}`. This endpoint SHALL be active only when the Spring profile includes `dev`. It SHALL NOT be exposed in production builds.

#### Scenario: Endpoint available in dev
- **WHEN** the application runs with `spring.profiles.active=dev`
- **THEN** `POST /api/internal/debug/assemble-prompt` returns 200 with the assembled payload

#### Scenario: Endpoint blocked in production
- **WHEN** the application runs with `spring.profiles.active=prod`
- **THEN** `POST /api/internal/debug/assemble-prompt` returns 404 (the bean is not registered)

#### Scenario: Layer 3 uses the endpoint
- **WHEN** the Python eval runner needs the assembled prompt for a given input
- **THEN** it calls the debug endpoint to fetch the canonical assembly
- **AND** it does NOT duplicate prompt-assembly logic in Python

### Requirement: Pass thresholds for CI gating
The Layer 3 harness SHALL expose configurable pass thresholds. Default values SHALL be: deterministic assertions ≥ 95% pass, behavioral assertions ≥ 80% pass, scratchpad assertions ≥ 95% pass. The runner SHALL exit with code 0 only when all three thresholds are met.

#### Scenario: Threshold met → exit 0
- **WHEN** all categories meet or exceed their thresholds
- **THEN** the process exits 0 and the markdown report's overall line reads `PASS`

#### Scenario: Threshold missed → exit 1
- **WHEN** any category falls below its threshold
- **THEN** the process exits 1 and the report's overall line reads `FAIL` with the failing category named

### Requirement: Run cadence
Layer 1 and Layer 2 SHALL run on every commit as part of `mvn test`. Layer 3 SHALL run nightly on the default branch and on every pull request that modifies any file under `backend-spring/src/main/java/com/edulingo/service/Chat*`, `backend-spring/src/main/java/com/edulingo/service/Prompt*`, `backend-spring/src/main/java/com/edulingo/dto/TopicDto.java`, or `eval/`.

#### Scenario: PR touching ChatService triggers Layer 3
- **WHEN** a pull request modifies `ChatService.java`
- **THEN** CI runs `python eval/run.py --all` against the local Ollama service
- **AND** the PR is blocked from merging if exit code is non-zero
