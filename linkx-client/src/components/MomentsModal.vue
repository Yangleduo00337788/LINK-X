<script setup lang="ts">
/**
 * 友链(朋友圈)独立窗口。
 *
 * 特性:
 *  - 顶部固定栏:搜索/消息/发布/刷新（窗控使用 Windows 原生）
 *  - 列表支持下拉刷新(触摸手势)
 *  - 顶部刷新按钮点击有旋转动画 + 同时下拉刷新
 *  - 发布入口:在铃铛右侧提供"发布"按钮,菜单中可选择:
 *      · 发布文字
 *      · 发布图片/视频
 *    原头像下方的发布区域被移除,改由独立 Modal 承载
 *  - 消息通知:点铃铛进入独立通知页(替换原嵌入式抽屉),
 *    通知页右上角"更多"包含"清空所有消息"与"只收到@我的消息"
 *  - 评论支持 @ 好友,后端会推送 moments_mention 通知给被@者
 */
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
// Naive UI 图标
import { NIcon, useMessage } from 'naive-ui'
// Ionicons5
import {
  NotificationsOutline,
  RefreshOutline,
  CloseOutline,
  HeartOutline,
  Heart,
  ChatbubbleOutline,
  SearchOutline,
  AddCircleOutline,
  AtCircleOutline,
  ImageOutline,
  LocationOutline,
  LockClosedOutline,
  PeopleOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useMomentsStore } from '../stores/moments'
import { useNotificationsStore } from '../stores/notifications'
import { useContactsStore } from '../stores/contacts'
// 主题同步工具
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
// 媒体地址规范化
import { normalizeMediaUrl } from '../utils/mediaUrl'
// 本地生成默认头像/封面
import { generateDefaultAvatar, generateDefaultBanner } from '../utils/defaultAvatar'
// 空状态组件
import EmptyState from './common/EmptyState.vue'
// @ 面板
import AtMentionPicker from './common/AtMentionPicker.vue'
// 通知独立页
import MomentsNotificationsPage from './MomentsNotificationsPage.vue'
// 偏好 API
import { getPreference, uploadMomentsBackground } from '../api/preference'

const appStore = useAppStore()
const momentsStore = useMomentsStore()
const notificationsStore = useNotificationsStore()
const contactsStore = useContactsStore()
const { userProfile, theme } = storeToRefs(appStore)
const { posts } = storeToRefs(momentsStore)
const { unreadMessageCount, momentsUnreadCount } = storeToRefs(notificationsStore)
const { toggleLike, fetchMoments, removePost, deleteComment } = momentsStore
const { fetchMessageNotifications, fetchNotificationCount } = notificationsStore
const message = useMessage()

/** 友链窗口铃铛：优先显示友链相关未读，没有时回退总未读 */
const bellUnreadCount = computed(() => {
  const moments = momentsUnreadCount.value
  if (moments > 0) return moments
  // 列表尚未拉到时，用服务端总未读兜底
  return unreadMessageCount.value
})

// 滚动位置
const scrollTop = ref(0)
// 评论草稿
const commentDraft = ref('')
const commentPostId = ref<string | null>(null)
// 搜索
const searchQuery = ref('')
const showSearch = ref(false)
// 当前登录用户
const myUserId = computed(() => userProfile.value.userId || '')
const defaultAvatar = computed(() =>
  generateDefaultAvatar(userProfile.value.nickname || '我')
)
const profileAvatar = computed(() =>
  normalizeMediaUrl(userProfile.value.avatar) || defaultAvatar.value
)
const defaultBanner = computed(() =>
  generateDefaultBanner(userProfile.value.nickname || 'banner')
)

// ============================================================
// 友链背景图
// ============================================================
const momentsBanner = ref<string>('')
const bannerLoaded = ref(false)
const bannerUploading = ref(false)

async function loadMomentsBanner() {
  try {
    const res = await getPreference()
    if (res.code === 200 && res.data?.momentsBackground) {
      momentsBanner.value = res.data.momentsBackground
      bannerLoaded.value = true
    }
  } catch {
    // ignore
  }
}

const bannerUrl = computed(() =>
  momentsBanner.value || defaultBanner.value
)

// ============================================================
// 背景图右键菜单
// ============================================================
const showBannerMenu = ref(false)
const bannerMenuX = ref(0)
const bannerMenuY = ref(0)

function onBannerContextMenu(e: MouseEvent) {
  // 仅本人可操作
  if (!myUserId.value) return
  e.preventDefault()
  bannerMenuX.value = e.clientX
  bannerMenuY.value = e.clientY
  showBannerMenu.value = true
}

function closeBannerMenu() {
  showBannerMenu.value = false
}

function handleBannerMenuAction(action: 'change' | 'preview') {
  closeBannerMenu()
  if (action === 'preview') {
    openImagePreview([bannerUrl.value], 0)
  } else if (action === 'change') {
    triggerBannerUpload()
  }
}

