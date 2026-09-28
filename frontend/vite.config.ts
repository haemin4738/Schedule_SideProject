import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'
import { resolve } from 'path'

export default defineConfig(({ mode }) => ({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': resolve(import.meta.dirname, 'src') },
  },
  server: {
    // 개발 서버에서 /api 요청을 백엔드로 전달한다 (브라우저 기준 같은 출처라 CORS 불필요, SSE 상대경로도 동작)
    proxy: {
      '/api': {
        // .env 또는 셸 환경변수 API_PROXY_TARGET 으로 변경 가능 (VITE_ 접두사가 아니라 번들에 노출되지 않음)
        target: loadEnv(mode, process.cwd(), '').API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
}))
