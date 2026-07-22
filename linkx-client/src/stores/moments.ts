/**
 * 朋友圈（Moments）Store
 * 管理动态帖子、点赞、评论及操作栏 UI 状态
 */

import { defineStore } from 'pinia'
import * as momentsApi from '../api/moments'
import { useAppStore } from './app'
import { useContactsStore } from './contacts'
import { normalizeMediaUrl, stripEphemeralMediaUrl } from '../utils/mediaUrl'

/** 单条评论 */
export interface MomentComment {
  id: string     // 评论 id
  userId: string // 评论者 ID
  user: string   // 评论者昵称
  avatar?: string // 评论者头像
  content: string // 评论正文
  /** 被 @ 的用户 ID 列表 */
  mentions?: number[]
}

/** 单条朋友圈动态 */
export interface MomentPost {
  id: string              // 动态 id
  userId: string          // 发布者 ID
  user: string            // 发布者昵称
  avatar: string          // 头像 URL
  content: string         // 文字内容
  images?: string[]       // 可选图片 URL 列表
  location?: string       // 所在位置
  atUsers?: string        // 提醒谁看，用户 ID 列表（JSON 字符串）
  atUserNames?: string[]  // 提醒谁看的昵称列表
  visibility?: number     // 可见性：0=公开，1=仅好友，2=私密
  time: string            // 时间戳
  likes: number           // 点赞数
  liked: boolean          // 当前用户是否已赞
  likedBy: string[]       // 点赞用户昵称列表
  comments: MomentComment[] // 评论列表
}

function mapPost(p: momentsApi.MomentsPost): MomentPost {
  const userId = String(p.userId)
  return {
    id: String(p.id),
    userId,
    user: p.nickname || '用户',
    avatar: resolveAuthorAvatar(userId, p.avatar),
    content: p.content,
    images: (p.images || []).map(url => toDisplayableMediaUrl(url)).filter(Boolean),
    location: p.location,
    atUsers: p.atUsers,
    atUserNames: Array.isArray(p.atUserNames) ? p.atUserNames : undefined,
    visibility: p.visibility,
    time: p.time,
    likes: p.likes ?? 0,
    liked: !!p.liked,
    likedBy: Array.isArray(p.likedBy) ? p.likedBy : [],
    comments: (p.comments || []).map(c => ({
      id: String(c.id),
      userId: String(c.userId),
      user: c.nickname || '用户',
      avatar: resolveAuthorAvatar(String(c.userId), c.avatar),
      content: c.content,
      mentions: Array.isArray(c.mentions) ? c.mentions : undefined
    }))
  }
}

/** 仅保留浏览器可直接加载的地址；object key（如 2026/07/18/a.png）视为无效，避免裂图 */
function toDisplayableMediaUrl(raw?: string | null): string {
  const url = normalizeMediaUrl(raw)
  if (!url) return ''
  if (
    /^https?:\/\//i.test(url) ||
    url.startsWith('/') ||
    url.startsWith('data:') ||
    url.startsWith('blob:')
  ) {
    return url
  }
  return ''
}

/**
 * 解析作者头像：接口签发 URL 优先；缺失时回退到本人资料 / 通讯录缓存。
 * 避免接口偶发空头像或本地脏持久化导致长期显示文字占位。
 */
function resolveAuthorAvatar(userId: string, apiAvatar?: string | null): string {
  const fromApi = toDisplayableMediaUrl(apiAvatar)
  if (fromApi) return fromApi

  const appStore = useAppStore()
  if (String(appStore.userProfile.userId || '') === String(userId)) {
    const mine = toDisplayableMediaUrl(appStore.userProfile.avatar)
    if (mine) return mine
  }

  try {
    const contacts = useContactsStore()
    const friend = contacts.items.find(c => String(c.userId ?? c.id) === String(userId))
    return toDisplayableMediaUrl(friend?.avatarUrl)
  } catch {
    return ''
  }
}

/** 持久化前去掉预签名/本机 MinIO 媒体，避免过期裂图污染本地缓存 */
function stripMediaForPersist(posts: MomentPost[]): MomentPost[] {
  return posts.map(p => ({
    ...p,
    avatar: stripEphemeralMediaUrl(p.avatar),
    images: (p.images || [])
      .map(url => stripEphemeralMediaUrl(url))
      .filter(Boolean),
    comments: (p.comments || []).map(c => ({
      ...c,
      avatar: c.avatar ? stripEphemeralMediaUrl(c.avatar) || undefined : undefined
    }))
  }))
}

