/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import {
  commentShortVideo,
  deleteShortVideo,
  deleteShortVideoComment,
  likeShortVideoComment,
  unlikeShortVideoComment,
  followShortVideoAuthor,
  getShortVideo,
  likeShortVideo,
  favoriteShortVideo,
  unfavoriteShortVideo,
  listFollowingShortVideos,
  listFriendsShortVideos,
  listFavoriteShortVideos,
  listLikedShortVideos,
  listShortVideoComments,
  listShortVideos,
  listUserShortVideos,
  publishShortVideo,
  recordShortVideoPlay,
  recordShortVideoShare,
  unfollowShortVideoAuthor,
  unlikeShortVideo,
  updateShortVideo,
  uploadShortVideoMedia,
  type ShortVideoPost
} from '../api/shortVideo'
import { useAppStore } from './app'
import { useExtensionDockStore } from './extensionDock'
import { t } from '../i18n'
import { isEncryptedShortVideoText } from '../utils/shortVideoText'
import { captureVideoCover } from '../utils/shortVideoCover'

export type ShortVideoFeedTab = 'following' | 'friends' | 'recommend'
export type ShortVideoPanelTabId = 'main'

let ensurePanelReadyTask: Promise<void> | null = null
let fetchFeedTask: Promise<void> | null = null
const playedSessionKeys = new Set<string>()
const sharedSessionKeys = new Set<string>()

function assertApiOk(res: { code?: number; message?: string }, fallback: string) {
  if (res.code !== 200) {
    throw new Error(res.message || fallback)
  }
}

function normalizeShortVideoList(data: unknown): ShortVideoPost[] {
  if (!Array.isArray(data)) return []
  return data.map(item => {
    const post = item as ShortVideoPost
    return {
      ...post,
      comments: Array.isArray(post.comments) ? post.comments : [],
      favorites: typeof post.favorites === 'number' ? post.favorites : 0,
      favorited: Boolean(post.favorited),
      shares: typeof post.shares === 'number' ? post.shares : 0,
      commentCount:
        typeof post.commentCount === 'number'
          ? post.commentCount
          : Array.isArray(post.comments)
            ? post.comments.length
            : 0
    }
  })
}

function normalizeCommentCount(post: ShortVideoPost): number {
  if (typeof post.commentCount === 'number') return post.commentCount
  return Array.isArray(post.comments) ? post.comments.length : 0
}

function forEachPostRef(
  lists: ShortVideoPost[][],
  postId: string,
  fn: (post: ShortVideoPost) => void
) {
  for (const list of lists) {
    const post = list.find(p => p.id === postId)
    if (post) fn(post)
  }
}

