import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { cleanupNaiveUiOverlays, resetSessionUi } from './resetSessionUi'

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
  useCallStore: () => ({ hangup: vi.fn(async () => undefined) })
}))

describe('resetSessionUi', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = '<div class="n-modal-mask"></div>'
  })

  it('cleanupNaiveUiOverlays 移除残留层', () => {
    cleanupNaiveUiOverlays()
    expect(document.querySelector('.n-modal-mask')).toBeNull()
  })

  it('resetSessionUi 调用各 store 清理', () => {
    expect(() => resetSessionUi()).not.toThrow()
  })
})
