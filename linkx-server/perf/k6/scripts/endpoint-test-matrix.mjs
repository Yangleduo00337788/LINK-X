#!/usr/bin/env node
/**
 * Compare OpenAPI endpoints.json against server test sources for a crude
 * "success-path automation" matrix (B coverage).
 *
 * Heuristic: an endpoint is "covered" if any *Test*.java / *IT*.java under
 * src/test/java contains both the HTTP method hint and a path fragment
 * (e.g. "/auth/login", "/user/me").
 *
 * Usage:
 *   node scripts/endpoint-test-matrix.mjs
 *   node scripts/endpoint-test-matrix.mjs --json > matrix.json
 *   node scripts/endpoint-test-matrix.mjs --fail-under 0.3
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const k6Root = path.resolve(__dirname, '..')
const serverRoot = path.resolve(k6Root, '../..')
const testRoot = path.join(serverRoot, 'src/test/java')
const endpointsPath = path.join(k6Root, 'endpoints.json')

const args = process.argv.slice(2)
const asJson = args.includes('--json')
const failUnderIdx = args.indexOf('--fail-under')
const failUnder = failUnderIdx >= 0 ? Number(args[failUnderIdx + 1]) : null

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name)
    if (ent.isDirectory()) walk(p, out)
    else if (/\.(java)$/.test(ent.name) && /(Test|IT)\.java$/.test(ent.name)) out.push(p)
  }
  return out
}

function loadCorpus() {
  const files = walk(testRoot)
  return files.map((f) => fs.readFileSync(f, 'utf8')).join('\n')
}

function pathHints(p) {
  const hints = new Set()
  hints.add(p)
  // without leading slash variants already included
  const parts = p.split('/').filter(Boolean)
  if (parts.length >= 2) hints.add('/' + parts.slice(0, 2).join('/'))
  if (parts.length >= 3) hints.add('/' + parts.slice(0, 3).join('/'))
  // strip path params for matching
  hints.add(p.replace(/\{[^}]+\}/g, ''))
  return [...hints]
}

function methodHints(method) {
  const m = method.toUpperCase()
  return [
    m,
    m.toLowerCase(),
    `MockMvcRequestBuilders.${m.toLowerCase()}`,
    `.${m.toLowerCase()}(`,
    `"${m}`,
  ]
}

const catalog = JSON.parse(fs.readFileSync(endpointsPath, 'utf8'))
const corpus = loadCorpus()
const rows = []

for (const ep of catalog.endpoints || []) {
  const methods = methodHints(ep.method)
  const paths = pathHints(ep.path)
  const hasMethod = methods.some((h) => corpus.includes(h))
  // Prefer exact path string in tests
  const hasPath =
    corpus.includes(`"${ep.path}"`) ||
    corpus.includes(`'${ep.path}'`) ||
    corpus.includes(`("${ep.path}`) ||
    paths.some((h) => h.length > 3 && (corpus.includes(`"${h}"`) || corpus.includes(`'${h}'`)))
  const covered = hasMethod && hasPath
  rows.push({
    method: ep.method,
    path: ep.path,
    operationId: ep.operationId,
    mutating: !!ep.mutating,
    covered,
  })
}

const total = rows.length
const coveredN = rows.filter((r) => r.covered).length
const ratio = total ? coveredN / total : 0
const uncovered = rows.filter((r) => !r.covered)

if (asJson) {
  console.log(
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        total,
        covered: coveredN,
        ratio,
        uncovered: uncovered.map((r) => `${r.method} ${r.path}`),
        rows,
      },
      null,
      2,
    ),
  )
} else {
  console.log(`endpoint-test-matrix: covered=${coveredN}/${total} (${(ratio * 100).toFixed(1)}%)`)
  console.log(`uncovered=${uncovered.length} (showing up to 40)`)
  for (const r of uncovered.slice(0, 40)) {
    console.log(`  ${r.method} ${r.path}`)
  }
  if (uncovered.length > 40) console.log(`  ... +${uncovered.length - 40} more`)
}

if (failUnder != null && !Number.isNaN(failUnder) && ratio < failUnder) {
  console.error(`FAIL: coverage ratio ${ratio.toFixed(3)} < ${failUnder}`)
  process.exit(1)
}