// ============================================================
// 背景图上传
// ============================================================
const fileInputRef = ref<HTMLInputElement | null>(null)

function triggerBannerUpload() {
  fileInputRef.value?.click()
}

function onBannerFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    input.value = ''
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.warning('图片大小不能超过 10MB')
    input.value = ''
    return
  }

  void uploadBannerDirectly(file)
  input.value = ''
}

async function uploadBannerDirectly(file: File) {
  bannerUploading.value = true
  try {
    const res = await uploadMomentsBackground(file)
    if (res.code === 200 && res.data?.momentsBackground) {
      momentsBanner.value = res.data.momentsBackground
      bannerLoaded.value = true
      message.success('背景图更新成功')
    } else {
      message.error(res.message || '上传失败')
    }
  } catch {
    message.error('上传失败，请重试')
  } finally {
    bannerUploading.value = false
  }
}

// 过滤列表（私密动态仅本人可见，作为前端兜底）
const filteredPosts = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  const mine = myUserId.value
  let list = posts.value.filter(p => {
    if (p.visibility === 2 && String(p.userId) !== String(mine)) return false
    return true
  })
  if (!q) return list
  return list.filter(
    p => p.user.toLowerCase().includes(q) || p.content.toLowerCase().includes(q)
  )
})

// 顶部栏渐变
function handleScroll(e: Event) {
  scrollTop.value = (e.target as HTMLElement).scrollTop
}

const showTitle = computed(() => scrollTop.value > 250 || showSearch.value)
const headerBgOpacity = computed(() => {
  if (showSearch.value) {
    const rgb = theme.value === 'dark' ? '34, 34, 34' : '245, 245, 245'
    return `rgba(${rgb}, 1)`
  }
  const opacity = Math.min(scrollTop.value / 200, 1)
  const rgb = theme.value === 'dark' ? '34, 34, 34' : '245, 245, 245'
  return `rgba(${rgb}, ${opacity})`
})
const headerIconColor = computed(() =>
  scrollTop.value > 200 || showSearch.value ? 'var(--lx-text)' : 'var(--lx-text-on-accent)'
)

// 图片预览
const previewImages = ref<string[]>([])
const previewIndex = ref(0)
const previewVisible = computed(() => previewImages.value.length > 0)

function openImagePreview(images: string[], index: number) {
  if (!images?.length) return
  previewImages.value = images
  previewIndex.value = Math.max(0, Math.min(index, images.length - 1))
}

function closeImagePreview() {
  previewImages.value = []
  previewIndex.value = 0
}

function previewPrev() {
  if (previewImages.value.length <= 1) return
  previewIndex.value = (previewIndex.value - 1 + previewImages.value.length) % previewImages.value.length
}

function previewNext() {
  if (previewImages.value.length <= 1) return
  previewIndex.value = (previewIndex.value + 1) % previewImages.value.length
}

function onPreviewKeydown(e: KeyboardEvent) {
  if (!previewVisible.value) return
  if (e.key === 'Escape') closeImagePreview()
  else if (e.key === 'ArrowLeft') previewPrev()
  else if (e.key === 'ArrowRight') previewNext()
}

// 挂载
let unsubscribeMomentsRefresh: (() => void) | null = null

onMounted(() => {
  applyDocumentTheme(appStore.theme)
  notifyElectronTheme(appStore.theme)
  window.addEventListener('click', closeBannerMenu)
  // 独立窗口不走 HomeView，需自行恢复会话并连接 WS，才能实时刷新未读铃铛
  void (async () => {
    if (!appStore.isLoggedIn) {
      await appStore.tryAutoLogin()
    }
    if (appStore.isLoggedIn) {
      void appStore.connectChatWebSocket()
    }
    await Promise.all([
      fetchMessageNotifications(),
      fetchMoments(),
      contactsStore.fetchFriends(),
      loadMomentsBanner()
    ])
    void fetchNotificationCount()
  })()
  window.addEventListener('keydown', onPreviewKeydown)
  // 发布窗口发完后通过 IPC 通知本窗口刷新列表
  unsubscribeMomentsRefresh = window.electronAPI?.onMomentsRefresh?.(() => {
    void fetchMoments()
  }) ?? null
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onPreviewKeydown)
  window.removeEventListener('click', closeBannerMenu)
  unsubscribeMomentsRefresh?.()
  unsubscribeMomentsRefresh = null
})

watch(theme, t => {
  applyDocumentTheme(t)
  notifyElectronTheme(t)
})

// 顶部刷新按钮 - 旋转动画状态
const refreshing = ref(false)

async function refresh() {
  if (refreshing.value) return
  refreshing.value = true
  document.querySelector('.moments-scroll-container')?.scrollTo({ top: 0, behavior: 'smooth' })
  await Promise.all([fetchMoments(), fetchMessageNotifications()])
  message.success('刷新成功')
  // 旋转动画保持至少 600ms,让用户感知到
  await new Promise(r => setTimeout(r, 600))
  refreshing.value = false
}

