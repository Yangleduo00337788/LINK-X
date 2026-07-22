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
  listNotes,
  createNote,
  updateNote,
  deleteNote,
  uploadNoteFile,
  resolveNoteMediaUrl
} from './note'

describe('api/note', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listNotes 应请求 GET /notes', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: [] })
    await listNotes()
    expect(apiClient.get).toHaveBeenCalledWith('/notes')
  })

  it('createNote / updateNote / deleteNote 应走对应方法', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ code: 200, data: {} })
    vi.mocked(apiClient.put).mockResolvedValue({ code: 200, data: {} })
    vi.mocked(apiClient.delete).mockResolvedValue({ code: 200, data: null })

    await createNote({ title: 't', content: 'c' })
    await updateNote('1', { content: 'c2' })
    await deleteNote('1')

    expect(apiClient.post).toHaveBeenCalledWith('/notes', { title: 't', content: 'c' })
    expect(apiClient.put).toHaveBeenCalledWith('/notes/1', { content: 'c2' })
    expect(apiClient.delete).toHaveBeenCalledWith('/notes/1')
  })

  it('uploadNoteFile 应提交 multipart', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      code: 200,
      data: { fileKey: 'k', url: 'https://x' }
    })
    const file = new File(['x'], 'a.png', { type: 'image/png' })
    await uploadNoteFile(file)

    expect(apiClient.post).toHaveBeenCalledWith(
      '/notes/upload',
      expect.any(FormData),
      expect.objectContaining({
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 120000
      })
    )
  })

  it('resolveNoteMediaUrl 应带 key 参数', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ code: 200, data: 'https://cdn/x' })
    await resolveNoteMediaUrl('2026/a.png')
    expect(apiClient.get).toHaveBeenCalledWith('/notes/media-url', {
      params: { key: '2026/a.png' }
    })
  })
})
