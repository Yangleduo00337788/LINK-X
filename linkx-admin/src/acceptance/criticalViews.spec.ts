import { describe, expect, it } from 'vitest'
import { existsSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * 管理页 route matrix：src/views 下全部 Vue 文件存在性（配合 Playwright E2E）。
 */
describe('critical admin views', () => {
  const viewsDir = resolve(__dirname, '../views')
  const views = readdirSync(viewsDir)
    .filter((file) => file.endsWith('.vue'))
    .sort()

  it('route matrix lists all view files under src/views', () => {
    expect(views.length).toBeGreaterThanOrEqual(27)
    expect(views).toContain('LoginView.vue')
    expect(views).toContain('DashboardView.vue')
  })

  for (const file of views) {
    it(`${file} exists`, () => {
      expect(existsSync(resolve(viewsDir, file))).toBe(true)
    })
  }
})