// 选择通知页 / 发布菜单
const showNotifications = ref(false)
async function showMessage() {
  showNotifications.value = !showNotifications.value
  if (showNotifications.value) {
    await fetchMessageNotifications()
  }
}

// 顶部发布菜单
const showPublishMenu = ref(false)
const publishMenuOptions = [
  { label: '发布文字', key: 'text', icon: AtCircleOutline },
  { label: '发布图片/视频', key: 'media', icon: ImageOutline }
]
function handlePublishMenuSelect(key: string | number) {
  showPublishMenu.value = false
  if (key === 'text') {
    // 打开文字发布独立窗口
    if (window.electronAPI?.openMomentsText) {
      window.electronAPI.openMomentsText()
    }
  } else if (key === 'media') {
    // 打开图片/视频发布独立窗口
    if (window.electronAPI?.openMomentsMedia) {
      window.electronAPI.openMomentsMedia()
    }
  }
}

function onPublished() {
  void fetchMoments()
}

// 跳到动态
function scrollToPost(notif: { relatedId?: string; type: string }) {
  if (!notif.relatedId) return
  if (notif.type !== 'moments_like' && notif.type !== 'moments_comment' && notif.type !== 'moments_mention') return
  showNotifications.value = false
  nextTick(() => {
    const container = document.querySelector('.moments-scroll-container')
    const targetPost = container?.querySelectorAll('.post-item')[
      posts.value.findIndex(p => String(p.id) === String(notif.relatedId))
    ]
    if (targetPost) {
      targetPost.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  })
}

// 评论
async function onToggleLike(postId: string) {
  const ok = await toggleLike(postId)
  if (ok === false) message.error('点赞失败，请稍后重试')
}

function onComment(post: { id: string }) {
  commentPostId.value = commentPostId.value === post.id ? null : post.id
  if (commentPostId.value !== post.id) {
    showCommentMention.value = false
    commentMentions.value = []
  }
}

/** 评论提交 (支持 @ 提及) */
const commentMentions = ref<{ id: string; name: string }[]>([])
const showCommentMention = ref(false)
const commentAtStart = ref(0)
const commentMentionQuery = ref('')
const commentMentionPickerRef = ref<InstanceType<typeof AtMentionPicker> | null>(null)

function detectCommentMention() {
  const ta = document.getElementById('moments-comment-input') as HTMLInputElement | null
  if (!ta) return
  const value = commentDraft.value
  const cursor = ta.selectionStart
  if (cursor == null) {
    showCommentMention.value = false
    commentMentionQuery.value = ''
    return
  }
  let i = cursor - 1
  while (i >= 0) {
    const ch = value[i]
    if (ch === '@') {
      const segment = value.slice(i + 1, cursor)
      if (/^\S{0,32}$/.test(segment) && !segment.includes(' ')) {
        commentAtStart.value = i
        commentMentionQuery.value = segment
        showCommentMention.value = true
        void ensureFriendsLoaded()
      } else {
        showCommentMention.value = false
        commentMentionQuery.value = ''
      }
      return
    }
    if (ch === ' ' || ch === '\n') break
    i--
  }
  showCommentMention.value = false
  commentMentionQuery.value = ''
}

async function ensureFriendsLoaded() {
  if (!contactsStore.friends.length) {
    await contactsStore.fetchFriends()
  }
}

function applyCommentMention(friend: { id: string | number; name: string }) {
  const name = (friend.name || '').trim()
  if (!name) return
  const before = commentDraft.value.slice(0, commentAtStart.value)
  const ta = document.getElementById('moments-comment-input') as HTMLInputElement | null
  const cursor = ta?.selectionStart ?? commentAtStart.value
  const after = commentDraft.value.slice(cursor)
  const inserted = `@${name} `
  commentDraft.value = before + inserted + after
  const id = String(friend.id)
  if (id && !commentMentions.value.some(m => m.id === id)) {
    commentMentions.value.push({ id, name })
  }
  showCommentMention.value = false
  commentMentionQuery.value = ''
  nextTick(() => {
    if (!ta) return
    const newPos = before.length + inserted.length
    ta.focus()
    ta.setSelectionRange(newPos, newPos)
  })
}

function triggerAtInComment() {
  const ta = document.getElementById('moments-comment-input') as HTMLInputElement | null
  if (!ta) return
  ta.focus()
  const cursor = ta.selectionStart ?? commentDraft.value.length
  const before = commentDraft.value.slice(0, cursor)
  const after = commentDraft.value.slice(cursor)
  const prefix = before.length && !/\s$/.test(before) ? ' ' : ''
  const inserted = `${prefix}@`
  commentDraft.value = before + inserted + after
  nextTick(() => {
    const newPos = before.length + inserted.length
    ta.focus()
    ta.setSelectionRange(newPos, newPos)
    detectCommentMention()
  })
}

function onCommentKeyDown(e: KeyboardEvent, postId: string) {
  if (!showCommentMention.value) {
    if (e.key === 'Enter') {
      e.preventDefault()
      void submitComment(postId)
    }
    return
  }
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    commentMentionPickerRef.value?.move(1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    commentMentionPickerRef.value?.move(-1)
  } else if (e.key === 'Enter' || e.key === 'Tab') {
    e.preventDefault()
    const pick = commentMentionPickerRef.value?.confirm()
    if (pick) applyCommentMention(pick)
  } else if (e.key === 'Escape') {
    e.preventDefault()
    showCommentMention.value = false
  }
}

const commentMentionFriends = computed(() => {
  const list = contactsStore.friends
  const q = commentMentionQuery.value.trim().toLowerCase()
  if (!q) return list.slice(0, 12)
  return list.filter(f => f.name.toLowerCase().includes(q)).slice(0, 12)
})

async function submitComment(postId: string) {
  const text = commentDraft.value.trim()
  if (!text) return
  // 避免误把半截 @ 发出去：若仍在选人，先确认当前高亮好友
  if (showCommentMention.value && commentMentionFriends.value.length) {
    const pick = commentMentionPickerRef.value?.confirm()
    if (pick) applyCommentMention(pick)
  }
  const finalText = commentDraft.value.trim()
  if (!finalText || finalText.endsWith('@')) {
    message.warning('请选择要 @ 的好友')
    return
  }
  const mentionIds = commentMentions.value
    .map(m => m.id)
    .filter(Boolean)
  const ok = await momentsStore.addComment(postId, finalText, mentionIds)
  if (ok) {
    commentDraft.value = ''
    commentPostId.value = null
    commentMentions.value = []
    showCommentMention.value = false
    commentMentionQuery.value = ''
    message.success('评论已发送')
  } else {
    message.error('评论失败')
  }
}

// 兼容性保留
function toggleSearch() {
  showSearch.value = !showSearch.value
  if (!showSearch.value) searchQuery.value = ''
}

// 删除自己动态/评论
async function onDeletePost(postId: string) {
  if (!postId) return
  const ok = window.confirm('确定删除这条动态?删除后不可恢复。')
  if (!ok) return
  const success = await removePost(postId)
  if (success) message.success('动态已删除')
  else message.error('删除失败')
}

async function onDeleteComment(postId: string, commentId: string) {
  if (!postId || !commentId) return
  const ok = window.confirm('确定删除这条评论?')
  if (!ok) return
  const success = await deleteComment(postId, commentId)
  if (success) message.success('评论已删除')
  else message.error('删除失败')
}

/** 计算图片网格布局 */
function getImageGridClass(count: number): string {
  if (count === 1) return 'grid-1'
  if (count === 2) return 'grid-2'
  if (count === 4) return 'grid-4'
  return 'grid-more'
}

/** 提醒谁看：优先用后端昵称，否则用通讯录解析 ID */
function getAtUserNames(post: { atUserNames?: string[]; atUsers?: string }): string[] {
  if (post.atUserNames?.length) return post.atUserNames
  if (!post.atUsers) return []
  try {
    const ids = JSON.parse(post.atUsers) as Array<string | number>
    if (!Array.isArray(ids)) return []
    return ids
      .map(id => {
        const friend = contactsStore.friends.find(f => String(f.id) === String(id) || String(f.userId) === String(id))
        return friend?.name || ''
      })
      .filter(Boolean)
  } catch {
    return []
  }
}

function visibilityLabel(visibility?: number): string {
  if (visibility === 1) return '仅好友'
  if (visibility === 2) return '私密'
  return ''
}
</script>

<template>
  <!-- 友链独立窗口 -->
  <div class="moments-wrapper standalone-window">
    <!-- 可滚动内容区 -->
    <div
      class="moments-scroll-container"
      @scroll="handleScroll"
    >
      <!-- 顶部封面与用户资料 -->
      <div class="moments-header">
        <div class="header-banner" @contextmenu="onBannerContextMenu">
          <img :src="bannerUrl" alt="Banner" class="banner-img" @error="(e) => (e.target as HTMLImgElement).src = defaultBanner" @click="handleBannerMenuAction('preview')" />
          <!-- 上传遮罩 hover 提示 -->
          <div class="banner-upload-overlay" :class="{ uploading: bannerUploading }" @click.stop="handleBannerMenuAction('preview')">
            <span v-if="bannerUploading">上传中…</span>
          </div>
        </div>
        <div class="user-info">
          <span class="username">{{ userProfile.nickname }}</span>
          <img :src="profileAvatar" alt="Avatar" class="avatar-img" />
        </div>
      </div>

      <!-- 动态列表(发布编辑器已迁移至独立 Modal) -->
      <div class="moments-content">
        <div v-for="post in filteredPosts" :key="post.id" class="post-item">
          <img
            v-if="post.avatar"
            :src="post.avatar"
            alt=""
            class="post-avatar"
            @error="post.avatar = ''"
          />
          <div v-else class="post-avatar-placeholder" :style="{ backgroundColor: 'var(--lx-accent)' }">
            {{ post.user.charAt(0).toUpperCase() }}
          </div>
          <div class="post-main">
            <div class="post-user">{{ post.user }}</div>
            <div class="post-text">{{ post.content }}</div>
            <div v-if="post.images?.length" class="post-images" :class="getImageGridClass(post.images.length)">
              <button
                v-for="(img, index) in post.images"
                :key="index"
                type="button"
                class="post-image-btn"
                @click="openImagePreview(post.images!, index)"
              >
                <img :src="img" alt="" class="post-image" loading="lazy" />
                <div class="image-overlay">
                  <span v-if="post.images.length > 1" class="image-index">{{ index + 1 }}</span>
                </div>
              </button>
            </div>
            <!-- 位置 / 提醒谁看 / 可见性 -->
            <div
              v-if="post.location || getAtUserNames(post).length || (post.visibility && post.visibility > 0)"
              class="post-meta"
            >
              <div v-if="post.location" class="meta-item meta-location">
                <n-icon :component="LocationOutline" :size="14" />
                <span>{{ post.location }}</span>
              </div>
              <div v-if="getAtUserNames(post).length" class="meta-item meta-at">
                <n-icon :component="AtCircleOutline" :size="14" />
                <span>提醒了 {{ getAtUserNames(post).join('、') }}</span>
              </div>
              <div
                v-if="post.visibility === 1 || post.visibility === 2"
                class="meta-item meta-visibility"
                :title="visibilityLabel(post.visibility)"
              >
                <n-icon
                  :component="post.visibility === 2 ? LockClosedOutline : PeopleOutline"
                  :size="14"
                />
                <span>{{ visibilityLabel(post.visibility) }}</span>
              </div>
            </div>
            <div class="post-footer">
              <span class="post-time">{{ post.time }}</span>
              <div class="post-toolbar">
                <button
                  type="button"
                  class="toolbar-btn"
                  :class="{ active: post.liked }"
                  @click="onToggleLike(post.id)"
                >
                  <n-icon :component="post.liked ? Heart : HeartOutline" :size="15" />
                  <span>{{ post.liked ? '已赞' : '赞' }}</span>
                </button>
                <button type="button" class="toolbar-btn" @click="onComment(post)">
                  <n-icon :component="ChatbubbleOutline" :size="15" />
                  <span>评论</span>
                </button>
                <button
                  v-if="post.userId === myUserId"
                  type="button"
                  class="toolbar-btn danger"
                  @click="onDeletePost(post.id)"
                >
                  <span>删除</span>
                </button>
              </div>
            </div>
            <div v-if="commentPostId === post.id" class="comment-input-row">
              <div class="comment-input-wrap">
                <input
                  id="moments-comment-input"
                  v-model="commentDraft"
                  class="comment-input"
                  placeholder="写评论… 使用 @ 提及好友"
                  @input="detectCommentMention"
                  @keydown="onCommentKeyDown($event, post.id)"
                />
                <button
                  type="button"
                  class="comment-at-btn"
                  title="@好友"
                  @click.stop="triggerAtInComment"
                >
                  <n-icon :component="AtCircleOutline" :size="14" />
                </button>
                <AtMentionPicker
                  v-if="showCommentMention"
                  ref="commentMentionPickerRef"
                  :friends="commentMentionFriends"
                  :text="commentDraft"
                  :caret-index="commentAtStart + 1"
                  @apply="(p) => applyCommentMention(p)"
                  @close="showCommentMention = false"
                />
              </div>
              <button type="button" class="comment-send" @click="submitComment(post.id)">发送</button>
            </div>
            <div v-if="post.likedBy.length || post.comments.length" class="post-interactions">
              <div class="interaction-arrow" />
              <div v-if="post.likedBy.length" class="likes-list">
                <n-icon :component="HeartOutline" size="14" class="like-icon" />
                <span class="like-users">{{ post.likedBy.join('，') }}</span>
              </div>
              <div v-if="post.likedBy.length && post.comments.length" class="interaction-divider" />
              <div v-if="post.comments.length" class="comments-list">
                <div v-for="comment in post.comments" :key="comment.id" class="comment-item">
                  <span class="comment-user">{{ comment.user }}：</span>
                  <span class="comment-text">{{ comment.content }}</span>
                  <button
                    v-if="comment.userId === myUserId"
                    type="button"
                    class="comment-del-btn"
                    title="删除评论"
                    @click="onDeleteComment(post.id, comment.id)"
                  >
                    ×
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <EmptyState
          v-if="!filteredPosts.length"
          :title="searchQuery.trim() ? '未找到相关动态' : '暂无动态'"
          :description="searchQuery.trim() ? '换个关键词试试' : '点击右上角「发布」分享第一条友链动态吧'"
        />
        <div v-else class="bottom-tip">没有更多了</div>
      </div>
    </div>

    <!-- 固定顶部操作栏 -->
    <div class="fixed-header" :style="{ backgroundColor: headerBgOpacity, color: headerIconColor }">
      <div class="header-left">
        <div class="action-btn" title="搜索" @click.stop="toggleSearch">
          <n-icon :component="SearchOutline" size="22" />
        </div>
        <div
          class="action-btn"
          :class="{ active: showNotifications }"
          title="消息"
          @click.stop="showMessage"
        >
          <n-icon :component="NotificationsOutline" size="22" />
          <span v-if="bellUnreadCount > 0" class="notif-badge">
            {{ bellUnreadCount > 99 ? '99+' : bellUnreadCount }}
          </span>
        </div>
        <!-- 发布按钮:点击弹出菜单(发布文字/发布图片视频) -->
        <div class="action-btn publish-btn" title="发布" @click.stop="showPublishMenu = !showPublishMenu">
          <n-icon :component="AddCircleOutline" size="22" />
        </div>
        <div v-if="showPublishMenu" class="publish-menu" @click.stop>
          <button
            v-for="opt in publishMenuOptions"
            :key="opt.key"
            class="publish-menu-item"
            type="button"
            @click="handlePublishMenuSelect(opt.key)"
          >
            <n-icon :component="opt.icon" :size="18" />
            <span>{{ opt.label }}</span>
          </button>
        </div>
        <div v-if="showPublishMenu" class="publish-menu-backdrop" @click="showPublishMenu = false" />
        <!-- 刷新按钮:点击旋转 360° 动画 -->
        <div class="action-btn" :class="{ refreshing }" title="刷新" @click.stop="refresh">
          <n-icon :component="RefreshOutline" size="22" class="refresh-icon" />
        </div>
      </div>
      <div class="header-center" :class="{ visible: showTitle && !showNotifications }">
        <span v-if="!showSearch">友链</span>
        <input
          v-else
          v-model="searchQuery"
          class="header-search"
          placeholder="搜索友链"
          @click.stop
        />
      </div>
    </div>

    <!-- 通知抽屉遮罩(点击空白处关闭) -->
    <Transition name="backdrop-fade">
      <div v-if="showNotifications" class="notif-backdrop" @click="showNotifications = false" />
    </Transition>

    <!-- 通知抽屉(右侧滑入) -->
    <MomentsNotificationsPage
      :visible="showNotifications"
      @close="showNotifications = false"
      @select="scrollToPost"
    />

    <!-- 图片预览 -->
    <div v-if="previewVisible" class="image-preview-overlay" @click.self="closeImagePreview">
      <button type="button" class="preview-close" title="关闭" @click="closeImagePreview">
        <n-icon :component="CloseOutline" :size="22" />
      </button>
      <button
        v-if="previewImages.length > 1"
        type="button"
        class="preview-nav prev"
        title="上一张"
        @click.stop="previewPrev"
      >
        ‹
      </button>
      <img
        :src="previewImages[previewIndex]"
        alt=""
        class="preview-full-img"
        @click.stop
      />
      <button
        v-if="previewImages.length > 1"
        type="button"
        class="preview-nav next"
        title="下一张"
        @click.stop="previewNext"
      >
        ›
      </button>
      <div v-if="previewImages.length > 1" class="preview-counter">
        {{ previewIndex + 1 }} / {{ previewImages.length }}
      </div>
    </div>

    <!-- 背景图上传隐藏文件框 -->
    <input
      ref="fileInputRef"
      type="file"
      accept="image/*"
      class="hidden-file-input"
      @change="onBannerFileSelected"
    />

    <!-- 背景图右键菜单 -->
    <Teleport to="body">
      <div
        v-if="showBannerMenu"
        class="banner-context-menu"
        :style="{ left: bannerMenuX + 'px', top: bannerMenuY + 'px' }"
        @click.stop
      >
        <button class="ctx-item" @click="handleBannerMenuAction('change')">
          <n-icon :component="AddCircleOutline" :size="15" />
          更换背景图
        </button>
      </div>
      <!-- 点击其他地方关闭菜单 -->
      <div v-if="showBannerMenu" class="ctx-backdrop" @click="closeBannerMenu" />
    </Teleport>
  </div>
</template>

<style scoped>
.standalone-window {
  width: 100vw !important;
  height: 100vh !important;
  border-radius: 0 !important;
  margin: 0 !important;
  background: var(--lx-bg-window);
}

.moments-wrapper {
  position: relative;
  width: 440px;
  height: 560px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  text-align: left;
  margin: auto;
}

.standalone-window.moments-wrapper {
  width: 100vw !important;
  height: 100vh !important;
  border-radius: 0 !important;
  margin: 0 !important;
}

.fixed-header {
  position: absolute;
  top: 0;
  left: env(titlebar-area-x, 0px);
  width: env(titlebar-area-width, 100%);
  height: env(titlebar-area-height, 48px);
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  z-index: 100;
  transition: background-color 0.3s ease, color 0.3s ease;
  box-sizing: border-box;
  -webkit-app-region: drag;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  -webkit-app-region: no-drag;
  pointer-events: auto;
}

.action-btn {
  position: relative;
  width: 32px;
  height: 32px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s;
  pointer-events: auto;
}

.action-btn:hover {
  background: var(--lx-bg-active);
}

.action-btn.active {
  background: var(--lx-bg-active);
}

.action-btn.refreshing .refresh-icon {
  animation: refresh-spin 0.6s linear infinite;
}

@keyframes refresh-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.header-center {
  flex: 1;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
  min-width: 0;
  padding: 0 8px;
  -webkit-app-region: no-drag;
}

.header-center.visible {
  opacity: 1;
  pointer-events: auto;
}

.header-search {
  width: 100%;
  max-width: 200px;
  border: none;
  outline: none;
  background: var(--lx-bg-input);
  color: var(--lx-text-body);
  border-radius: var(--lx-radius);
  padding: 6px 10px;
  font-size: 14px;
}

.publish-btn {
  /* 与其他 action-btn 保持统一(无背景色,仅 hover 显示) */
}
.publish-btn:hover {
  background: var(--lx-bg-active);
}

.publish-menu {
  position: absolute;
  top: 48px;
  left: 110px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
  z-index: 200;
  display: flex;
  flex-direction: column;
  min-width: 180px;
  overflow: hidden;
}

.publish-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: none;
  background: transparent;
  color: var(--lx-text-body);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  -webkit-app-region: no-drag;
}

