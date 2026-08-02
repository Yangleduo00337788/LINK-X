#!/usr/bin/env node
/**
 * 拉取 Apifox 云端接口并与本地 OpenAPI 对比
 * 用法：APIFOX_PROJECT_ID=... APIFOX_ACCESS_TOKEN=... node docs/api/_audit-apifox-cloud.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const projectId = process.env.APIFOX_PROJECT_ID || '8663484'
const token = process.env.APIFOX_ACCESS_TOKEN

if (!token) {
  console.error('需要 APIFOX_ACCESS_TOKEN')
  process.exit(1)
}

const API_BASE = 'https://api.apifox.com'
const API_VERSION = '2024-03-28'

async function apifoxPost(urlPath, body) {
  const res = await fetch(`${API_BASE}${urlPath}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'X-Apifox-Api-Version': API_VERSION,
    },
    body: JSON.stringify(body),
  })
  const text = await res.text()
  let json
  try {
    json = JSON.parse(text)
  } catch {
    json = { raw: text.slice(0, 500) }
  }
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} ${urlPath}: ${JSON.stringify(json).slice(0, 400)}`)
  }
  return json
}

function loadLocalOps() {
  const spec = JSON.parse(
    fs.readFileSync(path.join(repoRoot, 'docs/api/linkx-openapi.json'), 'utf8'),
  )
  const map = new Map()
  for (const [p, methods] of Object.entries(spec.paths || {})) {
    for (const [method, op] of Object.entries(methods)) {
      if (['get', 'post', 'put', 'patch', 'delete'].includes(method)) {
        map.set(method.toUpperCase() + ' ' + p, { method: method.toUpperCase(), path: p, tags: op.tags || [] })
      }
    }
  }
  return map
}

function loadApifoxOps(exported) {
  const map = new Map()
  const paths = exported.paths || exported.openapi?.paths || {}
  for (const [p, methods] of Object.entries(paths)) {
    for (const [method, op] of Object.entries(methods)) {
      if (['get', 'post', 'put', 'patch', 'delete'].includes(method)) {
        const key = method.toUpperCase() + ' ' + p
        map.set(key, {
          method: method.toUpperCase(),
          path: p,
          tags: op.tags || [],
          summary: op.summary || '',
          operationId: op.operationId || '',
        })
      }
    }
  }
  return map
}

async function main() {
  console.log('>>> 从 Apifox 导出 OpenAPI (project', projectId, ')...')
  const exported = await apifoxPost(`/v1/projects/${projectId}/export-openapi`, {
    scope: { type: 'ALL' },
    oasVersion: '3.1',
    exportFormat: 'JSON',
    options: {
      includeApifoxExtensionProperties: true,
      addFoldersToTags: false,
    },
  })

  const apifoxOps = loadApifoxOps(exported)
  const localOps = loadLocalOps()

  const onlyApifox = [...apifoxOps.keys()].filter((k) => !localOps.has(k)).sort()
  const onlyLocal = [...localOps.keys()].filter((k) => !apifoxOps.has(k)).sort()

  // 重复 path 检测（同 method+path 在 Apifox 出现多次）
  const pathCounts = new Map()
  for (const k of apifoxOps.keys()) {
    pathCounts.set(k, (pathCounts.get(k) || 0) + 1)
  }

  const report = {
    generatedAt: new Date().toISOString(),
    projectId,
    counts: {
      apifoxCloud: apifoxOps.size,
      localOpenApi: localOps.size,
      apifoxUiShown: 393,
      deltaVsLocal: apifoxOps.size - localOps.size,
    },
    onlyInApifox: onlyApifox.map((k) => apifoxOps.get(k)),
    onlyInLocal: onlyLocal.map((k) => localOps.get(k)),
    aligned: onlyApifox.length === 0 && onlyLocal.length === 0,
  }

  const outFile = path.join(__dirname, '_audit-apifox-cloud.json')
  fs.writeFileSync(outFile, JSON.stringify(report, null, 2))

  console.log('\n=== Apifox 云端 vs 本地 OpenAPI ===')
  console.log('Apifox 云端操作数:', apifoxOps.size)
  console.log('本地 OpenAPI 操作数:', localOps.size)
  console.log('Apifox UI 显示接口数: 393 (你截图)')
  console.log('差额 (云端-本地):', apifoxOps.size - localOps.size)
  console.log('')

  if (report.aligned) {
    console.log('✅ 云端与本地 OpenAPI 完全一致')
  } else {
    console.log('云端有、本地无:', onlyApifox.length)
    for (const k of onlyApifox) {
      const op = apifoxOps.get(k)
      console.log(`  ${k}  [${(op.tags || []).join(', ')}] ${op.summary || ''}`)
    }
    console.log('\n本地有、云端无:', onlyLocal.length)
    for (const k of onlyLocal) {
      const op = localOps.get(k)
      console.log(`  ${k}  [${(op.tags || []).join(', ')}]`)
    }
  }

  if (apifoxOps.size !== 393) {
    console.log('\n说明: UI 显示 393 可能与导出操作数不同，常见原因：')
    console.log('  - 重复导入的同名接口（OpenAPI + Postman 各导入一次）')
    console.log('  - 占位符目录 ${openapi.tag.xxx} 下的残留')
    console.log('  - client/admin 重复目录树')
    console.log('  可运行: node docs/api/cleanup-apifox-structure.mjs 清理')
  }

  console.log(`\n详细 JSON: ${outFile}`)
}

main().catch((e) => {
  console.error(e.message)
  process.exit(1)
})
