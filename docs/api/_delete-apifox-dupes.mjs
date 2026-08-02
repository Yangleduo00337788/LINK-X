#!/usr/bin/env node
/**
 * 删除 Apifox 中 Postman 导入产生的 /1 重复接口
 */
import { spawnSync } from 'node:child_process'

const projectId = process.env.APIFOX_PROJECT_ID || '8663484'
const token = process.env.APIFOX_ACCESS_TOKEN
if (!token) {
  console.error('需要 APIFOX_ACCESS_TOKEN')
  process.exit(1)
}

const TARGETS = new Set([
  'get /admin/banners/1',
  'get /admin/blacklist/1',
  'get /admin/feedback/1',
  'get /admin/notices/1',
  'get /admin/permissions/1',
  'get /admin/recommends/1',
  'get /admin/reviews/1',
  'get /admin/risk-events/1',
  'get /admin/roles/1',
  'get /admin/roles/1/menus',
  'get /admin/roles/1/permissions',
  'get /admin/roles/1/users',
])

function cli(args) {
  const r = spawnSync('npx', ['--yes', 'apifox-cli', ...args, '--access-token', token], {
    encoding: 'utf8',
    shell: true,
    maxBuffer: 30 * 1024 * 1024,
  })
  if (r.status !== 0) {
    throw new Error((r.stderr || r.stdout || 'cli failed').slice(0, 500))
  }
  return JSON.parse(r.stdout)
}

async function main() {
  const all = []
  for (let page = 1; page <= 10; page++) {
    const res = cli(['endpoint', 'list', '--project', projectId, '--page-size', '500', '--page', String(page)])
    if (!res?.data?.length) break
    all.push(...res.data)
    if (all.length >= (res.meta?.total || 0)) break
  }

  const hits = all.filter((e) => TARGETS.has(`${(e.method || '').toLowerCase()} ${e.path || ''}`))
  console.log(`找到 ${hits.length} 个待删除重复接口`)

  if (!hits.length) {
    console.log('无需删除')
    return
  }

  let ok = 0
  let fail = 0
  for (const ep of hits) {
    try {
      cli(['endpoint', 'delete', String(ep.id), '--project', projectId])
      console.log(`✓ 已删除 ${ep.method} ${ep.path} (id=${ep.id})`)
      ok++
    } catch (e) {
      console.log(`✗ 删除失败 ${ep.method} ${ep.path} (id=${ep.id}): ${e.message}`)
      fail++
    }
  }

  const after = cli(['endpoint', 'list', '--project', projectId, '--page-size', '1', '--page', '1'])
  console.log(`\n完成：成功 ${ok}，失败 ${fail}`)
  console.log(`当前接口总数：${after.meta?.total ?? '?'}`)
}

main().catch((e) => {
  console.error(e.message)
  process.exit(1)
})
