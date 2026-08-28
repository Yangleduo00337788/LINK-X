/**
 * 作者：yangleduo
 */
import { parseAgentAction } from '../types'
import { resolveChatSession } from '../sessionResolve'
import type { ChatSession } from '../../types'
import {
  ACTION_MATCH_BENCHMARK,
  ACTION_PARSE_BENCHMARK,
  BENCHMARK_MIN_PASS_RATE,
  SESSION_RESOLVE_BENCHMARK
} from './dataset'

export interface BenchmarkCaseResult {
  id: string
  description: string
  passed: boolean
  detail?: string
}

export interface BenchmarkReport {
  category: string
  total: number
  passed: number
  passRate: number
  results: BenchmarkCaseResult[]
}

export function evaluateSessionResolveBenchmark(sessions: ChatSession[]): BenchmarkReport {
  const results: BenchmarkCaseResult[] = []

  for (const caseItem of SESSION_RESOLVE_BENCHMARK) {
    const session = resolveChatSession(caseItem.args)
    const actualId = session?.id ?? null
    const passed = actualId === caseItem.expectedSessionId
    results.push({
      id: caseItem.id,
      description: caseItem.description,
      passed,
      detail: passed
        ? undefined
        : `expected ${caseItem.expectedSessionId}, got ${actualId}`
    })
  }

  const passed = results.filter(r => r.passed).length
  return {
    category: 'sessionResolve',
    total: results.length,
    passed,
    passRate: passed / results.length,
    results
  }
}

export function evaluateActionParseBenchmark(): BenchmarkReport {
  const results: BenchmarkCaseResult[] = []

  for (const caseItem of ACTION_PARSE_BENCHMARK) {
    const parsed = parseAgentAction(caseItem.raw)
    let passed = false
    let detail: string | undefined

    if (caseItem.expected === null) {
      passed = parsed === null
      if (!passed) detail = `expected null, got ${parsed?.name}`
    } else if (!parsed) {
      passed = false
      detail = 'expected action, got null'
    } else {
      passed =
        parsed.name === caseItem.expected.name &&
        JSON.stringify(parsed.arguments) === JSON.stringify(caseItem.expected.arguments)
      if (!passed) {
        detail = `got ${parsed.name} ${JSON.stringify(parsed.arguments)}`
      }
    }

    results.push({
      id: caseItem.id,
      description: caseItem.description,
      passed,
      detail
    })
  }

  const passedCount = results.filter(r => r.passed).length
  return {
    category: 'actionParse',
    total: results.length,
    passed: passedCount,
    passRate: passedCount / results.length,
    results
  }
}

export function evaluateActionMatchBenchmark(): BenchmarkReport {
  const results: BenchmarkCaseResult[] = []

  for (const caseItem of ACTION_MATCH_BENCHMARK) {
    const parsed = parseAgentAction({
      name: caseItem.name,
      arguments: caseItem.arguments
    })
    const missing = caseItem.requiredKeys.filter(
      key => !parsed || typeof parsed.arguments[key] !== 'string' || !String(parsed.arguments[key]).trim()
    )
    const passed = missing.length === 0
    results.push({
      id: caseItem.id,
      description: caseItem.description,
      passed,
      detail: passed ? undefined : `missing keys: ${missing.join(', ')}`
    })
  }

  const passedCount = results.filter(r => r.passed).length
  return {
    category: 'actionMatch',
    total: results.length,
    passed: passedCount,
    passRate: passedCount / results.length,
    results
  }
}

export function aggregateBenchmarkReports(reports: BenchmarkReport[]): {
  total: number
  passed: number
  passRate: number
  reports: BenchmarkReport[]
} {
  const total = reports.reduce((sum, r) => sum + r.total, 0)
  const passed = reports.reduce((sum, r) => sum + r.passed, 0)
  return {
    total,
    passed,
    passRate: total > 0 ? passed / total : 0,
    reports
  }
}

export function formatBenchmarkSummary(aggregate: ReturnType<typeof aggregateBenchmarkReports>): string {
  const lines = aggregate.reports.map(
    r => `${r.category}: ${r.passed}/${r.total} (${(r.passRate * 100).toFixed(1)}%)`
  )
  lines.push(
    `overall: ${aggregate.passed}/${aggregate.total} (${(aggregate.passRate * 100).toFixed(1)}%)`
  )
  return lines.join('\n')
}

export function assertBenchmarkPassRate(
  aggregate: ReturnType<typeof aggregateBenchmarkReports>,
  minRate = BENCHMARK_MIN_PASS_RATE
): void {
  if (aggregate.passRate < minRate) {
    const failed = aggregate.reports.flatMap(r => r.results.filter(item => !item.passed))
    throw new Error(
      `LinkMate Agent benchmark pass rate ${(aggregate.passRate * 100).toFixed(1)}% below ${(minRate * 100).toFixed(0)}%.\n` +
        failed.map(f => `[${f.id}] ${f.description}: ${f.detail ?? 'failed'}`).join('\n')
    )
  }
}
