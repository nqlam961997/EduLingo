# EduLingo — App Học Tiếng Anh Theo Lộ Trình Cá Nhân

> Demo dự án trường — AI-powered English learning app với Live2D avatar, TTS, lịch sử học và bài kiểm tra lên cấp.

---

## Kiến trúc hệ thống

```
                  ┌──────────────────┐
                  │      Caddy        │  :80 / :443 (auto Let's Encrypt khi DOMAIN set)
                  │  reverse proxy    │
                  └─────────┬────────┘
                            │
                  ┌─────────▼────────┐
                  │   Frontend Vue 3  │  internal :80 (nginx + Vue dist)
                  │   /api → backend  │
                  │   /ai  → ai-proxy │
                  └────┬──────────┬───┘
                       │          │
            ┌──────────▼─────┐  ┌─▼──────────────────┐
            │  Spring Boot   │  │  Python FastAPI     │
            │  JWT + JPA     │  │  Ollama proxy + TTS │
            │  :8080         │  │  :8000              │
            └────────┬───────┘  └──────────┬──────────┘
                     │                     │
            ┌────────▼───────┐    ┌────────▼────────┐
            │  PostgreSQL 16 │    │   Ollama (CPU)  │
            │  named volume  │    │   qwen2.5:3b    │
            └────────────────┘    └─────────────────┘
```

---

## Quickstart with Docker (recommended)

### Prerequisites

- **Docker Engine ≥ 24** and **Docker Compose v2**
  ```bash
  docker --version          # ≥ 24
  docker compose version    # v2.x
  ```