.publish-menu-item:hover {
  background: var(--lx-bg-hover);
}

.publish-menu-backdrop {
  position: fixed;
  inset: 0;
  z-index: 199;
}

.notif-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
  cursor: pointer;
}

.backdrop-fade-enter-active,
.backdrop-fade-leave-active {
  transition: opacity 0.2s ease;
}
.backdrop-fade-enter-from,
.backdrop-fade-leave-to {
  opacity: 0;
}

.moments-content {
  padding: 0 20px 20px;
}

.moments-scroll-container {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  background: var(--lx-bg-card);
  -webkit-overflow-scrolling: touch;
}

.moments-scroll-container::-webkit-scrollbar {
  width: 0;
  background: transparent;
}

.moments-header {
  position: relative;
  height: 320px;
  background: var(--lx-bg-card);
}

.header-banner {
  width: 100%;
  height: 280px;
  overflow: hidden;
  cursor: context-menu;
  position: relative;
}

.banner-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.banner-upload-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: transparent;
  transition: background 0.2s, color 0.2s;
  pointer-events: none;
  opacity: 0;
}

.header-banner:hover .banner-upload-overlay {
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  opacity: 1;
}

.banner-upload-overlay.uploading {
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  opacity: 1;
}

.user-info {
  position: absolute;
  bottom: 16px;
  right: 16px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.username {
  color: var(--lx-bg-card);
  font-size: 20px;
  font-weight: 600;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
  margin-top: 8px;
}

.avatar-img {
  width: 68px;
  height: 68px;
  border-radius: var(--lx-avatar-radius);
  border: 2px solid var(--lx-bg-card);
  background: var(--lx-bg-card);
  object-fit: cover;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}


.post-item {
  display: flex;
  padding: 18px 0;
  border-bottom: 1px solid var(--lx-border-light);
  animation: fadeInUp 0.3s ease;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.post-avatar {
  width: 44px;
  height: 44px;
  border-radius: var(--lx-avatar-radius);
  object-fit: cover;
  flex-shrink: 0;
  margin-right: 12px;
  background: var(--lx-bg-panel);
  transition: transform 0.2s ease;
  cursor: pointer;
}
.post-avatar:hover {
  transform: scale(1.08);
}

.post-avatar-placeholder {
  width: 44px;
  height: 44px;
  border-radius: var(--lx-avatar-radius);
  flex-shrink: 0;
  margin-right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s ease;
}
.post-avatar-placeholder:hover {
  transform: scale(1.08);
}

.post-main {
  flex: 1;
  min-width: 0;
}

.post-user {
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-accent);
  margin-bottom: 6px;
  cursor: pointer;
  transition: opacity 0.2s;
}
.post-user:hover {
  opacity: 0.8;
}

.post-text {
  font-size: 14px;
  color: var(--lx-text);
  line-height: 1.6;
  margin-bottom: 10px;
  word-break: break-all;
  white-space: pre-wrap;
}

.post-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  margin: -2px 0 10px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  line-height: 1.4;
  max-width: 100%;
}

