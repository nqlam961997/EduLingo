## ADDED Requirements

### Requirement: Root `docker-compose.yml` SHALL orchestrate all services as a single stack

The repository root SHALL contain a `docker-compose.yml` defining services `frontend`, `backend`, `ai-proxy`, `ollama`, `postgres`, and (optionally activated) `caddy`. All services SHALL be attached to a single user-defined bridge network named `edulingo-net`. Service-to-service communication SHALL use service names as DNS hostnames (e.g., `backend:8080`, `ai-proxy:8000`, `postgres:5432`, `ollama:11434`).

#### Scenario: Single command brings the stack up
- **WHEN** a developer runs `docker compose up -d` on a fresh clone with a valid `.env`
- **THEN** all five required services (frontend, backend, ai-proxy, ollama, postgres) start and eventually report a healthy status

#### Scenario: Service DNS resolution works inside the network
- **WHEN** a developer runs `docker compose exec backend curl -s http://ai-proxy:8000/health`
- **THEN** the response is HTTP 200, demonstrating intra-network DNS resolution by service name

### Requirement: Startup ordering SHALL be enforced via healthcheck-gated `depends_on`

Each service SHALL declare a Docker healthcheck. Dependencies SHALL use `depends_on: <service>: condition: service_healthy` so that a dependent service does not start until its dependencies are healthy. Specifically: `backend` depends on `postgres`; `ai-proxy` depends on `ollama`; `frontend` depends on `backend` and `ai-proxy`; `caddy` (when enabled) depends on `frontend`.

#### Scenario: Backend waits for Postgres
- **WHEN** the stack is started
- **THEN** the backend container SHALL NOT begin its startup process until the postgres healthcheck reports healthy

#### Scenario: AI-proxy waits for Ollama including model availability
- **WHEN** the stack is started on a fresh machine with no pre-pulled model
- **THEN** the ai-proxy container SHALL NOT report healthy until the ollama healthcheck (which includes model availability) reports healthy

### Requirement: Named volumes SHALL persist mutable state

The compose file SHALL declare named volumes for Postgres data (`edulingo_pg`) and Ollama model store (`ollama_models`). When the optional Caddy service is enabled, it SHALL also use a named volume (`caddy_data`) for Let's Encrypt certificate storage.

#### Scenario: Database survives container recreation
- **WHEN** a developer runs `docker compose down` followed by `docker compose up -d` (without `--volumes`)
- **THEN** previously registered user accounts and chat history are still queryable after the restart

#### Scenario: Pulled Ollama model survives container recreation
- **WHEN** a developer runs `docker compose down && docker compose up -d` after the model has been pulled
- **THEN** the ollama container does NOT re-download the model

#### Scenario: Postgres volume name is preserved from prior local-Postgres setup
- **WHEN** a user previously ran `backend-spring/docker-compose.yml` and accumulated data in the `edulingo_pg` volume
- **THEN** the new root-level compose continues to use the same volume name (`edulingo_pg`) and the existing data is accessible to the new backend container

### Requirement: Base compose SHALL NOT expose internal service ports to the host

In the base `docker-compose.yml`, only the public-facing service SHALL bind to a host port: `frontend` binds `${HTTP_PORT:-80}:80` when Caddy is disabled, and `caddy` binds `${HTTP_PORT:-80}:80` and `${HTTPS_PORT:-443}:443` when enabled. The `backend`, `ai-proxy`, `postgres`, and `ollama` services SHALL NOT bind to any host port in the base file.

#### Scenario: Postgres is not reachable from the host in base mode
- **WHEN** the stack is started with `docker compose up -d` (no overlay)
- **THEN** `psql -h localhost -p 5432` from the host fails to connect

#### Scenario: Backend is not reachable from the host in base mode
- **WHEN** the stack is started with `docker compose up -d` (no overlay)
- **THEN** `curl http://localhost:8080/actuator/health` from the host fails to connect

### Requirement: A `docker-compose.dev.yml` overlay SHALL provide developer conveniences

The repository SHALL contain a `docker-compose.dev.yml` file that, when composed via `-f docker-compose.yml -f docker-compose.dev.yml`, overrides the base configuration to (a) bind-mount source code into each runtime container, (b) replace the default command with the dev-server command for that service (Vite for frontend, Spring DevTools restart for backend, uvicorn with `--reload` for ai-proxy), (c) bind Postgres `:5432` to the host, and (d) disable the Caddy service if present.

#### Scenario: Editing a Vue file is reflected without rebuild
- **WHEN** the stack is running under the dev overlay and a developer edits `frontend-vue/src/views/ChatView.vue`
- **THEN** Vite HMR pushes the change to the browser without a full page reload and without rebuilding the frontend image

#### Scenario: Editing a Java file restarts the Spring application
- **WHEN** the stack is running under the dev overlay and a developer edits a `.java` file under `backend-spring/src/`
- **THEN** Spring DevTools restarts the backend within seconds without rebuilding the backend image

#### Scenario: Editing main.py reloads the FastAPI worker
- **WHEN** the stack is running under the dev overlay and a developer edits `backend-python/main.py`
- **THEN** uvicorn reloads the application without restarting the container

#### Scenario: Postgres is reachable from host in dev mode
- **WHEN** the stack is started with `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d`
- **THEN** `psql -h localhost -p 5432 -U edulingo edulingo` succeeds

### Requirement: All services SHALL set `restart: unless-stopped` in base compose

To recover from host reboots and transient failures, every long-running service in the base `docker-compose.yml` SHALL declare `restart: unless-stopped`. The dev overlay MAY override this with `restart: "no"` for faster iteration.

#### Scenario: Stack recovers after Docker daemon restart
- **WHEN** the stack is running on a VPS, the Docker daemon is restarted (e.g., `systemctl restart docker`)
- **THEN** all stack services are restarted automatically and reach healthy state without manual intervention
