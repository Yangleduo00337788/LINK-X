/**
 * 作者：yangleduo
 */
import { defineConfig } from '@playwright/test'
import path from 'node:path'

const harnessDir = path.resolve(__dirname, 'fixtures')

export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    trace: 'on-first-retry'
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' }
    }
  ],
  webServer: {
    command: `npx --yes serve ${harnessDir} -l 4173`,
    url: 'http://127.0.0.1:4173/agent-dom-harness.html',
    reuseExistingServer: !process.env.CI
  }
})
