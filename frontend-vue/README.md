# EduLingo Frontend (Vue 3)

Vue 3 + Vite + TypeScript + Pinia + Tailwind. Talks to the Spring Boot backend on port 8080.

## Run

```bash
cd frontend-vue
npm install
npm run dev
```

Vite proxies `/api/*` to `http://localhost:8080`, so start the Spring Boot backend first.

## Structure

- `src/api/edulingo.ts` — `streamChat()` (SSE) + `correctPicture()` (multipart)
- `src/stores/learner.ts` — current user email (persisted in localStorage)
- `src/views/ChatView.vue` — streaming chat with clickable suggestion chips
- `src/views/PictureView.vue` — upload image + English description, see corrections
