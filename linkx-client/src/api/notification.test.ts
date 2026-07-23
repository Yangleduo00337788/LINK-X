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
  listUnreadNotifications,
  listAllNotifications,
  listMineNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  deleteNotification,
  clearAllNotifications
} from './notification'

describe('api/notification', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listUnreadNotifications 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listUnreadNotifications()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('listAllNotifications 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listAllNotifications()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('listMineNotifications 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listMineNotifications()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('getUnreadCount 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getUnreadCount()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('markAsRead 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await markAsRead(10)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('markAllAsRead 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await markAllAsRead()
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('deleteNotification 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await deleteNotification(10)
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('clearAllNotifications 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await clearAllNotifications()
    expect(apiClient.delete).toHaveBeenCalled()
  })

})