- 8 GB free disk space (Ollama model pull is ~2 GB on first run).
- Outbound Internet (for `edge-tts` and, on VPS, Let's Encrypt).

### Laptop / classroom demo

```bash
cp .env.example .env
docker compose up -d
```

Then open **http://localhost** in your browser. First boot downloads the Ollama model (~2 GB, one-time) — watch progress with `docker compose logs -f ollama`.

### VPS with HTTPS

1. Point your domain's DNS A record at the VPS public IP **before** starting the stack (Caddy needs HTTP-01 reachability).
2. On the VPS:

   ```bash
   git clone <repo> && cd EduLingo
   cp .env.example .env

   # Edit .env and set (at minimum):
   #   DOMAIN=foo.example.com
   #   ACME_EMAIL=you@example.com
   #   JWT_SECRET=$(openssl rand -base64 48)
   #   POSTGRES_PASSWORD=$(openssl rand -base64 24)

   docker compose up -d
   ```

3. Visit `https://foo.example.com`. Caddy obtains a Let's Encrypt cert on first request (~30 s).

### Development with hot reload

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
```

This overlay swaps the prod images for dev servers (Vite, Spring DevTools, uvicorn `--reload`), bind-mounts your source, and exposes:

- Vue dev server: `http://localhost:5173`
- Spring backend: `http://localhost:8080`
- FastAPI ai-proxy: `http://localhost:8000`
- Postgres: `localhost:5432` (user `edulingo`, password from `.env`)

Edits to `.vue`, `.java`, or `.py` files reload without rebuilding images.

### Common operations

```bash
docker compose ps                          # service status + health
docker compose logs -f backend             # tail backend logs
docker compose exec postgres psql -U edulingo edulingo  # open SQL shell
docker compose down                        # stop (data persists)
docker compose down --volumes              # stop AND wipe DB + models
docker compose pull && docker compose up -d --build   # update images
```

### Known constraints

| Constraint | Why |
|---|---|
| First boot pulls ~2 GB Ollama model | Compose marks `ai-proxy` healthy only after the model is available. Subsequent boots skip the pull (cached in the `ollama_models` volume). |
| `edge-tts` needs Internet | Microsoft Azure TTS is hit on every `/ai/tts` request. No fully-offline alternative configured. |
| Let's Encrypt needs DNS first | DNS must resolve before `docker compose up`; otherwise the ACME HTTP-01 challenge fails. |
| Default model is CPU-friendly | `qwen2.5:3b` gives ~5–10 tok/s on a 4-vCPU box. Set `OLLAMA_MODEL=gemma2:9b` only if you have a GPU. |

### Migration from the old local-Postgres setup

Earlier versions had a Postgres-only compose at `backend-spring/docker-compose.yml`. The named volume (`edulingo_pg`) is **reused** by the root compose, so existing user accounts and chat history are preserved automatically.

---

## Running without Docker (advanced)

> Use this path only if you can't run Docker (e.g., locked-down lab machines). The Docker quickstart above is the supported happy path.

### Yêu cầu hệ thống

| Thành phần | Phiên bản | Ghi chú |
|-----------|----------|--------|
| Java (JDK) | 17 | IBM Semeru hoặc OpenJDK |
| Maven | 3.9+ | |
| Node.js | 18+ | |
| npm | 9+ | |
| Python | 3.10+ | |
| PostgreSQL | 14+ | |
| Ollama | latest | Chạy model `qwen2.5:3b` (hoặc `gemma2:9b` nếu có GPU) |

### Cài đặt môi trường

#### 1. PostgreSQL

```bash
# macOS (Homebrew)
brew install postgresql@14
brew services start postgresql@14

psql postgres -c "CREATE USER edulingo WITH PASSWORD 'edulingo';"
psql postgres -c "CREATE DATABASE edulingo OWNER edulingo;"
```

> Schema (tables) sẽ tự được tạo bởi Flyway khi Spring Boot khởi động lần đầu.

#### 2. Ollama + Model AI

```bash
brew install ollama
ollama pull qwen2.5:3b
ollama list
```

#### 3. Python Backend (FastAPI + TTS)

```bash
cd backend-python
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

#### 4. Spring Boot Backend

Cần **Java 17** trong `JAVA_HOME`. Maven wrapper (`mvnw`) đi kèm.

#### 5. Frontend Vue

```bash
cd frontend-vue
npm install
cp .env.example .env   # tuỳ chọn
```

### Chạy ứng dụng (4 terminals)

```bash
# Terminal 1 — Ollama
ollama serve

# Terminal 2 — Python backend
cd backend-python && source venv/bin/activate
uvicorn main:app --port 8000

# Terminal 3 — Spring backend
mvn spring-boot:run -f backend-spring/pom.xml

# Terminal 4 — Frontend
cd frontend-vue && npm run dev
```

Open **http://localhost:5173**.

---

## Cấu hình môi trường

All configuration lives in the root `.env` (copy from `.env.example`). The most important keys:

| Key | Default | Required for VPS? | Notes |
|---|---|---|---|
| `DOMAIN` | *(empty)* | ✅ | Set to your public domain to activate HTTPS via Caddy. |
| `JWT_SECRET` | *(empty)* | ✅ | Backend refuses to start with empty secret when `DOMAIN` is set. Generate: `openssl rand -base64 48`. |
| `POSTGRES_PASSWORD` | `edulingo` | ✅ | Change for any public deploy. |
| `ACME_EMAIL` | `admin@example.com` | recommended | Used for Let's Encrypt renewal notices. |
| `OLLAMA_MODEL` | `qwen2.5:3b` | – | CPU-friendly. Use `gemma2:9b` only on a GPU. |
| `AI_PROVIDER` | `local` | – | `local` (Ollama) / `openai` / `gemini`. |
| `GEMINI_API_KEY` | *(empty)* | only if AI_PROVIDER=gemini | |
| `OPENAI_API_KEY` | *(empty)* | only if AI_PROVIDER=openai | |
| `HTTP_PORT` / `HTTPS_PORT` | `80` / `443` | – | Override if those ports are taken on the host. |

---

## Tính năng chính

| Tính năng | Mô tả |
|-----------|-------|
| 🔐 **Đăng ký / Đăng nhập** | JWT authentication |
| 🏠 **Dashboard cá nhân** | CEFR level, lịch sử học, báo cáo lỗi, gợi ý hôm nay |
| 💬 **Chat AI theo tình huống** | 12 chủ đề, streaming, gợi ý câu trả lời |
| 🤖 **Live2D Avatar + TTS** | Nhân vật Haru nói chuyện với lip-sync thời gian thực |
| 🎤 **Voice Input** | Nói tiếng Anh, AI nhận diện và phản hồi |
| 🖼️ **Mô tả hình ảnh** | AI hỏi về ảnh, chấm điểm, sửa lỗi tiếng Anh |
| 🎓 **Bài kiểm tra lên cấp** | 10 câu trắc nghiệm, đạt ≥ 80% → nâng CEFR level |
| 📊 **Theo dõi lỗi cá nhân** | Tự động ghi nhận và phân tích lỗi Grammar, Vocabulary, ... |

---

## Cấu trúc thư mục

```
EduLingo/
├── docker-compose.yml         # Base — laptop & VPS
├── docker-compose.dev.yml     # Overlay — hot reload
├── .env.example               # Template for .env
│
├── backend-python/            # FastAPI — AI proxy + Text-to-Speech
│   ├── Dockerfile
│   ├── main.py
│   └── requirements.txt
│
├── backend-spring/            # Spring Boot — API chính
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/edulingo/
│       │   ├── config/        # SecurityConfig, CorsConfig, DeploymentSecurityCheck
│       │   ├── controller/    # REST endpoints
│       │   ├── service/       # Business logic
│       │   ├── entity/        # JPA entities
│       │   └── dto/           # Request/Response objects
│       └── resources/
│           ├── application.yml
│           └── db/migration/  # Flyway SQL migrations
│
├── frontend-vue/              # Vue 3 + Vite + Tailwind
│   ├── Dockerfile
│   ├── nginx.conf             # / + /api + /ai routing
│   ├── .env.example
│   └── src/
│       ├── config/env.ts      # VITE_* resolution
│       ├── views/             # Dashboard, Chat, Picture, Assessment, Vocabulary
│       ├── components/        # Live2DAvatar, TopicPicker
│       ├── api/               # edulingo.ts, auth.ts (bare /api/... paths)
│       ├── stores/            # Pinia auth store
│       └── router/
│
├── ollama/                    # Ollama image with auto-pull entrypoint
│   ├── Dockerfile
│   └── entrypoint.sh
│
├── caddy/
│   └── Caddyfile              # Conditional TLS via SITE_ADDRESS
│
└── openspec/                  # Change proposals & specs
    ├── changes/
    └── specs/
```

---

## Xử lý lỗi thường gặp

### Docker mode

**`docker compose up` succeeds but the page never loads.**
Check service health: `docker compose ps`. The most common cause is `ai-proxy` waiting on the first-run Ollama model pull (~2 GB). Tail with `docker compose logs -f ollama` — you'll see download progress.

**`Caddy: failed to obtain certificate`.**
DNS isn't pointing at the VPS yet, or ports 80/443 aren't reachable from the Internet. Fix DNS first, then `docker compose restart caddy`.

**`backend: Refusing to start: DOMAIN is set but JWT_SECRET is unset`.**
Generate a strong secret and put it in `.env`: `JWT_SECRET=$(openssl rand -base64 48)`.

**Port 80 or 443 already in use.**
Override in `.env`: `HTTP_PORT=8080`, `HTTPS_PORT=8443`. Then visit `http://localhost:8080`.

### Non-Docker mode

**`Unable to obtain connection from database`** — PostgreSQL chưa chạy.
**`Unable to load Live2D model`** — Thiếu file Live2D model trong `frontend-vue/public/models/haru/`.
**`Ollama connection refused`** — `ollama serve` chưa chạy.
**`edge-tts` không có giọng (TTS im lặng)** — Cần kết nối Internet.
**Trang trắng khi vào localhost:5173** — Chạy `npm run dev:fresh` để clear Vite cache.
