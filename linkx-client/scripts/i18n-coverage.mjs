import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const src = path.join(root, 'src')

function flattenKeys(obj, prefix = '') {
  const keys = []
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object' && !Array.isArray(v)) keys.push(...flattenKeys(v, key))
    else keys.push(key)
  }
  return keys
}

function walk(dir, exts, out = []) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name)
    if (ent.isDirectory() && !['node_modules', 'dist'].includes(ent.name)) walk(p, exts, out)
    else if (exts.some((e) => ent.name.endsWith(e))) out.push(p)
  }
  return out
}

function extractTKeys(content) {
  const keys = new Set()
  const re = /(?:\$t|[^a-zA-Z]t)\(\s*['"`]([^'"`]+)['"`]/g
  let m
  while ((m = re.exec(content)) !== null) keys.add(m[1])
  return keys
}

const zh = (await import(new URL(`file:///${path.join(src, 'i18n/locales/zh-CN.ts').replace(/\\/g, '/')}`))).default
const zhKeys = new Set(flattenKeys(zh))
const files = walk(src, ['.vue', '.ts']).filter((f) => !f.includes('i18n\\locales') && !f.includes('i18n/locales'))

const usedKeys = new Set()
for (const f of files) {
  for (const k of extractTKeys(fs.readFileSync(f, 'utf8'))) usedKeys.add(k)
}

const missing = [...usedKeys].filter((k) => !zhKeys.has(k)).sort()
console.log(`missing keys: ${missing.length}`)
for (const k of missing) console.log(k)
