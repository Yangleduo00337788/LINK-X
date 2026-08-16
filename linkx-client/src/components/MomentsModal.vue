<!-- 作者：yangleduo -->
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
import { useRoute, useRouter } from 'vue-router'
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
import { isEphemeralMediaUrl } from '../utils/mediaUrl'
// 本地生成默认头像/封面
import {
  generateDefaultBanner,
  resolveMomentsBackgroundUrl,
  resolveUserAvatarUrl
} from '../utils/defaultAvatar'
// 空状态组件
import EmptyState from './common/EmptyState.vue'
import OpsRecommendCarousel from './ops/OpsRecommendCarousel.vue'
// @ 面板
import AtMentionPicker from './common/AtMentionPicker.vue'
// 通知独立页
import MomentsNotificationsPage from './MomentsNotificationsPage.vue'
import MomentsComposerModal from './MomentsComposerModal.vue'
import MomentsPostImage from './moments/MomentsPostImage.vue'
import Avatar from './Avatar.vue'
import WindowCaptionButtons from './WindowCaptionButtons.vue'
import { resolveMomentsImageDisplaySrc } from '../utils/momentsMediaAccess'
import type { MomentPost } from '../stores/moments'
// 偏好 API
import { getPreference, uploadMomentsBackground } from '../api/preference'
import { useI18n } from '../i18n'
import { LxButton, LxIconButton } from './ui'

/** 嵌入 AppShell 主栏时为 true（Web）；独立 Electron 窗为 false */
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const appStore = useAppStore()
const momentsStore = useMomentsStore()
const notificationsStore = useNotificationsStore()
const contactsStore = useContactsStore()
const route = useRoute()
const router = useRouter()
const { userProfile, theme } = storeToRefs(appStore)
const { posts, hasMore, loadingMore, focusUserId, focusUserName, focusUserPosts, focusUserLoading, isUserFeed } =
  storeToRefs(momentsStore)
const { momentsUnreadCount } = storeToRefs(notificationsStore)
const { toggleLike, fetchMoments, loadMoreMoments, removePost, deleteComment, updatePost, loadFocusUserFeed, clearFocusUser, setFocusUser } =
  momentsStore
const { fetchMessageNotifications, fetchNotificationCount } = notificationsStore
const message = useMessage()
const { t } = useI18n()

/** 友链窗口铃铛：仅统计友链互动未读（点赞/评论/@ 等），不含聊天/日程/官方通知 */
const bellUnreadCount = computed(() => momentsUnreadCount.value)

// 滚动位置
const scrollTop = ref(0)
// 评论草稿
const commentDraft = ref('')
const commentPostId = ref<string | null>(null)
const replyParentId = ref<string | null>(null)
const replyParentName = ref('')
// 搜索
const searchQuery = ref('')
const showSearch = ref(false)
// 编辑动态
const editingPostId = ref<string | null>(null)
const editContent = ref('')
const editSaving = ref(false)
// 当前登录用户
const myUserId = computed(() => userProfile.value.userId || '')
const defaultBanner = computed(() =>
  generateDefaultBanner(userProfile.value.nickname || 'banner')
)

function onPostAvatarError(post: { avatar?: string; userId?: string }) {
  const url = resolveUserAvatarUrl(post.avatar, post.userId)
  if (url && isEphemeralMediaUrl(url)) {
    void recoverEphemeralMomentsMedia()
  }
}

/** 代理/预签名图裂图时防抖拉列表，换新 HMAC/预签名（避免长会话「代理链接已过期」） */
let mediaRecoverTimer: ReturnType<typeof setTimeout> | null = null
let mediaRecoverInFlight = false

function recoverEphemeralMomentsMedia() {
  if (mediaRecoverInFlight) return
  if (mediaRecoverTimer) clearTimeout(mediaRecoverTimer)
  mediaRecoverTimer = setTimeout(async () => {
    mediaRecoverTimer = null
    if (mediaRecoverInFlight) return
    mediaRecoverInFlight = true
    try {
      await fetchMoments({ q: searchQuery.value.trim() || undefined })
    } finally {
      mediaRecoverInFlight = false
    }
  }, 400)
}

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
    }
  } catch {
    // ignore
  } finally {
    bannerLoaded.value = true
  }
}

const bannerUrl = computed(() => {
  const resolved = resolveMomentsBackgroundUrl(momentsBanner.value, myUserId.value)
  if (resolved) return resolved
  return bannerLoaded.value ? defaultBanner.value : ''
})

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
    openRawImagePreview([bannerUrl.value], 0)
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
    message.warning(t('moments.selectImage'))
    input.value = ''
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.warning(t('moments.imageTooLarge'))
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
      message.success(t('moments.bannerUpdated'))
    } else {
      message.error(res.message || t('moments.uploadFail'))
    }
  } catch {
    message.error(t('moments.uploadFailRetry'))
  } finally {
    bannerUploading.value = false
  }
}

