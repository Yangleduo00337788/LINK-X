import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { ADMIN_ROUTE_PERMISSIONS } from './roleSmokeMatrix'

describe('路由权限与冒烟矩阵一致性', () => {
  it('ADMIN_ROUTE_PERMISSIONS 与 router/index.ts meta.permission 对齐', () => {
    const routerSrc = readFileSync(resolve(__dirname, '../router/index.ts'), 'utf-8')

    for (const route of ADMIN_ROUTE_PERMISSIONS) {
      if (!route.permission) continue
      // path 在 children 里是相对段，如 menus / users/:id
      const segment = route.path.replace(/^\/admin\//, '')
      expect(routerSrc, `route ${route.path} missing in router`).toContain(`path: '${segment}'`)
      expect(routerSrc, `permission ${route.permission} missing for ${route.path}`).toContain(
        `permission: '${route.permission}'`,
      )
    }
  })
})
