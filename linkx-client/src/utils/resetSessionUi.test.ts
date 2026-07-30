import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { cleanupNaiveUiOverlays, resetSessionUi } from './resetSessionUi'

const hangup = vi.fn(async () => undefined)
const cleanupLocal = vi.fn()

vi.mock('../stores/overlay', () => ({
  useOverlayStore: () => ({ closeAll: vi.fn() })
}))
vi.mock('../stores/settings', () => ({
  useSettingsStore: () => ({ closeSettings: vi.fn() })
}))
vi.mock('../stores/chatModals', () => ({
  useChatModalsStore: () => ({ closeAllModals: vi.fn() })
}))
vi.mock('../stores/call', () => ({
  useCallStore: () => ({ hangup })
}))
vi.mock('../stores/conference', () => ({
  useConferenceStore: () => ({
    cleanupLocal,
    phase: 'in_room'
  })
}))

describe('resetSessionUi', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = '<div class="n-modal-mask"></div>'
    hangup.mockClear()
    cleanupLocal.mockClear()
  })

  it('cleanupNaiveUiOverlays 移除残留层', () => {
    cleanupNaiveUiOverlays()
    expect(document.querySelector('.n-modal-mask')).toBeNull()
  })

  it('resetSessionUi 调用各 store 清理（含会议 WebRTC）', async () => {
    resetSessionUi()
    expect(hangup).toHaveBeenCalled()
    // 等动态 import('../stores/conference') 完成
    await vi.waitFor(() => {
      expect(cleanupLocal).toHaveBeenCalled()
    })
  })
})
