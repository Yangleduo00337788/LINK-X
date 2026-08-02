#!/usr/bin/env node
/**
 * 快速整理 Apifox 目录（并行删除，避免卡住）
 * 环境变量：APIFOX_PROJECT_ID、APIFOX_ACCESS_TOKEN
 */
import { spawn } from 'node:child_process'

const projectId = process.env.APIFOX_PROJECT_ID
const token = process.env.APIFOX_ACCESS_TOKEN
const CONCURRENCY = Number(process.env.APIFOX_CONCURRENCY || 12)

if (!projectId || !token) {
  console.error('需要 APIFOX_PROJECT_ID 和 APIFOX_ACCESS_TOKEN')
  process.exit(1)
}

const SMOKE_PREFIX = /^(0[1-5]-(客户端|管理端))/

function runCli(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(
      'npx',
      ['--yes', 'apifox-cli', ...args, '--access-token', token],
      { shell: true, stdio: ['ignore', 'pipe', 'pipe'] },
    )
    let out = ''
    child.stdout?.on('data', (d) => (out += d))
    child.stderr?.on('data', (d) => (out += d))
    child.on('close', (code) => {
      if (code !== 0) reject(new Error(out.slice(0, 500)))
      else resolve(out)
    })
  })
}

async function cliJson(args) {
  const out = await runCli(args)
  return JSON.parse(out)
}

async function poolMap(items, fn, concurrency = CONCURRENCY) {
  let i = 0
  let ok = 0
  let fail = 0
  async function worker() {
    while (i < items.length) {
      const idx = i++
      const item = items[idx]
      try {
        await fn(item)
        ok++
      } catch (e) {
        fail++
        console.warn('失败', item, e.message?.slice(0, 80))
      }
      if ((ok + fail) % 20 === 0) {
        console.log(`  进度 ${ok + fail}/${items.length} (成功 ${ok} 失败 ${fail})`)
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, () => worker()))
  return { ok, fail }
}

function isJunkTree(path) {
  return path === 'client' || path === 'admin' || path.startsWith('client/') || path.startsWith('admin/')
}

function folderScore(path) {
  if (!path || path === '根目录') return 30
  if (isJunkTree(path)) return 0
  if (SMOKE_PREFIX.test(path)) return 20
  if (!path.includes('/')) return 100
  return 40
}

async function listAllEndpoints() {
  const all = []
  for (let page = 1; page <= 10; page++) {
    const res = await cliJson([
      'endpoint', 'list', '--project', projectId, '--page-size', '500', '--page', String(page),
    ])
    if (!res?.data?.length) break
    all.push(...res.data)
    if (all.length >= res.meta?.total) break
  }
  return all
}

async function main() {
  console.log(`并行度 ${CONCURRENCY}`)

  const foldersRes = await cliJson(['folder', 'list', '--project', projectId, '--type', 'endpoint'])
  const folders = foldersRes?.data || []
  const folderById = new Map(folders.map((f) => [f.id, f]))
  const pathOf = (id) => folderById.get(id)?.path || ''

  let endpoints = await listAllEndpoints()
  console.log(`当前：${folders.length} 目录，${endpoints.length} 接口`)

  const junkIds = endpoints.filter((e) => isJunkTree(pathOf(e.folderId))).map((e) => e.id)
  if (junkIds.length) {
    console.log(`\n>>> 并行删除 client/admin 接口 ${junkIds.length} 个`)
    const r = await poolMap(junkIds, (id) =>
      runCli(['endpoint', 'delete', String(id), '--project', projectId]),
    )
    console.log(`完成 junk 接口：成功 ${r.ok} 失败 ${r.fail}`)
  }

  endpoints = await listAllEndpoints()
  const groups = new Map()
  for (const e of endpoints) {
    const key = `${(e.method || '').toLowerCase()} ${e.path}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(e)
  }
  const dupeIds = []
  for (const arr of groups.values()) {
    if (arr.length <= 1) continue
    const ranked = [...arr].sort((a, b) => folderScore(pathOf(b.folderId)) - folderScore(pathOf(a.folderId)))
    dupeIds.push(...ranked.slice(1).map((e) => e.id))
  }
  if (dupeIds.length) {
    console.log(`\n>>> 并行删除重复 path 接口 ${dupeIds.length} 个`)
    const r = await poolMap(dupeIds, (id) =>
      runCli(['endpoint', 'delete', String(id), '--project', projectId]),
    )
    console.log(`完成重复接口：成功 ${r.ok} 失败 ${r.fail}`)
  }

  const foldersRes2 = await cliJson(['folder', 'list', '--project', projectId, '--type', 'endpoint'])
  const junkFolders = (foldersRes2?.data || [])
    .filter((f) => isJunkTree(f.path || ''))
    .sort((a, b) => (b.path?.length || 0) - (a.path?.length || 0))

  if (junkFolders.length) {
    console.log(`\n>>> 并行删除 junk 目录 ${junkFolders.length} 个`)
    const r = await poolMap(junkFolders, (f) =>
      runCli(['folder', 'delete', String(f.id), '--project', projectId, '--type', 'endpoint']),
    )
    console.log(`完成目录：成功 ${r.ok} 失败 ${r.fail}`)
  }

  const finalFolders = await cliJson(['folder', 'list', '--project', projectId, '--type', 'endpoint'])
  const finalEndpoints = await listAllEndpoints()
  const junkLeft = (finalFolders?.data || []).filter((f) => isJunkTree(f.path || '')).length

  console.log('\n=== 完成 ===')
  console.log(`剩余目录 ${finalFolders?.data?.length}（junk ${junkLeft}）`)
  console.log(`剩余接口 ${finalEndpoints.length}`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
