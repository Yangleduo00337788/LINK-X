import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useMomentsStore } from './moments'

vi.mock('../api/moments', () => ({
  listMoments: vi.fn(),
  getUserMoments: vi.fn(),
  publishMoments: vi.fn(),
  updateMoments: vi.fn(),
  deleteMoments: vi.fn(),
  likeMoments: vi.fn(),
  unlikeMoments: vi.fn(),
  commentMoments: vi.fn(),
  deleteComment: vi.fn(),
  uploadMomentsMedia: vi.fn()
}))

vi.mock('./app', () => ({
  useAppStore: () => ({
    userProfile: {
      userId: '1',
      nickname: '测试用户',
      avatar: 'https://example.com/me.png'
    }
  })
}))

vi.mock('./contacts', () => ({
  useContactsStore: () => ({
    items: []
  })
}))

import * as momentsApi from '../api/moments'

const samplePost = {
  id: '10',
  userId: '1',
  nickname: '测试用户',
  avatar: 'https://example.com/me.png',
  content: '第一条',
  images: [],
  time: '12:00',
  likes: 0,
  liked: false,
  likedBy: [] as string[],
  comments: [] as Array<Record<string, unknown>>
}

describe('useMomentsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchMoments 应填充帖子并设置 hasMore', async () => {
    vi.mocked(momentsApi.listMoments).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: Array.from({ length: 20 }, (_, i) => ({
        ...samplePost,
        id: String(i + 1),
        content: `post-${i + 1}`
      }))
    })

    const store = useMomentsStore()
    await store.fetchMoments({ q: 'hello' })

    expect(momentsApi.listMoments).toHaveBeenCalledWith({ limit: 20, q: 'hello' })
    expect(store.posts).toHaveLength(20)
    expect(store.hasMore).toBe(true)
    expect(store.lastQuery).toBe('hello')
    expect(store.initialized).toBe(true)
  })

  it('loadMoreMoments 应按 beforeId 追加且去重', async () => {
    const store = useMomentsStore()
    store.posts = [{
      id: '20',
      userId: '1',
      user: '测试用户',
      avatar: '',
      content: '最新',
      time: '1',
      likes: 0,
      liked: false,
      likedBy: [],
      comments: []
    }]
    store.hasMore = true
    store.lastQuery = 'kw'

    vi.mocked(momentsApi.listMoments).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [
        { ...samplePost, id: '20', content: '重复' },
        { ...samplePost, id: '19', content: '更早' }
      ]
    })

    await store.loadMoreMoments()

    expect(momentsApi.listMoments).toHaveBeenCalledWith({
      beforeId: '20',
      limit: 20,
      q: 'kw'
    })
    expect(store.posts.map(p => p.id)).toEqual(['20', '19'])
    expect(store.hasMore).toBe(false)
    expect(store.loadingMore).toBe(false)
  })

  it('updatePost 应替换本地帖子', async () => {
    const store = useMomentsStore()
    store.posts = [{
      id: '10',
      userId: '1',
      user: '测试用户',
      avatar: '',
      content: '旧',
      time: '1',
      likes: 0,
      liked: false,
      likedBy: [],
      comments: []
    }]

    vi.mocked(momentsApi.updateMoments).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { ...samplePost, content: '新内容' }
    })

    const ok = await store.updatePost('10', { content: '新内容' })
    expect(ok).toBe(true)
    expect(store.posts[0].content).toBe('新内容')
  })

  it('addComment 应支持 parentId 嵌套回复', async () => {
    const store = useMomentsStore()
    store.posts = [{
      id: '10',
      userId: '1',
      user: '测试用户',
      avatar: '',
      content: '帖',
      time: '1',
      likes: 0,
      liked: false,
      likedBy: [],
      comments: []
    }]

    vi.mocked(momentsApi.commentMoments).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        id: '99',
        userId: '1',
        nickname: '测试用户',
        content: '回复你',
        time: 'now',
        parentId: '88',
        replyToNickname: '对方'
      }
    })

    const ok = await store.addComment('10', '回复你', [], '88')
    expect(ok).toBe(true)
    expect(momentsApi.commentMoments).toHaveBeenCalledWith('10', {
      content: '回复你',
      mentions: undefined,
      parentId: '88'
    })
    expect(store.posts[0].comments[0]).toMatchObject({
      id: '99',
      parentId: '88',
      replyToNickname: '对方'
    })
  })

  it('toggleLike 应从 false 切到 true', async () => {
    const store = useMomentsStore()
    store.posts = [{
      id: '10',
      userId: '1',
      user: '测试用户',
      avatar: '',
      content: '帖',
      time: '1',
      likes: 0,
      liked: false,
      likedBy: [],
      comments: []
    }]

    vi.mocked(momentsApi.likeMoments).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: null
    })

    const ok = await store.toggleLike('10')
    expect(ok).toBe(true)
    expect(store.posts[0].liked).toBe(true)
    expect(store.posts[0].likedBy).toContain('测试用户')
  })
})
