## ADDED Requirements

### Requirement: Backend image SHALL be built from source via a multi-stage Dockerfile

The Spring Boot backend SHALL be packaged into a Docker image built locally from `backend-spring/Dockerfile`. The build SHALL use a multi-stage approach: a Maven stage compiles the application using a JDK 17 base image and a runtime stage runs the resulting JAR using a JRE 17 base image. The runtime image SHALL be minimal (Alpine or distroless), SHALL NOT contain Maven or build tooling, and SHALL expose port `8080`.

#### Scenario: Backend image builds successfully from a clean checkout
- **WHEN** a developer runs `docker compose build backend` on a fresh clone with no prior build cache
- **THEN** the build completes without error and produces a runnable image whose default command starts the Spring Boot application

#### Scenario: Backend image uses Maven dependency cache between rebuilds
- **WHEN** a developer rebuilds the backend image after changing only Java source code (no dependency changes)
- **THEN** the Maven download step is skipped (cache hit) and the rebuild completes faster than the initial build

### Requirement: AI-proxy image SHALL be built from `backend-python/Dockerfile`

The FastAPI AI proxy SHALL be packaged into a Docker image based on `python:3.11-slim`. The build SHALL install dependencies from `backend-python/requirements.txt`. The runtime container SHALL run `uvicorn main:app --host 0.0.0.0 --port 8000`. The image SHALL expose port `8000`.

#### Scenario: AI-proxy image starts and serves health endpoint
- **WHEN** the ai-proxy container is started with default configuration
- **THEN** GET `http://<container>:8000/health` returns HTTP 200 with body containing `"status":"ok"`

### Requirement: Frontend image SHALL be built from a multi-stage Dockerfile that serves static assets via nginx

The Vue frontend SHALL be packaged into a Docker image built locally from `frontend-vue/Dockerfile`. The build SHALL use a multi-stage approach: a Node 20 stage runs `npm ci && npm run build` and an `nginx:alpine` runtime stage serves the resulting `dist/` directory. The runtime image SHALL include the custom `nginx.conf` at `/etc/nginx/conf.d/default.conf` and SHALL expose port `80`.

#### Scenario: Frontend image builds and serves index.html
- **WHEN** the frontend container is started and a request is made to `http://<container>:80/`
- **THEN** the response is HTTP 200 with the Vue application's `index.html`

#### Scenario: Build-time Vite env vars are baked into the bundle
- **WHEN** the frontend image is built with `--build-arg VITE_API_BASE=/api --build-arg VITE_LOCAL_AI_URL=/ai`
- **THEN** the compiled JavaScript bundle contains `/api` and `/ai` as the resolved values for `import.meta.env.VITE_API_BASE` and `import.meta.env.VITE_LOCAL_AI_URL`

### Requirement: Ollama image SHALL auto-pull the configured model on first run

The Ollama service SHALL use a custom image built from `ollama/Dockerfile` that extends the official `ollama/ollama` image with an `entrypoint.sh` script. The entrypoint SHALL start the Ollama server in the background, wait for it to become responsive, check whether the model named by the `OLLAMA_MODEL` environment variable (default `qwen2.5:3b`) exists in the model store, pull it via `ollama pull` if absent, and then run the server in the foreground.

#### Scenario: First-run downloads model and persists it
- **WHEN** the Ollama container is started for the first time with `OLLAMA_MODEL=qwen2.5:3b` and an empty `ollama_models` named volume
- **THEN** the entrypoint runs `ollama pull qwen2.5:3b`, the download completes successfully, and the model is written to `/root/.ollama/models` (the volume mount target)

#### Scenario: Subsequent runs skip the pull
- **WHEN** the Ollama container is restarted after a previous run completed the model download
- **THEN** the entrypoint detects the model is already present and SHALL NOT initiate a new download

#### Scenario: Custom model override works
- **WHEN** the container is started with `OLLAMA_MODEL=gemma2:2b` instead of the default
- **THEN** the entrypoint pulls `gemma2:2b` (not the default) and the `ollama/api/tags` endpoint reflects only the requested model

### Requirement: All custom Dockerfiles SHALL accompany `.dockerignore` files

Each directory containing a Dockerfile (`backend-spring/`, `backend-python/`, `frontend-vue/`, repo root) SHALL include a `.dockerignore` file that excludes `node_modules`, `target`, `__pycache__`, `.git`, `.idea`, `.vscode`, `.env`, and other artifacts that would either bloat the build context or leak local state into the image.

#### Scenario: Build context excludes node_modules
- **WHEN** a developer runs `docker compose build frontend` with `frontend-vue/node_modules` populated locally
- **THEN** the build context sent to the Docker daemon SHALL NOT include `node_modules`, and the build proceeds using `npm ci` inside the image

### Requirement: Runtime images SHALL run as non-root users where the base image supports it

Backend, ai-proxy, and frontend runtime images SHALL drop to a non-root user in their final stage. Ollama is exempt because its official image expects root for model store access.

#### Scenario: Backend runs as non-root
- **WHEN** the backend container is running and `docker exec backend whoami` is executed
- **THEN** the output is NOT `root`

#### Scenario: Frontend runs as non-root
- **WHEN** the frontend container is running and `docker exec frontend whoami` is executed
- **THEN** the output is NOT `root` (nginx official image uses `nginx` user by default)
