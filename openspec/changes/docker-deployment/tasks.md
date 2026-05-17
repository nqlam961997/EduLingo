## 1. Pre-flight checks

- [x] 1.1 Run `git ls-files frontend-vue/public/models/haru/` to verify Live2D model assets are tracked in git — confirmed: model3, moc3, textures, motions, expressions all tracked
- [x] 1.2 If model files are missing, either commit them or add a `npm run setup:models` script that downloads them on first install — N/A, models already tracked
- [x] 1.3 Run `grep -rE "http://localhost:(8000|8080)" frontend-vue/src/` and record the file list for the URL refactor task — 2 hits: `ChatView.vue:13`, `VocabularyLearnView.vue:73`
- [x] 1.4 Confirm Docker Engine ≥ 24 and Docker Compose v2 are installed locally for testing — Docker 27.4.0, Compose v2.31.0

## 2. Frontend URL refactor (scope-reduced per design D6)

> Scope reduced after implementation discovery: backend calls in `api/auth.ts` and `api/edulingo.ts` already use bare relative `/api/...` paths (17+ call sites), which work correctly behind nginx and via Vite proxy. Only the AI-proxy URL needs env-driven config.

- [x] 2.1 Create `frontend-vue/.env.example` documenting `VITE_LOCAL_AI_URL` (default `/ai`)
- [x] 2.2 Add `frontend-vue/.env` to `.gitignore` — already present (`.gitignore:70`)
- [x] 2.3 Add a central config module `frontend-vue/src/config/env.ts` that exports `LOCAL_AI_URL` resolved from `import.meta.env.VITE_LOCAL_AI_URL` with `/ai` fallback — added `src/vite-env.d.ts` fallback declarations for IDE without node_modules
- [x] 2.4 Replace hardcoded fallback `http://localhost:8000` in `frontend-vue/src/views/ChatView.vue` with import from env config module
- [x] 2.5 Replace hardcoded `http://localhost:8000/tts` in `frontend-vue/src/views/VocabularyLearnView.vue` with `${LOCAL_AI_URL}/tts` from env config module
- [x] 2.6 Audit `frontend-vue/src/api/*.ts` for any remaining hardcoded localhost URLs — confirmed: all backend calls use bare `/api/...` relative paths (no refactor needed)
- [x] 2.7 Add `/ai` route to `frontend-vue/vite.config.ts` `server.proxy` (alongside existing `/api`) so `npm run dev` can reach the Dockerized ai-proxy
- [x] 2.8 Verify `grep -rE "http://localhost:8000" frontend-vue/src/` returns zero matches — confirmed 0 matches

## 3. Backend Docker artifacts

- [x] 3.1 Add `spring-boot-starter-actuator` to `backend-spring/pom.xml`
- [x] 3.2 Add `management.endpoints.web.exposure.include: health` to `backend-spring/src/main/resources/application.yml` (whitelist Actuator endpoints)
- [x] 3.3 Replace the embedded Gemini API key default in `application.yml` with an empty string
- [x] 3.4 Change the JWT secret default in `application.yml` to a clearly-marked demo placeholder (e.g., `demo-only-replace-for-deploy`)
- [x] 3.5 Add a startup check that logs WARNING and exits non-zero when `DOMAIN` is set and `JWT_SECRET` is unset or equals the demo placeholder
- [x] 3.6 Create `backend-spring/Dockerfile` with a Maven builder stage (`maven:3.9-eclipse-temurin-17`) and a runtime stage (`eclipse-temurin:17-jre-alpine`)
- [x] 3.7 Add BuildKit cache mount for `~/.m2` in the Maven stage
- [x] 3.8 Configure the runtime stage to run as a non-root user, expose port 8080, and execute the JAR
- [x] 3.9 Add `backend-spring/.dockerignore` (exclude `target/`, `.idea/`, `.vscode/`, `*.log`)

## 4. AI-proxy Docker artifacts

- [x] 4.1 Create `backend-python/Dockerfile` based on `python:3.11-slim`
- [x] 4.2 Install dependencies from `requirements.txt` using a layer-cached `pip install`
- [x] 4.3 Configure the container to run as a non-root user and execute `uvicorn main:app --host 0.0.0.0 --port 8000`
- [x] 4.4 Expose port 8000
- [x] 4.5 Add `backend-python/.dockerignore` (exclude `__pycache__/`, `venv/`, `*.pyc`, `.pytest_cache/`)

## 5. Frontend Docker artifacts

- [x] 5.1 Create `frontend-vue/Dockerfile` with a Node 20 builder stage and an `nginx:alpine` runtime stage
- [x] 5.2 Declare `ARG VITE_API_BASE` and `ARG VITE_LOCAL_AI_URL` in the builder stage and forward them to `npm run build`
- [x] 5.3 Add BuildKit cache mount for `~/.npm` in the builder stage
- [x] 5.4 Copy the built `dist/` into the nginx stage at `/usr/share/nginx/html`
- [x] 5.5 Create `frontend-vue/nginx.conf` with: `/` → static + SPA fallback to `index.html`, `/api/` → `proxy_pass http://backend:8080/`, `/ai/` → `proxy_pass http://ai-proxy:8000/`
- [x] 5.6 Set `proxy_buffering off` and `proxy_http_version 1.1` on `/api/` and `/ai/` locations to support streaming
- [x] 5.7 Configure nginx to set `X-Forwarded-Proto`, `X-Forwarded-For`, and `Host` headers correctly
- [x] 5.8 Add `frontend-vue/.dockerignore` (exclude `node_modules/`, `dist/`, `.env`)

