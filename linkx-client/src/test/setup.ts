import { config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, vi } from 'vitest'

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual<typeof import('naive-ui')>('naive-ui')
  const createApi = () => ({
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    loading: vi.fn(),
    destroyAll: vi.fn(),
  })
  return {
    ...actual,
    useMessage: vi.fn(createApi),
    useDialog: vi.fn(createApi),
    useNotification: vi.fn(createApi),
  }
})

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

config.global.config = {
  warnHandler(message) {
    if (message.includes('Missing required prop') || message.includes('Invalid prop')) return
    console.warn(message)
  },
}

// Naive UI / 重型子组件默认 stub，避免组件测被第三方拖垮
config.global.stubs = {
  teleport: true,
  Transition: false,
  TransitionGroup: false,
  'n-button': true,
  'n-input': true,
  'n-modal': true,
  'n-dropdown': true,
  'n-popover': true,
  'n-tooltip': true,
  'n-avatar': true,
  'n-icon': true,
  'n-spin': true,
  'n-empty': true,
  'n-scrollbar': true,
  'n-tabs': true,
  'n-tab-pane': true,
  'n-form': true,
  'n-form-item': true,
  'n-select': true,
  'n-switch': true,
  'n-checkbox': true,
  'n-radio': true,
  'n-radio-group': true,
  'n-upload': true,
  'n-drawer': true,
  'n-card': true,
  'n-space': true,
  'n-tag': true,
  'n-divider': true,
  'n-list': true,
  'n-list-item': true,
  'n-menu': true,
  'n-badge': true,
  'n-progress': true,
  'n-slider': true,
  'n-date-picker': true,
  'n-time-picker': true,
  'n-color-picker': true,
  'n-config-provider': true,
  'n-message-provider': true,
  'n-dialog-provider': true,
  'n-notification-provider': true,
  RouterLink: true,
  RouterView: true,
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  localStorage.clear()
  sessionStorage.clear()
})