// 过滤列表（私密动态仅本人可见，作为前端兜底；搜索走服务端）
const filteredPosts = computed(() => {
  const source = isUserFeed.value ? focusUserPosts.value : posts.value
  const mine = myUserId.value
  return source.filter(p => {
    if (p.visibility === 2 && String(p.userId) !== String(mine)) return false
    return true
  })
})

const headerDisplayName = computed(() => {
  if (isUserFeed.value) {
    const name = focusUserName.value || focusUserPosts.value[0]?.user || t('modals.user')
    return t('moments.userFeedTitle', { name })
  }
  return userProfile.value.nickname
})

const headerAvatarUrl = computed(() => {
  if (isUserFeed.value) {
    const post = focusUserPosts.value[0]
    return resolveUserAvatarUrl(post?.avatar, post?.userId || focusUserId.value)
  }
  return resolveUserAvatarUrl(userProfile.value.avatar, userProfile.value.userId)
})

/** 从路由或 store 同步「查看某人友链」焦点 */
async function syncFocusFromRoute() {
  const qUserId = typeof route.query.userId === 'string' ? route.query.userId.trim() : ''
  const qName = typeof route.query.name === 'string' ? route.query.name : ''
  if (qUserId) {
    setFocusUser(qUserId, qName || null)
    await loadFocusUserFeed()
    return
  }
  // 独立窗：hash 去掉 userId 时退出个人友链；嵌入模式保留 store 里已设的焦点
  if (!props.embedded && focusUserId.value) {
    clearFocusUser()
    await fetchMoments({ q: searchQuery.value.trim() || undefined })
    return
  }
  if (focusUserId.value) {
    await loadFocusUserFeed()
  }
}

async function exitUserFeed() {
  clearFocusUser()
  if (route.query.userId || route.query.name) {
    await router.replace({ path: route.path, query: {} })
  }
  await fetchMoments({ q: searchQuery.value.trim() || undefined })
}

let searchTimer: number | null = null
watch(searchQuery, (q) => {
  if (isUserFeed.value) return
  if (searchTimer != null) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    void fetchMoments({ q: q.trim() || undefined })
  }, 320)
})

function isVideoUrl(url?: string): boolean {
  if (!url) return false
  const path = url.split('?')[0].toLowerCase()
  return /\.(mp4|webm|mov|m4v)$/i.test(path)
}

// 顶部栏渐变
function handleScroll(e: Event) {
  const el = e.target as HTMLElement
  scrollTop.value = el.scrollTop
  if (
    !isUserFeed.value &&
    hasMore.value &&
    !loadingMore.value &&
    el.scrollHeight - el.scrollTop - el.clientHeight < 120
  ) {
    void loadMoreMoments()
  }
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
type PreviewItem = { url: string; imageId?: string }
const previewItems = ref<PreviewItem[]>([])
const previewIndex = ref(0)
const previewDisplaySrc = ref('')
let previewBlobUrl: string | null = null
const previewVisible = computed(() => previewItems.value.length > 0)

function revokePreviewBlob() {
  if (previewBlobUrl) {
    URL.revokeObjectURL(previewBlobUrl)
    previewBlobUrl = null
  }
}

async function refreshPreviewDisplaySrc() {
  revokePreviewBlob()
  previewDisplaySrc.value = ''
  const item = previewItems.value[previewIndex.value]
  if (!item) return
  const resolved = await resolveMomentsImageDisplaySrc(item.imageId, item.url)
  if (resolved.blobUrlToRevoke) {
    previewBlobUrl = resolved.blobUrlToRevoke
  }
  previewDisplaySrc.value = resolved.src
}

watch([previewItems, previewIndex], () => {
  void refreshPreviewDisplaySrc()
})

function openRawImagePreview(images: string[], index = 0) {
  const items: PreviewItem[] = images.map(url => ({ url }))
  if (!items.length) return
  previewItems.value = items
  previewIndex.value = Math.max(0, Math.min(index, items.length - 1))
}

function openImagePreview(post: MomentPost, index: number) {
  const items: PreviewItem[] = []
  post.images?.forEach((url, i) => {
    if (!isVideoUrl(url)) {
      const imageId = post.imageIds?.[i]
      items.push({
        url,
        imageId: imageId?.trim() ? imageId : undefined
      })
    }
  })
  if (!items.length) return
  const clickedUrl = post.images?.[index]
  let start = 0
  if (clickedUrl && !isVideoUrl(clickedUrl)) {
    const found = items.findIndex(it => it.url === clickedUrl)
    if (found >= 0) start = found
  }
  previewItems.value = items
  previewIndex.value = start
}

function closeImagePreview() {
  previewItems.value = []
  previewIndex.value = 0
  revokePreviewBlob()
  previewDisplaySrc.value = ''
}

function previewPrev() {
  if (previewItems.value.length <= 1) return
  previewIndex.value = (previewIndex.value - 1 + previewItems.value.length) % previewItems.value.length
}

function previewNext() {
  if (previewItems.value.length <= 1) return
  previewIndex.value = (previewIndex.value + 1) % previewItems.value.length
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
  if (!props.embedded) {
    applyDocumentTheme(appStore.theme)
    notifyElectronTheme(appStore.theme)
  }
  window.addEventListener('click', closeBannerMenu)
  // 独立窗口不走 HomeView，需自行恢复会话并连接 WS；嵌入模式由 AppShell 已登录
  void (async () => {
    if (!props.embedded && !appStore.isLoggedIn) {
      await appStore.tryAutoLogin()
    }
    if (!props.embedded && appStore.isLoggedIn) {
      void appStore.connectChatWebSocket()
    }
    await Promise.all([
      fetchMessageNotifications(),
      contactsStore.fetchFriends(),
      loadMomentsBanner(),
      syncFocusFromRoute().then(async () => {
        if (!focusUserId.value) await fetchMoments()
      })
    ])
    void fetchNotificationCount()
  })()
  window.addEventListener('keydown', onPreviewKeydown)
  // 发布窗口发完后通过 IPC 通知本窗口刷新列表
  unsubscribeMomentsRefresh = window.electronAPI?.onMomentsRefresh?.(() => {
    if (focusUserId.value) void loadFocusUserFeed()
    else void fetchMoments()
  }) ?? null
})

