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
  listFavorites,
  getFavoriteStorage,
  listFavoriteTags,
  createFavoriteTag,
  deleteFavoriteTag,
  addFavorite,
  removeFavorite,
  updateFavorite
} from './favorite'

describe('api/favorite', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listFavorites 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listFavorites()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('getFavoriteStorage 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await getFavoriteStorage()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('listFavoriteTags 应调用 apiClient', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: null } as any)
    await listFavoriteTags()
    expect(apiClient.get).toHaveBeenCalled()
  })

  it('createFavoriteTag 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await createFavoriteTag()
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('deleteFavoriteTag 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await deleteFavoriteTag('1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('addFavorite 应调用 apiClient', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: null } as any)
    await addFavorite()
    expect(apiClient.post).toHaveBeenCalled()
  })

  it('removeFavorite 应调用 apiClient', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null } as any)
    await removeFavorite('1')
    expect(apiClient.delete).toHaveBeenCalled()
  })

  it('updateFavorite 应调用 apiClient', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: null } as any)
    await updateFavorite('1')
    expect(apiClient.put).toHaveBeenCalled()
  })

})
