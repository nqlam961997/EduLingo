import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // Spring Boot backend
      '/api': 'http://localhost:8080',
      // FastAPI ai-proxy (when running the Dockerized stack on port 80,
      // these target localhost:8000 by default — adjust via env if needed)
      '/ai': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/ai/, ''),
      },
    },
  },
  optimizeDeps: {
    include: [
      'pixi.js',
      'pixi-live2d-display',
      'pixi-live2d-display/cubism4',
    ],
    exclude: [],
    force: false,
  },
})
