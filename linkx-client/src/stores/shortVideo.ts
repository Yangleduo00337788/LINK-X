/**
 * 作者：yangleduo
 */
import { defineStore } from 'pinia'
import {
  commentShortVideo,
  deleteShortVideo,
  followShortVideoAuthor,
  likeShortVideo,
  listFollowingShortVideos,
  listFriendsShortVideos,
  listShortVideos,
  listUserShortVideos,
  publishShortVideo,
  recordShortVideoPlay,
  unfollowShortVideoAuthor,
  unlikeShortVideo,
  uploadShortVideoMedia,
  type ShortVideoPost
} from '../api/shortVideo'
import { useAppStore } from './app'
import { useExtensionDockStore } from './extensionDock'
import { t } from '../i18n'
import { isEncryptedShortVideoText } from '../utils/shortVideoText'

export type ShortVideoFeedTab = 'live' | 'following' | 'friends' | 'recommend'
export type ShortVideoPanelTabId = 'main'

let ensurePanelReadyTask: Promise<void> | null = null

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
    myPostsLoading: false
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
    registerOpenTab(tabId: ShortVideoPanelTabId) {
      if (!tabId || this.openTabIds.includes(tabId)) return
      this.openTabIds.push(tabId)
    },

    async ensurePanelReady() {
      if (ensurePanelReadyTask) return ensurePanelReadyTask

      ensurePanelReadyTask = (async () => {
        if (this.openTabIds.length === 0) {
          this.registerOpenTab('main')
          this.activeTabId = 'main'
        } else if (!this.activeTabId) {
          this.activeTabId = this.openTabIds[this.openTabIds.length - 1]
        }
        if (!this.initialized) {
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
        void this.fetchFeed(true)
      } else if (!this.activeTabId) {
        this.activeTabId = this.openTabIds[this.openTabIds.length - 1]
      }
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
      if (!this.initialized) {
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
      if (this.loading) return
      if (this.feedTab === 'live') {
        if (reset) {
          this.posts = []
          this.activeIndex = 0
          this.hasMore = false
          this.initialized = true
        }
        return
      }
      if (!reset && !this.hasMore) return
      this.loading = true
      try {
        const beforeId = reset ? undefined : this.posts[this.posts.length - 1]?.id
        const res = this.feedTab === 'following'
          ? await listFollowingShortVideos({ beforeId, limit: 20 })
          : this.feedTab === 'friends'
            ? await listFriendsShortVideos({ beforeId, limit: 20 })
            : await listShortVideos({ beforeId, limit: 20 })
        const rows = res.data || []
        if (reset) {
          this.posts = rows
          this.activeIndex = 0
        } else {
          this.posts.push(...rows)
        }
        this.hasMore = rows.length >= 20
        this.initialized = true
      } finally {
        this.loading = false
      }
    },

    async publish(file: File, description: string, visibility = 0) {
      this.publishing = true
      try {
        const uploadRes = await uploadShortVideoMedia(file)
        if (uploadRes.code !== 200 || !uploadRes.data) {
          throw new Error(uploadRes.message || 'upload failed')
        }
        const videoKey = uploadRes.data
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
          durationMs,
          visibility
        })
        if (res.code !== 200) {
          throw new Error(res.message || 'publish failed')
        }
        if (res.data) {
          const post = res.data
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
      if (post.liked) {
        await unlikeShortVideo(post.id)
        post.liked = false
        post.likes = Math.max(0, post.likes - 1)
      } else {
        await likeShortVideo(post.id)
        post.liked = true
        post.likes += 1
      }
    },

    async toggleFollow(post: ShortVideoPost) {
      const me = String(useAppStore().userProfile.userId || '')
      if (!post.userId || post.userId === me) return
      if (post.followingAuthor) {
        await unfollowShortVideoAuthor(post.userId)
        post.followingAuthor = false
      } else {
        await followShortVideoAuthor(post.userId)
        post.followingAuthor = true
      }
    },

    async fetchMyPosts() {
      const userId = String(useAppStore().userProfile.userId || '')
      if (!userId) {
        this.myPosts = []
        return
      }
      this.myPostsLoading = true
      try {
        const res = await listUserShortVideos(userId, { limit: 50 })
        this.myPosts = res.data || []
      } finally {
        this.myPostsLoading = false
      }
    },

    async deletePost(postId: string) {
      await deleteShortVideo(postId)
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
    },

    async addComment(postId: string, content: string) {
      const res = await commentShortVideo(postId, { content })
      const post = this.posts.find(p => p.id === postId)
      if (post && res.data) {
        if (isEncryptedShortVideoText(res.data.content)) {
          await this.fetchFeed(true)
        } else {
          post.comments = [...post.comments, res.data]
        }
      }
      return res.data
    },

    async markPlayed(postId: string) {
      try {
        await recordShortVideoPlay(postId)
        const post = this.posts.find(p => p.id === postId)
        if (post) {
          post.playCount = (post.playCount || 0) + 1
        }
      } catch {
        /* ignore */
      }
    },

    async searchFeed(keyword: string) {
      const q = keyword.trim()
      if (!q) {
        return this.fetchFeed(true)
      }
      this.loading = true
      try {
        const res = await listShortVideos({ limit: 20, q })
        this.posts = res.data || []
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
