#!/usr/bin/env node
/**
 * 从 OpenAPI JSON 生成全端点清单 endpoints.json
 * 用法: node generate-full-api.js [openapi.json]
 */
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const input = process.argv[2] || path.join(root, 'openapi.json')
const output = path.join(root, 'endpoints.json')

if (!fs.existsSync(input)) {
  console.error(`OpenAPI file not found: ${input}`)
  console.error('Run export-openapi.sh against a running server, or place openapi.json under perf/k6/')
  process.exit(1)
}

const spec = JSON.parse(fs.readFileSync(input, 'utf8'))
const paths = spec.paths || {}
const endpoints = []

for (const [rawPath, methods] of Object.entries(paths)) {
  for (const [method, op] of Object.entries(methods)) {
    const m = method.toUpperCase()
    if (!['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'].includes(m)) continue
    endpoints.push({
      method: m,
      path: rawPath,
      operationId: op.operationId || `${m} ${rawPath}`,
      tags: op.tags || [],
      // 写操作在全扫时默认降权 / 可跳过
      mutating: !['GET', 'HEAD', 'OPTIONS'].includes(m),
      security: Array.isArray(op.security) ? op.security.length > 0 : true,
    })
  }
}

endpoints.sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method))

const payload = {
  generatedAt: new Date().toISOString(),
  source: path.basename(input),
  count: endpoints.length,
  endpoints,
}

fs.writeFileSync(output, JSON.stringify(payload, null, 2))
console.log(`Wrote ${output} (${endpoints.length} endpoints)`)
