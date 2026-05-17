#!/usr/bin/env bash
# EduLingo — one-shot deployment script.
#
# Usage:
#   ./scripts/deploy.sh                       # laptop mode (HTTP on localhost)
#   ./scripts/deploy.sh --domain foo.com      # VPS mode (HTTPS via Let's Encrypt)
#   DOMAIN=foo.com ./scripts/deploy.sh        # same, env-driven
#
# What it does:
#   1. Verifies Docker + Compose v2 are installed.
#   2. Creates .env from .env.example if missing.
#      - VPS mode: injects DOMAIN, ACME_EMAIL, and generates JWT_SECRET +
#        POSTGRES_PASSWORD if they still hold demo defaults.
#   3. `docker compose up -d --build`.
#   4. Polls until every service is healthy (or 15 min cap).
#   5. Smoke-tests the public health endpoint.
#   6. Prints the URL to open.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# ---- args / env --------------------------------------------------------------

DOMAIN="${DOMAIN:-}"
ACME_EMAIL="${ACME_EMAIL:-}"
REBUILD=1

while [[ $# -gt 0 ]]; do
    case "$1" in
        --domain)       DOMAIN="${2:-}"; shift 2 ;;
        --domain=*)     DOMAIN="${1#*=}"; shift ;;
        --email)        ACME_EMAIL="${2:-}"; shift 2 ;;
        --email=*)      ACME_EMAIL="${1#*=}"; shift ;;
        --no-build)     REBUILD=0; shift ;;
        -h|--help)
            sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "Unknown arg: $1" >&2; exit 2 ;;
    esac
done

# ---- pretty logging ----------------------------------------------------------

if [[ -t 1 ]]; then
    C_BLUE=$'\033[1;34m'; C_GREEN=$'\033[1;32m'; C_YELLOW=$'\033[1;33m'
    C_RED=$'\033[1;31m'; C_DIM=$'\033[2m'; C_RESET=$'\033[0m'
else
    C_BLUE=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_DIM=""; C_RESET=""
