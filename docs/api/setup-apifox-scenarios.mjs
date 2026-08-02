#!/usr/bin/env node
/**
 * 在 Apifox 创建「场景用例」，解决自动化测试「全部运行」灰色不可点
 */
import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const projectId = process.env.APIFOX_PROJECT_ID || '8663484'
const token = process.env.APIFOX_ACCESS_TOKEN
if (!token) {
  console.error('需要 APIFOX_ACCESS_TOKEN')
  process.exit(1)
}

function cli(args) {
  const r = spawnSync('npx', ['--yes', 'apifox-cli', ...args, '--access-token', token], {
    encoding: 'utf8',
    shell: true,
    maxBuffer: 30 * 1024 * 1024,
  })
  if (r.status !== 0) throw new Error((r.stderr || r.stdout || 'cli failed').slice(0, 600))
  return JSON.parse(r.stdout)
}

function listEndpoints() {
  const all = []
  for (let page = 1; page <= 20; page++) {
    const res = cli(['endpoint', 'list', '--project', projectId, '--page-size', '500', '--page', String(page)])
    if (!res?.data?.length) break
    all.push(...res.data)
    if (all.length >= (res.meta?.total || 0)) break
  }
  return all
}

function parseSmokeGroups() {
  const file = path.join(__dirname, 'linkx-smoke-scenarios.postman_collection.json')
  const col = JSON.parse(fs.readFileSync(file, 'utf8'))
  const groups = []
  for (const folder of col.item || []) {
    const steps = []
    for (const item of folder.item || []) {
      const m = (item.name || '').match(/^(GET|POST|PUT|PATCH|DELETE)\s+(\S+)/)
      if (m) steps.push({ method: m[1].toLowerCase(), path: m[2] })
    }
    if (steps.length) groups.push({ name: folder.name, steps })
  }
  return groups
}

function findFolderId(name) {
  const res = cli(['folder', 'list', '--project', projectId, '--type', 'test-scenario'])
  const hit = (res.data || []).find((f) => f.name === name)
  return hit?.id
}

function ensureFolder(name) {
  let id = findFolderId(name)
  if (id) return id
  const created = cli(['folder', 'create', '--project', projectId, '--type', 'test-scenario', '--name', name])
  return created.data?.id
}

function listScenarios() {
  const res = cli(['test-scenario', 'list', '--project', projectId])
  return res.data || []
}

async function main() {
  console.log('>>> 读取冒烟集合与 Apifox 接口列表')
  const groups = parseSmokeGroups()
  const endpoints = listEndpoints()
  const epMap = new Map()
  for (const ep of endpoints) {
    epMap.set(`${(ep.method || '').toLowerCase()} ${ep.path}`, ep.id)
  }
  console.log(`冒烟分组 ${groups.length} 个，Apifox 接口 ${endpoints.length} 个`)

  const folderId = ensureFolder('LinkX Smoke')
  console.log(`场景目录 ID: ${folderId}`)

  const existing = new Map(listScenarios().map((s) => [s.name, s.id]))

  for (const group of groups) {
    const ids = []
    const missing = []
    for (const step of group.steps) {
      const key = `${step.method} ${step.path}`
      const id = epMap.get(key)
      if (id) ids.push(id)
      else missing.push(key)
    }
    if (missing.length) {
      console.warn(`⚠ ${group.name} 缺少接口: ${missing.slice(0, 3).join(', ')}${missing.length > 3 ? '...' : ''}`)
    }
    if (!ids.length) {
      console.warn(`跳过 ${group.name}（无匹配接口）`)
      continue
    }

    let scenarioId = existing.get(group.name)
    const safeName = group.name.replace(/[<>]/g, '')
    if (!scenarioId) scenarioId = existing.get(safeName)
    const createName = group.name.includes('<') ? safeName : group.name
    if (!scenarioId) {
      const created = cli([
        'test-scenario',
        'create',
        '--project',
        projectId,
        '--name',
        createName,
        '--folder-id',
        String(folderId),
        '--priority',
        '0',
        '--description',
        'LinkX 冒烟场景（自动生成）',
      ])
      scenarioId = created.data?.id
      console.log(`+ 创建场景: ${group.name} (id=${scenarioId})`)
    } else {
      console.log(`= 已存在场景: ${group.name} (id=${scenarioId})`)
    }

    const batchSize = 15
    for (let i = 0; i < ids.length; i += batchSize) {
      const chunk = ids.slice(i, i + batchSize)
      cli([
        'test-scenario',
        'import-steps',
        String(scenarioId),
        '--project',
        projectId,
        '--source',
        'endpoint',
        '--ids',
        chunk.join(','),
        '--sync',
        'manual',
      ])
    }
    console.log(`  导入 ${ids.length} 个步骤`)
  }

  const final = listScenarios()
  console.log(`\n✅ 完成。场景用例共 ${final.length} 个`)
  console.log('请在 Apifox：自动化测试 → 场景用例 → LinkX Smoke → 选择场景 → 运行')
  console.log('环境请选：本地开发环境（勿选「相对路径」）')
}

main().catch((e) => {
  console.error(e.message)
  process.exit(1)
})