watch(
  () => [route.query.userId, route.query.name] as const,
  () => {
    void syncFocusFromRoute()
  }
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onPreviewKeydown)
  window.removeEventListener('click', closeBannerMenu)
  revokePreviewBlob()
  if (mediaRecoverTimer) {
    clearTimeout(mediaRecoverTimer)
    mediaRecoverTimer = null
  }
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
  if (focusUserId.value) {
    await Promise.all([loadFocusUserFeed(), fetchMessageNotifications()])
  } else {
    await Promise.all([fetchMoments(), fetchMessageNotifications()])
  }
  message.success(t('moments.refreshOk'))
  // 旋转动画保持至少 600ms,让用户感知到
  await new Promise(r => setTimeout(r, 600))
  refreshing.value = false
}

// 选择通知页 / 发布菜单
const showNotifications = ref(false)
const bellAnchorRef = ref<HTMLElement | null>(null)
async function showMessage() {
  showNotifications.value = !showNotifications.value
  if (showNotifications.value) {
    await fetchMessageNotifications()
  }
}

// 顶部发布菜单
const showPublishMenu = ref(false)
const showComposer = ref(false)
const composerMode = ref<'text' | 'media'>('text')
const publishMenuOptions = computed(() => [
  { label: t('moments.publishText'), key: 'text', icon: AtCircleOutline },
  { label: t('moments.publishMedia'), key: 'media', icon: ImageOutline }
])
function handlePublishMenuSelect(key: string | number) {
  showPublishMenu.value = false
  if (key === 'text') {
    if (window.electronAPI?.openMomentsText) {
      window.electronAPI.openMomentsText()
    } else {
      composerMode.value = 'text'
      showComposer.value = true
    }
  } else if (key === 'media') {
    if (window.electronAPI?.openMomentsMedia) {
      window.electronAPI.openMomentsMedia()
    } else {
      composerMode.value = 'media'
      showComposer.value = true
    }
  }
}

function onComposerPublished() {
  showComposer.value = false
  void fetchMoments({ q: searchQuery.value.trim() || undefined })
}

// 跳到动态
function scrollToPost(notif: { relatedId?: string; type: string }) {
  if (!notif.relatedId) return
  if (
    notif.type !== 'moments_like' &&
    notif.type !== 'moments_comment' &&
    notif.type !== 'moments_mention' &&
    notif.type !== 'moments_at'
  )
    return
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
  if (ok === false) message.error(t('moments.likeFail'))
}

function onComment(post: { id: string }) {
  commentPostId.value = commentPostId.value === post.id ? null : post.id
  if (commentPostId.value !== post.id) {
    showCommentMention.value = false
    commentMentions.value = []
    replyParentId.value = null
    replyParentName.value = ''
  }
}

function startReply(postId: string, comment: { id: string; user: string }) {
  commentPostId.value = postId
  replyParentId.value = comment.id
  replyParentName.value = comment.user
  commentDraft.value = ''
  nextTick(() => {
    document.getElementById('moments-comment-input')?.focus()
  })
}

