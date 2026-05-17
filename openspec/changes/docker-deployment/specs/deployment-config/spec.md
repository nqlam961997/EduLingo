## ADDED Requirements

### Requirement: A committed `.env.example` SHALL document every environment variable consumed by the stack

The repository root SHALL contain a `.env.example` file that enumerates every environment variable read by any service in the compose stack, including: `DOMAIN`, `HTTP_PORT`, `HTTPS_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION`, `AI_PROVIDER`, `LOCAL_AI_URL`, `OLLAMA_URL`, `OLLAMA_MODEL`, `TTS_VOICE`, `GEMINI_API_KEY`, `OPENAI_API_KEY`, `VITE_API_BASE`, `VITE_LOCAL_AI_URL`, and `CORS_ORIGINS`. Each variable SHALL have a comment indicating whether it is required, its default, and any special considerations for laptop versus VPS deployment.

#### Scenario: New contributor can launch the stack from `.env.example` alone
- **WHEN** a new contributor runs `cp .env.example .env && docker compose up -d` without further edits
- **THEN** the stack starts successfully and the application is reachable at `http://localhost`

### Requirement: The real `.env` file SHALL be gitignored

The repository's `.gitignore` SHALL include `.env` (root) and `frontend-vue/.env` to prevent secrets from being committed.

#### Scenario: `.env` is not tracked by git
- **WHEN** a developer creates a root `.env` file with secrets and runs `git status`
- **THEN** `.env` is not listed as an untracked file (it is matched by `.gitignore`)

### Requirement: `JWT_SECRET` SHALL be required (no insecure default applies) for VPS deployments

When the `DOMAIN` environment variable is set (signaling a VPS deployment), the backend SHALL refuse to start if `JWT_SECRET` is unset or equal to the demo default value. The compose file SHALL fail-fast with a clear error message in this case rather than starting an insecure backend.

#### Scenario: VPS deploy without JWT_SECRET fails fast
- **WHEN** a user sets `DOMAIN=foo.example.com` in `.env`, leaves `JWT_SECRET` empty, and runs `docker compose up`
- **THEN** the backend container exits with a non-zero status and logs a clear error such as `JWT_SECRET must be set when DOMAIN is configured`

#### Scenario: Laptop deploy works with default JWT secret
- **WHEN** a user leaves `DOMAIN` empty in `.env` and leaves `JWT_SECRET` empty (relying on the application.yml demo default)
- **THEN** the backend starts normally for local-only use

### Requirement: `CORS_ORIGINS` SHALL be derived from `DOMAIN` when both are present

If `DOMAIN` is set and `CORS_ORIGINS` is not explicitly overridden in `.env`, the backend SHALL receive `CORS_ORIGINS=https://${DOMAIN}` so that browser requests from the public site are accepted. Explicit user overrides of `CORS_ORIGINS` (when set in `.env`) SHALL take precedence.

#### Scenario: VPS CORS allows the configured domain
- **WHEN** `DOMAIN=foo.example.com` is set and `CORS_ORIGINS` is left at its default
- **THEN** the backend accepts CORS preflight requests from `Origin: https://foo.example.com`

#### Scenario: Explicit CORS override takes precedence
- **WHEN** `DOMAIN=foo.example.com` is set and `CORS_ORIGINS=https://foo.example.com,https://staging.foo.example.com` is also set
- **THEN** the backend accepts requests from both domains and ignores the auto-derived value

### Requirement: All Spring application properties SHALL be overridable via environment variables

The backend's `application.yml` SHALL continue to use `${ENV_VAR:default}` syntax for every value that may differ between laptop and VPS — including database connection, AI provider configuration, JWT settings, and CORS origins. The compose file SHALL pass through these variables from `.env`. No source code change SHALL be required to switch between AI providers, change the database password, or modify CORS settings.

#### Scenario: AI provider can be switched via env
- **WHEN** a user sets `AI_PROVIDER=gemini` and `GEMINI_API_KEY=...` in `.env` and runs `docker compose up -d`
- **THEN** the backend uses Gemini for chat completions without any source code change

### Requirement: Sensitive defaults in source SHALL be replaced with neutral placeholders

The hardcoded Gemini API key currently embedded in `backend-spring/src/main/resources/application.yml` (`GEMINI_API_KEY` default) SHALL be replaced with an empty default, and the JWT secret default SHALL be marked as `demo-only-replace-for-deploy`. The compose stack and `.env.example` SHALL document how to set production values.

#### Scenario: No leaked Gemini key in source
- **WHEN** a maintainer searches `backend-spring/src/main/resources/application.yml` for the prior Gemini key value
- **THEN** zero matches are found

#### Scenario: Backend log warns when running with the demo JWT secret in production-like context
- **WHEN** the backend starts with the default `demo-only-replace-for-deploy` JWT secret and `DOMAIN` is set
- **THEN** the backend logs a clear WARNING line and refuses to serve (per the JWT_SECRET requirement above)

### Requirement: The README SHALL document a Docker-first quickstart for laptop and VPS

The root `README.md` SHALL contain a "Quickstart with Docker" section preceding the existing manual setup instructions. The section SHALL include: (a) prerequisites (Docker Engine ≥ 24, Compose v2), (b) laptop steps (`cp .env.example .env && docker compose up -d`, open `http://localhost`), (c) VPS steps (set `DOMAIN`, `JWT_SECRET`, `POSTGRES_PASSWORD`; point DNS; `docker compose up -d`), (d) dev overlay usage, (e) a "Known constraints" subsection (Internet required for edge-tts and Let's Encrypt; first-run model pull ~2GB).

#### Scenario: README quickstart is sufficient to reach a working laptop deploy
- **WHEN** a reader follows the laptop quickstart section verbatim on a fresh Docker-enabled machine
- **THEN** the reader reaches a working application at `http://localhost` without consulting any other documentation

#### Scenario: README quickstart documents VPS prerequisites including DNS-first ordering
- **WHEN** a reader follows the VPS quickstart section
- **THEN** the section explicitly instructs them to configure DNS before running `docker compose up` so that Caddy's ACME HTTP-01 challenge can succeed
