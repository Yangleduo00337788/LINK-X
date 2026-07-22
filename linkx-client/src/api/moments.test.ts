import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import { apiClient } from './client'
import {
  listMoments,
  updateMoments,
  commentMoments,
  uploadMomentsMedia
} from './moments'

describe('api/moments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listMoments 应传递分页与搜索参数', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: [] })
    await listMoments({ beforeId: '9', limit: 20, q: 'kw' })
    expect(apiClient.get).toHaveBeenCalledWith('/moments', {
      params: { beforeId: '9', limit: 20, q: 'kw' }
    })
  })

  it('updateMoments 应 PUT 到指定动态', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: {} })
    await updateMoments('12', { content: '新' })
    expect(apiClient.put).toHaveBeenCalledWith('/moments/12', { content: '新' })
  })

  it('commentMoments 应携带 parentId', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: {} })
    await commentMoments('12', { content: '回复', parentId: '3' })
    expect(apiClient.post).toHaveBeenCalledWith('/moments/12/comment', {
      content: '回复',
      parentId: '3'
    })
  })

  it('uploadMomentsMedia 应提交 multipart', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: 'key' })
    const file = new File(['x'], 'v.mp4', { type: 'video/mp4' })
    await uploadMomentsMedia(file)
    expect(apiClient.post).toHaveBeenCalledWith(
      '/moments/upload',
      expect.any(FormData),
      expect.objectContaining({ timeout: 120000 })
    )
  })
})
