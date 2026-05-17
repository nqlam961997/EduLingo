## ADDED Requirements

### Requirement: Frontend nginx SHALL path-route requests to backend, ai-proxy, and static assets

The `frontend` container SHALL run nginx with a configuration that defines a single server block listening on port `80`. The server SHALL route requests as follows:
- `location /api/` SHALL `proxy_pass http://backend:8080` (no trailing slash — forwards the `/api/...` path UNCHANGED, because Spring controllers are already mapped under `/api/...` via `@RequestMapping("/api/...")`).
- `location /ai/` SHALL `proxy_pass http://ai-proxy:8000/` (trailing slash — strips the `/ai` prefix when forwarding, because the FastAPI app uses bare paths like `/health`, `/tts`, `/stream`).
- `location /` SHALL serve static files from `/usr/share/nginx/html` with `try_files $uri $uri/ /index.html` to support Vue Router's HTML5 history mode.

The configuration SHALL set `proxy_buffering off` on the `/api/` and `/ai/` locations to support Server-Sent Events / streaming responses from `/api/.../stream` and `/ai/stream`.

#### Scenario: Browser hits backend through nginx
- **WHEN** the browser sends `POST http://localhost/api/auth/login`
- **THEN** nginx forwards the request to `http://backend:8080/api/auth/login` (prefix preserved) and returns the backend's response unchanged

#### Scenario: Browser hits FastAPI through nginx
- **WHEN** the browser sends `POST http://localhost/ai/tts`
- **THEN** nginx forwards the request to `http://ai-proxy:8000/tts` and streams the binary audio response back

#### Scenario: Streaming responses are not buffered
- **WHEN** the browser opens a streaming connection to `http://localhost/ai/stream`
- **THEN** tokens arrive incrementally at the browser as they are generated, without nginx-induced buffering delays

#### Scenario: Vue Router deep links are served as index.html
- **WHEN** the browser navigates directly to `http://localhost/dashboard/history`
- **THEN** nginx returns `index.html` (HTTP 200) so that the Vue Router can resolve the route client-side

### Requirement: Caddy SHALL act as the public entrypoint, with TLS activated only when `DOMAIN` is set

The repository SHALL include `caddy/Caddyfile` defining a single site block whose site address is `${SITE_ADDRESS}`, substituted by the root compose from `${DOMAIN:-:80}`. The Caddy service SHALL be present in the base compose file and SHALL be the only public-facing service (binds `${HTTP_PORT:-80}:80` and `${HTTPS_PORT:-443}:443`). The `frontend` service SHALL NOT bind to any host port; it is reachable only on the internal `edulingo-net` network.

When `DOMAIN` is set (e.g., `foo.example.com`), the `${SITE_ADDRESS}` substitution resolves to that domain and Caddy auto-provisions a Let's Encrypt certificate on first request. When `DOMAIN` is empty, `${SITE_ADDRESS}` resolves to `:80` and Caddy serves plain HTTP on every interface without requesting any certificate.

This unified design — Caddy always running, TLS conditional on `DOMAIN` — keeps the compose graph identical on laptop and VPS, avoiding the port-conflict workarounds required by a "Caddy only runs when activated" model. The ~50 MB Caddy image cost is accepted.

#### Scenario: VPS with DOMAIN set gets HTTPS via Let's Encrypt
- **WHEN** a user sets `DOMAIN=foo.example.com` in `.env`, points the DNS A record at the VPS IP, and runs `docker compose up -d`
- **THEN** Caddy obtains a valid Let's Encrypt certificate within five minutes and `https://foo.example.com` serves the application

#### Scenario: Laptop with DOMAIN unset uses plain HTTP
- **WHEN** a user leaves `DOMAIN` empty in `.env` and runs `docker compose up -d`
- **THEN** Caddy serves plain HTTP on `:80` (no cert request); `http://localhost` serves the application; `frontend` remains accessible only on the internal network

#### Scenario: Caddy certificate state survives restarts
- **WHEN** Caddy has previously obtained a certificate and the stack is restarted
- **THEN** Caddy reuses the existing certificate from the `caddy_data` named volume and does NOT re-issue against Let's Encrypt

#### Scenario: Frontend container is not publicly reachable
- **WHEN** the stack is running and a user attempts to reach the frontend container directly on the host
- **THEN** there is no host port mapping for the `frontend` service; only Caddy is reachable from outside the Docker network

### Requirement: HTTP traffic SHALL be redirected to HTTPS when Caddy is active

When the Caddy service is active (`DOMAIN` is set), incoming HTTP traffic on port 80 SHALL be redirected to HTTPS on port 443 via a 301 redirect. Caddy's default behavior provides this redirect automatically when an explicit site address (with no `http://` prefix) is configured.

#### Scenario: HTTP request is redirected to HTTPS
- **WHEN** a browser visits `http://foo.example.com/login` on a VPS with `DOMAIN` set
- **THEN** the response is HTTP 301 with `Location: https://foo.example.com/login`

### Requirement: Streaming and WebSocket-compatible headers SHALL be preserved end-to-end

Both the frontend nginx and the optional Caddy reverse proxy SHALL preserve `Connection`, `Upgrade`, `X-Forwarded-Proto`, `X-Forwarded-For`, and `Host` headers so that the backend correctly identifies the original request scheme and client IP. Streaming endpoints SHALL function identically whether accessed via direct nginx (`http://localhost`) or fronted by Caddy (`https://<DOMAIN>`).

#### Scenario: Backend sees correct scheme on VPS
- **WHEN** a request enters via `https://foo.example.com/api/auth/me` on a VPS with Caddy active
- **THEN** Spring's `request.getScheme()` reports `https` (via `X-Forwarded-Proto`), which prevents mixed-content issues when the backend constructs absolute URLs

#### Scenario: Streaming chat works through both proxies
- **WHEN** a streaming chat request flows browser → caddy → frontend nginx → ai-proxy
- **THEN** tokens are delivered incrementally to the browser with no buffering delay greater than 200ms per token