function cancelReply() {
  replyParentId.value = null
  replyParentName.value = ''
}

function startEditPost(post: { id: string; content: string }) {
  editingPostId.value = post.id
  editContent.value = post.content || ''
}

function cancelEditPost() {
  editingPostId.value = null
  editContent.value = ''
}

async function saveEditPost(postId: string) {
  const text = editContent.value.trim()
  if (!text) {
    message.warning(t('moments.publishNeedContent'))
    return
  }
  editSaving.value = true
  try {
    const ok = await updatePost(postId, { content: text })
    if (ok) {
      message.success(t('moments.editPostOk'))
      cancelEditPost()
    } else {
      message.error(t('moments.editPostFail'))
    }
  } finally {
    editSaving.value = false
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
    message.warning(t('moments.selectAtFriend'))
    return
  }
  const mentionIds = commentMentions.value
    .map(m => m.id)
    .filter(Boolean)
  const ok = await momentsStore.addComment(
    postId,
    finalText,
    mentionIds,
    replyParentId.value || undefined
  )
  if (ok) {
    commentDraft.value = ''
    commentPostId.value = null
    commentMentions.value = []
    showCommentMention.value = false
    commentMentionQuery.value = ''
    replyParentId.value = null
    replyParentName.value = ''
    message.success(t('moments.commentSent'))
  } else {
    message.error(t('moments.commentFail'))
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
  const ok = window.confirm(t('moments.deletePostConfirm'))
  if (!ok) return
  const success = await removePost(postId)
  if (success) message.success(t('moments.postDeleted'))
  else message.error(t('moments.deleteFail'))
}

async function onDeleteComment(postId: string, commentId: string) {
  if (!postId || !commentId) return
  const ok = window.confirm(t('moments.deleteCommentConfirm'))
  if (!ok) return
  const success = await deleteComment(postId, commentId)
  if (success) message.success(t('moments.commentDeleted'))
  else message.error(t('moments.deleteFail'))
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
  if (visibility === 1) return t('moments.friendsOnly')
  if (visibility === 2) return t('moments.private')
  return ''
}

const showMomentsOps = ref(false)
</script>

<template>
  <!-- 友链：独立窗或嵌入 AppShell -->
  <div class="moments-wrapper" :class="props.embedded ? 'embedded' : 'standalone-window'">
    <!-- 可滚动内容区 -->
    <div
      class="moments-scroll-container"
      @scroll="handleScroll"
    >
      <!-- 顶部封面与用户资料 -->
      <div class="moments-header">
        <div class="header-banner" @contextmenu="onBannerContextMenu">
          <img
            v-if="bannerUrl"
            :src="bannerUrl"
            alt="Banner"
            class="banner-img"
            referrerpolicy="no-referrer"
            @error="(e) => { const img = e.target as HTMLImageElement; if (img.src !== defaultBanner) img.src = defaultBanner }"
            @click="handleBannerMenuAction('preview')"
          />
          <!-- 上传遮罩 hover 提示 -->
          <div class="banner-upload-overlay" :class="{ uploading: bannerUploading }" @click.stop="handleBannerMenuAction('preview')">
            <span v-if="bannerUploading">{{ t('moments.uploading') }}</span>
          </div>
        </div>
        <div class="user-info">
          <div class="user-info-text">
            <span class="username">{{ headerDisplayName }}</span>
          </div>
          <Avatar
            :size="88"
            color="var(--lx-accent)"
            :text="headerDisplayName || '?'"
            :image-url="headerAvatarUrl || undefined"
            class="avatar-img"
          />
        </div>
      </div>

      <div v-show="showMomentsOps" class="moments-ops-slot">
        <OpsRecommendCarousel
          slot-code="moments"
          :height="108"
          :radius="12"
          :show-arrow="true"
          @loaded="(p) => (showMomentsOps = p.count > 0)"
        />
      </div>

      <!-- 动态列表(发布编辑器已迁移至独立 Modal) -->
      <div class="moments-content">
        <div v-for="post in filteredPosts" :key="post.id" class="post-item">
          <Avatar
            :size="44"
            color="var(--lx-accent)"
            :text="post.user"
            :image-url="resolveUserAvatarUrl(post.avatar, post.userId) || undefined"
            class="post-avatar"
            @image-error="onPostAvatarError(post)"
          />
          <div class="post-main">
            <div class="post-user">{{ post.user }}</div>
            <div v-if="editingPostId === post.id" class="post-edit-box">
              <textarea v-model="editContent" class="post-edit-input" rows="3" />
              <div class="post-edit-actions">
                <LxButton variant="moments-tool" @click="cancelEditPost">{{ t('common.cancel') }}</LxButton>
                <LxButton variant="sm-primary" :disabled="editSaving" @click="saveEditPost(post.id)">
                  {{ t('common.save') }}
                </LxButton>
              </div>
            </div>
            <div v-else class="post-text">{{ post.content }}</div>
            <div v-if="post.images?.length" class="post-images" :class="getImageGridClass(post.images.length)">
              <template v-for="(img, index) in post.images" :key="index">
                <video
                  v-if="isVideoUrl(img)"
                  :src="img"
                  class="post-image post-video"
                  controls
                  preload="metadata"
                  referrerpolicy="no-referrer"
                />
                <button
                  v-else
                  type="button"
                  class="post-image-btn"
                  @click="openImagePreview(post, index)"
                >
                  <MomentsPostImage
                    :url="img"
                    :image-id="post.imageIds?.[index]"
                  />
                  <div class="image-overlay">
                    <span v-if="post.images.length > 1" class="image-index">{{ index + 1 }}</span>
                  </div>
                </button>
              </template>
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
                <span>{{ t('moments.reminded', { names: getAtUserNames(post).join('、') }) }}</span>
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
                <LxButton
                  variant="moments-tool"
                  :class="{ 'is-active': post.liked }"
                  @click="onToggleLike(post.id)"
                >
                  <n-icon :component="post.liked ? Heart : HeartOutline" :size="15" />
                  <span>{{ post.liked ? t('moments.liked') : t('moments.like') }}</span>
                </LxButton>
                <LxButton variant="moments-tool" @click="onComment(post)">
                  <n-icon :component="ChatbubbleOutline" :size="15" />
                  <span>{{ t('moments.comment') }}</span>
                </LxButton>
                <LxButton
                  v-if="post.userId === myUserId"
                  variant="moments-tool"
                  @click="startEditPost(post)"
                >
                  <span>{{ t('moments.editPost') }}</span>
                </LxButton>
                <LxButton
                  v-if="post.userId === myUserId"
                  variant="moments-tool-danger"
                  @click="onDeletePost(post.id)"
                >
                  <span>{{ t('common.delete') }}</span>
                </LxButton>
              </div>
            </div>
            <div v-if="commentPostId === post.id" class="comment-input-row">
              <div v-if="replyParentId" class="reply-hint">
                <span>{{ t('moments.replyTo', { name: replyParentName }) }}</span>
                <button type="button" class="reply-cancel" @click="cancelReply">{{ t('common.cancel') }}</button>
              </div>
              <div class="comment-input-wrap">
                <input
                  id="moments-comment-input"
                  v-model="commentDraft"
                  class="comment-input"
                  :placeholder="replyParentId ? t('moments.replyTo', { name: replyParentName }) : t('moments.commentPhAt')"
                  @input="detectCommentMention"
                  @keydown="onCommentKeyDown($event, post.id)"
                />
                <button
                  type="button"
                  class="comment-at-btn"
                  :title="t('moments.atFriend')"
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
              <LxButton variant="send" @click="submitComment(post.id)">{{ t('chat.send') }}</LxButton>
            </div>
            <div v-if="post.likedBy.length || post.comments.length" class="post-interactions">
              <div class="interaction-arrow" />
              <div v-if="post.likedBy.length" class="likes-list">
                <n-icon :component="HeartOutline" size="14" class="like-icon" />
                <span class="like-users">{{ post.likedBy.join('，') }}</span>
              </div>
              <div v-if="post.likedBy.length && post.comments.length" class="interaction-divider" />
              <div v-if="post.comments.length" class="comments-list">
                <div
                  v-for="comment in post.comments"
                  :key="comment.id"
                  class="comment-item"
                  :class="{ 'comment-item--reply': !!comment.parentId }"
                >
                  <span class="comment-user">{{ comment.user }}</span>
                  <span v-if="comment.replyToNickname" class="comment-reply-to">
                    {{ t('moments.reply') }} {{ comment.replyToNickname }}
                  </span>
                  <span class="comment-user">：</span>
                  <span class="comment-text">{{ comment.content }}</span>
                  <button
                    type="button"
                    class="comment-reply-btn"
                    @click="startReply(post.id, comment)"
                  >
                    {{ t('moments.reply') }}
                  </button>
                  <button
                    v-if="comment.userId === myUserId"
                    type="button"
                    class="comment-del-btn"
                    :title="t('moments.deleteComment')"
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
          v-if="!filteredPosts.length && !focusUserLoading"
          :title="searchQuery.trim() ? t('moments.noMatch') : t('moments.empty')"
          :description="
            searchQuery.trim()
              ? t('moments.tryOtherKeyword')
              : isUserFeed
                ? t('moments.userFeedEmptyHint')
                : t('moments.emptyHint')
          "
        />
        <div v-else-if="filteredPosts.length && !isUserFeed" class="bottom-tip">
          <LxButton
            v-if="hasMore"
            variant="link"
            :disabled="loadingMore"
            @click="loadMoreMoments"
          >
            {{ loadingMore ? t('moments.loadingMore') : t('moments.loadMore') }}
          </LxButton>
          <span v-else>{{ t('moments.noMore') }}</span>
        </div>
      </div>
    </div>

    <!-- 固定顶部操作栏 -->
    <div class="fixed-header" :style="{ backgroundColor: headerBgOpacity, color: headerIconColor }">
      <div class="header-left">
        <LxIconButton variant="banner" :title="t('common.search')" @click.stop="toggleSearch">
          <n-icon :component="SearchOutline" size="22" />
        </LxIconButton>
        <div ref="bellAnchorRef" class="bell-anchor">
          <LxIconButton
            variant="banner"
            :active="showNotifications"
            :title="t('moments.messages')"
            @click.stop="showMessage"
          >
            <n-icon :component="NotificationsOutline" size="22" />
            <span v-if="bellUnreadCount > 0" class="notif-badge">
              {{ bellUnreadCount > 99 ? '99+' : bellUnreadCount }}
            </span>
          </LxIconButton>
        </div>
        <!-- 发布按钮:点击弹出菜单(发布文字/发布图片视频) -->
        <LxIconButton
          variant="banner"
          class="publish-btn"
          :title="t('moments.publishAction')"
          @click.stop="showPublishMenu = !showPublishMenu"
        >
          <n-icon :component="AddCircleOutline" size="22" />
        </LxIconButton>
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
        <LxIconButton
          variant="banner"
          :class="{ refreshing }"
          :title="t('moments.refresh')"
          @click.stop="refresh"
        >
          <n-icon :component="RefreshOutline" size="22" class="refresh-icon" />
        </LxIconButton>
        <LxButton
          v-if="isUserFeed"
          variant="ghost"
          class="back-feed-btn"
          :title="t('moments.backToFeed')"
          @click.stop="exitUserFeed"
        >
          {{ t('moments.backToFeed') }}
        </LxButton>
      </div>
      <div class="header-center" :class="{ visible: showTitle && !showNotifications }">
        <span v-if="!showSearch">{{ t('moments.title') }}</span>
        <input
          v-else
          v-model="searchQuery"
          class="header-search"
          :placeholder="t('moments.searchPh')"
          @click.stop
        />
      </div>
      <div v-if="!embedded" class="header-right">
        <WindowCaptionButtons show-pin />
      </div>
    </div>

    <!-- 点击空白处关闭通知弹层（无遮罩色） -->
    <div
      v-if="showNotifications"
      class="notif-dismiss-layer"
      @click="showNotifications = false"
    />

    <MomentsNotificationsPage
      :visible="showNotifications"
      :anchor-el="bellAnchorRef"
      @close="showNotifications = false"
      @select="scrollToPost"
    />

    <!-- 图片预览 -->
    <div v-if="previewVisible" class="image-preview-overlay" @click.self="closeImagePreview">
      <button type="button" class="preview-close" :title="t('common.close')" @click="closeImagePreview">
        <n-icon :component="CloseOutline" :size="22" />
      </button>
      <button
        v-if="previewItems.length > 1"
        type="button"
        class="preview-nav prev"
        :title="t('moments.prevImage')"
        @click.stop="previewPrev"
      >
        ‹
      </button>
      <img
        v-if="previewDisplaySrc"
        :src="previewDisplaySrc"
        alt=""
        class="preview-full-img"
        referrerpolicy="no-referrer"
        @click.stop
      />
      <button
        v-if="previewItems.length > 1"
        type="button"
        class="preview-nav next"
        :title="t('moments.nextImage')"
        @click.stop="previewNext"
      >
        ›
      </button>
      <div v-if="previewItems.length > 1" class="preview-counter">
        {{ previewIndex + 1 }} / {{ previewItems.length }}
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

    <!-- Web / 嵌入模式：应用内发布浮层 -->
    <MomentsComposerModal
      v-model:visible="showComposer"
      :initial-mode="composerMode"
      @published="onComposerPublished"
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
          {{ t('moments.changeBanner') }}
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
  width: 500px;
  height: 720px;
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

.moments-wrapper.embedded {
  width: 100% !important;
  height: 100% !important;
  max-width: none;
  border-radius: 0 !important;
  margin: 0 !important;
  background: var(--lx-bg-card);
}

.fixed-header {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 48px;
  min-height: 48px;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  padding: 0 0 0 var(--lx-space-lg);
  z-index: var(--lx-z-header);
  transition: background-color var(--lx-duration-slow) ease, color var(--lx-duration-slow) ease;
  box-sizing: border-box;
  -webkit-app-region: drag;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  -webkit-app-region: no-drag;
  pointer-events: auto;
  align-self: center;
}

.header-right {
  display: flex;
  align-items: stretch;
  flex-shrink: 0;
  -webkit-app-region: no-drag;
}

.back-feed-btn {
  height: 28px;
  padding: 0 var(--lx-space-md);
  border: 1px solid currentColor;
  border-radius: var(--lx-radius-pill);
  background: rgba(255, 255, 255, 0.18);
  color: inherit;
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-none);
  white-space: nowrap;
  cursor: pointer;
  pointer-events: auto;
  transition: background var(--lx-duration-md), opacity var(--lx-duration-md);
}