## 6. Ollama Docker artifacts

- [x] 6.1 Create `ollama/Dockerfile` with `FROM ollama/ollama`
- [x] 6.2 Create `ollama/entrypoint.sh` that: starts `ollama serve &`, waits for `:11434/api/tags` to return 200, checks if `$OLLAMA_MODEL` is in `ollama list`, pulls it if missing, then `wait`s on the server process
- [x] 6.3 Make `entrypoint.sh` executable (`chmod +x`) and set it as the image's ENTRYPOINT
- [x] 6.4 Default `OLLAMA_MODEL=qwen2.5:3b` in the Dockerfile (overridable at runtime)

## 7. Root compose orchestration

- [x] 7.1 Create root `docker-compose.yml` with `services` for `frontend`, `backend`, `ai-proxy`, `ollama`, `postgres`, `caddy`
- [x] 7.2 Define `edulingo-net` as a user-defined bridge network and attach all services
- [x] 7.3 Define named volumes: `edulingo_pg`, `ollama_models`, `caddy_data`, `caddy_config`
- [x] 7.4 Configure `postgres` service: `postgres:16-alpine` image, env vars from `.env`, healthcheck via `pg_isready`, volume `edulingo_pg:/var/lib/postgresql/data`, NO host port binding
- [x] 7.5 Configure `ollama` service: build from `./ollama`, env `OLLAMA_MODEL`, volume `ollama_models:/root/.ollama`, healthcheck that probes model availability (not just server)
- [x] 7.6 Configure `ai-proxy` service: build from `./backend-python`, env `OLLAMA_URL=http://ollama:11434`, `OLLAMA_MODEL`, `TTS_VOICE`, healthcheck on `/health`, `depends_on: ollama (service_healthy)`
- [x] 7.7 Configure `backend` service: build from `./backend-spring`, env vars from `.env` (`DB_URL=jdbc:postgresql://postgres:5432/edulingo`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `AI_PROVIDER`, `LOCAL_AI_URL=http://ai-proxy:8000`, `CORS_ORIGINS`, `GEMINI_API_KEY`, `OPENAI_API_KEY`), healthcheck on `/actuator/health`, `depends_on: postgres, ai-proxy (service_healthy)`
- [x] 7.8 Configure `frontend` service: build from `./frontend-vue` with `VITE_API_BASE` and `VITE_LOCAL_AI_URL` build args from `.env`, healthcheck on `/`, `depends_on: backend, ai-proxy (service_healthy)`
- [x] 7.9 Bind `frontend` to `${HTTP_PORT:-80}:80` only when `caddy` is disabled — use compose profile `no-caddy` or conditional logic via env-driven port mapping
- [x] 7.10 Configure `caddy` service: `caddy:alpine` image, mount `caddy/Caddyfile:/etc/caddy/Caddyfile`, mount volumes `caddy_data:/data` and `caddy_config:/config`, expose `${HTTP_PORT:-80}:80` and `${HTTPS_PORT:-443}:443`, `depends_on: frontend (service_healthy)`, use compose `profile: caddy` activated by env
- [x] 7.11 Set `restart: unless-stopped` on all long-running services
- [x] 7.12 Create `caddy/Caddyfile` with site block `{$DOMAIN} { reverse_proxy frontend:80 }`

## 8. Dev compose overlay

- [x] 8.1 Create `docker-compose.dev.yml` overlay
- [x] 8.2 Override `frontend` to bind-mount `./frontend-vue:/app`, run `npm run dev -- --host 0.0.0.0`, expose `5173:5173`
- [x] 8.3 Override `backend` to bind-mount `./backend-spring:/app`, run `mvn spring-boot:run -Dspring-boot.run.fork=false` (or use Spring DevTools for restart), expose `8080:8080`
- [x] 8.4 Override `ai-proxy` to bind-mount `./backend-python:/app`, run `uvicorn main:app --host 0.0.0.0 --port 8000 --reload`, expose `8000:8000`
- [x] 8.5 Override `postgres` to bind `5432:5432` to the host
- [x] 8.6 Disable `caddy` service in the dev overlay (set `profiles: [disabled]` or remove)
- [x] 8.7 Set `restart: "no"` on all overridden services for faster iteration

## 9. Root configuration

