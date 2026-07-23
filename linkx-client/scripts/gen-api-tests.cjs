const fs = require('fs')
const path = require('path')

const apiDir = path.join(__dirname, '../src/api')
const files = fs
  .readdirSync(apiDir)
  .filter((f) => f.endsWith('.ts') && !f.includes('.test.') && f !== 'client.ts')

for (const f of files) {
  const base = f.replace(/\.ts$/, '')
  const testPath = path.join(apiDir, `${base}.test.ts`)
  if (fs.existsSync(testPath)) continue

  const src = fs.readFileSync(path.join(apiDir, f), 'utf8')
  const exports = [...src.matchAll(/export\s+(?:async\s+)?function\s+(\w+)/g)].map((m) => m[1])
  if (!exports.length) {
    console.log('skip', base)
    continue
  }

  let body = `import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn()
  }
}))

import { apiClient } from './client'
import {
  ${exports.join(',\n  ')}
} from './${base}'

describe('api/${base}', () => {
  beforeEach(() => vi.clearAllMocks())

`

  for (const fn of exports) {
    const re = new RegExp(
      `export\\s+(?:async\\s+)?function\\s+${fn}\\s*\\(([^)]*)\\)[\\s\\S]*?return\\s+apiClient\\.(\\w+)`
    )
    const m = src.match(re)
    const method = m ? m[2] : 'get'
    const params = m ? m[1] : ''
    const args = []
    const parts = params
      .split(',')
      .map((p) => p.trim())
      .filter(Boolean)

    for (const p of parts) {
      if (p.includes('=') || p.startsWith('opts') || p.includes('?:')) continue
      if (/File/.test(p)) args.push("new File(['x'], 'a.bin')")
      else if (/payload|Request|DTO|body/i.test(p)) args.push('{} as any')
      else if (/limit|number/.test(p)) args.push('10')
      else if (/boolean/.test(p)) args.push('true')
      else if (/keyword|q\b|type|category|album|content|title|name/.test(p)) args.push("'x'")
      else args.push("'1'")
    }

    body += `  it('${fn} 应调用 apiClient', async () => {
    vi.mocked(apiClient.${method}).mockResolvedValue({ code: 200, data: null } as any)
    await ${fn}(${args.join(', ')})
    expect(apiClient.${method}).toHaveBeenCalled()
  })

`
  }

  body += `})
`
  fs.writeFileSync(testPath, body)
  console.log('wrote', testPath, exports.length)
}
