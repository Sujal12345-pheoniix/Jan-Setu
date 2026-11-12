/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath, URL } from 'node:url';

// The dev server proxies /api to the backend so the browser sees a single origin and the
// HttpOnly, SameSite=Strict refresh cookie works without cross-site cookie relaxations.
const backend = process.env.JANSETU_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
      '/actuator/health': { target: backend, changeOrigin: false },
      '/swagger-ui': { target: backend, changeOrigin: false },
      '/v3/api-docs': { target: backend, changeOrigin: false },
    },
  },
  preview: { port: 4173, proxy: { '/api': { target: backend, changeOrigin: false }, '/actuator/health': { target: backend } } },
  build: { sourcemap: true, chunkSizeWarningLimit: 900 },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
});
