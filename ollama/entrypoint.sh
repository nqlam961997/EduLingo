#!/usr/bin/env sh
# Start Ollama in the background, wait for it to become responsive, ensure the
# configured model is present (pull on first run only), then hand the server
# the foreground so signals and logs propagate cleanly.
#
# We use `ollama list` rather than curl because the ollama/ollama base image
# is minimal and does not include curl/wget.
set -eu

MODEL="${OLLAMA_MODEL:-qwen2.5:3b}"
# Optional smaller model used by the chat scratchpad extractor (Phase 2 of
# chat-context-awareness). Leave empty to disable the extra pull.
EXTRACTION_MODEL="${OLLAMA_EXTRACTION_MODEL:-}"

echo "[entrypoint] starting ollama serve in background..."
ollama serve &
SERVE_PID=$!

# Wait for the API to come up (up to ~90s).
i=0
until ollama list >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 90 ]; then
    echo "[entrypoint] ollama did not become responsive in 90s — aborting." >&2
    kill "$SERVE_PID" 2>/dev/null || true
    exit 1
  fi
  sleep 1
done
echo "[entrypoint] ollama is responsive after ${i}s."

ensure_model() {
  m="$1"
  [ -z "$m" ] && return 0
  if ollama list | awk 'NR>1 {print $1}' | grep -Fxq "$m"; then
    echo "[entrypoint] model '$m' already present — skipping pull."
  else
    echo "[entrypoint] pulling model '$m' (first-run, this can be slow)..."
    ollama pull "$m"
    echo "[entrypoint] pull '$m' complete."
  fi
}

ensure_model "$MODEL"
ensure_model "$EXTRACTION_MODEL"

# Hand off to the running server so PID 1 is the long-lived process.
echo "[entrypoint] ready; tailing ollama serve."
wait "$SERVE_PID"