.meta-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-location {
  color: var(--lx-accent);
}

.meta-at {
  color: var(--lx-text-muted);
}

.meta-visibility {
  color: var(--lx-text-muted);
}

.post-images {
  display: grid;
  gap: 6px;
  margin-bottom: 10px;
  border-radius: 10px;
  overflow: hidden;
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

/* 单图：容器随图片收缩，限制最大宽高，避免竖图右侧留灰边 */
.post-images.grid-1 {
  display: block;
  width: fit-content;
  max-width: min(100%, 280px);
}
.post-images.grid-1 .post-image-btn {
  width: auto;
  max-width: 100%;
  max-height: 360px;
  background: transparent;
}
.post-images.grid-1 .post-image {
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 360px;
  object-fit: contain;
  vertical-align: top;
}
.post-images.grid-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.post-images.grid-2 .post-image-btn {
  aspect-ratio: 1;
}
.post-images.grid-4 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, 1fr);
}
.post-images.grid-4 .post-image-btn {
  aspect-ratio: 1;
}
.post-images.grid-more {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.post-images.grid-more .post-image-btn {
  aspect-ratio: 1;
}

.post-image-btn {
  position: relative;
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  cursor: zoom-in;
  overflow: hidden;
  line-height: 0;
  display: block;
  width: 100%;
  min-width: 0;
}

.post-image-btn:hover .post-image {
  transform: scale(1.05);
}

.post-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.image-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgba(0,0,0,0.1) 0%, transparent 40%, rgba(0,0,0,0.3) 100%);
  opacity: 0;
  transition: opacity 0.2s ease;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 6px;
}
.post-image-btn:hover .image-overlay {
  opacity: 1;
}