// 定义并导出 moments Store
export const useMomentsStore = defineStore('moments', {
  // 初始状态
  state: () => ({
    posts: [] as MomentPost[],                               // 动态列表（从后端加载）
    uiShowActions: {} as Record<string, boolean>,         // 每条动态是否展开点赞/评论操作栏
    initialized: false                                     // 是否已从后端加载
  }),

  actions: {
    /** 从后端加载朋友圈动态 */
    async fetchMoments() {
      try {
        const res = await momentsApi.listMoments()
        if (res.code === 200 && res.data) {
          this.posts = res.data.map(mapPost)
          this.initialized = true
        }
      } catch (e) {
        console.error('加载朋友圈失败:', e)
      }
    },

    /** 从后端加载指定用户的动态 */
    async fetchUserMoments(userId: string) {
      try {
        const res = await momentsApi.getUserMoments(userId)
        if (res.code === 200 && res.data) {
          return res.data.map(mapPost)
        }
      } catch (e) {
        console.error('加载用户动态失败:', e)
      }
      return []
    },

    /** 发布新动态 */
    async addPost(
      content: string,
      images?: string[],
      options?: {
        location?: string
        atUsers?: number[]
        visibility?: number
      }
    ) {
      const appStore = useAppStore()
      try {
        console.log('[Moments] 发布动态:', { content, imagesCount: images?.length, options })
        const res = await momentsApi.publishMoments({
          content,
          images,
          location: options?.location,
          atUsers: options?.atUsers,
          visibility: options?.visibility
        })
        console.log('[Moments] 发布结果:', res)
        if (res.code === 200 && res.data) {
          const mapped = mapPost(res.data)
          // 后端未返回头像时，回退到当前登录用户头像
          if (!mapped.avatar) {
            mapped.avatar = toDisplayableMediaUrl(appStore.userProfile.avatar)
          }
          if (!mapped.user || mapped.user === '用户') {
            mapped.user = appStore.userProfile.nickname || '我'
          }
          mapped.time = '刚刚'
          this.posts.unshift(mapped)
          return true
        }
        console.error('[Moments] 发布失败:', res.message)
      } catch (e) {
        console.error('[Moments] 发布异常:', e)
      }
      return false
    },

    /** 切换点赞状态（允许赞自己的动态） */
    async toggleLike(postId: string) {
      const id = String(postId)
      const post = this.posts.find(p => String(p.id) === id)
      if (!post) return false
      const appStore = useAppStore()
      const userName = appStore.userProfile.nickname || '我'
      if (!Array.isArray(post.likedBy)) post.likedBy = []

      const wasLiked = post.liked
      try {
        const res = wasLiked
          ? await momentsApi.unlikeMoments(id)
          : await momentsApi.likeMoments(id)
        if (res.code !== 200) {
          console.error('点赞操作失败:', res.message)
          return false
        }
        post.liked = !wasLiked
        if (post.liked) {
          if (!post.likedBy.includes(userName)) {
            post.likedBy.push(userName)
          }
        } else {
          post.likedBy = post.likedBy.filter(n => n !== userName)
        }
        post.likes = post.likedBy.length
        this.uiShowActions[id] = false
        return true
      } catch (e) {
        console.error('点赞操作失败:', e)
        return false
      }
    },

    /** 添加评论（支持 mentions 列表） */
    async addComment(postId: string, content: string, mentions: Array<string | number> = []) {
      const post = this.posts.find(p => String(p.id) === String(postId))
      if (!post || !content.trim()) return false
      const appStore = useAppStore()

      try {
        const res = await momentsApi.commentMoments(postId, {
          content: content.trim(),
          mentions: mentions.length ? mentions : undefined
        })
        if (res.code === 200 && res.data) {
          const c = res.data
          const myAvatar = toDisplayableMediaUrl(appStore.userProfile.avatar)
          post.comments.push({
            id: String(c.id),
            userId: String(c.userId),
            user: c.nickname || appStore.userProfile.nickname,
            avatar: myAvatar,
            content: c.content,
            mentions: Array.isArray(c.mentions) ? c.mentions : undefined
          })
          this.uiShowActions[postId] = false
          return true
        }
      } catch (e) {
        console.error('评论失败:', e)
      }
      return false
    },

    /** 删除评论 */
    async deleteComment(postId: string, commentId: string) {
      try {
        const res = await momentsApi.deleteComment(commentId)
        if (res.code === 200) {
          const post = this.posts.find(p => p.id === postId)
          if (post) {
            post.comments = post.comments.filter(c => c.id !== commentId)
          }
          return true
        }
      } catch (e) {
        console.error('删除评论失败:', e)
      }
      return false
    },

    /** 删除动态 */
    async removePost(postId: string) {
      try {
        const res = await momentsApi.deleteMoments(postId)
        if (res.code === 200) {
          this.posts = this.posts.filter(p => p.id !== postId)
          return true
        }
      } catch (e) {
        console.error('删除动态失败:', e)
      }
      return false
    },

    /** 切换操作栏显示 */
    toggleActions(postId: string) {
      this.uiShowActions[postId] = !this.uiShowActions[postId]
    },

    /** 查询操作栏状态 */
    isActionsOpen(postId: string) {
      return !!this.uiShowActions[postId]
    }
  },

  // 持久化帖子文案等；预签名头像/图片不落盘，防止过期裂图污染本地缓存
  persist: {
    key: 'linkx-moments',
    paths: ['posts'],
    serializer: {
      serialize: (value: { posts?: MomentPost[] }) =>
        JSON.stringify({
          ...value,
          posts: stripMediaForPersist(Array.isArray(value?.posts) ? value.posts : [])
        }),
      deserialize: (value: string) => {
        const parsed = JSON.parse(value) as { posts?: MomentPost[] }
        return {
          ...parsed,
          posts: stripMediaForPersist(Array.isArray(parsed?.posts) ? parsed.posts : [])
        }
      }
    }
  }
})
