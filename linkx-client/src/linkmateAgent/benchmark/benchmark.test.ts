/**
 * 作者：yangleduo
 */
import { describe, expect, it } from 'vitest'
import { STANDARD_SESSIONS } from '../test/fixtures'
import { setupAgentTestStores } from '../test/storeHarness'
import {
  aggregateBenchmarkReports,
  assertBenchmarkPassRate,
  evaluateActionMatchBenchmark,
  evaluateActionParseBenchmark,
  evaluateSessionResolveBenchmark,
  formatBenchmarkSummary
} from './evaluate'
import { BENCHMARK_MIN_PASS_RATE } from './dataset'

describe('linkmateAgent benchmark', () => {
  it('session resolve accuracy meets baseline', () => {
    setupAgentTestStores({ sessions: STANDARD_SESSIONS })
    const report = evaluateSessionResolveBenchmark(STANDARD_SESSIONS)
    expect(report.passRate).toBeGreaterThanOrEqual(BENCHMARK_MIN_PASS_RATE)
  })

  it('action parse accuracy meets baseline', () => {
    const report = evaluateActionParseBenchmark()
    expect(report.passRate).toBeGreaterThanOrEqual(BENCHMARK_MIN_PASS_RATE)
  })

  it('action parameter match meets baseline', () => {
    const report = evaluateActionMatchBenchmark()
    expect(report.passRate).toBeGreaterThanOrEqual(BENCHMARK_MIN_PASS_RATE)
  })

  it('overall agent precision baseline', () => {
    setupAgentTestStores({ sessions: STANDARD_SESSIONS })
    const aggregate = aggregateBenchmarkReports([
      evaluateSessionResolveBenchmark(STANDARD_SESSIONS),
      evaluateActionParseBenchmark(),
      evaluateActionMatchBenchmark()
    ])
    // 输出通过率供 CI 日志查看
    console.info('[linkmate-agent-benchmark]\n' + formatBenchmarkSummary(aggregate))
    assertBenchmarkPassRate(aggregate, BENCHMARK_MIN_PASS_RATE)
    expect(aggregate.passRate).toBeGreaterThanOrEqual(BENCHMARK_MIN_PASS_RATE)
  })
})
