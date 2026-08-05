import { defineConfig, devices } from '@playwright/test'

const host = process.env.PLAYWRIGHT_HOST || '127.0.0.1'
const port = process.env.PLAYWRIGHT_PORT || '5174'
const baseURL = process.env.ADMIN_WEB_BASE || `http://${host}:${port}`

export default defineConfig({
  testDir: './e2e',
  testMatch: 'rbac-consistency.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'line',
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