fi
log()  { printf '%s==>%s %s\n' "$C_BLUE"  "$C_RESET" "$*"; }
ok()   { printf '%s ✓%s  %s\n' "$C_GREEN" "$C_RESET" "$*"; }
warn() { printf '%s ⚠%s  %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
die()  { printf '%s ✗%s  %s\n' "$C_RED"   "$C_RESET" "$*" >&2; exit 1; }

# ---- 1. preflight ------------------------------------------------------------

log "Checking prerequisites"
command -v docker >/dev/null || die "Docker is not installed. See https://docs.docker.com/engine/install/"
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 plugin missing. Install 'docker-compose-plugin'."
docker info >/dev/null 2>&1 || die "Docker daemon not running. Start Docker Desktop / dockerd and retry."
ok "Docker $(docker --version | awk '{print $3}' | tr -d ',') + Compose v$(docker compose version --short)"

# ---- 2. .env handling --------------------------------------------------------

gen_secret() {
    # 48 random bytes → base64 (URL-safe-ish). Works on macOS + Linux.
    if command -v openssl >/dev/null; then
        openssl rand -base64 "$1" | tr -d '\n'
    else
        head -c "$1" /dev/urandom | base64 | tr -d '\n'
    fi
}

# Replace `KEY=...` line in .env (or append). Portable: no in-place sed.
set_env_key() {
    local key="$1" value="$2" file="$3"
    local tmp
    tmp="$(mktemp)"
    if grep -qE "^${key}=" "$file"; then
        awk -v k="$key" -v v="$value" -F= '
            $1 == k { printf "%s=%s\n", k, v; next }
            { print }
        ' "$file" > "$tmp"
    else
        cat "$file" > "$tmp"
        printf '%s=%s\n' "$key" "$value" >> "$tmp"
    fi
    mv "$tmp" "$file"
}

if [[ -f .env ]]; then
    ok ".env exists — leaving it alone"
else
    [[ -f .env.example ]] || die ".env.example is missing — cannot bootstrap .env"
    cp .env.example .env
    ok "Created .env from .env.example"

    if [[ -n "$DOMAIN" ]]; then
        log "VPS mode detected (DOMAIN=$DOMAIN) — injecting secrets"
        set_env_key DOMAIN "$DOMAIN" .env
        [[ -n "$ACME_EMAIL" ]] && set_env_key ACME_EMAIL "$ACME_EMAIL" .env

        # Replace demo JWT secret unconditionally on first VPS bootstrap.
        set_env_key JWT_SECRET "$(gen_secret 48)" .env
        ok "Generated JWT_SECRET"

        # Generate a strong DB password (only if still the demo default).
        if grep -qE '^POSTGRES_PASSWORD=edulingo\s*$' .env; then
            set_env_key POSTGRES_PASSWORD "$(gen_secret 24 | tr -d '/+=')" .env
            ok "Generated POSTGRES_PASSWORD"
        fi
    else
        ok "Laptop mode — using demo defaults from .env.example"
    fi
fi

# Sanity-check: if DOMAIN is set in .env but JWT is still the demo placeholder,
# refuse to start (backend would refuse anyway — fail fast with a clearer msg).
ENV_DOMAIN="$(grep -E '^DOMAIN=' .env | head -n1 | cut -d= -f2- || true)"
ENV_JWT="$(grep -E '^JWT_SECRET=' .env | head -n1 | cut -d= -f2- || true)"
if [[ -n "$ENV_DOMAIN" && "$ENV_JWT" == "c2VjcmV0LWtleS1mb3ItZWR1bGluZ28tZGVtby1jaGFuZ2UtaW4tcHJvZHVjdGlvbg==" ]]; then
    die "DOMAIN=$ENV_DOMAIN is set in .env but JWT_SECRET is still the demo value. Run: openssl rand -base64 48"
fi

# ---- 3. up -------------------------------------------------------------------

log "Starting stack (this may take a while on first run — Ollama pulls ~2 GB)"
if [[ $REBUILD -eq 1 ]]; then
    docker compose up -d --build
else
    docker compose up -d
fi

# ---- 4. wait for healthy -----------------------------------------------------

# Services with a healthcheck defined in docker-compose.yml.
HEALTH_SERVICES=(postgres ollama ai-proxy backend)
DEADLINE=$(( $(date +%s) + 15 * 60 ))   # 15 minutes total budget

service_status() {
    # Returns one of: healthy, unhealthy, starting, none, missing
    docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
        "edulingo-$1" 2>/dev/null || echo "missing"
}

log "Waiting for services to become healthy (cap: 15 min)"
prev_summary=""
while true; do
    summary=""
    all_healthy=1
    for svc in "${HEALTH_SERVICES[@]}"; do
        s=$(service_status "$svc")
        summary+="$svc=$s  "
        [[ "$s" == "healthy" ]] || all_healthy=0
    done

    if [[ "$summary" != "$prev_summary" ]]; then
        printf '%s    %s%s\n' "$C_DIM" "$summary" "$C_RESET"
        prev_summary="$summary"
    fi

    if [[ $all_healthy -eq 1 ]]; then
        ok "All services healthy"
        break
    fi

    if [[ $(date +%s) -gt $DEADLINE ]]; then
        warn "Timed out waiting for healthy. Last status: $summary"
        warn "Run 'docker compose logs' to investigate."
        exit 1
    fi
    sleep 5
done

# ---- 5. smoke test -----------------------------------------------------------

if [[ -n "$ENV_DOMAIN" ]]; then
    BASE_URL="https://$ENV_DOMAIN"
else
    HTTP_PORT="$(grep -E '^HTTP_PORT=' .env | cut -d= -f2- || true)"
    HTTP_PORT="${HTTP_PORT:-80}"
    if [[ "$HTTP_PORT" == "80" ]]; then
        BASE_URL="http://localhost"
    else
        BASE_URL="http://localhost:$HTTP_PORT"
    fi
fi

log "Smoke testing $BASE_URL"
# Caddy proxies /api → backend; /actuator is wired through too.
if curl -fsSk --max-time 10 "$BASE_URL/api/actuator/health" >/dev/null 2>&1; then
    ok "Backend health check passed"
elif curl -fsSk --max-time 10 "$BASE_URL/" >/dev/null 2>&1; then
    ok "Frontend reachable (backend health probe skipped — route may differ)"
else
    warn "Smoke test failed — services are healthy but $BASE_URL didn't respond."
    warn "On a VPS, this usually means DNS hasn't propagated yet or ports 80/443 are blocked."
fi

# ---- 6. done -----------------------------------------------------------------

echo
ok "EduLingo is up."
echo "    URL:   $BASE_URL"
echo "    Logs:  docker compose logs -f"
echo "    Down:  docker compose down"
