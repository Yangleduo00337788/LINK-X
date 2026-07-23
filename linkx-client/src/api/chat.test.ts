import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn()
  }
}))

import { apiClient } from './client'
import {
  listSessions,
  openPrivateChat,
  listMessages,
  uploadChatFile,
  searchMessages,
  recallMessage
} from './chat'

describe('api/chat', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listSessions 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listSessions()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('openPrivateChat 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await openPrivateChat('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('listMessages 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listMessages('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('uploadChatFile 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await uploadChatFile('1', new File(['x'], 'a.bin'))
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('searchMessages 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await searchMessages('x')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('recallMessage 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await recallMessage('1', '1')
    expect(apiClient.post).toHaveBeenCalled()
  })

})
