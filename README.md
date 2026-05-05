# EduLingo — App Học Tiếng Anh Theo Lộ Trình Cá Nhân

> Demo dự án trường — AI-powered English learning app với Live2D avatar, TTS, lịch sử học và bài kiểm tra lên cấp.

---

## Kiến trúc hệ thống

```
┌─────────────────┐     REST/SSE      ┌──────────────────────┐
│  Frontend Vue 3  │ ←────────────── │  Spring Boot :8080    │
│  Vite + Tailwind │                  │  JWT Auth, JPA, Flyway│
└─────────────────┘                   └──────────┬───────────┘
                                                  │ REST
                                       ┌──────────▼───────────┐
                                       │  Python FastAPI :8000 │
                                       │  Ollama proxy + TTS   │
                                       └──────────┬───────────┘
                                                  │ HTTP
                                       ┌──────────▼───────────┐
                                       │  Ollama :11434        │
                                       │  Model: gemma2:9b     │
                                       └──────────────────────┘
```

---

## Yêu cầu hệ thống

| Thành phần | Phiên bản | Ghi chú |
|-----------|----------|--------|
| Java (JDK) | 17 | IBM Semeru hoặc OpenJDK |
| Maven | 3.9+ | |
| Node.js | 18+ | |
| npm | 9+ | |
| Python | 3.10+ | |
| PostgreSQL | 14+ | |
| Ollama | latest | Chạy model gemma2:9b |

---

## Cài đặt môi trường

### 1. PostgreSQL

```bash
# macOS (Homebrew)
brew install postgresql@14
brew services start postgresql@14

# Tạo database và user
psql postgres -c "CREATE USER edulingo WITH PASSWORD 'edulingo';"
psql postgres -c "CREATE DATABASE edulingo OWNER edulingo;"
```

> **Lưu ý:** Schema (tables) sẽ tự được tạo bởi Flyway khi Spring Boot khởi động lần đầu.

---

### 2. Ollama + Model AI

```bash
# Cài Ollama (macOS)
brew install ollama

# Hoặc tải từ https://ollama.com

# Kéo model gemma2:9b (~5.5GB)
ollama pull gemma2:9b

# Kiểm tra model đã có
ollama list
```

---

### 3. Python Backend (FastAPI + TTS)

```bash
cd backend-python

# Tạo virtual environment (khuyến nghị)
python3 -m venv venv
source venv/bin/activate      # macOS/Linux
# venv\Scripts\activate       # Windows

# Cài dependencies
pip install -r requirements.txt
```

---

### 4. Spring Boot Backend

Không cần cài Maven toàn cục nếu dùng wrapper. Chỉ cần **Java 17** trong `JAVA_HOME`.

```bash
# Kiểm tra Java
java -version   # phải là 17.x

# (macOS) Nếu Java không nhận:
export JAVA_HOME=/Library/Java/JavaVirtualMachines/ibm-semeru-open-17.jdk/Contents/Home
```

---

### 5. Frontend Vue

```bash
cd frontend-vue
npm install
```

---

## Chạy ứng dụng

> Cần mở **4 terminal** riêng, chạy theo thứ tự sau.

### Terminal 1 — Ollama

```bash
ollama serve
```

> Kiểm tra: `http://localhost:11434` hiện `Ollama is running`

---

### Terminal 2 — Python Backend (AI proxy + TTS)

```bash
cd backend-python
source venv/bin/activate       # nếu dùng venv

uvicorn main:app --port 8000
```

> Kiểm tra: `http://localhost:8000/docs`

---

### Terminal 3 — Spring Boot Backend

```bash
# macOS — nếu Maven đã trong PATH
mvn spring-boot:run -f backend-spring/pom.xml

# macOS — nếu dùng đường dẫn tuyệt đối
JAVA_HOME=/Library/Java/JavaVirtualMachines/ibm-semeru-open-17.jdk/Contents/Home \
  /Users/<your-user>/Documents/cis/env/maven/apache-maven-3.9.6/bin/mvn \
  spring-boot:run -f backend-spring/pom.xml
```

> Khởi động xong khi log hiện: `Started EdulingoApplication in X.X seconds`

---

### Terminal 4 — Frontend Vue

```bash
cd frontend-vue
npm run dev
```

> Mở trình duyệt: **http://localhost:5173**

---

## Cấu hình (tuỳ chọn)

Tạo file `frontend-vue/.env` để override mặc định:

```env
VITE_LOCAL_AI_URL=http://localhost:8000
VITE_LIVE2D_MODEL=/models/haru/haru_greeter_t03.model3.json
```

Biến môi trường Spring Boot (`application.yml` đã có giá trị mặc định):

| Biến | Mặc định | Mô tả |
|------|----------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/edulingo` | PostgreSQL URL |
| `DB_USER` | `edulingo` | DB username |
| `DB_PASSWORD` | `edulingo` | DB password |
| `AI_PROVIDER` | `local` | `local` / `openai` / `gemini` |
| `LOCAL_AI_URL` | `http://localhost:8000` | Python backend URL (khi dùng `local`) |
| `OPENAI_API_KEY` | *(trống)* | Bắt buộc khi `AI_PROVIDER=openai` |
| `OPENAI_MODEL` | `gpt-4o-mini` | Model chat (khi dùng `openai`) |
| `GEMINI_API_KEY` | *(key demo)* | Bắt buộc khi `AI_PROVIDER=gemini` |

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
EduLingo-main/
├── backend-python/          # FastAPI — AI proxy + Text-to-Speech
│   ├── main.py
│   └── requirements.txt
│
├── backend-spring/          # Spring Boot — API chính
│   └── src/main/
│       ├── java/com/edulingo/
│       │   ├── controller/  # REST endpoints
│       │   ├── service/     # Business logic
│       │   ├── entity/      # JPA entities
│       │   └── dto/         # Request/Response objects
│       └── resources/
│           ├── application.yml
│           ├── db/migration/            # Flyway SQL migrations
│           ├── assessment-questions.json # Ngân hàng câu hỏi kiểm tra
│           └── static/topic-images/     # Ảnh demo 12 chủ đề
│
└── frontend-vue/            # Vue 3 + Vite + Tailwind
    ├── src/
    │   ├── views/           # DashboardView, ChatView, PictureView, AssessmentView
    │   ├── components/      # Live2DAvatar, TopicPicker
    │   ├── api/             # edulingo.ts, auth.ts
    │   ├── stores/          # Pinia auth store
    │   └── router/
    └── public/
        ├── live2dcubismcore.min.js
        └── models/haru/     # Live2D model files
```

---

## Xử lý lỗi thường gặp

### `Unable to obtain connection from database`
PostgreSQL chưa chạy. Chạy: `brew services start postgresql@14`

### `Unable to load Live2D model`
Thiếu file Live2D. Tải Haru model từ [Live2D Sample Data](https://www.live2d.com/en/learn/sample/) và đặt vào `frontend-vue/public/models/haru/`.

### `Ollama connection refused`
Ollama chưa chạy. Chạy: `ollama serve`

### `edge-tts` không có giọng (TTS im lặng)
Cần kết nối Internet — `edge-tts` dùng Microsoft Azure TTS online.

### Trang trắng khi vào localhost:5173
Chạy `npm run dev:fresh` để clear Vite cache.
