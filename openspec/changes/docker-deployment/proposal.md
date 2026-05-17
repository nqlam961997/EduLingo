## Why

EduLingo today requires four manual terminals (Postgres, Ollama, FastAPI, Spring Boot, Vue) plus host-level Java/Maven/Python/Node/PostgreSQL installations to run — making it impractical to demo on a teacher's machine or share via a VPS. A single `docker compose up` deployment removes that friction and is the prerequisite for both classroom grading and VPS-hosted demos.

## What Changes

- Add multi-stage Dockerfiles for `backend-spring/`, `backend-python/`, `frontend-vue/`, and a custom `ollama/` image with auto-pull entrypoint.
- Add a root `docker-compose.yml` orchestrating all five services (frontend, backend, ai-proxy, ollama, postgres) plus an optional Caddy service for HTTPS — Caddy activates when `DOMAIN` is set in `.env`.
- Add `docker-compose.dev.yml` overlay providing source bind-mounts and hot-reload (Vite HMR, Spring DevTools, uvicorn `--reload`).
- Bundle Ollama inside compose with `qwen2.5:3b` as the default CPU-friendly model; first-run auto-pulls into a named volume.
- **BREAKING (developer workflow):** Remove `backend-spring/docker-compose.yml` — Postgres orchestration moves to root compose. Local-host runs still work but are no longer the documented path.
- Refactor frontend hardcoded `http://localhost:8000` and similar URLs to `import.meta.env.VITE_API_BASE` (default `/api`) and `import.meta.env.VITE_LOCAL_AI_URL` (default `/ai`). Nginx in the frontend image path-routes `/api` → backend, `/ai` → ai-proxy, `/` → static Vue.
- Externalize secrets and host-specific values via root `.env` (committed `.env.example`, gitignored `.env`). Require `JWT_SECRET`, `POSTGRES_PASSWORD`, `DOMAIN` (when HTTPS desired) for VPS deploys.
- Replace 4-terminal section of `README.md` with a Docker quickstart for laptop and VPS.

## Capabilities

### New Capabilities

- `container-runtime`: Per-service Dockerfiles and the Ollama bootstrap (auto-pull on first run). Defines image build contracts and runtime entrypoints.
- `compose-orchestration`: Root `docker-compose.yml` and `docker-compose.dev.yml` overlay — service graph, networks, volumes, healthchecks, depends_on ordering, dev vs prod port exposure.
- `edge-routing`: Frontend `nginx.conf` path routing (`/`, `/api`, `/ai`) plus optional Caddy reverse proxy with auto-Let's Encrypt for VPS HTTPS.
- `frontend-env-config`: Vite environment-variable contract for API/AI base URLs and refactor of all hardcoded backend URLs in `frontend-vue/src/` to use `import.meta.env.VITE_*`.
- `deployment-config`: `.env.example` schema, env propagation into services, CORS origin derivation from `DOMAIN`, JWT secret hardening for VPS.

### Modified Capabilities

<!-- None — this is the repo's first OpenSpec change; no existing specs to modify. -->

## Impact

**Affected code**
- `backend-spring/docker-compose.yml` → deleted (folded into root)
- `frontend-vue/src/views/ChatView.vue`, `frontend-vue/src/views/VocabularyLearnView.vue`, `frontend-vue/src/api/*.ts` → refactor hardcoded URLs to `import.meta.env.VITE_*`
- `backend-spring/src/main/resources/application.yml` → `CORS_ORIGINS` env wiring (already env-driven, but `.env.example` documents required values for VPS)
- `README.md` → Docker quickstart replaces 4-terminal section
- `.gitignore` → add `.env`

**New artifacts**
- `docker-compose.yml`, `docker-compose.dev.yml`, `.env.example`, `.dockerignore` (root)
- `backend-spring/Dockerfile`, `backend-spring/.dockerignore`
- `backend-python/Dockerfile`, `backend-python/.dockerignore`
- `frontend-vue/Dockerfile`, `frontend-vue/nginx.conf`, `frontend-vue/.dockerignore`
- `ollama/Dockerfile`, `ollama/entrypoint.sh`
- `caddy/Caddyfile`

**Affected dependencies / runtime**
- Docker Engine ≥ 24 and Docker Compose v2 become hard requirements for the documented happy path.
- Host installations of Java/Maven/Python/Node/Ollama/Postgres become optional (still supported for non-Docker workflows).
- Ollama default model changes from `gemma2:9b` (5.5GB, unusable on CPU) to `qwen2.5:3b` (~2GB, CPU-viable) — `OLLAMA_MODEL` env keeps host overrides working.

**Affected APIs / network surface**
- In Docker mode, the browser hits a single origin (`http://localhost` on laptop, `https://<DOMAIN>` on VPS) — frontend, `/api`, `/ai` share one URL.
- Postgres `:5432` is not exposed in base compose; exposed only in dev overlay.

**Operational**
- VPS deploys require DNS pointed at the VPS IP before `docker compose up` so Caddy can complete the ACME HTTP-01 challenge.
- First-run on a fresh machine downloads ~2GB Ollama model (one-time, persists in named volume).
- `edge-tts` continues to require outbound Internet access to Microsoft Azure TTS.
