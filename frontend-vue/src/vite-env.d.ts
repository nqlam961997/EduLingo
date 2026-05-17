// Fallback declarations for Vite's `import.meta.env` so TypeScript works
// even when node_modules isn't installed (e.g., fresh clone before `npm ci`).
// When node_modules is present, vite/client provides richer types via the
// "types": ["vite/client"] entry in tsconfig.json.

interface ImportMetaEnv {
  readonly VITE_LOCAL_AI_URL?: string
  readonly VITE_LIVE2D_MODEL?: string
  readonly [key: string]: string | undefined
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
