/**
 * 作者：yangleduo
 */
import { defineConfig, devices } from '@playwright/test'
import './e2e/helpers/loadLocalEnv'

const adminBase = process.env.E2E_ADMIN_BASE_URL || 'http://127.0.0.1:5174'
const clientBase = process.env.E2E_CLIENT_BASE_URL || 'http://127.0.0.1:5173'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    trace: 'on-first-retry',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'api',
      testMatch: '**/api/**/*.spec.ts',
      use: {
        baseURL: process.env.E2E_API_BASE_URL || 'http://127.0.0.1:8080'
      }
    },
    {
      name: 'admin',
      testMatch: '**/admin/**/*.spec.ts',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: adminBase
      }
    },
    {
      name: 'client',
      testMatch: '**/client/**/*.spec.ts',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: clientBase
      }
    }
  ]
})