.back-feed-btn:hover {
  background: rgba(255, 255, 255, 0.32);
}

.header-center {
  flex: 1;
  text-align: center;
  font-size: var(--lx-font-xl);
  font-weight: 600;
  opacity: 0;
  transition: opacity var(--lx-duration-slow) ease;
  pointer-events: none;
  min-width: 0;
  padding: 0 var(--lx-space);
  -webkit-app-region: no-drag;
  align-self: center;
  display: flex;
  align-items: center;
  justify-content: center;
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
  padding: var(--lx-space-sm) var(--lx-space-md);
  font-size: var(--lx-font);
}

.publish-menu {
  position: absolute;
  top: 48px;
  left: 110px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: var(--lx-shadow-float);
  z-index: var(--lx-z-sheet);
  display: flex;
  flex-direction: column;
  min-width: 180px;
  overflow: hidden;
}

.publish-menu-item {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  padding: var(--lx-space-md) var(--lx-space-xl);
  border: none;
  background: transparent;
  color: var(--lx-text-body);
  font-size: var(--lx-font-md);
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
  z-index: var(--lx-z-sheet-under);
}

.bell-anchor {
  position: relative;
}

.notif-dismiss-layer {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-moments-top);
  background: transparent;
  cursor: default;
}

.moments-ops-slot {
  padding: var(--lx-space-lg) var(--lx-space-3xl) 0;
}

