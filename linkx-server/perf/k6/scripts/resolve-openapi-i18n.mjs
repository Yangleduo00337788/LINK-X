#!/usr/bin/env node
/**
 * 将 openapi.json 中的 ${openapi.tag.xxx} 占位符替换为中文（与运行时 Swagger 一致）
 * 用法：node resolve-openapi-i18n.mjs [input] [output]
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(__dirname, '../../../..')
const defaultIn = join(repoRoot, 'linkx-server/perf/k6/openapi.json')
const defaultOut = join(repoRoot, 'docs/api/linkx-openapi.json')
const propsPath = join(repoRoot, 'linkx-server/src/main/resources/i18n/openapi.properties')

const input = process.argv[2] || defaultIn
const output = process.argv[3] || defaultOut

function loadMessages(path) {
  const map = new Map()
  const text = readFileSync(path, 'utf8')
  for (const line of text.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const idx = trimmed.indexOf('=')
    if (idx < 0) continue
    map.set(trimmed.slice(0, idx).trim(), trimmed.slice(idx + 1).trim())
  }
  return map
}

function resolveValue(value, messages) {
  if (typeof value !== 'string') return value
  return value.replace(/\$\{([^}]+)}/g, (full, key) => {
    return messages.get(key) ?? full
  })
}

function walkResolve(obj, messages) {
  if (obj == null) return obj
  if (typeof obj === 'string') return resolveValue(obj, messages)
  if (Array.isArray(obj)) return obj.map((item) => walkResolve(item, messages))
  if (typeof obj === 'object') {
    const out = {}
    for (const [k, v] of Object.entries(obj)) {
      out[k] = walkResolve(v, messages)
    }
    return out
  }
  return obj
}

const messages = loadMessages(propsPath)
const spec = JSON.parse(readFileSync(input, 'utf8'))
const resolved = walkResolve(spec, messages)

// 统计替换
let unresolved = 0
const unresolvedKeys = new Set()
const json = JSON.stringify(resolved)
const re = /\$\{openapi\.[^}]+}/g
let m
while ((m = re.exec(json)) !== null) {
  unresolved++
  unresolvedKeys.add(m[0])
}

writeFileSync(output, JSON.stringify(resolved, null, 2), 'utf8')
console.log(`Wrote ${output}`)
console.log(`Resolved tags from ${propsPath}`)
if (unresolved > 0) {
  console.warn(`Warning: ${unresolved} unresolved placeholders:`, [...unresolvedKeys].slice(0, 10))
} else {
  console.log('All openapi placeholders resolved to Chinese.')
}
