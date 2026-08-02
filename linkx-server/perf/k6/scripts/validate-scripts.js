#!/usr/bin/env node
/** CI 静态校验：确保 k6 脚本与端点清单可解析（不启动服务） */
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const required = [
  'full-api.js',
  'scenarios/hot-path.js',
  'endpoints.sample.json',
  'lib/guards.js',
  'scripts/endpoint-test-matrix.mjs',
]

let failed = false
for (const rel of required) {
  const p = path.join(root, rel)
  if (!fs.existsSync(p)) {
    console.error(`MISSING: ${rel}`)
    failed = true
    continue
  }
  const text = fs.readFileSync(p, 'utf8')
  if (rel.endsWith('.json')) {
    JSON.parse(text)
    console.log(`OK json ${rel}`)
  } else if (rel.startsWith('lib/') || rel.startsWith('scripts/')) {
    if (
      !text.includes('export ') &&
      !text.includes('import ') &&
      !text.includes('module.exports') &&
      !text.includes('require(')
    ) {
      console.error(`INVALID helper script: ${rel}`)
      failed = true
    } else {
      console.log(`OK helper ${rel}`)
    }
  } else if (!text.includes('export const options') && !text.includes('export default')) {
    console.error(`INVALID k6 script (no export): ${rel}`)
    failed = true
  } else {
    console.log(`OK script ${rel}`)
  }
}

if (failed) process.exit(1)
console.log('k6 load-smoke static validation passed')