.moments-content {
  padding: 0 var(--lx-space-3xl) var(--lx-space-3xl);
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
  font-size: var(--lx-font-md);
  color: transparent;
  transition: background var(--lx-duration-md), color var(--lx-duration-md);
  pointer-events: none;
  opacity: 0;
}

.header-banner:hover .banner-upload-overlay {
  background: rgba(0, 0, 0, 0.35);
  color: var(--lx-text-on-accent);
  opacity: 1;
}

.banner-upload-overlay.uploading {
  background: rgba(0, 0, 0, 0.45);
  color: var(--lx-text-on-accent);
  opacity: 1;
}

.user-info {
  position: absolute;
  bottom: 16px;
  right: 16px;
  display: flex;
  align-items: flex-start;
  gap: var(--lx-space-2xl);
}

.username {
  color: var(--lx-bg-card);
  font-size: var(--lx-font-4xl);
  font-weight: 600;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
  margin-top: var(--lx-space);
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
  padding: var(--lx-space-2xl) 0;
  border-bottom: 1px solid var(--lx-border-light);
  animation: fadeInUp var(--lx-duration-slow) ease;
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
  margin-right: var(--lx-space-lg);
  background: var(--lx-bg-panel);
  transition: transform var(--lx-duration-md) ease;
  cursor: pointer;
}
.post-avatar:hover {
  transform: scale(1.08);
}