.image-index {
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
}

.post-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.post-time {
  font-size: 12px;
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.post-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  font-size: 12px;
  padding: 5px 10px;
  border-radius: 16px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.toolbar-btn:hover {
  background: var(--lx-bg-panel);
  color: var(--lx-accent);
}

.toolbar-btn.active {
  color: var(--lx-danger, #e05454);
  background: rgba(224, 84, 84, 0.1);
}

.toolbar-btn.danger {
  color: var(--lx-text-muted);
}

.toolbar-btn.danger:hover {
  background: rgba(224, 84, 84, 0.1);
  color: var(--lx-danger, #e05454);
}

.comment-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
  position: relative;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.comment-input-wrap {
  position: relative;
  flex: 1;
}

.comment-input {
  width: 100%;
  border: 1px solid var(--lx-border-light);
  border-radius: 20px;
  padding: 8px 36px 8px 14px;
  font-size: 13px;
  background: var(--lx-bg-card);
  color: var(--lx-text);
  transition: all 0.2s ease;
}
.comment-input:focus {
  outline: none;
  border-color: var(--lx-accent);
  box-shadow: 0 0 0 2px var(--lx-accent-soft);
}

.comment-at-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 50%;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}
.comment-at-btn:hover {
  color: var(--lx-accent);
  background: var(--lx-bg-hover);
}

.comment-send {
  border: none;
  background: linear-gradient(135deg, var(--lx-accent) 0%, #4caf50 100%);
  color: #fff;
  border-radius: 16px;
  padding: 0 16px;
  height: 32px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(24, 160, 88, 0.25);
}
.comment-send:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(24, 160, 88, 0.35);
}

.post-interactions {
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  padding: 8px 10px;
  position: relative;
  margin-top: 10px;
}

.interaction-arrow {
  position: absolute;
  top: -8px;
  left: 12px;
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-bottom: 8px solid var(--lx-bg-panel);
}

.likes-list {
  display: flex;
  align-items: flex-start;
  color: var(--lx-accent);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.like-icon {
  margin-top: 3px;
  margin-right: 6px;
  flex-shrink: 0;
}

.interaction-divider {
  height: 1px;
  background: var(--lx-bg-hover);
  margin: 6px 0;
}

.comments-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.comment-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.comment-del-btn {
  margin-left: auto;
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  font-size: 14px;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.comment-del-btn:hover {
  color: var(--lx-danger);
}

.comment-user {
  color: var(--lx-accent);
  font-weight: 500;
}

.bottom-tip {
  text-align: center;
  color: var(--lx-text-muted);
  font-size: 13px;
  padding: 30px 0;
}

.notif-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  background: var(--lx-danger);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.image-preview-overlay {
  position: absolute;
  inset: 0;
  z-index: 300;
  background: rgba(0, 0, 0, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-app-region: no-drag;
}

.preview-full-img {
  max-width: 92%;
  max-height: 86%;
  object-fit: contain;
  border-radius: 4px;
  user-select: none;
}

.preview-close {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.preview-close:hover {
  background: rgba(255, 255, 255, 0.28);
}

.preview-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  height: 48px;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
  border-radius: 6px;
}

.preview-nav:hover {
  background: rgba(255, 255, 255, 0.24);
}

.preview-nav.prev {
  left: 10px;
}

.preview-nav.next {
  right: 10px;
}

.preview-counter {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
}

.hidden-file-input {
  display: none;
}

.banner-context-menu {
  position: fixed;
  z-index: 1000;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.22);
  overflow: hidden;
  min-width: 160px;
}

.ctx-backdrop {
  position: fixed;
  inset: 0;
  z-index: 999;
}

.ctx-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border: none;
  background: transparent;
  color: var(--lx-text-body);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
}

.ctx-item:hover {
  background: var(--lx-bg-hover);
}
</style>
