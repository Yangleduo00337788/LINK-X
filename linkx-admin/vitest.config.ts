import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.{test,spec}.{js,ts}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      // Phase 3.1：先锁工具/矩阵层 40%+；api/router/stores/i18n/views 后续轮次纳入（见 COVERAGE.md）
      include: [
        'src/utils/format.ts',
        'src/utils/mediaUrl.ts',
        'src/utils/menuI18n.ts',
        'src/acceptance/roleSmokeMatrix.ts',
      ],
      exclude: ['src/**/*.{test,spec}.{js,ts}'],
      thresholds: {
        lines: 40,
        statements: 40,
        functions: 30,
        branches: 30,
      },
    },
  },
})
