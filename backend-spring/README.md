# EduLingo Backend (Spring Boot)

Spring Boot 3 + WebFlux backend. Two features:

1. **Chat with AI** — SSE streaming, personalized to learner level + recurring mistakes.
2. **Describe a picture** — multipart upload, Gemini Vision corrects the description and tracks errors.

## Stack

- Java 21, Spring Boot 3.3 (WebFlux)
- PostgreSQL 16 + JPA + Flyway migrations
- Gemini `gemini-2.5-flash` via REST

## Layered structure

```
com.edulingo
├── EdulingoApplication.java
├── config/        WebClient, CORS
├── controller/    ChatController, PictureController
├── service/       ChatService, PictureService, PersonalizationService, GeminiService
├── repository/    LearnerProfileRepository, ErrorPatternRepository
├── entity/        LearnerProfile, ErrorPattern (JPA @Entity)
├── dto/           ChatRequest, ChatReply, CorrectionResponse, ErrorItem
└── mapper/        ErrorPatternMapper (DTO ↔ entity)
```

## Run

```bash
docker compose up -d            # Postgres on :5432
export GEMINI_API_KEY=...       # https://aistudio.google.com/apikey
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`. Flyway runs `V1__init.sql` on first boot.

## Environment

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/edulingo` |
| `DB_USER` | `edulingo` |
| `DB_PASSWORD` | `edulingo` |
| `GEMINI_API_KEY` | _(required)_ |
| `GEMINI_MODEL` | `gemini-2.5-flash` |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:3000` |

## API

### Chat (SSE)

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","message":"Hi! How was your weekend?"}'
```

### Picture description

```bash
curl -X POST http://localhost:8080/api/picture/describe \
  -F email=alice@example.com \
  -F description="A boy is play football in the park yesterday." \
  -F image=@./park.jpg
```

Response:

```json
{
  "corrected": "A boy was playing football in the park yesterday.",
  "errors": [
    {"type":"tense","original":"is play","fixed":"was playing","explain_vi":"..."}
  ],
  "score": 70,
  "tips": ["..."]
}
```

## How personalization works

- First call for an email auto-creates a `LearnerProfile` (default level A2).
- Each picture correction extracts `errors[]` → `ErrorPatternMapper` upserts into `error_pattern` (count++).
- Next chat / picture call injects the learner's top 5 recurring mistakes into the system prompt.
- No vector DB, no RAG — just SQL.
