# EduLingo Chat — Layer 3 Eval Harness

Live-model golden-conversation rubric runner. Part of the `chat-context-awareness` change.

Layers 1 and 2 (static prompt + mock-model behavior tests) live in
`backend-spring/src/test/java/` and run on every `mvn test`. Layer 3 is the
live-model layer — slow, probabilistic — and lives here.

## What it does

For each golden conversation under `goldens/`, the runner:

1. Fetches the canonical assembled prompt from the backend's dev-only
   `POST /api/internal/debug/assemble-prompt` endpoint (avoids duplicating
   `PromptAssembler` logic in Python).
2. Submits each fixed learner input in sequence to the configured Ollama
   model.
3. Evaluates per-turn assertions (deterministic structural + probabilistic
   behavioral) and end-of-chat scratchpad assertions.
4. Writes a markdown report at `reports/<timestamp>.md` with a pass/fail
   matrix and a regression diff against the previous run.
5. Exits 0 only when all default thresholds are met:
   - Deterministic assertions ≥ 95% pass
   - Behavioral assertions ≥ 80% pass
   - Scratchpad assertions ≥ 95% pass

## Setup

```bash
cd eval
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

The backend must be running with `spring.profiles.active=dev` (so the debug
endpoint is exposed) and Ollama must be reachable.

## Run

```bash
python run.py --all                                # all goldens
python run.py --golden restaurant_basic            # one golden
python run.py --all --trials 5                     # raise probabilistic N
python run.py --all --no-regression                # skip diff vs previous
```

Env vars:

| Var | Default | Purpose |
| --- | --- | --- |
| `EDULINGO_BACKEND_URL` | `http://localhost:8080` | Spring debug endpoint host |
| `OLLAMA_URL`           | `http://localhost:11434` | Ollama API host |
| `OLLAMA_MODEL`         | `gemma2:9b` | Chat model |
| `EVAL_TRIALS`          | `3` | Trials per probabilistic assertion |

## Run cadence

Layer 3 should run:
- Nightly on `main`
- Pre-merge on PRs touching `ChatService.java`, `PromptAssembler.java`,
  `TopicDto.java`, or `eval/`

See spec `chat-eval-harness` for the formal contract.
