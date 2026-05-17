## ADDED Requirements

### Requirement: Frontend code SHALL NOT contain hardcoded AI-proxy URLs

All references in `frontend-vue/src/` to `http://localhost:8000` (the FastAPI AI proxy) SHALL be replaced with reads from a shared environment-driven configuration module. After the change, `grep -rE "http://localhost:8000" frontend-vue/src/` SHALL return zero matches.

Backend calls (Spring Boot) are out of scope: the existing codebase already uses bare relative paths (`/api/...`) in `frontend-vue/src/api/auth.ts` and `frontend-vue/src/api/edulingo.ts`, which work correctly both behind the production nginx (single-origin routing) and in dev mode (via Vite's `server.proxy` configuration). No refactor of backend call sites is required.

#### Scenario: No hardcoded AI-proxy URLs in source
- **WHEN** a maintainer searches the Vue source tree for hardcoded AI-proxy URLs
- **THEN** zero results are returned in `frontend-vue/src/**/*.ts` and `frontend-vue/src/**/*.vue`

#### Scenario: Backend relative paths remain untouched
- **WHEN** a maintainer inspects `frontend-vue/src/api/auth.ts` and `frontend-vue/src/api/edulingo.ts`
- **THEN** `fetch('/api/...')` calls remain as bare relative paths (no `VITE_API_BASE` prefix), matching the pre-existing convention

### Requirement: Frontend SHALL resolve the AI-proxy base URL from `import.meta.env.VITE_LOCAL_AI_URL`

A single shared configuration module (`frontend-vue/src/config/env.ts`) SHALL export `LOCAL_AI_URL` resolved from `import.meta.env.VITE_LOCAL_AI_URL`, defaulting to `/ai` when the variable is unset at build time. All AI-proxy calls (TTS, streaming generation, vocabulary speech) SHALL import this constant rather than reading the env var directly or hardcoding a URL.

#### Scenario: Default value applies when env var is unset
- **WHEN** the frontend is built with no `VITE_LOCAL_AI_URL` set
- **THEN** at runtime, AI-proxy calls resolve to relative paths under `/ai/...` (e.g., `/ai/tts`, `/ai/stream`)

#### Scenario: Absolute URL override works for split-origin deployments
- **WHEN** the frontend is built with `VITE_LOCAL_AI_URL=https://ai.example.com`
- **THEN** at runtime, AI-proxy calls resolve to that absolute URL (used for advanced deployments)

### Requirement: The same compiled frontend bundle SHALL work on laptop and VPS without rebuild

When the frontend image is built with the default `VITE_LOCAL_AI_URL=/ai`, the resulting bundle SHALL function correctly both when served from `http://localhost` (laptop, no Caddy) and `https://<DOMAIN>` (VPS, with Caddy) without requiring a separate build for each target. Backend `/api/...` calls are already same-origin relative; AI calls are made same-origin relative by the new env contract.

#### Scenario: Same image serves both laptop and VPS
- **WHEN** the same frontend image (built once with default env vars) is deployed to a laptop and to a VPS with `DOMAIN` set
- **THEN** both deployments serve a functional UI that successfully calls `/api/...` and `/ai/...` endpoints on their respective origins

### Requirement: `frontend-vue/Dockerfile` SHALL accept `VITE_LOCAL_AI_URL` as a build arg

The frontend Dockerfile SHALL declare `ARG VITE_LOCAL_AI_URL` in the build stage and SHALL forward it to the `vite build` invocation (via the build process's environment) so that the value is baked into the bundle. The root `docker-compose.yml` SHALL pass this arg from `.env` via the `build.args` mapping.

#### Scenario: Build arg overrides default
- **WHEN** a developer runs `VITE_LOCAL_AI_URL=/custom-ai docker compose build frontend`
- **THEN** the resulting image's bundle contains `/custom-ai` as the AI proxy base instead of the default `/ai`

### Requirement: The Vite dev server SHALL proxy both `/api` and `/ai` paths

`frontend-vue/vite.config.ts` SHALL configure `server.proxy` to route `/api` to the Spring backend and `/ai` to the FastAPI AI proxy. This lets `npm run dev` (running outside Docker) reach a Dockerized backend stack without any additional configuration.

#### Scenario: Vite dev proxy forwards backend calls
- **WHEN** a developer runs `npm run dev` against a running Docker stack and the browser sends `GET http://localhost:5173/api/topics`
- **THEN** Vite forwards the request to the Spring backend and returns the response

#### Scenario: Vite dev proxy forwards AI calls
- **WHEN** a developer runs `npm run dev` against a running Docker stack and the browser sends `POST http://localhost:5173/ai/tts`
- **THEN** Vite forwards the request to the FastAPI ai-proxy and returns the audio response

### Requirement: A `frontend-vue/.env.example` SHALL document the available Vite variables

A committed `frontend-vue/.env.example` file SHALL list `VITE_LOCAL_AI_URL` with its default and a brief comment explaining when to override it. Developers running Vite outside Docker SHALL copy this file to `.env` if they need to override defaults.

#### Scenario: Local `npm run dev` works against running Docker stack
- **WHEN** a developer runs `npm run dev` with default env values and the Docker stack is running on `http://localhost`
- **THEN** the Vite dev server at `http://localhost:5173` successfully calls the Dockerized backend and ai-proxy through Vite's `server.proxy`
