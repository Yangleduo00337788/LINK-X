#!/usr/bin/env node
/**
 * 通过 Apifox 开放 REST API 导入（绕过 CLI Automation 限制）
 * 用法：APIFOX_PROJECT_ID=... APIFOX_ACCESS_TOKEN=... node docs/api/import-via-rest.mjs
 */
import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const apiDir = join(__dirname)
const projectId = process.env.APIFOX_PROJECT_ID
const token = process.env.APIFOX_ACCESS_TOKEN

if (!projectId || !token) {
  console.error('需要 APIFOX_PROJECT_ID 和 APIFOX_ACCESS_TOKEN')
  process.exit(1)
}

const API_BASE = 'https://api.apifox.com'
const API_VERSION = '2024-03-28'

async function apifoxPost(path, body) {
  const res = await fetch(`${API_BASE}${path}`, {
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
    json = { raw: text }
  }
  if (!res.ok) {
    console.error(`HTTP ${res.status}`, JSON.stringify(json, null, 2))
    throw new Error(`Apifox API failed: ${path}`)
  }
  return json
}

function readJson(path) {
  return readFileSync(path, 'utf8')
}

async function importOpenApi() {
  const file = join(apiDir, 'linkx-openapi.json')
  console.log('>>> 导入 OpenAPI:', file)
  const result = await apifoxPost(`/v1/projects/${projectId}/import-openapi`, {
    input: readJson(file),
    options: {
      endpointOverwriteBehavior: 'OVERWRITE_EXISTING',
      schemaOverwriteBehavior: 'OVERWRITE_EXISTING',
      updateFolderOfChangedEndpoint: true,
    },
  })
  console.log('OpenAPI counters:', result?.data?.counters)
  if (result?.data?.errors?.length) console.warn('errors:', result.data.errors)
  return result
}

async function importPostman(label, file, extraOptions = {}) {
  console.log(`>>> 导入 Postman (${label}):`, file)
  const collection = readJson(file)
  const result = await apifoxPost(`/v1/projects/${projectId}/import-postman-collection`, {
    input: collection,
    options: {
      endpointOverwriteBehavior: 'OVERWRITE_EXISTING',
      endpointCaseOverwriteBehavior: 'OVERWRITE_EXISTING',
      updateFolderOfChangedEndpoint: true,
      ...extraOptions,
    },
  })
  console.log(`${label} counters:`, result?.data?.counters)
  if (result?.data?.errors?.length) console.warn('errors:', result.data.errors)
  return result
}

try {
  await importOpenApi()
  await importPostman('full', join(apiDir, 'linkx-full.postman_collection.json'))
  await importPostman('smoke', join(apiDir, 'linkx-smoke-scenarios.postman_collection.json'))
  console.log('\n全部导入完成。请在 Apifox LinkX 项目中查看接口与用例。')
} catch (e) {
  console.error(e.message)
  process.exit(1)
}
