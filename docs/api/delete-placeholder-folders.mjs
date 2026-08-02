#!/usr/bin/env node
/**
 * 删除 Apifox 中 ${openapi.tag.xxx} 占位符目录
 * 需要：APIFOX_PROJECT_ID、APIFOX_ACCESS_TOKEN
 * 且 Apifox 客户端已开启：项目设置 → 功能设置 → 外部 AI 编辑权限 → 主分支直接编辑
 */
import { spawnSync } from 'node:child_process'

const projectId = process.env.APIFOX_PROJECT_ID
const token = process.env.APIFOX_ACCESS_TOKEN
if (!projectId || !token) {
  console.error('需要 APIFOX_PROJECT_ID 和 APIFOX_ACCESS_TOKEN')
  process.exit(1)
}

function apifox(args) {
  const r = spawnSync('npx', ['--yes', 'apifox-cli', ...args, '--access-token', token], {
    encoding: 'utf8',
    shell: true,
    maxBuffer: 20 * 1024 * 1024,
  })
  if (r.stdout) process.stdout.write(r.stdout)
  if (r.stderr) process.stderr.write(r.stderr)
  return r.status === 0 ? JSON.parse(r.stdout) : null
}

const list = apifox(['folder', 'list', '--project', projectId, '--type', 'endpoint'])
if (!list?.success) {
  console.error('无法列出目录，请检查 Token 或开启主分支 AI 编辑权限')
  process.exit(1)
}

const placeholders = (list.data || []).filter((f) =>
  (f.name || '').includes('${openapi.tag.'),
)
console.log(`找到 ${placeholders.length} 个占位符目录`)

let ok = 0
let fail = 0
for (const folder of placeholders) {
  const r = spawnSync(
    'npx',
    [
      '--yes',
      'apifox-cli',
      'folder',
      'delete',
      String(folder.id),
      '--project',
      projectId,
      '--type',
      'endpoint',
      '--access-token',
      token,
    ],
    { encoding: 'utf8', shell: true },
  )
  const out = (r.stdout || '') + (r.stderr || '')
  if (r.status === 0 && out.includes('"success": true')) {
    console.log(`✓ 已删除 ${folder.name} (${folder.id})`)
    ok++
  } else {
    console.log(`✗ 删除失败 ${folder.name} (${folder.id})`)
    if (out.includes('403075') || out.includes('Automation caller')) {
      console.error(
        '\n请在 Apifox 客户端开启：项目设置 → 功能设置 → 外部 AI 编辑权限 → 主分支直接编辑权限\n然后重新运行本脚本。',
      )
      process.exit(1)
    }
    fail++
  }
}

console.log(`完成：成功 ${ok}，失败 ${fail}`)
