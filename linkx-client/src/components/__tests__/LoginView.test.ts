/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { validateUsername, validatePassword } from '@/utils/validation'

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(async () => ({ code: 200, data: null })),
    post: vi.fn(async () => ({ code: 200, data: null })),
    put: vi.fn(async () => ({ code: 200, data: null })),
    delete: vi.fn(async () => ({ code: 200, data: null }))
  }
}))

describe('LoginView 相关逻辑', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('validation 工具与登录页共用规则', () => {
    expect(validateUsername('')).toBeTruthy()
    expect(validateUsername('user_01')).toBeNull()
    expect(validatePassword('short')).toBeTruthy()
    expect(validatePassword('pass1234', true)).toBeNull()
  })

  it('可异步导入 LoginView 模块', async () => {
    const mod = await import('@/components/LoginView.vue')
    expect(mod.default).toBeTruthy()
  }, 15000)
})
