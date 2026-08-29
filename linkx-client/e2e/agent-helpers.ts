/**
 * 作者：yangleduo
 */
import { expect, type Page } from '@playwright/test'

export async function waitHarness(page: Page) {
  await page.goto('/agent-sim-harness.html')
  await page.waitForFunction(() => window.__agentE2E?.ready === true)
}

export async function runAction(
  page: Page,
  name: string,
  args: Record<string, unknown>
) {
  return page.evaluate(
    async ({ actionName, actionArgs }) => {
      const api = window.__agentE2E!
      return api.runAction(
        actionName as Parameters<NonNullable<typeof window.__agentE2E>['runAction']>[0],
        actionArgs
      )
    },
    { actionName: name, actionArgs: args }
  )
}

export async function runPipeline(
  page: Page,
  actions: Array<{ name: string; arguments: Record<string, unknown> }>,
  options?: { manualConfirm?: boolean; autoReject?: boolean }
) {
  return page.evaluate(
    async ({ rows, opts }) => {
      const api = window.__agentE2E!
      return api.runPipeline(
        rows as Parameters<NonNullable<typeof window.__agentE2E>['runPipeline']>[0],
        opts
      )
    },
    { rows: actions, opts: options }
  )
}

export function tomorrowDateKey() {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export async function resetHarnessNav(page: Page, nav = 'calendar') {
  await page.evaluate(n => window.__agentE2E?.setNav(n), nav)
  await expect(page.getByTestId('current-nav')).toHaveText(nav)
}
