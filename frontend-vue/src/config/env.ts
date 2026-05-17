// Shared frontend runtime configuration resolved from Vite build-time env vars.
// Backend calls intentionally use bare relative `/api/...` paths in src/api/*.ts
// (routed by nginx in prod and Vite's server.proxy in dev), so no API_BASE here.

export const LOCAL_AI_URL: string = import.meta.env.VITE_LOCAL_AI_URL ?? '/ai'

export const LIVE2D_MODEL: string =
  import.meta.env.VITE_LIVE2D_MODEL ?? '/models/haru/haru_greeter_t03.model3.json'