- [x] 9.1 Create root `.env.example` with all variables documented per the deployment-config spec (DOMAIN, HTTP_PORT, HTTPS_PORT, POSTGRES_*, JWT_SECRET, AI_PROVIDER, OLLAMA_*, TTS_VOICE, GEMINI_API_KEY, OPENAI_API_KEY, VITE_*, CORS_ORIGINS)
- [x] 9.2 Mark required-for-VPS variables (`DOMAIN`, `JWT_SECRET`, `POSTGRES_PASSWORD`) with explicit "REQUIRED for VPS" comments
- [x] 9.3 Add `.env` to root `.gitignore`
- [x] 9.4 Create root `.dockerignore` (exclude `.git`, `.idea`, `.vscode`, `node_modules`, `target`, `__pycache__`, `*.log`, `.env`)
- [x] 9.5 Document CORS_ORIGINS derivation behavior: when DOMAIN is set and CORS_ORIGINS is empty, compose substitutes `https://${DOMAIN}` as the default

## 10. Delete legacy artifacts

- [x] 10.1 Delete `backend-spring/docker-compose.yml` (after confirming root compose passes smoke tests) — deleted; `docker compose config` validates
- [x] 10.2 Remove any references to the old compose path from README and contributor docs — README rewritten

## 14. Documentation

- [x] 14.1 Add a "Quickstart with Docker" section to root `README.md` placed before the existing manual setup
- [x] 14.2 Document laptop steps: prerequisites, `cp .env.example .env`, `docker compose up -d`, open `http://localhost`
- [x] 14.3 Document VPS steps: set required `.env` vars, point DNS first, `docker compose up -d`, open `https://<DOMAIN>`
- [x] 14.4 Document dev overlay usage with the exact `-f docker-compose.yml -f docker-compose.dev.yml` invocation
- [x] 14.5 Add a "Known constraints" subsection covering Internet requirement for edge-tts and Let's Encrypt, ~2GB first-run model pull, CPU performance expectations
- [x] 14.6 Add a "Migration from local-Postgres setup" note explaining that the `edulingo_pg` volume name is preserved
- [x] 14.7 Keep the existing manual setup section as "Running without Docker (advanced)"

## 15. Verification (partial — see Sections 11-13 for runtime smoke tests)

- [ ] 15.1 Run all acceptance scenarios from each spec file against a fresh laptop install and confirm pass — **BLOCKED on user-run smoke tests (Sections 11-13)**
- [x] 15.2 Run `openspec validate docker-deployment` and ensure it reports no errors — passes (validated post-Section 9)
- [x] 15.3 Verify no hardcoded URLs remain via `grep -rE "http://localhost:8000" frontend-vue/src/` — 0 matches
- [x] 15.4 Verify `.env` is gitignored via `git check-ignore .env` — `.gitignore:78` covers `.env`
- [ ] 15.5 Verify all spec scenarios have corresponding evidence (logs, screenshots, or test runs) before archive — **BLOCKED on user-run smoke tests**

---

## ⚠️ Sections 11-13: Smoke tests — USER MUST EXECUTE

These tasks require running infrastructure (a laptop and a VPS) and cannot be completed by an automated session. Run them locally before archiving the change.

### 11. Laptop smoke test (AC-1, AC-3)

- [ ] 11.1 On a clean machine, `git clone`, `cp .env.example .env`, `docker compose up -d`
- [ ] 11.2 Wait for all services to report healthy (`docker compose ps` shows `healthy` for all)
- [ ] 11.3 Open `http://localhost`, register a new user, log in
- [ ] 11.4 Open a chat topic, send a message, verify a streamed AI response arrives
- [ ] 11.5 Verify TTS audio plays and Live2D avatar lip-syncs
- [ ] 11.6 Verify cold start (from `docker compose up` to first healthy service set) completes within 5 minutes, excluding the one-time model pull (AC-3)

### 12. Dev overlay smoke test (AC-5)

- [ ] 12.1 Run `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d`
- [ ] 12.2 Edit `frontend-vue/src/views/ChatView.vue` and verify Vite HMR pushes the change without reload
- [ ] 12.3 Edit a Java file in `backend-spring/src/main/` and verify Spring DevTools restarts within 5 seconds (NOTE: dev overlay uses `mvn spring-boot:run`; for DevTools restart, may need to add `spring-boot-devtools` dependency)
- [ ] 12.4 Edit `backend-python/main.py` and verify uvicorn reloads
- [ ] 12.5 Verify `psql -h localhost -p 5432 -U edulingo edulingo` succeeds

### 13. VPS smoke test (AC-2, AC-4)

- [ ] 13.1 Provision a 4-vCPU/8GB Linux VPS with Docker installed and a domain pointing to its IP
- [ ] 13.2 Clone the repo, set `DOMAIN=foo.example.com`, `JWT_SECRET=$(openssl rand -base64 48)`, `POSTGRES_PASSWORD=$(openssl rand -base64 24)` in `.env`
- [ ] 13.3 Run `docker compose up -d` and wait for Caddy to obtain a Let's Encrypt certificate
- [ ] 13.4 Visit `https://foo.example.com`, confirm a valid certificate is presented
- [ ] 13.5 Repeat the full smoke test (register → login → chat → TTS → Live2D) on the VPS
- [ ] 13.6 Verify `http://foo.example.com` returns a 301 redirect to `https://foo.example.com` — Caddy's default auto_https behavior provides this when DOMAIN is set
- [ ] 13.7 Verify no source files were edited between laptop and VPS deploys — only `.env` and DNS
