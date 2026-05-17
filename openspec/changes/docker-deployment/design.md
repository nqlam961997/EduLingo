## Context

EduLingo is a four-service application (Vue 3 frontend, Spring Boot 3.3.5 backend, FastAPI AI proxy, Ollama LLM) plus PostgreSQL 16. Today, running it requires installing JDK 17, Maven 3.9+, Node 18+, Python 3.10+, PostgreSQL 14+, and Ollama on the host, then orchestrating four terminals in a specific order. This makes laptop demos and VPS deployments impractical.

The repo already has a partial step toward containerization: `backend-spring/docker-compose.yml` containerizes only Postgres. The Spring Boot backend, FastAPI proxy, and Vue frontend each read config from environment variables, but the frontend embeds hardcoded `http://localhost:8000` URLs in several views, which prevents a single bundle from working on both `http://localhost` and `https://<domain>`.

The deployment target is bimodal: developer laptops (HTTP, hot reload nice-to-have) and a single Linux VPS without GPU (HTTPS via Let's Encrypt). The same compose stack should serve both, with environment-driven activation of HTTPS and dev-only conveniences.

Stakeholders: the developer (today's workflow), classmates and the grading teacher (one-command demo), future contributors (predictable onboarding).

## Goals / Non-Goals

**Goals:**
- `docker compose up` on a fresh Docker-enabled host stands up the entire stack — laptop or VPS.
- Same `docker-compose.yml` base file works on laptop (`http://localhost`) and VPS (`https://<DOMAIN>`); only `.env` values differ.
- Ollama runs inside compose with a CPU-viable default model; the ~2GB model pull happens automatically on first run and persists.
- Hot-reload developer experience is available through an opt-in overlay (`docker-compose.dev.yml`), not the default path.
- Frontend code uses environment-driven base URLs so the same build artifact is portable across hosts.
- Secrets (`JWT_SECRET`, `POSTGRES_PASSWORD`, optional `GEMINI_API_KEY`/`OPENAI_API_KEY`) come from `.env`, not source files.

**Non-Goals:**
- Kubernetes, Helm, multi-host orchestration.
- Image registry workflows (GHCR push/pull, signed images, SBOM publication).
- GPU passthrough or accelerated Ollama inference.
- Offline / air-gapped operation (`edge-tts` and Let's Encrypt both need outbound Internet).
- Production-grade secret management (Vault / SOPS / cloud KMS).
- Backup/restore automation for the Postgres volume.
- Horizontal scaling, blue/green, canary, or zero-downtime deploys.
- Replacing PostgreSQL, Ollama, or any other component.

## Decisions

### D1. Service composition: 5 services in one stack (+ optional Caddy)

The stack contains `frontend`, `backend`, `ai-proxy`, `ollama`, `postgres`, and a conditionally-enabled `caddy`. All services share one user-defined bridge network (`edulingo-net`); only `frontend` (or `caddy` when `DOMAIN` is set) binds to the host.

**Alternatives considered:**
- *Ollama on host, everything else in compose.* Rejected because it breaks the "one command" promise on VPS and forces per-host install instructions.
- *Cloud LLM only (`AI_PROVIDER=gemini`).* Rejected because the project explicitly wants to preserve the "local AI" capability.
- *Separate compose files per concern (database, app, ai).* Rejected because it multiplies the surface area users must understand for a demo workflow.

### D2. Default model: `qwen2.5:3b` (overridable)

The current README documents `gemma2:9b` (~5.5GB, ~30s/response on CPU). For a CPU-only VPS this is unusable. `qwen2.5:3b` (~1.9GB, ~5–10 tok/s on CPU) is the new default. `OLLAMA_MODEL` env var preserves overrides.

**Alternatives considered:** `gemma2:2b` (faster but weaker), `llama3.2:3b` (comparable, but qwen2.5 has stronger multilingual support which matches the Vietnamese-language README and helps EFL learners).

### D3. Ollama bootstrap: custom image with entrypoint auto-pull

A thin `ollama/Dockerfile` (`FROM ollama/ollama`) plus `entrypoint.sh` runs `ollama serve` in the background, waits for readiness, and pulls `$OLLAMA_MODEL` only if not already present in the named volume. Subsequent ups skip the pull.

**Alternatives considered:**
- *Init container (`depends_on: service_completed_successfully`).* More compose machinery, same effect.
- *Manual one-time `docker compose exec ollama ollama pull ...`.* Adds a user-facing step. Rejected for UX.

### D4. Edge routing: nginx inside `frontend` image + Caddy always present (TLS conditional on `DOMAIN`)

The `frontend` service is a multi-stage image: Node builds Vue, output is served by an `nginx:alpine` stage. `nginx.conf` serves `/` as static assets and reverse-proxies `/api/*` → `backend:8080` and `/ai/*` → `ai-proxy:8000` (with prefix strip).

**Caddy is always part of the compose graph** (not a conditionally-activated profile). The Caddyfile uses `{$SITE_ADDRESS}` as its site address, which docker-compose substitutes from `${DOMAIN:-:80}`. This means:
- `DOMAIN` unset → `SITE_ADDRESS=:80` → Caddy serves plain HTTP, no cert request.
- `DOMAIN=foo.com` → `SITE_ADDRESS=foo.com` → Caddy auto-provisions Let's Encrypt on first request.

The `frontend` service exposes its port only on the internal network (`expose: [80]`, no `ports:` mapping). Caddy is the sole public-facing service.

**Why this over "Caddy only runs when DOMAIN is set":** Compose profiles control whether services run, not their per-service config — they cannot override `frontend.ports` from another service's profile activation. The alternatives were (a) require users to set `HTTP_PORT=8081` when activating Caddy (UX wart), (b) add a third compose overlay file `docker-compose.tls.yml` (increases command-line complexity), or (c) accept ~50 MB Caddy image cost on laptop installs. Option (c) wins — laptop installs aren't trying to optimize image size, and the same `docker compose up -d` invocation works identically on both deployment targets.

**Alternatives considered:**
- *Caddy serves static + proxies everything (skip frontend nginx).* Rejected because then dev mode (no Caddy) needs another way to combine static + API routing, fragmenting the design.
- *Host-level reverse proxy on VPS.* Rejected because it splits configuration between in-repo and outside-repo artifacts and weakens the "one command" property.
- *Skip HTTPS for the demo.* Rejected — the teacher will be grading via a public URL; browsers and password-managers flag HTTP.
- *Three compose files (base + dev + tls overlay).* Considered during implementation; rejected to keep command-line ergonomics simple (`docker compose up` works everywhere).

### D5. Two compose files via overlay (`-f base -f dev`)

`docker-compose.yml` is the prod-built base. `docker-compose.dev.yml` overrides specific services to bind-mount source and run dev servers (Vite, Spring DevTools, uvicorn `--reload`). Developers run `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`; everyone else runs `docker compose up`.

**Alternatives considered:** Compose profiles. Profiles selectively enable services but cannot override commands or volumes cleanly — overlay files give us the per-service overrides we need.

### D6. Frontend env contract: `VITE_LOCAL_AI_URL` only (backend stays bare-relative)

The pre-existing codebase already uses bare relative paths (`/api/...`) in `frontend-vue/src/api/auth.ts` and `frontend-vue/src/api/edulingo.ts` for all backend calls (17+ call sites). These work correctly behind production nginx (single-origin routing) and in dev mode (via Vite's `server.proxy`). Refactoring them to use a `VITE_API_BASE` env var would touch 17+ files for zero functional gain.

Therefore, only `VITE_LOCAL_AI_URL` is introduced — for the AI proxy, where the gap actually exists (`VocabularyLearnView.vue` hardcodes `http://localhost:8000/tts`; `ChatView.vue` reads `import.meta.env.VITE_LOCAL_AI_URL` but with the wrong fallback). A shared `frontend-vue/src/config/env.ts` module exports `LOCAL_AI_URL` (default `/ai`); the two views import from it. Build-time injected via `ARG VITE_LOCAL_AI_URL` in `frontend-vue/Dockerfile`. The Vite dev proxy gains a `/ai` route alongside the existing `/api` route so `npm run dev` continues to work against a Dockerized stack.

**Alternatives considered:**
- *Full refactor to `VITE_API_BASE` everywhere.* 17+ file touch for zero functional improvement; rejected during implementation.
- *Hybrid optional override (`VITE_API_BASE` honored if set, else bare `/api`).* Adds one helper for marginal flexibility nobody asked for; rejected.
- *Runtime config via `window.__CONFIG__` injected by entrypoint.* More complex; needed only if a single image must support arbitrary origins. Not our case.
- *Funnel all FastAPI traffic through Spring.* Cleanest API surface, but requires Spring controllers to proxy `/tts`, `/stream`, `/generate` — significantly more code than path-routing in nginx. Deferred.

### D7. Postgres port: internal-only in base, host-bound in dev overlay

Base compose does not map `5432` to the host. The dev overlay does, so developers can `psql -h localhost -p 5432 edulingo`.

### D8. Secrets and host-specific config in root `.env`

`.env.example` is committed with safe defaults and required-field comments. Real `.env` is gitignored. Compose uses variable substitution (`${JWT_SECRET}`, `${DOMAIN}`, etc.). Spring's existing `application.yml` already uses `${ENV_VAR:default}` syntax — no Spring changes needed beyond removing the embedded Gemini default key.

**Required for VPS:** `JWT_SECRET` (32+ random bytes), `POSTGRES_PASSWORD`, `DOMAIN`.
**Optional:** `OLLAMA_MODEL`, `TTS_VOICE`, `GEMINI_API_KEY`, `OPENAI_API_KEY`, `AI_PROVIDER`.

### D9. CORS strategy: same-origin via nginx, env-driven origins for safety

Because the browser hits a single origin (`http://localhost` or `https://<DOMAIN>`) and nginx path-routes internally, traditional CORS preflight is unnecessary in the happy path. Spring's `CORS_ORIGINS` is still env-driven and defaults to `http://localhost,http://localhost:80,https://${DOMAIN}` when `DOMAIN` is set — keeping the door open for direct API hits during debugging.

### D10. Healthchecks drive startup ordering

- `postgres`: `pg_isready` (unchanged from existing compose).
- `ollama`: HTTP GET `:11434/api/tags` returns 200 once model server is reachable.
- `ai-proxy`: HTTP GET `:8000/health`.
- `backend`: HTTP GET `:8080/actuator/health` (requires adding `spring-boot-starter-actuator` — minor pom.xml change).
- `frontend`: HTTP GET `:80/`.
- `caddy`: standard nginx-style readiness on `:80`.

`depends_on` uses `condition: service_healthy` for the chains: `backend → postgres`, `ai-proxy → ollama`, `frontend → backend, ai-proxy`.

### D11. Build performance: BuildKit cache mounts

Spring image uses `RUN --mount=type=cache,target=/root/.m2` so Maven dependencies cache across rebuilds. Frontend uses `RUN --mount=type=cache,target=/root/.npm`. Documented as requiring `DOCKER_BUILDKIT=1` (default in modern Docker).

## Risks / Trade-offs

- **2GB first-run model pull is silent and slow** → ai-proxy and backend will be healthy but chat requests fail until pull completes. Mitigation: README warning, optional pre-pull command, and `ollama` healthcheck waits for model availability (not just server reachability). Trade-off accepted because automating it is the right UX even if first run is slow.

- **CPU-only Ollama gives 5–10 tok/s** → noticeably slower than gemma2:9b on GPU. Mitigation: documented as a constraint; users with a GPU can override `OLLAMA_MODEL=gemma2:9b` and pass `--gpus all` (out of base compose scope but mentioned in README).

- **Caddy ACME requires DNS to resolve before `up`** → first-run on a fresh VPS with wrong DNS will spam Let's Encrypt with failures. Mitigation: README explicit ordering ("point DNS first, then `docker compose up`"); Caddy retries with backoff anyway.

- **`edge-tts` needs Internet to Microsoft Azure** → degrades silently if blocked. Mitigation: documented in README under "known constraints"; out of scope to replace.

- **Live2D model files (~30MB binaries) at `frontend-vue/public/models/haru/`** → if gitignored, the built image will be missing assets and Live2D will fail to load. Mitigation: tasks include explicit `git ls-files` check; if missing, document a `npm run setup:models` script or commit them.

- **Existing `backend-spring/docker-compose.yml` deletion** → users with existing local volumes (`edulingo_pg`) may worry about data loss. Mitigation: the named volume name is preserved (`edulingo_pg`), so existing data is automatically picked up; documented in README under "migration from local-Postgres setup".

- **`JWT_SECRET` and Gemini API key currently have insecure defaults in `application.yml`** → leaving them in source after this change keeps the demo-friendly local path working but is a latent security issue. Mitigation: defaults remain for laptop convenience; `.env.example` flags them with "MUST override for VPS deploy"; archive note acknowledges this is demo-grade.

- **BuildKit cache mounts require Docker ≥ 23 / Compose v2** → users on older Docker may see slower builds but no failure. Mitigation: README states minimum versions; cache mounts degrade gracefully.

- **Two compose files complicate command muscle memory** → "is `up` enough or do I need `-f dev`?" Mitigation: a single-line `make dev` / `make up` helper in README, or a `compose.sh` wrapper. Lightweight, optional.

## Migration Plan

The repo has no existing Docker-based deployment to migrate from, but it has a working local-host workflow that some contributors may already use.

**Cutover steps:**
1. Land container artifacts on a branch without touching existing local-host docs.
2. Verify laptop smoke test passes (`docker compose up` → register → chat → TTS).
3. Refactor frontend URLs and verify both Docker mode and `npm run dev` continue to work (Vite dev server can still hit `http://localhost:8080` via proxy or env override).
4. Update README to lead with Docker quickstart; keep "Local development without Docker" as a secondary section for power users.
5. Delete `backend-spring/docker-compose.yml` only after root compose passes smoke tests.

**Rollback:** revert the merge commit. The old local-host workflow is unchanged on disk (Maven, npm, Python venv all still work), so rollback is just removing the new files.

## Open Questions

- **Live2D model assets status** — verify `git ls-files frontend-vue/public/models/haru/` returns the model files. If empty (gitignored), task list must include either committing them or adding a download script. Resolution required before tasks.md.

- **Caddy site address format** — should we support a bare-IP fallback (e.g., `http://1.2.3.4`) when `DOMAIN` is empty but the user still wants HTTPS via a self-signed cert, or do we keep it simple (no DOMAIN → no Caddy → plain HTTP)? Currently leaning toward the latter.

- **`make` / wrapper script** — yes or no? Default to "no" unless one of the acceptance criteria fails because of compose command confusion.

- **Spring Boot actuator** — adding `spring-boot-starter-actuator` for healthcheck is a tiny pom change with security implications (Actuator endpoints are publicly accessible by default). Either configure `management.endpoints.web.exposure.include=health` (whitelist just `/health`) or use a `curl` healthcheck against an existing endpoint. Leaning toward Actuator with whitelist.