.post-main {
  flex: 1;
  min-width: 0;
}

.post-user {
  font-size: var(--lx-font-lg);
  font-weight: 600;
  color: var(--lx-accent);
  margin-bottom: var(--lx-space-sm);
  cursor: pointer;
  transition: opacity var(--lx-duration-md);
}
.post-user:hover {
  opacity: 0.8;
}

.post-text {
  font-size: var(--lx-font);
  color: var(--lx-text);
  line-height: var(--lx-leading-relaxed);
  margin-bottom: var(--lx-space-md);
  word-break: break-all;
  white-space: pre-wrap;
}

.post-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lx-space) var(--lx-space-xl);
  margin: -var(--lx-space-2xs) 0 var(--lx-space-md);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading);
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
  gap: var(--lx-space-sm);
  margin-bottom: var(--lx-space-md);
  border-radius: var(--lx-radius-xl);
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
  transition: transform var(--lx-duration-slow) ease;
}

.image-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgba(0,0,0,0.1) 0%, transparent 40%, rgba(0,0,0,0.3) 100%);
  opacity: 0;
  transition: opacity var(--lx-duration-md) ease;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: var(--lx-space-sm);
}
.post-image-btn:hover .image-overlay {
  opacity: 1;
}

.image-index {
  background: rgba(0, 0, 0, 0.5);
  color: var(--lx-text-on-accent);
  font-size: var(--lx-font-xs);
  padding: var(--lx-space-2xs) var(--lx-space-sm);
  border-radius: var(--lx-radius-2xs);
}

