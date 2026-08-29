/**
 * 作者：yangleduo
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { runStartupVersionFlow, resetStartupVersionFlowForTests } from './startupVersion'
import * as versionApi from '../api/version'

vi.mock('../api/version', () => ({
  checkUpdate: vi.fn()
}))

vi.mock('./appUpdate', () => ({
  runStartupAutoUpdate: vi.fn()
}))

const ctx = {
  message: { loading: vi.fn(), destroyAll: vi.fn(), error: vi.fn(), info: vi.fn() },
  dialog: { info: vi.fn(), warning: vi.fn() },
  t: (key: string) => key
} as never

describe('runStartupVersionFlow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    resetStartupVersionFlowForTests()
  })

  it('triggers auto update when server reports hasUpdate', async () => {
    const { runStartupAutoUpdate } = await import('./appUpdate')
    vi.mocked(versionApi.checkUpdate).mockResolvedValue({
      code: 200,
      data: {
        version: '1.0.2',
        currentVersion: '1.0.1',
        hasUpdate: true,
        forceUpdate: false,
        channel: 'stable',
        releaseNotes: 'new',
        downloadUrl: 'https://example.com/a.exe'
      }
    } as never)

    const result = await runStartupVersionFlow(ctx)
    expect(runStartupAutoUpdate).toHaveBeenCalledOnce()
    expect(result.whatsNew.show).toBe(false)
  })

  it('returns whats new when already on latest', async () => {
    const { runStartupAutoUpdate } = await import('./appUpdate')
    vi.mocked(versionApi.checkUpdate).mockResolvedValue({
      code: 200,
      data: {
        version: '1.0.1',
        currentVersion: '1.0.1',
        hasUpdate: false,
        forceUpdate: false,
        channel: 'stable',
        releaseNotes: 'latest',
        currentReleaseNotes: '本次更新内容'
      }
    } as never)

    const result = await runStartupVersionFlow(ctx)
    expect(runStartupAutoUpdate).not.toHaveBeenCalled()
    expect(result.whatsNew).toEqual({ show: true, notes: '本次更新内容' })
  })
})
