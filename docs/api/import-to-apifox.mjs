#!/usr/bin/env node
/**
 * 一键导入 LinkX API 到 Apifox（需 Apifox CLI + 项目凭证）
 *
 * 环境变量：
 *   APIFOX_ACCESS_TOKEN  - Apifox 开放 API Token（项目设置 → 开放 API）
 *   APIFOX_PROJECT_ID    - 项目 ID
 *
 * 用法：
 *   node docs/api/import-to-apifox.mjs
 *   node docs/api/import-to-apifox.mjs --skip-openapi
 *   node docs/api/import-to-apifox.mjs --only smoke
 */
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(__dirname, '../../')
const apiDir = join(repoRoot, 'docs/api')

const args = process.argv.slice(2)
const skipOpenapi = args.includes('--skip-openapi')
const only = args.includes('--only') ? args[args.indexOf('--only') + 1] : null

const projectId = process.env.APIFOX_PROJECT_ID
const accessToken = process.env.APIFOX_ACCESS_TOKEN

if (!projectId || !accessToken) {
  console.error('请设置环境变量 APIFOX_PROJECT_ID 和 APIFOX_ACCESS_TOKEN')
  console.error('获取方式：Apifox → 项目设置 → 开放 API')
  console.error('')
  console.error('PowerShell 示例：')
  console.error('  $env:APIFOX_PROJECT_ID="你的项目ID"')
  console.error('  $env:APIFOX_ACCESS_TOKEN="你的Token"')
  console.error('  node docs/api/import-to-apifox.mjs')
  process.exit(1)
}

function runRestImport() {
  console.log('使用 Apifox 开放 REST API 导入（推荐，绕过 CLI AI 权限限制）...')
  const result = spawnSync(process.execPath, [join(apiDir, 'import-via-rest.mjs')], {
    stdio: 'inherit',
    env: { ...process.env, APIFOX_PROJECT_ID: projectId, APIFOX_ACCESS_TOKEN: accessToken },
  })
  if (result.status !== 0) process.exit(result.status ?? 1)
}

function runApifoxCli(args) {
  const cli = process.platform === 'win32' ? 'apifox.cmd' : 'apifox'
  let result = spawnSync(cli, [...args, '--access-token', accessToken], {
    stdio: 'inherit',
    shell: true,
  })
  if (result.status !== 0) {
    console.log('本地未安装 apifox CLI，尝试 npx apifox-cli ...')
    result = spawnSync('npx', ['--yes', 'apifox-cli', ...args, '--access-token', accessToken], {
      stdio: 'inherit',
      shell: true,
    })
  }
  return result.status === 0
}

const imports = []

if (!only || only === 'openapi') {
  imports.push({
    format: 'openapi',
    file: join(apiDir, 'linkx-openapi.json'),
    label: 'OpenAPI 全量接口定义',
  })
}

if (!only || only === 'full') {
  imports.push({
    format: 'postman',
    file: join(apiDir, 'linkx-full.postman_collection.json'),
    label: 'Postman 全量集合（含断言）',
  })
}

if (!only || only === 'smoke') {
  imports.push({
    format: 'postman',
    file: join(apiDir, 'linkx-smoke-scenarios.postman_collection.json'),
    label: '冒烟场景用例',
  })
}

if (skipOpenapi) {
  imports.splice(
    0,
    imports.length,
    ...imports.filter((i) => i.format !== 'openapi'),
  )
}

for (const item of imports) {
  if (!existsSync(item.file)) {
    console.error(`文件不存在: ${item.file}`)
    console.error('请先运行: node linkx-server/perf/k6/scripts/generate-postman-collection.mjs --all')
    process.exit(1)
  }
}

// 默认走 REST API（OpenAPI + Postman 含用例）
if (!only || only === 'openapi' || only === 'full' || only === 'smoke') {
  runRestImport()
} else {
  for (const item of imports) {
    console.log(`\n>>> CLI 导入 ${item.label}: ${item.file}`)
    const ok = runApifoxCli([
      'import',
      '--project',
      projectId,
      '--format',
      item.format,
      '--file',
      item.file,
    ])
    if (!ok) {
      console.error('Apifox CLI 导入失败，可改用: node docs/api/import-via-rest.mjs')
      process.exit(1)
    }
  }
}

console.log('\n全部导入完成。请在 Apifox 中：')
console.log('  1. 导入环境文件 docs/api/linkx-apifox-environment.json（项目 → 环境）')
console.log('  2. 填写 adminPassword / clientPassword 等变量')
console.log('  3. 在自动化测试中运行「LinkX Smoke Scenarios」')
