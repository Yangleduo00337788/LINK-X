/**
 * 作者：yangleduo
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const getSessionMeta = vi.fn()
const setSessionMeta = vi.fn()
const upsertMessages = vi.fn()

vi.stubGlobal('window', {
  electronAPI: {
    chatDb: {
      getSessionMeta,
      setSessionMeta,
      upsertMessages,
      getLastId: vi.fn(),
      getRecent: vi.fn(),
      getBefore: vi.fn(),
      hasOlder: vi.fn(),
      clearAll: vi.fn(),
      migrateLegacy: vi.fn()
    }
  }
})

const { persistMessages, advanceLastSyncId } = await import('./chatMessageStore')

describe('advanceLastSyncId', () => {
  beforeEach(() => {
    getSessionMeta.mockReset()
    setSessionMeta.mockReset()
    upsertMessages.mockReset()
  })

  it('does not write when older page ids are below the cursor', async () => {
    getSessionMeta.mockResolvedValue({ lastSyncId: '1000' })
    await advanceLastSyncId('s1', [{ id: '800' }, { id: '849' }])
    expect(setSessionMeta).not.toHaveBeenCalled()
  })

  it('writes when incoming ids are newer', async () => {
    getSessionMeta.mockResolvedValue({ lastSyncId: '1000' })
    await advanceLastSyncId('s1', [{ id: '1001' }, { id: '1100' }])
    expect(setSessionMeta).toHaveBeenCalledWith('s1', { lastSyncId: '1100' })
  })
})

describe('persistMessages lastSyncId', () => {
  beforeEach(() => {
    getSessionMeta.mockReset()
    setSessionMeta.mockReset()
    upsertMessages.mockResolvedValue(undefined)
  })

  it('still upserts older history but does not rewind the cursor', async () => {
    getSessionMeta.mockResolvedValue({ lastSyncId: '5000' })
    await persistMessages('s1', [
      { id: '10', sessionId: 's1', content: 'old', time: '00:00', isSelf: false }
    ] as never)
    expect(upsertMessages).toHaveBeenCalled()
    expect(setSessionMeta).not.toHaveBeenCalled()
  })
})
