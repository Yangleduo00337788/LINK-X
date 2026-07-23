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
  listEvents,
  listEventsByDate,
  getEvent,
  createEvent,
  updateEvent,
  deleteEvent,
  fireReminder
} from './calendar'

describe('api/calendar', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listEvents 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listEvents()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('listEventsByDate 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listEventsByDate('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('getEvent 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getEvent('1')
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('createEvent 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await createEvent({} as any)
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('updateEvent 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updateEvent('1', {} as any)
    expect(apiClient.put).toHaveBeenCalled()
  })

  it('deleteEvent 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await deleteEvent('1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('fireReminder 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await fireReminder('1')
    expect(apiClient.post).toHaveBeenCalled()
  })

})
