#!/usr/bin/env node
/**
 * 审计：Java Controller ↔ OpenAPI ↔ Postman(Apifox 导入源) 覆盖对比
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const controllerDir = path.join(repoRoot, 'linkx-server/src/main/java/com/linkx/server/controller')

function walk(dir, out = []) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name)
    if (ent.isDirectory()) walk(p, out)
    else if (ent.name.endsWith('Controller.java')) out.push(p)
  }
  return out
}

function extractPathsFromValue(raw) {
  if (!raw) return []
  raw = raw.trim()
  if (raw.startsWith('{')) {
    const m = raw.match(/value\s*=\s*(\{[^}]+\}|"[^"]+"|'[^']+')/)
    if (m) raw = m[1]
  }
  if (raw.startsWith('"') || raw.startsWith("'")) return [raw.slice(1, -1)]
  if (raw.startsWith('{')) {
    return [...raw.matchAll(/"([^"]+)"/g)].map((m) => m[1])
  }
  return [raw]
}

function joinPaths(base, sub) {
  const b = base.replace(/\/$/, '')
  const s = sub.startsWith('/') ? sub : '/' + sub
  if (!b) return s || '/'
  if (!s || s === '/') return b || '/'
  return (b + s).replace(/\/+/g, '/')
}

function parseController(file) {
  const text = fs.readFileSync(file, 'utf8')
  const rel = path.relative(controllerDir, file).replace(/\\/g, '/')
  let classBase = ''
  const classReq = text.match(/@RequestMapping\s*\(([^)]*)\)/)
  if (classReq) {
    const valMatch = classReq[1].match(/(?:value\s*=\s*)?(["'{][^,)]*)/)
    if (valMatch) {
      const paths = extractPathsFromValue(valMatch[1])
      classBase = paths[0] || ''
    }
  }

  const endpoints = []
  const methodRegex = /@(Get|Post|Put|Delete|Patch)Mapping(?:\s*\(([^)]*)\))?/g
  let m
  while ((m = methodRegex.exec(text)) !== null) {
    const ann = m[1].toUpperCase()
    const body = m[2] || ''
    const methods = [ann]
    let subPaths = ['/']
    const pathMatch = body.match(/(?:value|path)\s*=\s*(\{[^}]+\}|"[^"]*"|'[^']*')/)
    if (pathMatch) {
      subPaths = extractPathsFromValue(pathMatch[1])
      if (!subPaths.length) subPaths = ['/']
    } else {
      const bare = body.match(/^"([^"]*)"/)
      if (bare) subPaths = [bare[1] || '/']
    }
    for (const sp of subPaths) {
      const full = joinPaths(classBase, sp)
      for (const method of methods) {
        endpoints.push({ method, path: full, file: rel })
      }
    }
  }
  return endpoints
}

function loadOpenApi(file) {
  const spec = JSON.parse(fs.readFileSync(file, 'utf8'))
  const map = new Map()
  for (const [p, methods] of Object.entries(spec.paths || {})) {
    for (const [method, op] of Object.entries(methods)) {
      if (['get', 'post', 'put', 'patch', 'delete'].includes(method)) {
        map.set(method.toUpperCase() + ' ' + p, {
          method: method.toUpperCase(),
          path: p,
          operationId: op.operationId,
          tags: op.tags || [],
        })
      }
    }
  }
  return map
}

function walkPm(items, out = []) {
  for (const item of items || []) {
    if (item.request) {
      const method = item.request.method
      // Postman 名称含 OpenAPI 路径，如 "GET /notes/{noteId}"
      const nameMatch = (item.name || '').match(/^(GET|POST|PUT|PATCH|DELETE)\s+(\S+)/)
      if (nameMatch) {
        out.push({ method: nameMatch[1], path: nameMatch[2] })
        continue
      }
      let url = item.request.url
      if (typeof url === 'object') {
        const pathParts = url.path || []
        url = '/' + pathParts.join('/')
      }
      url = url.replace(/\{\{baseUrl\}\}/g, '').replace(/^\/api/, '')
      if (!url.startsWith('/')) url = '/' + url
      out.push({ method, path: url })
    }
    if (item.item) walkPm(item.item, out)
  }
  return out
}

function loadPostman(file) {
  const pm = JSON.parse(fs.readFileSync(file, 'utf8'))
  const map = new Map()
  for (const ep of walkPm(pm.item)) {
    map.set(ep.method + ' ' + ep.path, ep)
  }
  return map
}

// ---- main ----
const javaEndpoints = []
for (const f of walk(controllerDir)) {
  javaEndpoints.push(...parseController(f))
}
const javaSet = new Map()
for (const ep of javaEndpoints) {
  const key = ep.method + ' ' + ep.path
  if (!javaSet.has(key)) javaSet.set(key, ep)
}

const openApiSet = loadOpenApi(path.join(repoRoot, 'linkx-server/perf/k6/openapi.json'))
const pmFull = loadPostman(path.join(repoRoot, 'docs/api/linkx-full.postman_collection.json'))
const pmClient = loadPostman(path.join(repoRoot, 'docs/api/linkx-client.postman_collection.json'))
const pmAdmin = loadPostman(path.join(repoRoot, 'docs/admin/linkx-admin.postman_collection.json'))

const inJavaNotOpenApi = [...javaSet.keys()].filter((k) => !openApiSet.has(k)).sort()
const inOpenApiNotJava = [...openApiSet.keys()].filter((k) => !javaSet.has(k)).sort()
const inOpenApiNotPm = [...openApiSet.keys()].filter((k) => !pmFull.has(k)).sort()
const inJavaNotPm = [...javaSet.keys()].filter((k) => !pmFull.has(k)).sort()

const unionPm = new Set([...pmClient.keys(), ...pmAdmin.keys()])
const missingFromUnion = [...openApiSet.keys()].filter((k) => !unionPm.has(k))

let clientCount = 0
let adminCount = 0
for (const k of openApiSet.keys()) {
  if (k.includes(' /admin/') || k.endsWith(' /admin')) adminCount++
  else clientCount++
}

const report = {
  generatedAt: new Date().toISOString(),
  counts: {
    javaControllers: javaSet.size,
    openApi: openApiSet.size,
    postmanFull: pmFull.size,
    postmanClient: pmClient.size,
    postmanAdmin: pmAdmin.size,
    openApiClient: clientCount,
    openApiAdmin: adminCount,
  },
  gaps: {
    javaNotInOpenApi: inJavaNotOpenApi.map((k) => ({ ...javaSet.get(k), key: k })),
    openApiNotInJava: inOpenApiNotJava.map((k) => ({ ...openApiSet.get(k), key: k })),
    openApiNotInPostmanFull: inOpenApiNotPm.map((k) => ({ ...openApiSet.get(k), key: k })),
    javaNotInPostmanFull: inJavaNotPm.map((k) => ({ ...javaSet.get(k), key: k })),
    openApiNotInClientAdminUnion: missingFromUnion.map((k) => ({ ...openApiSet.get(k), key: k })),
  },
  aligned:
    inJavaNotOpenApi.length === 0 &&
    inOpenApiNotJava.length === 0 &&
    inOpenApiNotPm.length === 0,
}

const outFile = path.join(__dirname, '_audit-coverage.json')
fs.writeFileSync(outFile, JSON.stringify(report, null, 2))

console.log('=== LinkX 接口 → Apifox 覆盖审计 ===\n')
console.log('Java Controller 端点:  ', report.counts.javaControllers)
console.log('OpenAPI 操作数:      ', report.counts.openApi, ` (客户端 ${clientCount} + 管理端 ${adminCount})`)
console.log('Postman 全量:        ', report.counts.postmanFull)
console.log('Postman 客户端:      ', report.counts.postmanClient)
console.log('Postman 管理端:      ', report.counts.postmanAdmin)
console.log('')

if (report.aligned) {
  console.log('✅ Java ↔ OpenAPI ↔ Postman 全量 完全一致（381 个操作）')
} else {
  console.log('⚠️  存在差异，详见下方')
}

console.log('\n--- Java 有但 OpenAPI 无 ---', inJavaNotOpenApi.length)
for (const k of inJavaNotOpenApi) {
  const ep = javaSet.get(k)
  console.log(`  ${k}  (${ep.file})`)
}

console.log('\n--- OpenAPI 有但 Java 无 ---', inOpenApiNotJava.length)
for (const k of inOpenApiNotJava) console.log(`  ${k}`)

console.log('\n--- OpenAPI 有但 Postman全量无 ---', inOpenApiNotPm.length)
for (const k of inOpenApiNotPm) console.log(`  ${k}`)

console.log('\n--- OpenAPI 有但 client+admin 并集无 ---', missingFromUnion.length)
for (const k of missingFromUnion) console.log(`  ${k}`)

console.log(`\n详细 JSON: ${outFile}`)
