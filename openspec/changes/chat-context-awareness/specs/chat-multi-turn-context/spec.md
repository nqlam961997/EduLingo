## ADDED Requirements

### Requirement: Multi-turn ChatMessage record
The system SHALL define a `ChatMessage` record with `role` (enum: `user` | `assistant`) and `content` (String). This record SHALL be the only history shape consumed by `AiService` implementations.

#### Scenario: Roles are typed
- **WHEN** a `ChatMessage` is constructed with `role == "system"` or any other non-enum value
- **THEN** construction fails at compile time (enum) or runtime validation

### Requirement: AiService accepts multi-turn messages
`AiService.streamGenerate` SHALL take a system prompt string and a `List<ChatMessage>` of alternating user/assistant turns. The non-streaming `AiService.generate` SHALL adopt the same signature. The previous signatures taking a single `userText` String SHALL be removed.

#### Scenario: Multi-turn signature
- **WHEN** any client calls `AiService.streamGenerate(systemPrompt, messages)`
- **THEN** the call compiles and runs against all three implementations: `GeminiService`, `OpenAiService`, `LocalAiService`
- **AND** the previous flat-string signature is no longer present in `AiService`

### Requirement: GeminiService sends contents array
`GeminiService` SHALL translate the `List<ChatMessage>` into the Gemini API's `contents` array, using Gemini's role values (`user`, `model`) and preserving turn order. The `systemInstruction` field SHALL carry the system prompt.

#### Scenario: Restaurant 4-turn chat
- **WHEN** Gemini is invoked with a system prompt and 4 messages (alternating user/assistant starting with assistant)
- **THEN** the outbound HTTP body's `contents` array has 4 entries in the same order, with assistant turns mapped to role `model`
- **AND** the `systemInstruction.parts[0].text` equals the system prompt

### Requirement: OpenAiService sends messages array
`OpenAiService` SHALL translate the `List<ChatMessage>` into the OpenAI Chat Completions `messages` array, prefixed by a `{role: "system", content: systemPrompt}` entry, preserving turn order.

#### Scenario: System message first
- **WHEN** OpenAI is invoked with a system prompt and N chat messages
- **THEN** the outbound body's `messages` array has N+1 entries
- **AND** the first entry is `{role: "system", content: <systemPrompt>}`

### Requirement: LocalAiService and Python bridge accept multi-turn
`LocalAiService` SHALL send a `{system_prompt, messages}` body to the Python `/generate` and `/stream` endpoints. The Python service SHALL forward the messages array unchanged to Ollama's `/api/chat` endpoint.

#### Scenario: Python forwards multi-turn to Ollama
- **WHEN** Spring calls Python `/stream` with `{system_prompt, messages: [...]}`
- **THEN** Python posts to Ollama `/api/chat` with `messages = [{role:"system",...}, ...messages]`
- **AND** the flattened "Learner: ... \n You: ..." string is no longer constructed anywhere in the stack

### Requirement: ChatService assembles messages from history
`ChatService.reply` SHALL transform `ChatRequest.history` plus the newest learner message into a `List<ChatMessage>` ordered chronologically: every `MessageItem` with role `user` becomes a `ChatMessage` with role `user`, and every `MessageItem` with role `assistant` becomes a `ChatMessage` with role `assistant`. The newest learner message SHALL be appended as the final user `ChatMessage`.

#### Scenario: 5-turn history reconstruction
- **WHEN** history has 5 messages (3 user + 2 assistant interleaved) and the newest message is "no just pasta"
- **THEN** the assembled `List<ChatMessage>` has 6 entries
- **AND** the last entry is `ChatMessage(role=user, content="no just pasta")`
- **AND** turn order matches the history order

### Requirement: Assistant history stores parsed reply text
When the frontend appends an assistant turn to history, it SHALL store the parsed `reply` text (after `tryParseJson`), not the raw JSON streamed from the model. When `tryParseJson` fails, the raw text SHALL be stored.

#### Scenario: JSON-formatted reply is unwrapped before history append
- **WHEN** the model streams `{"reply":"Of course!","suggestions":[],"errors":[]}`
- **THEN** `messages[]` in `ChatView.vue` stores `{role: "assistant", text: "Of course!"}` (parsed reply only)
- **AND** the JSON wrapper is not visible to the next turn's prompt assembly

### Requirement: First-turn handling
On the first learner turn, `ChatService.reply` SHALL receive a `history` of length 1 (containing only the curated `opening` line as an assistant message) plus the newest learner message. The resulting `List<ChatMessage>` SHALL have exactly 2 entries.

#### Scenario: First learner reply
- **WHEN** the learner sends their first message after seeing the opening line
- **THEN** the assembled messages array has length 2: `[{role:assistant, content:opening}, {role:user, content:newestMessage}]`

### Requirement: No flattened pseudo-transcript
The strings `"Learner: "` and `"You: "` SHALL NOT appear in any prompt assembled by `PromptAssembler` or sent to any AI provider. The legacy `buildConversation` method SHALL be removed.

#### Scenario: No legacy markers in prompt
- **WHEN** any topic + any history is assembled into a request
- **THEN** the system prompt and messages contents contain neither `"Learner: "` nor `"You: "` as turn markers
- **AND** Layer 1 tests assert this for all 12 topics
