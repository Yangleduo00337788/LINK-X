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
  uploadChatFileSmart,
  checkFileHash,
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

  it('checkFileHash 应调用秒传接口', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: { exists: false } } as any)
    await checkFileHash({ hash: 'a'.repeat(64), fileName: 'a.bin' })
    expect(apiClient.post).toHaveBeenCalledWith('/chat/upload/check-hash', expect.any(Object))
  })

  it('uploadChatFileSmart 秒传命中时应跳过实际上传', async () => {
    const digest = vi.spyOn(crypto.subtle, 'digest').mockResolvedValue(new Uint8Array(32).buffer)
    vi.mocked(apiClient.post).mockResolvedValue({
      code: 200,
      data: { exists: true, url: 'https://cdn/x', objectKey: 'k', fileName: 'a.bin', fileSize: 1 }
    } as any)
    const res = await uploadChatFileSmart('1', new File(['x'], 'a.bin', { type: 'application/octet-stream' }))
    expect(res.data?.url).toBe('https://cdn/x')
    expect(apiClient.post).toHaveBeenCalledTimes(1)
    digest.mockRestore()
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