.post-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--lx-space);
  margin-bottom: var(--lx-space);
}

.post-time {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.post-toolbar {
  display: flex;
  align-items: center;
  gap: var(--lx-space-xs);
  flex-wrap: nowrap;
}

.comment-input-row {
  display: flex;
  gap: var(--lx-space);
  margin-bottom: var(--lx-space);
  align-items: center;
  position: relative;
  animation: slideDown var(--lx-duration-md) ease;
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
  border-radius: var(--lx-radius-3xl);
  padding: var(--lx-space) var(--lx-space-6xl-minus) var(--lx-space) var(--lx-space-xl);
  font-size: var(--lx-font-md);
  background: var(--lx-bg-card);
  color: var(--lx-text);
  transition: all var(--lx-duration-md) ease;
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
  transition: all var(--lx-duration-md) ease;
}
.comment-at-btn:hover {
  color: var(--lx-accent);
  background: var(--lx-bg-hover);
}

.post-interactions {
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  padding: var(--lx-space) var(--lx-space-md);
  position: relative;
  margin-top: var(--lx-space-md);
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
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
  word-break: break-all;
}

.like-icon {
  margin-top: var(--lx-space-2xs);
  margin-right: var(--lx-space-sm);
  flex-shrink: 0;
}

.interaction-divider {
  height: 1px;
  background: var(--lx-bg-hover);
  margin: var(--lx-space-sm) 0;
}

.comments-list {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
}

.comment-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lx-space-sm);
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
  word-break: break-all;
}

.comment-item--reply {
  padding-left: var(--lx-space-lg);
  border-left: 2px solid var(--lx-border-light);
}

.comment-reply-to {
  color: var(--lx-text-muted);
  font-size: var(--lx-font-sm);
}

.comment-reply-btn {
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  font-size: var(--lx-font-sm);
  cursor: pointer;
  padding: 0 var(--lx-space-xs);
}

.comment-reply-btn:hover {
  color: var(--lx-accent);
}

.comment-del-btn {
  margin-left: auto;
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  font-size: var(--lx-font);
  cursor: pointer;
  padding: 0 var(--lx-space-xs);
  line-height: var(--lx-leading-none);
}

.comment-del-btn:hover {
  color: var(--lx-danger);
}

.comment-user {
  color: var(--lx-accent);
  font-weight: 500;
}

.reply-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  margin-bottom: var(--lx-space-sm);
}

.reply-cancel {
  border: none;
  background: transparent;
  color: var(--lx-accent);
  cursor: pointer;
  font-size: var(--lx-font-sm);
}

.post-edit-box {
  margin: var(--lx-space-sm) 0 var(--lx-space-md);
}

.post-edit-input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-sm);
  padding: var(--lx-space) var(--lx-space-md);
  background: var(--lx-bg-card);
  color: var(--lx-text);
  resize: vertical;
  font-size: var(--lx-font);
}

.post-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lx-space);
  margin-top: var(--lx-space-sm);
}

.post-video {
  width: 100%;
  max-height: 280px;
  border-radius: var(--lx-radius-xs);
  background: var(--lx-black);
}

.bottom-tip {
  text-align: center;
  color: var(--lx-text-muted);
  font-size: var(--lx-font-md);
  padding: var(--lx-space-5xl-tight) 0;
}

.notif-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 var(--lx-space-xs);
  background: var(--lx-danger);
  color: var(--lx-text-on-accent);
  font-size: var(--lx-font-2xs);
  font-weight: 600;
  border-radius: var(--lx-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: var(--lx-leading-none);
}

.image-preview-overlay {
  position: absolute;
  inset: 0;
  z-index: var(--lx-z-sheet-over);
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
  border-radius: var(--lx-radius-2xs);
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
  color: var(--lx-text-on-accent);
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
  color: var(--lx-text-on-accent);
  font-size: var(--lx-font-6xl);
  line-height: var(--lx-leading-none);
  cursor: pointer;
  border-radius: var(--lx-radius-xs);
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
  font-size: var(--lx-font-md);
}

.hidden-file-input {
  display: none;
}

.banner-context-menu {
  position: fixed;
  z-index: var(--lx-z-toast);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.22);
  overflow: hidden;
  min-width: 160px;
}

.ctx-backdrop {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-popover);
}

.ctx-item {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  width: 100%;
  padding: var(--lx-space-md) var(--lx-space-xl);
  border: none;
  background: transparent;
  color: var(--lx-text-body);
  font-size: var(--lx-font-md);
  text-align: left;
  cursor: pointer;
  transition: background var(--lx-duration);
}

.ctx-item:hover {
  background: var(--lx-bg-hover);
}
</style>
