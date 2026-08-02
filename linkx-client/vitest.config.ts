import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    UnoCSS(),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{js,ts}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      include: ['src/**/*.{ts,vue}'],
      exclude: ['src/**/*.d.ts', 'src/**/types/**', 'src/test/**', 'src/**/*.{test,spec}.{js,ts}'],
      // Phase 3.1 门禁；目标逐步抬升至 100%（见 docs/testing/COVERAGE.md）
      thresholds: {
        lines: 40,
        functions: 20,
        branches: 30,
        statements: 40,
      },
    },
  },
})
