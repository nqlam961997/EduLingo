import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
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