export const useShortVideoStore = defineStore('shortVideo', {
  state: () => ({
    feedTab: 'recommend' as ShortVideoFeedTab,
    posts: [] as ShortVideoPost[],
    loading: false,
    publishing: false,
    activeIndex: 0,
    hasMore: true,
    initialized: false,
    openTabIds: [] as ShortVideoPanelTabId[],
    activeTabId: '' as ShortVideoPanelTabId | '',
    myPosts: [] as ShortVideoPost[],
    myPostsLoading: false,
    authorPosts: [] as ShortVideoPost[],
    authorPostsLoading: false,
    authorProfile: null as { userId: string; nickname?: string; avatar?: string } | null,
    favoritePosts: [] as ShortVideoPost[],
    favoritePostsLoading: false,
    likedPosts: [] as ShortVideoPost[],
    likedPostsLoading: false,
    feedError: '' as string
  }),

  getters: {
    openTabs(state): Array<{ id: ShortVideoPanelTabId; title: string }> {
      return state.openTabIds.map(id => ({
        id,
        title: t('nav.shortVideo')
      }))
    }
  },

  actions: {
    async ensureAuthReady() {
      const appStore = useAppStore()
      if (appStore.isLoggedIn && appStore.userProfile.userId) return
      if (!appStore.authInitializing) {
        await appStore.tryAutoLogin()
      }
      const deadline = Date.now() + 15000
      while (appStore.authInitializing && Date.now() < deadline) {
        await new Promise<void>(resolve => setTimeout(resolve, 50))
      }
    },

    registerOpenTab(tabId: ShortVideoPanelTabId) {
      if (!tabId || this.openTabIds.includes(tabId)) return
      this.openTabIds.push(tabId)
    },

    async ensurePanelReady() {
      if (ensurePanelReadyTask) return ensurePanelReadyTask

      ensurePanelReadyTask = (async () => {
        await this.ensureAuthReady()
        if (this.openTabIds.length === 0) {
          this.registerOpenTab('main')
          this.activeTabId = 'main'
        } else if (!this.activeTabId) {
          this.activeTabId = this.openTabIds[this.openTabIds.length - 1]
        }
        if (!this.initialized || this.posts.length === 0) {
          await this.fetchFeed(true)
        }
      })().finally(() => {
        ensurePanelReadyTask = null
      })

      return ensurePanelReadyTask
    },

    openPanel() {
      useAppStore().setNav('chat')
      if (!this.openTabIds.length) {
        this.registerOpenTab('main')
        this.activeTabId = 'main'
      } else if (!this.activeTabId) {
        this.activeTabId = this.openTabIds[this.openTabIds.length - 1]
      }
      void this.ensurePanelReady()
      const tabId = (this.activeTabId || 'main') as ShortVideoPanelTabId
      useExtensionDockStore().activateTab(`shortVideo:${tabId}`)
    },

    collapsePanel() {
      useExtensionDockStore().collapsePanel()
    },

    expandPanel() {
      useExtensionDockStore().expandPanel()
      if (this.openTabIds.length === 0) {
        void this.ensurePanelReady()
      }
    },

    closePanel() {
      this.openTabIds = []
      this.activeTabId = ''
      useExtensionDockStore().syncAfterTabsChanged()
    },

    async selectTab(tabId: ShortVideoPanelTabId) {
      if (!this.openTabIds.includes(tabId)) return
      this.activeTabId = tabId
      if (!this.initialized || this.posts.length === 0) {
        await this.fetchFeed(true)
      }
    },

    closeTab(tabId: ShortVideoPanelTabId) {
      const remaining = this.openTabIds.filter(id => id !== tabId)
      this.openTabIds = remaining
      if (remaining.length === 0) {
        this.activeTabId = ''
        useExtensionDockStore().syncAfterTabsChanged()
        return
      }
      if (this.activeTabId === tabId) {
        const next = remaining[remaining.length - 1]
        void this.selectTab(next)
      }
      useExtensionDockStore().syncAfterTabsChanged()
    },

    closeAllTabs() {
      this.openTabIds = []
      this.activeTabId = ''
      useExtensionDockStore().syncAfterTabsChanged()
    },

    setPanelWidth(width: number) {
      useExtensionDockStore().setPanelWidth(width)
    },

    setFeedTab(tab: ShortVideoFeedTab) {
      this.feedTab = tab
      this.posts = []
      this.activeIndex = 0
      this.hasMore = true
      return this.fetchFeed(true)
    },

    async fetchFeed(reset = false) {
      if (fetchFeedTask) {
        await fetchFeedTask
        if (!reset) return
      }

      if (!reset && !this.hasMore) return

      fetchFeedTask = (async () => {
        this.loading = true
        this.feedError = ''
        try {
          await this.ensureAuthReady()
          const beforeId = reset ? undefined : this.posts[this.posts.length - 1]?.id
          const res = this.feedTab === 'following'
            ? await listFollowingShortVideos({ beforeId, limit: 20 })
            : this.feedTab === 'friends'
              ? await listFriendsShortVideos({ beforeId, limit: 20 })
              : await listShortVideos({ beforeId, limit: 20 })
          if (res.code !== 200) {
            throw new Error(res.message || 'fetch feed failed')
          }
          const rows = normalizeShortVideoList(res.data)
          if (reset) {
            this.posts = rows
            this.activeIndex = 0
          } else {
            this.posts.push(...rows)
          }
          this.hasMore = rows.length >= 20
          this.initialized = true
        } catch (e) {
          this.feedError = e instanceof Error ? e.message : String(e)
          if (reset) {
            this.posts = []
            this.activeIndex = 0
          }
          throw e
        } finally {
          this.loading = false
        }
      })().finally(() => {
        fetchFeedTask = null
      })

      return fetchFeedTask
    },

    async publish(file: File, description: string, visibility = 0) {
      this.publishing = true
      try {
        const uploadRes = await uploadShortVideoMedia(file)
        if (uploadRes.code !== 200 || !uploadRes.data) {
          throw new Error(uploadRes.message || 'upload failed')
        }
        const videoKey = uploadRes.data
        let coverKey: string | undefined
        try {
          const coverBlob = await captureVideoCover(file)
          const coverFile = new File([coverBlob], 'cover.jpg', { type: 'image/jpeg' })
          const coverRes = await uploadShortVideoMedia(coverFile)
          if (coverRes.code === 200 && coverRes.data) {
            coverKey = coverRes.data
          }
        } catch {
          /* optional */
        }
        let durationMs: number | undefined
        try {
          const ms = await readVideoDuration(file)
          if (Number.isFinite(ms) && ms > 0) {
            durationMs = ms
          }
        } catch {
          /* optional */
        }
        const res = await publishShortVideo({
          description: description.trim(),
          videoKey,
          coverKey,
          durationMs,
          visibility
        })
        if (res.code !== 200) {
          throw new Error(res.message || 'publish failed')
        }
        if (res.data) {
          const post = normalizeShortVideoList([res.data])[0]
          if (isEncryptedShortVideoText(post.description)) {
            await this.fetchFeed(true)
          } else {
            this.posts.unshift(post)
            this.myPosts.unshift(post)
            this.activeIndex = 0
          }
        }
        return res.data
      } finally {
        this.publishing = false
      }
    },

    async toggleLike(post: ShortVideoPost) {
      const lists = [this.posts, this.myPosts, this.authorPosts, this.likedPosts]
      if (post.liked) {
        const res = await unlikeShortVideo(post.id)
        assertApiOk(res, 'unlike failed')
        forEachPostRef(lists, post.id, p => {
          p.liked = false
          p.likes = Math.max(0, p.likes - 1)
        })
        const likedIdx = this.likedPosts.findIndex(p => p.id === post.id)
        if (likedIdx >= 0) {
          this.likedPosts.splice(likedIdx, 1)
        }
      } else {
        const res = await likeShortVideo(post.id)
        assertApiOk(res, 'like failed')
        forEachPostRef(lists, post.id, p => {
          p.liked = true
          p.likes += 1
        })
      }
    },

    async toggleFavorite(post: ShortVideoPost) {
      const lists = [this.posts, this.myPosts, this.authorPosts, this.favoritePosts]
      if (post.favorited) {
        const res = await unfavoriteShortVideo(post.id)
        assertApiOk(res, 'unfavorite failed')
        forEachPostRef(lists, post.id, p => {
          p.favorited = false
          p.favorites = Math.max(0, (p.favorites ?? 0) - 1)
        })
        const favIdx = this.favoritePosts.findIndex(p => p.id === post.id)
        if (favIdx >= 0) {
          this.favoritePosts.splice(favIdx, 1)
        }
      } else {
        const res = await favoriteShortVideo(post.id)
        assertApiOk(res, 'favorite failed')
        forEachPostRef(lists, post.id, p => {
          p.favorited = true
          p.favorites = (p.favorites ?? 0) + 1
        })
      }
    },

    async toggleFollow(post: ShortVideoPost) {
      const me = String(useAppStore().userProfile.userId || '')
      if (!post.userId || post.userId === me) return
      if (post.followingAuthor) {
        const res = await unfollowShortVideoAuthor(post.userId)
        assertApiOk(res, 'unfollow failed')
        post.followingAuthor = false
      } else {
        const res = await followShortVideoAuthor(post.userId)
        assertApiOk(res, 'follow failed')
        post.followingAuthor = true
      }
    },

    async fetchMyPosts() {
      await this.ensureAuthReady()
      const userId = String(useAppStore().userProfile.userId || '')
      if (!userId) {
        this.myPosts = []
        return
      }
      this.myPostsLoading = true
      try {
        const res = await listUserShortVideos(userId, { limit: 50 })
        if (res.code === 200) {
          this.myPosts = normalizeShortVideoList(res.data)
        } else {
          this.myPosts = []
        }
      } finally {
        this.myPostsLoading = false
      }
    },

    async fetchAuthorPosts(userId: string, profile?: { nickname?: string; avatar?: string }) {
      await this.ensureAuthReady()
      const id = String(userId || '').trim()
      if (!id) {
        this.authorPosts = []
        this.authorProfile = null
        return
      }
      this.authorProfile = {
        userId: id,
        nickname: profile?.nickname,
        avatar: profile?.avatar
      }
      this.authorPostsLoading = true
      try {
        const res = await listUserShortVideos(id, { limit: 50 })
        if (res.code === 200) {
          this.authorPosts = normalizeShortVideoList(res.data)
          const first = this.authorPosts[0]
          if (first && !this.authorProfile.nickname) {
            this.authorProfile = {
              userId: id,
              nickname: first.nickname,
              avatar: first.avatar
            }
          }
        } else {
          this.authorPosts = []
        }
      } finally {
        this.authorPostsLoading = false
      }
    },

    clearAuthorPosts() {
      this.authorPosts = []
      this.authorProfile = null
    },

    async fetchFavoritePosts() {
      await this.ensureAuthReady()
      this.favoritePostsLoading = true
      try {
        const res = await listFavoriteShortVideos({ limit: 50 })
        if (res.code === 200) {
          this.favoritePosts = normalizeShortVideoList(res.data)
        } else {
          this.favoritePosts = []
        }
      } finally {
        this.favoritePostsLoading = false
      }
    },

    async fetchLikedPosts() {
      await this.ensureAuthReady()
      this.likedPostsLoading = true
      try {
        const res = await listLikedShortVideos({ limit: 50 })
        if (res.code === 200) {
          this.likedPosts = normalizeShortVideoList(res.data)
        } else {
          this.likedPosts = []
        }
      } finally {
        this.likedPostsLoading = false
      }
    },

    async deletePost(postId: string) {
      const res = await deleteShortVideo(postId)
      assertApiOk(res, 'delete failed')
      const idx = this.posts.findIndex(p => p.id === postId)
      if (idx >= 0) {
        this.posts.splice(idx, 1)
        if (this.activeIndex >= this.posts.length) {
          this.activeIndex = Math.max(0, this.posts.length - 1)
        }
      }
      const myIdx = this.myPosts.findIndex(p => p.id === postId)
      if (myIdx >= 0) {
        this.myPosts.splice(myIdx, 1)
      }
      const authorIdx = this.authorPosts.findIndex(p => p.id === postId)
      if (authorIdx >= 0) {
        this.authorPosts.splice(authorIdx, 1)
      }
    },

    async openPostById(postId: string) {
      await this.ensureAuthReady()
      const res = await getShortVideo(postId)
      if (res.code !== 200 || !res.data) {
        throw new Error(res.message || 'post not found')
      }
      const post = normalizeShortVideoList([res.data])[0]
      const existingIdx = this.posts.findIndex(p => p.id === postId)
      if (existingIdx >= 0) {
        this.posts[existingIdx] = post
        this.activeIndex = existingIdx
      } else {
        this.posts = [post, ...this.posts.filter(p => p.id !== postId)]
        this.activeIndex = 0
      }
      this.feedTab = 'recommend'
      this.hasMore = true
      this.initialized = true
      return post
    },

    async updatePost(postId: string, payload: { description?: string; visibility?: number }) {
      const res = await updateShortVideo(postId, payload)
      if (res.code !== 200 || !res.data) {
        throw new Error(res.message || 'update failed')
      }
      const updated = normalizeShortVideoList([res.data])[0]
      const apply = (list: ShortVideoPost[]) => {
        const idx = list.findIndex(p => p.id === postId)
        if (idx >= 0) {
          list[idx] = { ...list[idx], ...updated, comments: list[idx].comments }
        }
      }
      apply(this.posts)
      apply(this.myPosts)
      apply(this.authorPosts)
      return updated
    },

    async fetchComments(postId: string, reset = false) {
      const post = this.posts.find(p => p.id === postId)
      if (!post) return []
      const beforeId = reset ? undefined : post.comments[0]?.id
      const res = await listShortVideoComments(postId, { beforeId, limit: 50 })
      if (res.code !== 200) {
        throw new Error(res.message || 'fetch comments failed')
      }
      const rows = Array.isArray(res.data) ? res.data : []
      if (reset) {
        post.comments = rows
      } else if (rows.length > 0) {
        const existing = new Set(post.comments.map(c => c.id))
        const older = rows.filter(c => !existing.has(c.id))
        post.comments = [...older, ...post.comments]
      }
      if (typeof post.commentCount !== 'number') {
        post.commentCount = normalizeCommentCount(post)
      }
      return post.comments
    },

    async addComment(
      postId: string,
      content: string,
      parentId?: string,
      mentions?: Array<string | number>,
      imageKey?: string
    ) {
      const res = await commentShortVideo(postId, {
        content: content || undefined,
        parentId,
        mentions: mentions && mentions.length > 0 ? mentions : undefined,
        imageKey: imageKey || undefined
      })
      assertApiOk(res, 'comment failed')
      const post = this.posts.find(p => p.id === postId)
      if (post) {
        await this.fetchComments(postId, true)
        if (typeof post.commentCount !== 'number') {
          post.commentCount = normalizeCommentCount(post)
        } else {
          post.commentCount = Math.max(post.commentCount, post.comments.length)
        }
      }
      return res.data
    },

    async removeComment(postId: string, commentId: string) {
      const res = await deleteShortVideoComment(commentId)
      assertApiOk(res, 'delete comment failed')
      const post = this.posts.find(p => p.id === postId)
      if (post) {
        const before = post.comments.length
        post.comments = post.comments.filter(c => c.id !== commentId)
        if (post.comments.length < before) {
          post.commentCount = Math.max(0, normalizeCommentCount(post) - 1)
        }
      }
    },

    async toggleCommentLike(postId: string, comment: ShortVideoComment) {
      const post = this.posts.find(p => p.id === postId)
      if (!post) return
      const target = post.comments.find(c => c.id === comment.id)
      if (!target) return
      if (target.liked) {
        const res = await unlikeShortVideoComment(comment.id)
        assertApiOk(res, 'unlike comment failed')
        target.liked = false
        target.likes = Math.max(0, (target.likes ?? 0) - 1)
      } else {
        const res = await likeShortVideoComment(comment.id)
        assertApiOk(res, 'like comment failed')
        target.liked = true
        target.likes = (target.likes ?? 0) + 1
      }
    },

    commentCount(post: ShortVideoPost) {
      return normalizeCommentCount(post)
    },

    async markPlayed(postId: string) {
      if (playedSessionKeys.has(postId)) return
      try {
        const res = await recordShortVideoPlay(postId)
        assertApiOk(res, 'record play failed')
        playedSessionKeys.add(postId)
        const bump = (list: ShortVideoPost[]) => {
          const post = list.find(p => p.id === postId)
          if (post) {
            post.playCount = (post.playCount || 0) + 1
          }
        }
        bump(this.posts)
        bump(this.myPosts)
        bump(this.authorPosts)
      } catch {
        /* ignore */
      }
    },

    async markShared(postId: string) {
      if (sharedSessionKeys.has(postId)) return
      try {
        const res = await recordShortVideoShare(postId)
        assertApiOk(res, 'record share failed')
        sharedSessionKeys.add(postId)
        forEachPostRef(
          [this.posts, this.myPosts, this.authorPosts, this.favoritePosts, this.likedPosts],
          postId,
          post => {
            post.shares = (post.shares ?? 0) + 1
          }
        )
      } catch {
        /* ignore */
      }
    },

    async searchFeed(keyword: string) {
      const q = keyword.trim()
      if (!q) {
        return this.fetchFeed(true)
      }
      if (fetchFeedTask) {
        await fetchFeedTask
      }
      this.loading = true
      try {
        const res = await listShortVideos({ limit: 20, q })
        if (res.code !== 200) {
          throw new Error(res.message || 'search failed')
        }
        this.posts = normalizeShortVideoList(res.data)
        this.activeIndex = 0
        this.hasMore = false
        this.initialized = true
      } finally {
        this.loading = false
      }
    },

    setActiveIndex(index: number) {
      this.activeIndex = index
    }
  }
})

function readVideoDuration(file: File): Promise<number> {
  return new Promise((resolve, reject) => {
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      const ms = Math.round(video.duration * 1000)
      URL.revokeObjectURL(video.src)
      resolve(ms)
    }
    video.onerror = () => reject(new Error('duration'))
    video.src = URL.createObjectURL(file)
  })
}
