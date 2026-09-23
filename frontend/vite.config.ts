/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// In development the API is proxied, so the browser sees one origin and the backend needs no CORS setup.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.BACKEND_URL ?? 'http://localhost:8081'
  return {
    plugins: [react(), tailwindcss()],
    server: {
      proxy: {
        '/api': backend,
        '/v3/api-docs': backend,
        '/swagger-ui': backend,
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      restoreMocks: true,
    },
  }
})
