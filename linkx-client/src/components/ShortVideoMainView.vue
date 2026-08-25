<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 短视频主视图：竖屏滑动 Feed（抖音 / 视频号风格）
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon, NInput, NDropdown, NRadio, NRadioGroup, useDialog, useMessage, type DropdownOption } from 'naive-ui'
import {
  ArrowDown,
  ArrowUp,
  Bookmark,
  BookmarkOutline,
  ChatbubbleOutline,
  CloseOutline,
  EllipsisHorizontal,
  HappyOutline,
  Heart,
  HeartOutline,
  ImageOutline,
  Pause,
  PersonOutline,
  Play,
  PricetagsOutline,
  SearchOutline,
  ShareSocialOutline,
  NotificationsOutline,
  VideocamOutline,
  VolumeHighOutline,
  VolumeMuteOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { LxButton } from './ui'
import AtMentionPicker from './common/AtMentionPicker.vue'
import { useShortVideoStore, type ShortVideoFeedTab, SHORT_VIDEO_MAX_BYTES, SHORT_VIDEO_MAX_DURATION_MS } from '../stores/shortVideo'
import { useAppStore } from '../stores/app'
import { useNotificationsStore } from '../stores/notifications'
import { useContactsStore } from '../stores/contacts'
import { useI18n } from '../i18n'
import { resolveApiErrorMessage } from '../api/client'
import Avatar from './Avatar.vue'
import ShortVideoNotificationsPage from './ShortVideoNotificationsPage.vue'
import ShortVideoTopicPlaza from './ShortVideoTopicPlaza.vue'
import ShortVideoTopicDetail from './ShortVideoTopicDetail.vue'
import ShortVideoFollowingList from './ShortVideoFollowingList.vue'
import ShortVideoSearchPage from './ShortVideoSearchPage.vue'
import ShortVideoSearchNav from './ShortVideoSearchNav.vue'
import ShortVideoSubPageShell from './ShortVideoSubPageShell.vue'
import ForwardPickerModal from './chat/ForwardPickerModal.vue'
import { CHAT_EMOJIS } from '../constants/emojis'
import { buildShortVideoCommentTree } from '../utils/shortVideoComments'
import { uploadShortVideoMedia, shareShortVideoToChat, listHotShortVideoTopics, listHotShortVideos, countFollowingShortVideoUsers, reportShortVideo } from '../api/shortVideo'
import { resolveUserAvatarUrl } from '../utils/defaultAvatar'
import { resolveShortVideoDisplaySrc, buildShortVideoMediaApiUrl } from '../utils/shortVideoMediaAccess'
import { readableShortVideoText } from '../utils/shortVideoText'
import {
  clearShortVideoSearchHistory,
  loadShortVideoSearchHistory,
  removeShortVideoSearchHistoryItem,
  saveShortVideoSearchQuery
} from '../utils/shortVideoSearchHistory'
import { readVideoDurationMs } from '../utils/shortVideoCover'
import { copyText } from '../utils/clipboard'
import type { ShortVideoComment, ShortVideoPost, ShortVideoTopic, ShortVideoFollowingUser } from '../api/shortVideo'

const { t } = useI18n()
const message = useMessage()
const dialog = useDialog()
const route = useRoute()
const router = useRouter()
const store = useShortVideoStore()
const appStore = useAppStore()
const notificationsStore = useNotificationsStore()
const contactsStore = useContactsStore()
const { feedTab, posts, loading, publishing, publishProgress, activeIndex, myPosts, myPostsLoading, myPostsLoadingMore, feedError, authorPosts, authorPostsLoading, authorPostsLoadingMore, authorPostsHasMore, authorProfile, favoritePosts, favoritePostsLoading, favoritePostsLoadingMore, likedPosts, likedPostsLoading, likedPostsLoadingMore, searchMode, searchQuery } = storeToRefs(store)
const { messageNotifs } = storeToRefs(notificationsStore)

const mineTabs = computed(() => [
  { key: 'works' as const, label: t('shortVideo.myVideos'), icon: VideocamOutline },
  { key: 'favorites' as const, label: t('shortVideo.myFavorites'), icon: BookmarkOutline },
  { key: 'likes' as const, label: t('shortVideo.myLikes'), icon: HeartOutline }
])

const currentMinePosts = computed(() => {
  if (mineTab.value === 'works') return myPosts.value
  if (mineTab.value === 'favorites') return favoritePosts.value
  return likedPosts.value
})

const currentMineLoading = computed(() => {
  if (mineTab.value === 'works') return myPostsLoading.value
  if (mineTab.value === 'favorites') return favoritePostsLoading.value
  return likedPostsLoading.value
})

const currentMineLoadingMore = computed(() => {
  if (mineTab.value === 'works') return myPostsLoadingMore.value
  if (mineTab.value === 'favorites') return favoritePostsLoadingMore.value
  return likedPostsLoadingMore.value
})

const sharePreviewText = computed(() => {
  const post = sharePostTarget.value
  if (!post) return ''
  return readableShortVideoText(post.description) || t('shortVideo.empty')
})

const sharePreviewImageUrl = computed(() => {
  const post = sharePostTarget.value
  if (!post) return ''
  return buildShortVideoMediaApiUrl(post.id, 'cover')
})

const currentMineEmptyText = computed(() => {
  if (mineTab.value === 'works') return t('shortVideo.noMyVideos')
  if (mineTab.value === 'favorites') return t('shortVideo.noFavorites')
  return t('shortVideo.noLikes')
})

const shortVideoUnreadCount = computed(() =>
  messageNotifs.value.filter(
    n => n.readStatus === 0 && typeof n.type === 'string' && n.type.startsWith('short_video_')
  ).length
)

const feedRef = ref<HTMLElement | null>(null)
const videoRefs = ref<Record<string, HTMLVideoElement | null>>({})
const videoSrcMap = ref<Record<string, string>>({})
const blobRevokeList = ref<string[]>([])
const progressMap = ref<Record<string, number>>({})
const userPausedIds = ref(new Set<string>())
const playbackTick = ref(0)
const muted = ref(false)
const commentOpenFor = ref<string | null>(null)
const commentText = ref('')
const publishOpen = ref(false)
const publishDesc = ref('')
const publishVisibility = ref(0)
const fileInputRef = ref<HTMLInputElement | null>(null)
const pendingFile = ref<File | null>(null)
const searchOpen = ref(false)
const topicPlazaOpen = ref(false)
const topicDetailOpen = ref(false)
const topicDetailName = ref('')
const followingListOpen = ref(false)
const followingCount = ref(0)
const searchText = ref('')
const searchHistory = ref<string[]>([])
const hotTopics = ref<ShortVideoTopic[]>([])
const hotTopicsLoading = ref(false)
const hotVideos = ref<ShortVideoPost[]>([])
const hotVideosLoading = ref(false)
const mineOpen = ref(false)
type MineTab = 'works' | 'favorites' | 'likes'
const mineTab = ref<MineTab>('works')
const sharePostTarget = ref<ShortVideoPost | null>(null)
const shareForwardOpen = ref(false)
const shareForwardLoading = ref(false)
const highlightCommentId = ref<string | null>(null)
const editOpen = ref(false)
const editTarget = ref<ShortVideoPost | null>(null)
const editDesc = ref('')
const editVisibility = ref(0)
const replyToComment = ref<ShortVideoComment | null>(null)
const mediaSyncToken = ref(0)
const playErrorToastShown = ref(false)
const playbackRate = ref(1)
const showPlaybackControls = ref(false)
const showNotifications = ref(false)
const bellAnchorRef = ref<HTMLElement | null>(null)
const commentLoadingFor = ref<string | null>(null)
const commentLoadingMoreFor = ref<string | null>(null)
const authorOpen = ref(false)
const authorFollowLoading = ref(false)
const authorCoverFailed = ref<Record<string, boolean>>({})
const mineCoverFailed = ref<Record<string, boolean>>({})
const favoriteCoverFailed = ref<Record<string, boolean>>({})
const likedCoverFailed = ref<Record<string, boolean>>({})
const reportOpen = ref(false)
const reportTarget = ref<ShortVideoPost | null>(null)
const reportReason = ref('spam')
const reportDetail = ref('')
const reportSubmitting = ref(false)
const commentMentions = ref<{ id: string; name: string }[]>([])
const showCommentMention = ref(false)
const commentAtStart = ref(0)
const commentMentionQuery = ref('')
const commentMentionPickerRef = ref<InstanceType<typeof AtMentionPicker> | null>(null)
const commentImageInputRef = ref<HTMLInputElement | null>(null)
const commentImageKey = ref('')
const commentImagePreview = ref('')
const commentImageUploading = ref(false)
const showCommentEmoji = ref(false)
const commentEmojis = [...CHAT_EMOJIS]
const PLAYBACK_RATES = [1, 1.5, 2, 0.75]
const FEED_WINDOW_RADIUS = 2

const feedTabs: Array<{ id: ShortVideoFeedTab; label: string }> = [
  { id: 'following', label: t('shortVideo.following') },
  { id: 'friends', label: t('shortVideo.friends') },
  { id: 'recommend', label: t('shortVideo.recommend') }
]

const activePost = computed(() => posts.value[activeIndex.value])
const commentPost = computed(() => {
  const id = commentOpenFor.value
  if (!id) return null
  return posts.value.find(p => p.id === id) ?? null
})
const commentTree = computed(() => {
  if (!commentPost.value) return []
  return buildShortVideoCommentTree(commentPost.value.comments)
})
const canSubmitComment = computed(() => {
  return Boolean(commentText.value.trim() || commentImageKey.value) && !commentImageUploading.value
})
const showFeed = computed(() => posts.value.length > 0)
const currentUserId = computed(() => String(appStore.userProfile.userId || ''))

const isAuthorSelf = computed(() => {
  const id = authorProfile.value?.userId
  return Boolean(id && id === currentUserId.value)
})

const visibilityOptions = computed(() => [
  { value: 0, label: t('moments.public'), desc: t('moments.publicDesc') },
  { value: 1, label: t('moments.friendsOnly'), desc: t('moments.friendsOnlyDesc') },
  { value: 2, label: t('moments.private'), desc: t('moments.privateDesc') }
])

const reportReasonOptions = computed(() => [
  { value: 'spam', label: t('modals.reportReasonSpam') },
  { value: 'harassment', label: t('modals.reportReasonHarassment') },
  { value: 'fraud', label: t('modals.reportReasonFraud') },
  { value: 'porn', label: t('modals.reportReasonPorn') },
  { value: 'illegal', label: t('modals.reportReasonIllegal') },
  { value: 'other', label: t('modals.reportReasonOther') }
])

const commentMentionFriends = computed(() => {
  const list = contactsStore.friends
  const q = commentMentionQuery.value.trim().toLowerCase()
  if (!q) return list.slice(0, 12)
  return list.filter(f => f.name.toLowerCase().includes(q)).slice(0, 12)
})

const commentPlaceholder = computed(() => {
  if (!replyToComment.value) return t('shortVideo.commentPh')
  return t('moments.replyTo', {
    name: replyToComment.value.nickname || t('shortVideo.author')
  })
})

function isOwnPost(post: ShortVideoPost) {
  return Boolean(post.userId && currentUserId.value && post.userId === currentUserId.value)
}

async function bootstrapFeed() {
  const deepLinkPostId = String(route.query.post || '').trim()
  try {
    if (deepLinkPostId) {
      await store.ensureAuthReady()
      await store.openPostById(deepLinkPostId)
    } else {
      await store.ensurePanelReady()
    }
    await syncMediaSources()
    if (deepLinkPostId) {
      await nextTick()
      scrollToIndex(store.activeIndex)
      void router.replace({ path: route.path })
    }
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.loadFail')))
  }
}

async function retryFeed() {
  try {
    await store.fetchFeed(true)
    await syncMediaSources()
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.loadFail')))
  }
}

onMounted(() => {
  void bootstrapFeed()
})

watch(
  () => route.query.post,
  postId => {
    const id = String(postId || '').trim()
    if (!id) return
    void (async () => {
      try {
        await store.openPostById(id)
        await syncMediaSources()
        await nextTick()
        scrollToIndex(store.activeIndex)
        void router.replace({ path: route.path })
      } catch {
        message.error(t('shortVideo.postNotFound'))
      }
    })()
  }
)

onBeforeUnmount(() => {
  revokeBlobUrls()
})

watch(
  () => posts.value[0]?.id,
  (id, prev) => {
    if (prev && id !== prev) {
      videoSrcMap.value = {}
    }
    if (id) {
      void preloadMediaAround(activeIndex.value)
    }
  }
)

watch(
  () => posts.value.length,
  (len, prevLen) => {
    if (len > (prevLen ?? 0)) {
      void preloadMediaAround(activeIndex.value)
    }
  }
)

watch(activeIndex, (idx, prevIdx) => {
  playErrorToastShown.value = false
  showPlaybackControls.value = false
  const prevPost = posts.value[prevIdx ?? -1]
  if (prevPost) {
    userPausedIds.value.delete(prevPost.id)
  }
  void preloadMediaAround(idx)
})

watch(feedTab, () => {
  commentOpenFor.value = null
  replyToComment.value = null
})

function revokeBlobUrls() {
  for (const url of blobRevokeList.value) {
    try {
      URL.revokeObjectURL(url)
    } catch {
      /* ignore */
    }
  }
  blobRevokeList.value = []
}

async function preloadMediaAround(centerIndex: number) {
  const token = ++mediaSyncToken.value
  const next: Record<string, string> = { ...videoSrcMap.value }
  const keepIds = new Set<string>()
  const indices: number[] = []
  for (let i = centerIndex - FEED_WINDOW_RADIUS; i <= centerIndex + FEED_WINDOW_RADIUS; i++) {
    if (i >= 0 && i < posts.value.length) indices.push(i)
  }
  for (const idx of indices) {
    const post = posts.value[idx]
    if (!post) continue
    keepIds.add(post.id)
    if (next[post.id]) continue
    const { src } = await resolveShortVideoDisplaySrc(post.id, 'video', post.videoUrl)
    if (token !== mediaSyncToken.value) return
    if (src) next[post.id] = src
  }
  for (const id of Object.keys(next)) {
    if (!keepIds.has(id)) {
      delete next[id]
    }
  }
  if (token !== mediaSyncToken.value) return
  videoSrcMap.value = next
  await nextTick()
  playActiveVideo()
}

async function syncMediaSources() {
  videoSrcMap.value = {}
  await preloadMediaAround(activeIndex.value)
}

function setVideoRef(id: string, el: HTMLVideoElement | null) {
  if (el) {
    const isNew = videoRefs.value[id] !== el
    videoRefs.value[id] = el
    if (isNew && activePost.value?.id === id && !userPausedIds.value.has(id)) {
      void nextTick(() => playActiveVideo())
    }
    return
  }
  delete videoRefs.value[id]
}

async function onTabChange(tab: ShortVideoFeedTab) {
  if (feedTab.value === tab) return
  await store.setFeedTab(tab)
  await nextTick()
  playActiveVideo()
}

function onScroll() {
  const el = feedRef.value
  if (!el) return
  const height = el.clientHeight || 1
  const index = Math.round(el.scrollTop / height)
  if (index !== activeIndex.value) {
    store.setActiveIndex(index)
  }
  if (index >= posts.value.length - 3 && !loading.value) {
    if (store.searchMode) {
      void store.searchFeed(store.searchQuery, false)
    } else {
      void store.fetchFeed(false)
    }
  }
}

function scrollToIndex(index: number) {
  const el = feedRef.value
  if (!el || index < 0 || index >= posts.value.length) return
  store.setActiveIndex(index)
  el.scrollTo({ top: index * el.clientHeight, behavior: 'auto' })
}

function goPrev() {
  scrollToIndex(activeIndex.value - 1)
}

function goNext() {
  scrollToIndex(activeIndex.value + 1)
}

function playActiveVideo() {
  showPlaybackControls.value = false
  const current = activePost.value
  if (!current) return
  if (userPausedIds.value.has(current.id)) return
  const video = videoRefs.value[current.id]
  if (!video) {
    void nextTick(() => {
      const retry = videoRefs.value[current.id]
      if (retry && activePost.value?.id === current.id && !userPausedIds.value.has(current.id)) {
        playActiveVideo()
      }
    })
    return
  }
  for (const [id, el] of Object.entries(videoRefs.value)) {
    if (!el) continue
    if (id === current.id) {
      el.muted = muted.value
      el.playbackRate = playbackRate.value
      void el.play().then(() => {
        playbackTick.value++
        void store.markPlayed(current.id)
      }).catch(() => {
        playbackTick.value++
      })
    } else {
      el.pause()
      el.currentTime = 0
      progressMap.value[id] = 0
    }
  }
}

function onVideoError(postId: string) {
  const current = activePost.value
  if (!current || current.id !== postId || playErrorToastShown.value) return
  playErrorToastShown.value = true
  message.error(t('shortVideo.playFail'))
}

function togglePlay(post: ShortVideoPost) {
  const video = videoRefs.value[post.id]
  if (!video) return
  if (video.paused) {
    userPausedIds.value.delete(post.id)
    void video.play().then(() => {
      playbackTick.value++
    }).catch(() => {
      playbackTick.value++
    })
    if (post.id === activePost.value?.id) {
      showPlaybackControls.value = false
    }
  } else {
    userPausedIds.value.add(post.id)
    video.pause()
    playbackTick.value++
    if (post.id === activePost.value?.id) {
      showPlaybackControls.value = true
    }
  }
}

function toggleMute() {
  muted.value = !muted.value
  const video = activePost.value ? videoRefs.value[activePost.value.id] : null
  if (video) video.muted = muted.value
}

function cyclePlaybackRate() {
  const idx = PLAYBACK_RATES.indexOf(playbackRate.value)
  playbackRate.value = PLAYBACK_RATES[(idx + 1) % PLAYBACK_RATES.length]
  const video = activePost.value ? videoRefs.value[activePost.value.id] : null
  if (video) video.playbackRate = playbackRate.value
}

function playbackRateLabel() {
  const rate = playbackRate.value
  return rate % 1 === 0 ? `${rate}.0` : String(rate)
}

function onTimeUpdate(postId: string, e: Event) {
  const video = e.target as HTMLVideoElement
  if (!video.duration) return
  progressMap.value[postId] = video.currentTime / video.duration
}

function onVideoPlay(postId: string) {
  playbackTick.value++
  if (postId === activePost.value?.id) {
    showPlaybackControls.value = false
  }
}

function onVideoPause(postId: string) {
  playbackTick.value++
  if (postId === activePost.value?.id && userPausedIds.value.has(postId)) {
    showPlaybackControls.value = true
  }
}

function isPaused(post: ShortVideoPost) {
  void playbackTick.value
  if (post.id !== activePost.value?.id) return false
  const video = videoRefs.value[post.id]
  if (!video || !videoSrc(post)) return false
  return video.paused
}

function formatCount(n: number) {
  if (n >= 100000) return '10万+'
  if (n >= 10000) return `${Math.floor(n / 10000)}万+`
  return String(n)
}

function displayCommentCount(post: ShortVideoPost) {
  return store.commentCount(post)
}

function canLoadMoreComments(post: ShortVideoPost) {
  return post.comments.length < displayCommentCount(post)
}

async function openComments(post: ShortVideoPost, opts?: { highlightCommentId?: string }) {
  if (commentOpenFor.value === post.id && !opts?.highlightCommentId) {
    closeComments()
    return
  }
  commentOpenFor.value = post.id
  commentText.value = ''
  replyToComment.value = null
  commentMentions.value = []
  showCommentMention.value = false
  highlightCommentId.value = opts?.highlightCommentId || null
  commentLoadingFor.value = post.id
  try {
    await store.fetchComments(post.id, true)
    await scrollToHighlightedComment()
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.commentFail')))
  } finally {
    commentLoadingFor.value = null
  }
}

async function scrollToHighlightedComment() {
  const id = highlightCommentId.value
  if (!id) return
  await nextTick()
  window.setTimeout(() => {
    document.getElementById(`sv-comment-${id}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    window.setTimeout(() => {
      highlightCommentId.value = null
    }, 2500)
  }, 150)
}

function closeComments() {
  commentOpenFor.value = null
  replyToComment.value = null
  commentMentions.value = []
  showCommentMention.value = false
  showCommentEmoji.value = false
  clearCommentImage()
}

async function loadMoreComments(post: ShortVideoPost) {
  if (!canLoadMoreComments(post) || commentLoadingMoreFor.value === post.id) return
  commentLoadingMoreFor.value = post.id
  try {
    await store.fetchComments(post.id, false)
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.commentFail')))
  } finally {
    commentLoadingMoreFor.value = null
  }
}

async function handleNotificationSelect(notif: { relatedId?: string; extraId?: string; type: string }) {
  if (!notif.relatedId) return
  if (
    notif.type !== 'short_video_like' &&
    notif.type !== 'short_video_comment' &&
    notif.type !== 'short_video_mention'
  ) {
    return
  }
  showNotifications.value = false
  try {
    await store.openPostById(String(notif.relatedId))
    await syncMediaSources()
    await nextTick()
    scrollToIndex(store.activeIndex)
    if (notif.type === 'short_video_comment' || notif.type === 'short_video_mention') {
      const post = posts.value[store.activeIndex]
      if (post) {
        await openComments(post, { highlightCommentId: notif.extraId })
      }
    }
  } catch {
    message.error(t('shortVideo.postNotFound'))
  }
}

async function openAuthorProfile(post: ShortVideoPost) {
  if (!post.userId) return
  await openAuthorProfileByUser(post.userId, { nickname: post.nickname, avatar: post.avatar })
}

async function openAuthorProfileByUser(
  userId: string,
  profile?: { nickname?: string; avatar?: string }
) {
  authorCoverFailed.value = {}
  authorOpen.value = true
  mineOpen.value = false
  followingListOpen.value = false
  await store.fetchAuthorPosts(userId, profile)
}

async function openAuthorVideo(item: ShortVideoPost) {
  authorOpen.value = false
  try {
    await store.openPostById(item.id)
    await syncMediaSources()
    await nextTick()
    scrollToIndex(store.activeIndex)
  } catch {
    message.error(t('shortVideo.postNotFound'))
  }
}

function openFollowingList() {
  followingListOpen.value = true
}

function closeFollowingList() {
  followingListOpen.value = false
}

async function loadFollowingCount() {
  try {
    const res = await countFollowingShortVideoUsers()
    followingCount.value = res.code === 200 && typeof res.data === 'number' ? res.data : 0
  } catch {
    followingCount.value = 0
  }
}

function onFollowingUserSelect(user: ShortVideoFollowingUser) {
  void openAuthorProfileByUser(user.userId, { nickname: user.nickname, avatar: user.avatar })
}

async function onFollowingUnfollow(userId: string) {
  try {
    await store.toggleFollowAuthor(userId, true)
    if (followingCount.value > 0) {
      followingCount.value -= 1
    }
  } catch {
    message.error(t('shortVideo.followFail'))
  }
}

function closeAuthorProfile() {
  authorOpen.value = false
  store.clearAuthorPosts()
}

async function toggleAuthorFollow() {
  const profile = authorProfile.value
  if (!profile?.userId || isAuthorSelf.value) return
  authorFollowLoading.value = true
  try {
    await store.toggleFollowAuthor(profile.userId, profile.followingAuthor)
  } catch {
    message.error(t('shortVideo.followFail'))
  } finally {
    authorFollowLoading.value = false
  }
}

async function messageAuthor() {
  const profile = authorProfile.value
  if (!profile?.userId || isAuthorSelf.value) return
  try {
    await appStore.openPrivateChat(
      profile.userId,
      profile.nickname || t('shortVideo.author'),
      resolveUserAvatarUrl(profile.avatar, profile.userId)
    )
    closeAuthorProfile()
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('modals.openSessionFail')))
  }
}

function openReport(post: ShortVideoPost) {
  reportTarget.value = post
  reportReason.value = 'spam'
  reportDetail.value = ''
  reportOpen.value = true
}

function confirmNotInterested(post: ShortVideoPost) {
  dialog.info({
    title: t('shortVideo.notInterested'),
    content: t('shortVideo.notInterestedHint'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await store.markNotInterested(post.id)
        message.success(t('shortVideo.notInterestedOk'))
        await syncMediaSources()
        await nextTick()
        scrollToIndex(store.activeIndex)
      } catch (e) {
        message.error(resolveApiErrorMessage(e, t('shortVideo.notInterestedFail')))
      }
    }
  })
}

function confirmBlockAuthor(post: ShortVideoPost) {
  if (!post.userId) return
  dialog.warning({
    title: t('shortVideo.blockAuthor'),
    content: t('shortVideo.blockAuthorConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await store.blockAuthor(post.userId!)
        message.success(t('shortVideo.blockAuthorOk'))
        await syncMediaSources()
        await nextTick()
        scrollToIndex(store.activeIndex)
      } catch (e) {
        message.error(resolveApiErrorMessage(e, t('shortVideo.blockAuthorFail')))
      }
    }
  })
}

async function submitReport() {
  const post = reportTarget.value
  if (!post) return
  const detail = reportDetail.value.trim()
  reportSubmitting.value = true
  try {
    const res = await reportShortVideo(post.id, {
      reason: reportReason.value,
      detail: detail || undefined
    })
    if (res.code === 200) {
      message.success(t('shortVideo.reportOk'))
      reportOpen.value = false
      reportTarget.value = null
    } else {
      message.error(res.message || t('shortVideo.reportFail'))
    }
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.reportFail')))
  } finally {
    reportSubmitting.value = false
  }
}

function openPublish() {
  publishOpen.value = true
  publishDesc.value = ''
  publishVisibility.value = 0
  pendingFile.value = null
  mineOpen.value = false
}

async function openMine() {
  mineTab.value = 'works'
  mineCoverFailed.value = {}
  favoriteCoverFailed.value = {}
  likedCoverFailed.value = {}
  mineOpen.value = true
  await Promise.all([
    store.fetchMyPosts(true),
    store.fetchFavoritePosts(true),
    store.fetchLikedPosts(true),
    loadFollowingCount()
  ])
}

function onMineBodyScroll(e: Event) {
  const el = e.target as HTMLElement
  if (currentMineLoading.value || currentMineLoadingMore.value) return
  if (el.scrollTop + el.clientHeight < el.scrollHeight - 80) return
  void store.loadMoreMineTab(mineTab.value)
}

function onAuthorBodyScroll(e: Event) {
  const el = e.target as HTMLElement
  if (authorPostsLoading.value || authorPostsLoadingMore.value || !authorPostsHasMore.value) return
  if (el.scrollTop + el.clientHeight < el.scrollHeight - 80) return
  const userId = authorProfile.value?.userId
  if (!userId) return
  void store.fetchAuthorPosts(userId, undefined, false)
}

async function openMyVideo(item: ShortVideoPost) {
  mineOpen.value = false
  try {
    await store.openPostById(item.id)
    await syncMediaSources()
    await nextTick()
    scrollToIndex(store.activeIndex)
  } catch {
    message.error(t('shortVideo.postNotFound'))
  }
}

function buildShareLink(post: ShortVideoPost) {
  const base = window.location.href.split('#')[0]
  return `${base}#/short-video?post=${post.id}`
}

function openShareSheet(post: ShortVideoPost) {
  sharePostTarget.value = post
}

function closeShareSheet() {
  sharePostTarget.value = null
  shareForwardOpen.value = false
}

function openShareForward() {
  shareForwardOpen.value = true
}

async function confirmShareToChat(payload: { targetIds: string[]; leaveMessage: string }) {
  const post = sharePostTarget.value
  if (!post) return
  shareForwardLoading.value = true
  try {
    const res = await shareShortVideoToChat(post.id, {
      conversationIds: payload.targetIds,
      leaveMessage: payload.leaveMessage || undefined
    })
    if (res.code !== 200) {
      throw new Error(res.message || 'share failed')
    }
    void store.markShared(post.id)
    message.success(t('shortVideo.shareToChatOk'))
    shareForwardOpen.value = false
    closeShareSheet()
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.shareToChatFail')))
  } finally {
    shareForwardLoading.value = false
  }
}

async function confirmShareCopy() {
  const post = sharePostTarget.value
  if (!post) return
  const ok = await copyText(buildShareLink(post))
  if (ok) {
    void store.markShared(post.id)
    message.success(t('shortVideo.shareCopied'))
    closeShareSheet()
  } else {
    message.error(t('shortVideo.shareFail'))
  }
}

function moreOptions(post: ShortVideoPost): DropdownOption[] {
  const options: DropdownOption[] = [
    { label: t('viewer.copyLink'), key: 'copy' }
  ]
  if (!isOwnPost(post)) {
    options.push({ label: t('shortVideo.notInterested'), key: 'not-interested' })
    options.push({ label: t('shortVideo.blockAuthor'), key: 'block-author' })
    options.push({ label: t('shortVideo.report'), key: 'report' })
  }
  if (isOwnPost(post)) {
    options.push({ type: 'divider', key: 'divider' })
    options.push({ label: t('moments.editPost'), key: 'edit' })
    options.push({ label: t('shortVideo.deletePost'), key: 'delete' })
  }
  return options
}

function onMoreSelect(key: string, post: ShortVideoPost) {
  if (key === 'copy') {
    openShareSheet(post)
    return
  }
  if (key === 'report') {
    openReport(post)
    return
  }
  if (key === 'not-interested') {
    confirmNotInterested(post)
    return
  }
  if (key === 'block-author') {
    confirmBlockAuthor(post)
    return
  }
  if (key === 'edit') {
    openEdit(post)
    return
  }
  if (key === 'delete') {
    confirmDeletePost(post)
  }
}

function openEdit(post: ShortVideoPost) {
  editTarget.value = post
  editDesc.value = readableShortVideoText(post.description)
  editVisibility.value = post.visibility ?? 0
  editOpen.value = true
  mineOpen.value = false
}

async function submitEdit() {
  if (!editTarget.value) return
  try {
    await store.updatePost(editTarget.value.id, {
      description: editDesc.value.trim(),
      visibility: editVisibility.value
    })
    message.success(t('moments.editPostOk'))
    editOpen.value = false
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('moments.editPostFail')))
  }
}

function confirmDeletePost(post: ShortVideoPost) {
  dialog.warning({
    title: t('shortVideo.deletePost'),
    content: t('shortVideo.deletePostConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await store.deletePost(post.id)
        message.success(t('shortVideo.deletePostOk'))
        await syncMediaSources()
      } catch (e) {
        message.error(resolveApiErrorMessage(e, t('shortVideo.deletePostFail')))
      }
    }
  })
}

function pickVideo() {
  fileInputRef.value?.click()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('video/')) {
    message.warning(t('shortVideo.videoOnly'))
    return
  }
  if (file.size > SHORT_VIDEO_MAX_BYTES) {
    message.warning(t('shortVideo.videoTooLarge', { max: '100MB' }))
    return
  }
  pendingFile.value = file
}

async function submitPublish() {
  if (!pendingFile.value) {
    message.warning(t('shortVideo.needVideo'))
    return
  }
  try {
    const durationMs = await readVideoDurationMs(pendingFile.value)
    if (durationMs > SHORT_VIDEO_MAX_DURATION_MS) {
      message.warning(t('shortVideo.videoTooLong', { max: 60 }))
      return
    }
  } catch {
    /* optional */
  }
  try {
    await store.publish(pendingFile.value, publishDesc.value, publishVisibility.value)
    message.success(t('shortVideo.publishOk'))
    publishOpen.value = false
    await syncMediaSources()
    await nextTick()
    feedRef.value?.scrollTo({ top: 0 })
    playActiveVideo()
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.publishFail')))
  }
}

async function toggleFavorite(post: ShortVideoPost) {
  try {
    await store.toggleFavorite(post)
  } catch {
    message.error(t('shortVideo.favoriteFail'))
  }
}

function displayFavoriteCount(post: ShortVideoPost) {
  return post.favorites ?? 0
}

async function toggleLike(post: ShortVideoPost) {
  try {
    await store.toggleLike(post)
  } catch {
    message.error(t('shortVideo.likeFail'))
  }
}

async function toggleFollow(post: ShortVideoPost) {
  if (isOwnPost(post)) return
  try {
    await store.toggleFollow(post)
  } catch {
    message.error(t('shortVideo.followFail'))
  }
}

function startReply(comment: ShortVideoComment) {
  replyToComment.value = comment
  commentText.value = ''
  commentMentions.value = []
  showCommentMention.value = false
  showCommentEmoji.value = false
  void nextTick(() => {
    const ta = document.getElementById('short-video-comment-input') as HTMLInputElement | null
    ta?.focus()
  })
}

function cancelReply() {
  replyToComment.value = null
}

function clearCommentImage() {
  if (commentImagePreview.value.startsWith('blob:')) {
    try {
      URL.revokeObjectURL(commentImagePreview.value)
    } catch {
      /* ignore */
    }
  }
  commentImageKey.value = ''
  commentImagePreview.value = ''
  if (commentImageInputRef.value) {
    commentImageInputRef.value.value = ''
  }
}

function pickCommentImage() {
  commentImageInputRef.value?.click()
}

async function onCommentImageSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.warning(t('shortVideo.commentImageOnly'))
    input.value = ''
    return
  }
  commentImageUploading.value = true
  try {
    const res = await uploadShortVideoMedia(file)
    if (res.code !== 200 || !res.data) {
      throw new Error(res.message || 'upload failed')
    }
    clearCommentImage()
    commentImageKey.value = res.data
    commentImagePreview.value = URL.createObjectURL(file)
  } catch (err) {
    message.error(resolveApiErrorMessage(err, t('shortVideo.commentImageFail')))
  } finally {
    commentImageUploading.value = false
    input.value = ''
  }
}

function toggleCommentEmoji() {
  showCommentEmoji.value = !showCommentEmoji.value
  if (showCommentEmoji.value) {
    showCommentMention.value = false
  }
}

function appendCommentEmoji(emoji: string) {
  commentText.value += emoji
  showCommentEmoji.value = false
  void nextTick(() => {
    const ta = document.getElementById('short-video-comment-input') as HTMLInputElement | null
    ta?.focus()
  })
}

async function ensureFriendsLoaded() {
  if (!contactsStore.friends.length) {
    await contactsStore.fetchFriends()
  }
}

function detectCommentMention() {
  const ta = document.getElementById('short-video-comment-input') as HTMLInputElement | null
  if (!ta) return
  const value = commentText.value
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

function applyCommentMention(friend: { id: string; name: string }) {
  const name = (friend.name || '').trim()
  if (!name) return
  const before = commentText.value.slice(0, commentAtStart.value)
  const ta = document.getElementById('short-video-comment-input') as HTMLInputElement | null
  const cursor = ta?.selectionStart ?? commentAtStart.value
  const after = commentText.value.slice(cursor)
  const inserted = `@${name} `
  commentText.value = before + inserted + after
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

function onCommentKeyDown(e: KeyboardEvent, post: ShortVideoPost) {
  if (!showCommentMention.value) {
    if (e.key === 'Enter') {
      e.preventDefault()
      void submitComment(post)
    }
    return
  }
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    commentMentionPickerRef.value?.move(1)
    return
  }
  if (e.key === 'ArrowUp') {
    e.preventDefault()
    commentMentionPickerRef.value?.move(-1)
    return
  }
  if (e.key === 'Enter' || e.key === 'Tab') {
    e.preventDefault()
    const pick = commentMentionPickerRef.value?.confirm()
    if (pick) applyCommentMention(pick)
    return
  }
  if (e.key === 'Escape') {
    showCommentMention.value = false
  }
}

async function toggleCommentLike(post: ShortVideoPost, comment: ShortVideoComment) {
  try {
    await store.toggleCommentLike(post.id, comment)
  } catch {
    message.error(t('shortVideo.commentLikeFail'))
  }
}

function confirmDeleteComment(post: ShortVideoPost, comment: ShortVideoComment) {
  dialog.warning({
    title: t('shortVideo.deleteComment'),
    content: t('shortVideo.deleteCommentConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await store.removeComment(post.id, comment.id)
        message.success(t('shortVideo.deleteCommentOk'))
      } catch (e) {
        message.error(resolveApiErrorMessage(e, t('shortVideo.deleteCommentFail')))
      }
    }
  })
}

async function submitComment(post: ShortVideoPost) {
  if (showCommentMention.value && commentMentionFriends.value.length) {
    const pick = commentMentionPickerRef.value?.confirm()
    if (pick) applyCommentMention(pick)
  }
  const text = commentText.value.trim()
  if ((!text && !commentImageKey.value) || text.endsWith('@') || commentImageUploading.value) return
  const mentionIds = commentMentions.value.map(m => m.id).filter(Boolean)
  const imageKey = commentImageKey.value || undefined
  try {
    await store.addComment(post.id, text, replyToComment.value?.id, mentionIds, imageKey)
    commentText.value = ''
    commentMentions.value = []
    replyToComment.value = null
    showCommentMention.value = false
    showCommentEmoji.value = false
    clearCommentImage()
    message.success(t('shortVideo.commentOk'))
  } catch {
    message.error(t('shortVideo.commentFail'))
  }
}

function refreshSearchHistory() {
  searchHistory.value = loadShortVideoSearchHistory()
}

function removeSearchHistoryItem(query: string) {
  removeShortVideoSearchHistoryItem(query)
  refreshSearchHistory()
}

function clearSearchHistory() {
  clearShortVideoSearchHistory()
  refreshSearchHistory()
}

async function submitSearch() {
  const q = searchText.value.trim()
  searchOpen.value = false
  try {
    if (!q) {
      await store.clearSearch()
    } else if (q.startsWith('#') || q.startsWith('＃')) {
      saveShortVideoSearchQuery(q)
      refreshSearchHistory()
      await openTopicDetail(q.slice(1))
    } else {
      saveShortVideoSearchQuery(q)
      refreshSearchHistory()
      await store.searchFeed(q, true)
      await syncMediaSources()
    }
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.loadFail')))
  }
}

async function pickSearchHistory(query: string) {
  searchText.value = query
  await submitSearch()
}

async function openTopicDetail(name: string) {
  const normalized = name.replace(/^[#＃]/, '').trim()
  if (!normalized) return
  topicPlazaOpen.value = false
  searchOpen.value = false
  topicDetailName.value = normalized
  topicDetailOpen.value = true
}

function closeTopicDetail() {
  topicDetailOpen.value = false
  topicDetailName.value = ''
}

async function playTopicVideo(post: ShortVideoPost) {
  const name = topicDetailName.value
  topicDetailOpen.value = false
  if (!name) return
  const q = `#${name}`
  try {
    saveShortVideoSearchQuery(q)
    refreshSearchHistory()
    searchText.value = q
    await store.searchFeed(q, true)
    let idx = store.posts.findIndex(p => p.id === post.id)
    if (idx < 0) {
      await store.openPostById(post.id)
      store.searchMode = true
      store.searchQuery = q
      idx = store.posts.findIndex(p => p.id === post.id)
    }
    if (idx >= 0) {
      store.setActiveIndex(idx)
    }
    await syncMediaSources()
    await nextTick()
    scrollToIndex(store.activeIndex)
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.loadFail')))
  }
}

async function searchHashtag(tag: string) {
  const q = tag.startsWith('#') ? tag : `#${tag}`
  searchText.value = q
  saveShortVideoSearchQuery(q)
  refreshSearchHistory()
  await openTopicDetail(tag)
}

async function loadHotTopics() {
  hotTopicsLoading.value = true
  try {
    const res = await listHotShortVideoTopics(10)
    hotTopics.value = res.code === 200 && Array.isArray(res.data) ? res.data : []
  } catch {
    hotTopics.value = []
  } finally {
    hotTopicsLoading.value = false
  }
}

async function loadHotVideos() {
  hotVideosLoading.value = true
  try {
    const res = await listHotShortVideos(10)
    hotVideos.value = res.code === 200 && Array.isArray(res.data) ? res.data : []
  } catch {
    hotVideos.value = []
  } finally {
    hotVideosLoading.value = false
  }
}

async function openHotVideo(post: ShortVideoPost) {
  searchOpen.value = false
  try {
    await store.openPostById(post.id)
    await syncMediaSources()
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.loadFail')))
  }
}

async function onTopicPlazaSelect(tag: string) {
  await openTopicDetail(tag)
}

watch(searchOpen, open => {
  if (open) {
    refreshSearchHistory()
    void loadHotTopics()
    void loadHotVideos()
  }
})

watch(searchMode, mode => {
  if (mode) {
    searchText.value = searchQuery.value
  }
})

async function clearSearch() {
  searchText.value = ''
  try {
    await store.clearSearch()
    await syncMediaSources()
  } catch (e) {
    message.error(resolveApiErrorMessage(e, t('shortVideo.loadFail')))
  }
}

function avatarUrl(post: ShortVideoPost) {
  return resolveUserAvatarUrl(post.avatar, post.userId)
}

function coverPoster(post: ShortVideoPost) {
  return buildShortVideoMediaApiUrl(post.id, 'cover')
}

function authorCoverFailedFor(postId: string) {
  return Boolean(authorCoverFailed.value[postId])
}

function onAuthorCoverError(postId: string) {
  authorCoverFailed.value = { ...authorCoverFailed.value, [postId]: true }
}

function mineCoverFailedFor(postId: string) {
  if (mineTab.value === 'works') return Boolean(mineCoverFailed.value[postId])
  if (mineTab.value === 'favorites') return Boolean(favoriteCoverFailed.value[postId])
  return Boolean(likedCoverFailed.value[postId])
}

function onMineCoverError(postId: string) {
  if (mineTab.value === 'works') {
    mineCoverFailed.value = { ...mineCoverFailed.value, [postId]: true }
    return
  }
  if (mineTab.value === 'favorites') {
    favoriteCoverFailed.value = { ...favoriteCoverFailed.value, [postId]: true }
    return
  }
  likedCoverFailed.value = { ...likedCoverFailed.value, [postId]: true }
}

function shouldRenderSlide(index: number) {
  return Math.abs(index - activeIndex.value) <= FEED_WINDOW_RADIUS
}

function videoSrc(post: ShortVideoPost) {
  return videoSrcMap.value[post.id] || ''
}

function videoPreload(index: number) {
  return Math.abs(index - activeIndex.value) <= 1 ? 'auto' : 'metadata'
}
</script>

<template>
  <div class="short-video-main" :class="{ 'short-video-main--feed': showFeed }">
    <div class="short-video-stage">
      <div v-if="loading && posts.length === 0" class="short-video-empty">
        {{ t('common.loading') }}
      </div>

      <div v-else-if="posts.length === 0" class="short-video-empty">
        <p>{{ feedError || t('shortVideo.empty') }}</p>
        <button type="button" class="short-video-empty-publish" @click="openPublish">
          <NIcon :component="VideocamOutline" :size="18" />
          {{ t('shortVideo.publish') }}
        </button>
        <button
          v-if="feedError"
          type="button"
          class="short-video-empty-retry"
          @click="retryFeed"
        >
          {{ t('shortVideo.retryLoad') }}
        </button>
      </div>

      <div
        v-else
        ref="feedRef"
        class="short-video-feed"
        @scroll.passive="onScroll"
      >
        <section
          v-for="(post, index) in posts"
          :key="post.id"
          class="short-video-slide"
        >
          <template v-if="shouldRenderSlide(index)">
          <video
            v-if="videoSrc(post)"
            :ref="el => setVideoRef(post.id, el as HTMLVideoElement | null)"
            class="short-video-player"
            :src="videoSrc(post)"
            :poster="coverPoster(post)"
            playsinline
            loop
            :muted="muted"
            :preload="videoPreload(index)"
            @click="togglePlay(post)"
            @timeupdate="onTimeUpdate(post.id, $event)"
            @play="onVideoPlay(post.id)"
            @pause="onVideoPause(post.id)"
            @error="onVideoError(post.id)"
          />
          <div
            v-else
            class="short-video-player short-video-player--loading"
            :style="{ backgroundImage: `url(${coverPoster(post)})` }"
          >
            {{ t('common.loading') }}
          </div>

          <button
            v-if="isPaused(post)"
            type="button"
            class="short-video-play-overlay"
            aria-label="play"
            @click.stop="togglePlay(post)"
          >
            <NIcon :component="Play" :size="56" />
          </button>

          <div class="short-video-gradient" />

          <button
            type="button"
            class="short-video-tap-layer"
            aria-label="toggle play"
            @click="togglePlay(post)"
          />

          <div class="short-video-overlay">
            <div class="short-video-meta">
              <p v-if="readableShortVideoText(post.description)" class="short-video-desc">
                {{ readableShortVideoText(post.description) }}
              </p>
              <div v-if="post.topics?.length" class="short-video-tags">
                <button
                  v-for="tag in post.topics"
                  :key="tag"
                  type="button"
                  class="short-video-hashtag"
                  @click.stop="searchHashtag(tag)"
                >
                  #{{ tag }}
                </button>
              </div>
              <div class="short-video-author" @click.stop="openAuthorProfile(post)">
                <Avatar
                  :text="(post.nickname || t('shortVideo.author')).slice(0, 1)"
                  color="transparent"
                  :size="40"
                  :image-url="avatarUrl(post)"
                />
                <span class="short-video-name">{{ post.nickname || t('shortVideo.author') }}</span>
                <button
                  v-if="post.userId && !isOwnPost(post) && !post.followingAuthor"
                  type="button"
                  class="short-video-follow-pill"
                  @click.stop="toggleFollow(post)"
                >
                  +{{ t('shortVideo.follow') }}
                </button>
              </div>
            </div>

            <div class="short-video-rail">
              <div
                v-if="post.id === activePost?.id"
                class="short-video-nav-arrows"
              >
                <button
                  type="button"
                  class="short-video-nav-btn"
                  :disabled="activeIndex <= 0"
                  @click="goPrev"
                >
                  <NIcon :component="ArrowUp" :size="16" />
                </button>
                <button
                  type="button"
                  class="short-video-nav-btn"
                  :disabled="activeIndex >= posts.length - 1"
                  @click="goNext"
                >
                  <NIcon :component="ArrowDown" :size="16" />
                </button>
              </div>

              <div class="short-video-actions">
                <button type="button" class="short-video-action" @click="toggleLike(post)">
                  <NIcon
                    :component="post.liked ? Heart : HeartOutline"
                    :size="34"
                    :color="post.liked ? '#fe2c55' : '#fff'"
                  />
                  <span>{{ formatCount(post.likes) }}</span>
                </button>
                <button type="button" class="short-video-action" @click="openComments(post)">
                  <NIcon :component="ChatbubbleOutline" :size="32" />
                  <span>{{ formatCount(displayCommentCount(post)) }}</span>
                </button>
                <button type="button" class="short-video-action" @click="toggleFavorite(post)">
                  <NIcon
                    :component="post.favorited ? Bookmark : BookmarkOutline"
                    :size="32"
                    :color="post.favorited ? '#ffc107' : '#fff'"
                  />
                  <span>{{ formatCount(displayFavoriteCount(post)) }}</span>
                </button>
                <button type="button" class="short-video-action" :title="t('shortVideo.share')" @click="openShareSheet(post)">
                  <NIcon :component="ShareSocialOutline" :size="32" />
                </button>
              </div>
            </div>
          </div>

          <div
            v-if="showPlaybackControls && isPaused(post) && post.id === activePost?.id"
            class="short-video-controls"
          >
            <button type="button" class="short-video-controls__btn" @click="togglePlay(post)">
              <NIcon :component="isPaused(post) ? Play : Pause" :size="14" />
            </button>
            <div class="short-video-progress">
              <div
                class="short-video-progress__bar"
                :style="{ width: `${(progressMap[post.id] || 0) * 100}%` }"
              />
            </div>
            <button type="button" class="short-video-controls__speed" @click="cyclePlaybackRate">
              {{ t('shortVideo.speed', { n: playbackRateLabel() }) }}
            </button>
            <button type="button" class="short-video-controls__btn" @click="toggleMute">
              <NIcon :component="muted ? VolumeMuteOutline : VolumeHighOutline" :size="16" />
            </button>
            <n-dropdown
              trigger="click"
              placement="top-end"
              :options="moreOptions(post)"
              @select="key => onMoreSelect(String(key), post)"
            >
              <button type="button" class="short-video-controls__btn">
                <NIcon :component="EllipsisHorizontal" :size="16" />
              </button>
            </n-dropdown>
          </div>
          </template>
          <div v-else class="short-video-slide-placeholder" aria-hidden="true" />
        </section>
      </div>

      <div
        v-if="commentPost"
        class="short-video-comment-sheet"
        @click.self="closeComments"
      >
        <div class="short-video-comment-drawer" @click.stop>
          <div class="short-video-comment-header">
            <span class="short-video-comment-header__title">
              {{ t('shortVideo.commentCountTitle', { n: displayCommentCount(commentPost) }) }}
            </span>
            <button
              type="button"
              class="short-video-comment-header__close"
              :aria-label="t('common.close')"
              @click="closeComments"
            >
              <NIcon :component="CloseOutline" :size="22" />
            </button>
          </div>

          <div class="short-video-comment-list">
            <p v-if="commentLoadingFor === commentPost.id" class="short-video-comment-empty">
              {{ t('common.loading') }}
            </p>
            <template v-else>
              <button
                v-if="canLoadMoreComments(commentPost)"
                type="button"
                class="short-video-comment-load-more"
                :disabled="commentLoadingMoreFor === commentPost.id"
                @click="loadMoreComments(commentPost)"
              >
                {{ commentLoadingMoreFor === commentPost.id ? t('common.loading') : t('shortVideo.loadMoreComments') }}
              </button>
              <p
                v-if="commentTree.length === 0 && commentLoadingFor !== commentPost.id"
                class="short-video-comment-empty short-video-comment-empty--center"
              >
                {{ t('shortVideo.noComments') }}
              </p>
              <ShortVideoCommentNode
                v-for="node in commentTree"
                :key="node.id"
                :node="node"
                :post="commentPost"
                :current-user-id="currentUserId"
                :highlight-comment-id="highlightCommentId"
                @reply="startReply"
                @delete="c => commentPost && confirmDeleteComment(commentPost, c)"
                @like="c => commentPost && toggleCommentLike(commentPost, c)"
              />
            </template>
          </div>

          <div class="short-video-comment-footer">
            <div v-if="replyToComment" class="short-video-reply-hint">
              <span>{{ t('moments.replyTo', { name: replyToComment.nickname || t('shortVideo.author') }) }}</span>
              <button type="button" class="short-video-comment-btn" @click="cancelReply">
                {{ t('common.cancel') }}
              </button>
            </div>
            <div v-if="commentImagePreview" class="short-video-comment-image-preview">
              <img :src="commentImagePreview" alt="" />
              <button type="button" class="short-video-comment-image-preview__remove" @click="clearCommentImage">
                <NIcon :component="CloseOutline" :size="14" />
              </button>
            </div>
            <div v-if="showCommentEmoji" class="short-video-comment-emoji-panel">
              <button
                v-for="emoji in commentEmojis"
                :key="emoji"
                type="button"
                class="short-video-comment-emoji-btn"
                @click="appendCommentEmoji(emoji)"
              >
                {{ emoji }}
              </button>
            </div>
            <div class="short-video-comment-compose">
              <button
                type="button"
                class="short-video-comment-compose__tool"
                :title="t('chat.emoji')"
                @click="toggleCommentEmoji"
              >
                <NIcon :component="HappyOutline" :size="20" />
              </button>
              <button
                type="button"
                class="short-video-comment-compose__tool"
                :title="t('shortVideo.commentImage')"
                :disabled="commentImageUploading"
                @click="pickCommentImage"
              >
                <NIcon :component="ImageOutline" :size="20" />
              </button>
              <div class="short-video-comment-compose__input-wrap">
                <input
                  id="short-video-comment-input"
                  v-model="commentText"
                  class="short-video-comment-compose__input"
                  type="text"
                  :placeholder="commentPlaceholder"
                  @input="detectCommentMention"
                  @keydown="onCommentKeyDown($event, commentPost)"
                />
                <AtMentionPicker
                  v-if="showCommentMention"
                  ref="commentMentionPickerRef"
                  :friends="commentMentionFriends"
                  :text="commentText"
                  :caret-index="commentAtStart + 1"
                  placement="top"
                  @apply="applyCommentMention"
                  @close="showCommentMention = false"
                />
              </div>
              <button
                type="button"
                class="short-video-comment-compose__send"
                :disabled="!canSubmitComment"
                @click="submitComment(commentPost)"
              >
                {{ commentImageUploading ? t('common.loading') : t('shortVideo.send') }}
              </button>
            </div>
            <input
              ref="commentImageInputRef"
              type="file"
              accept="image/*"
              class="short-video-comment-image-input"
              @change="onCommentImageSelected"
            />
          </div>
        </div>
      </div>

      <header
        class="short-video-topbar"
        :class="{
          'short-video-topbar--overlay': showFeed && !searchMode,
          'short-video-topbar--inline-search': searchMode
        }"
      >
        <div v-if="searchMode" class="short-video-topbar__search-nav">
          <ShortVideoSearchNav
            :model-value="searchText"
            @update:model-value="searchText = $event"
            @back="clearSearch"
            @submit="submitSearch"
          />
        </div>
        <div v-else class="short-video-topbar__row">
        <div class="short-video-topbar__side short-video-topbar__side--left">
          <button type="button" class="short-video-topbar__icon" :title="t('shortVideo.publish')" @click="openPublish">
            <NIcon :component="VideocamOutline" :size="22" />
          </button>
          <button type="button" class="short-video-topbar__icon" :title="t('shortVideo.topicPlaza')" @click="topicPlazaOpen = true">
            <NIcon :component="PricetagsOutline" :size="22" />
          </button>
        </div>

        <nav class="short-video-topbar__tabs">
          <button
            v-for="tab in feedTabs"
            :key="tab.id"
            type="button"
            class="short-video-topbar__tab"
            :class="{ active: feedTab === tab.id }"
            @click="onTabChange(tab.id)"
          >
            {{ tab.label }}
          </button>
        </nav>

        <div class="short-video-topbar__side short-video-topbar__side--right">
          <button
            ref="bellAnchorRef"
            type="button"
            class="short-video-topbar__icon short-video-topbar__icon--bell"
            :title="t('shortVideo.allInteractiveMessages')"
            @click="showNotifications = !showNotifications"
          >
            <NIcon :component="NotificationsOutline" :size="22" />
            <span v-if="shortVideoUnreadCount > 0" class="short-video-bell-badge">
              {{ shortVideoUnreadCount > 99 ? '99+' : shortVideoUnreadCount }}
            </span>
          </button>
          <button type="button" class="short-video-topbar__icon" :title="t('shortVideo.search')" @click="searchOpen = true">
            <NIcon :component="SearchOutline" :size="22" />
          </button>
          <button type="button" class="short-video-topbar__icon" :title="t('shortVideo.mine')" @click="openMine">
            <NIcon :component="PersonOutline" :size="22" />
          </button>
        </div>
        </div>
      </header>
    </div>

    <ShortVideoTopicPlaza :open="topicPlazaOpen" @close="topicPlazaOpen = false" @select="onTopicPlazaSelect" />
    <ShortVideoTopicDetail
      :open="topicDetailOpen"
      :topic-name="topicDetailName"
      @close="closeTopicDetail"
      @play="playTopicVideo"
    />

    <ShortVideoSearchPage
      :open="searchOpen"
      :search-text="searchText"
      :search-history="searchHistory"
      :hot-topics="hotTopics"
      :hot-topics-loading="hotTopicsLoading"
      :hot-videos="hotVideos"
      :hot-videos-loading="hotVideosLoading"
      @close="searchOpen = false"
      @update:search-text="searchText = $event"
      @submit="submitSearch"
      @pick-history="pickSearchHistory"
      @remove-history="removeSearchHistoryItem"
      @clear-history="clearSearchHistory"
      @pick-topic="searchHashtag"
      @pick-video="openHotVideo"
    />

    <ShortVideoSubPageShell
      v-if="authorOpen"
      :title="authorProfile?.nickname || t('shortVideo.author')"
      body-class="sv-subpage__body--flush"
      @close="closeAuthorProfile"
    >
      <div class="sv-profile-header">
        <div class="sv-profile-header__row">
          <Avatar
            :text="(authorProfile?.nickname || t('shortVideo.author')).slice(0, 1)"
            color="transparent"
            :size="48"
            :image-url="resolveUserAvatarUrl(authorProfile?.avatar, authorProfile?.userId)"
          />
          <div class="sv-profile-header__info">
            <span class="sv-profile-header__name">
              {{ authorProfile?.nickname || t('shortVideo.author') }}
            </span>
            <div class="sv-profile-stats">
              <div class="sv-profile-stat">
                <span class="sv-profile-stat__value">{{ authorProfile?.postCount ?? authorPosts.length }}</span>
                <span class="sv-profile-stat__label">{{ t('shortVideo.authorVideos') }}</span>
              </div>
              <div class="sv-profile-stat">
                <span class="sv-profile-stat__value">{{ authorProfile?.followerCount ?? 0 }}</span>
                <span class="sv-profile-stat__label">{{ t('shortVideo.authorFollowers') }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="!isAuthorSelf" class="sv-profile-actions">
          <LxButton
            size="small"
            :variant="authorProfile?.followingAuthor ? 'block' : 'modal-primary'"
            :loading="authorFollowLoading"
            @click="toggleAuthorFollow"
          >
            {{ authorProfile?.followingAuthor ? t('shortVideo.followed') : t('shortVideo.follow') }}
          </LxButton>
          <LxButton size="small" variant="block" @click="messageAuthor">
            {{ t('shortVideo.messageAuthor') }}
          </LxButton>
        </div>
      </div>
      <h4 class="sv-profile-section-title">{{ t('shortVideo.authorVideos') }}</h4>
      <div class="short-video-mine-body" @scroll="onAuthorBodyScroll">
        <div v-if="authorPostsLoading" class="short-video-mine-empty">{{ t('common.loading') }}</div>
        <div v-else-if="authorPosts.length === 0" class="short-video-mine-empty">{{ t('shortVideo.noAuthorVideos') }}</div>
        <div v-else class="short-video-mine-grid">
          <div
            v-for="item in authorPosts"
            :key="item.id"
            class="short-video-mine-cell"
          >
            <button
              type="button"
              class="short-video-mine-tile"
              :title="readableShortVideoText(item.description) || t('shortVideo.empty')"
              @click="openAuthorVideo(item)"
            >
              <img
                v-if="!authorCoverFailedFor(item.id)"
                :src="coverPoster(item)"
                class="short-video-mine-tile__cover"
                alt=""
                loading="lazy"
                @error="onAuthorCoverError(item.id)"
              />
              <div v-else class="short-video-mine-tile__fallback">
                <NIcon :component="VideocamOutline" :size="22" />
              </div>
              <span class="short-video-mine-tile__plays">
                <NIcon :component="Play" :size="10" />
                {{ formatCount(item.playCount || 0) }}
              </span>
            </button>
          </div>
        </div>
        <div v-if="authorPostsLoadingMore" class="short-video-mine-empty short-video-mine-empty--inline">
          {{ t('common.loading') }}
        </div>
      </div>
    </ShortVideoSubPageShell>

    <div v-if="reportOpen" class="short-video-modal" @click.self="reportOpen = false">
      <div class="short-video-modal-card">
        <h3>{{ t('shortVideo.reportTitle') }}</h3>
        <p class="short-video-report-hint">{{ t('shortVideo.reportHint') }}</p>
        <div class="short-video-report-reasons">
          <n-radio-group v-model:value="reportReason" name="sv-report-reason">
            <div v-for="opt in reportReasonOptions" :key="opt.value" class="short-video-report-reason">
              <n-radio :value="opt.value">{{ opt.label }}</n-radio>
            </div>
          </n-radio-group>
        </div>
        <NInput
          v-model:value="reportDetail"
          type="textarea"
          :placeholder="t('modals.reportDetailPh')"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
        <div class="short-video-modal-actions">
          <LxButton @click="reportOpen = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" :loading="reportSubmitting" @click="submitReport">
            {{ t('modals.reportSubmit') }}
          </LxButton>
        </div>
      </div>
    </div>

    <ShortVideoSubPageShell
      v-if="mineOpen"
      :title="t('shortVideo.mine')"
      body-class="sv-subpage__body--flush"
      @close="mineOpen = false"
    >
      <div class="sv-profile-header">
        <div class="sv-profile-header__row">
          <Avatar
            :text="(appStore.userProfile.nickname || t('shortVideo.author')).slice(0, 1)"
            color="transparent"
            :size="56"
            :image-url="resolveUserAvatarUrl(appStore.userProfile.avatar, appStore.userProfile.userId)"
          />
          <div class="sv-profile-header__info">
            <span class="sv-profile-header__name">
              {{ appStore.userProfile.nickname || t('shortVideo.author') }}
            </span>
            <button type="button" class="sv-profile-publish" @click="openPublish">
              <NIcon :component="VideocamOutline" :size="16" />
              {{ t('shortVideo.publish') }}
            </button>
            <div class="sv-profile-stats">
              <button type="button" class="sv-profile-stat sv-profile-stat--link" @click="openFollowingList">
                <span class="sv-profile-stat__value">{{ followingCount }}</span>
                <span class="sv-profile-stat__label">{{ t('shortVideo.following') }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="sv-profile-tabs" role="tablist">
        <button
          v-for="tab in mineTabs"
          :key="tab.key"
          type="button"
          role="tab"
          class="sv-profile-tab"
          :class="{ 'sv-profile-tab--active': mineTab === tab.key }"
          :aria-selected="mineTab === tab.key"
          @click="mineTab = tab.key"
        >
          <NIcon :component="tab.icon" :size="18" />
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <div class="short-video-mine-body" @scroll="onMineBodyScroll">
        <div v-if="currentMineLoading" class="short-video-mine-empty">{{ t('common.loading') }}</div>
        <div v-else-if="currentMinePosts.length === 0" class="short-video-mine-empty">
          {{ currentMineEmptyText }}
        </div>
        <div v-else class="short-video-mine-grid">
          <div
            v-for="item in currentMinePosts"
            :key="`${mineTab}-${item.id}`"
            class="short-video-mine-cell"
          >
            <div class="short-video-mine-tile-wrap">
              <button
                type="button"
                class="short-video-mine-tile"
                :title="readableShortVideoText(item.description) || t('shortVideo.empty')"
                @click="openMyVideo(item)"
              >
                <img
                  v-if="!mineCoverFailedFor(item.id)"
                  :src="coverPoster(item)"
                  class="short-video-mine-tile__cover"
                  alt=""
                  loading="lazy"
                  @error="onMineCoverError(item.id)"
                />
                <div v-else class="short-video-mine-tile__fallback">
                  <NIcon :component="VideocamOutline" :size="22" />
                </div>
                <span class="short-video-mine-tile__plays">
                  <NIcon :component="Play" :size="10" />
                  {{ formatCount(item.playCount || 0) }}
                </span>
              </button>
              <div v-if="mineTab === 'works'" class="short-video-mine-tile__actions">
                <button
                  type="button"
                  class="short-video-mine-tile__action"
                  @click.stop="openEdit(item)"
                >
                  {{ t('moments.editPost') }}
                </button>
                <button
                  type="button"
                  class="short-video-mine-tile__action short-video-mine-tile__action--danger"
                  @click.stop="confirmDeletePost(item)"
                >
                  {{ t('common.delete') }}
                </button>
              </div>
            </div>
          </div>
        </div>
        <div v-if="currentMineLoadingMore" class="short-video-mine-empty short-video-mine-empty--inline">
          {{ t('common.loading') }}
        </div>
      </div>
    </ShortVideoSubPageShell>

    <ShortVideoFollowingList
      :open="followingListOpen"
      @close="closeFollowingList"
      @select="onFollowingUserSelect"
      @unfollow="onFollowingUnfollow"
    />

    <div v-if="sharePostTarget" class="short-video-share-sheet" @click.self="closeShareSheet">
      <div class="short-video-share-panel" @click.stop>
        <div class="short-video-share-panel__header">
          <span>{{ t('shortVideo.shareTitle') }}</span>
          <button
            type="button"
            class="short-video-share-panel__close"
            :aria-label="t('common.close')"
            @click="closeShareSheet"
          >
            <NIcon :component="CloseOutline" :size="20" />
          </button>
        </div>
        <button type="button" class="short-video-share-panel__item" @click="openShareForward">
          <NIcon :component="ChatbubbleOutline" :size="22" />
          <span>{{ t('shortVideo.shareToChat') }}</span>
        </button>
        <button type="button" class="short-video-share-panel__item" @click="confirmShareCopy">
          <NIcon :component="ShareSocialOutline" :size="22" />
          <span>{{ t('viewer.copyLink') }}</span>
        </button>
      </div>
    </div>

    <ForwardPickerModal
      v-model:show="shareForwardOpen"
      embedded
      :loading="shareForwardLoading"
      :preview-text="sharePreviewText"
      :preview-image-url="sharePreviewImageUrl"
      @confirm="confirmShareToChat"
    />

    <div v-if="publishOpen" class="short-video-modal" @click.self="!publishing && (publishOpen = false)">
      <div class="short-video-modal-card">
        <h3>{{ t('shortVideo.publishTitle') }}</h3>
        <p class="short-video-publish-hint">{{ pendingFile?.name || t('shortVideo.pickVideo') }}</p>
        <p class="short-video-publish-limit">{{ t('shortVideo.uploadLimitHint', { size: '100MB', seconds: 60 }) }}</p>
        <LxButton size="small" :disabled="publishing" @click="pickVideo">{{ t('shortVideo.pickVideo') }}</LxButton>
        <div v-if="publishing" class="short-video-publish-progress-wrap">
          <span class="short-video-publish-progress__text">
            {{ t('shortVideo.uploadingProgress', { n: publishProgress }) }}
          </span>
          <div class="short-video-publish-progress">
            <div class="short-video-publish-progress__bar" :style="{ width: `${publishProgress}%` }" />
          </div>
        </div>
        <NInput
          v-model:value="publishDesc"
          type="textarea"
          :placeholder="t('shortVideo.descPh')"
          :autosize="{ minRows: 3, maxRows: 6 }"
          :disabled="publishing"
        />
        <div class="short-video-visibility">
          <span class="short-video-visibility__label">{{ t('moments.whoCanSee') }}</span>
          <n-radio-group v-model:value="publishVisibility" class="short-video-visibility__options">
            <div v-for="opt in visibilityOptions" :key="opt.value" class="short-video-visibility__option">
              <n-radio :value="opt.value">{{ opt.label }}</n-radio>
              <span class="short-video-visibility__desc">{{ opt.desc }}</span>
            </div>
          </n-radio-group>
        </div>
        <div class="short-video-modal-actions">
          <LxButton :disabled="publishing" @click="publishOpen = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" :loading="publishing" :disabled="publishing" @click="submitPublish">
            {{ t('shortVideo.publish') }}
          </LxButton>
        </div>
      </div>
    </div>

    <div v-if="editOpen" class="short-video-modal" @click.self="editOpen = false">
      <div class="short-video-modal-card">
        <h3>{{ t('shortVideo.editTitle') }}</h3>
        <NInput
          v-model:value="editDesc"
          type="textarea"
          :placeholder="t('shortVideo.descPh')"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
        <div class="short-video-visibility">
          <span class="short-video-visibility__label">{{ t('moments.whoCanSee') }}</span>
          <n-radio-group v-model:value="editVisibility" class="short-video-visibility__options">
            <div v-for="opt in visibilityOptions" :key="opt.value" class="short-video-visibility__option">
              <n-radio :value="opt.value">{{ opt.label }}</n-radio>
              <span class="short-video-visibility__desc">{{ opt.desc }}</span>
            </div>
          </n-radio-group>
        </div>
        <div class="short-video-modal-actions">
          <LxButton @click="editOpen = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" @click="submitEdit">
            {{ t('common.save') }}
          </LxButton>
        </div>
      </div>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      accept="video/*"
      class="hidden-input"
      @change="onFileChange"
    />

    <div
      v-if="showNotifications"
      class="notif-dismiss-layer"
      @click="showNotifications = false"
    />

    <ShortVideoNotificationsPage
      embedded
      :visible="showNotifications"
      :anchor-el="bellAnchorRef"
      @close="showNotifications = false"
      @select="handleNotificationSelect"
    />
  </div>
</template>

<style scoped>
.short-video-main {
  flex: 1;
  min-height: 0;
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  background: #111;
}

.short-video-main--feed {
  background: #000;
}

.short-video-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.short-video-topbar {
  position: relative;
  z-index: 30;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  background: var(--lx-bg-panel);
}

.short-video-topbar__row {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  min-height: 48px;
  padding: 0 10px;
  width: 100%;
}

.short-video-topbar__row--search {
  grid-template-columns: var(--lx-size-control) 1fr auto;
  gap: var(--lx-space);
  padding: var(--lx-space) var(--lx-space-md);
}

.short-video-topbar--inline-search {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 30;
  background: var(--lx-bg-card);
  border-bottom: 1px solid var(--lx-border-light);
}

.short-video-topbar__search-nav {
  width: 100%;
  padding: var(--lx-space) var(--lx-space-md);
}

.short-video-topbar--overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.5) 0%, rgba(0, 0, 0, 0) 100%);
  border-bottom: none;
}

.short-video-topbar__side {
  display: flex;
  align-items: center;
  min-width: 0;
}

.short-video-topbar__side--left {
  justify-content: flex-start;
  gap: 4px;
}

.short-video-topbar__side--right {
  justify-content: flex-end;
  gap: 4px;
}

.short-video-topbar__icon {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.short-video-topbar--overlay .short-video-topbar__icon {
  color: #fff;
}

.short-video-topbar__icon:hover {
  background: rgba(255, 255, 255, 0.12);
}

.short-video-topbar__icon--bell {
  position: relative;
}

.short-video-bell-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--lx-danger);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  pointer-events: none;
}

.short-video-topbar__tabs {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
}

.short-video-topbar__tab {
  position: relative;
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  font-size: 15px;
  font-weight: 500;
  padding: 10px 2px;
  cursor: pointer;
  white-space: nowrap;
}

.short-video-topbar--overlay .short-video-topbar__tab {
  color: rgba(255, 255, 255, 0.72);
}

.short-video-topbar__tab.active {
  color: var(--lx-text-body);
  font-weight: 600;
}

.short-video-topbar--overlay .short-video-topbar__tab.active {
  color: #fff;
}

.short-video-topbar__tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 4px;
  width: 22px;
  height: 2px;
  transform: translateX(-50%);
  border-radius: 2px;
  background: var(--lx-accent);
}

.short-video-topbar--overlay .short-video-topbar__tab.active::after {
  background: #fff;
}

.short-video-feed {
  position: absolute;
  inset: 0;
  overflow-y: auto;
  scroll-snap-type: y mandatory;
  scrollbar-width: none;
  background: #000;
}

.short-video-feed::-webkit-scrollbar {
  display: none;
}

.short-video-slide {
  position: relative;
  height: 100%;
  min-height: 100%;
  scroll-snap-align: start;
  scroll-snap-stop: always;
  background: #000;
}

.short-video-slide-placeholder {
  width: 100%;
  height: 100%;
  min-height: 100%;
  background: #000;
}

.short-video-player {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #000;
  cursor: pointer;
}

.short-video-player--loading {
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
  background-color: #000;
  background-size: cover;
  background-position: center;
}

.short-video-play-overlay {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 12;
  width: 72px;
  height: 72px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);
}

.short-video-gradient {
  position: absolute;
  inset: auto 0 0;
  height: 46%;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, 0.58) 100%);
  pointer-events: none;
  z-index: 8;
}

.short-video-tap-layer {
  position: absolute;
  inset: 0;
  z-index: 9;
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  cursor: pointer;
}

.short-video-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 48px 8px 64px 12px;
  pointer-events: none;
  z-index: 10;
}

.short-video-meta,
.short-video-rail {
  pointer-events: auto;
}

.short-video-meta {
  max-width: calc(100% - 58px);
  min-width: 0;
  color: #fff;
}

.short-video-desc {
  margin: 0 0 8px;
  line-height: 1.4;
  font-size: 13px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.short-video-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 8px;
}

.short-video-hashtag {
  border: none;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  color: #6eb6ff;
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
}

.short-video-hashtag:hover {
  background: rgba(255, 255, 255, 0.28);
}

.short-video-author {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.short-video-name {
  font-weight: 600;
  font-size: 14px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
}

.short-video-follow-pill {
  border: none;
  border-radius: var(--lx-radius-xs);
  background: #20d492;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 3px 8px;
  flex-shrink: 0;
  cursor: pointer;
}

.short-video-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.short-video-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.short-video-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: #fff;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 11px;
  min-width: 44px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
}

.short-video-controls {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 8px;
  z-index: 14;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fff;
}

.short-video-controls__btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.short-video-controls__speed {
  font-size: 11px;
  opacity: 0.9;
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: #fff;
  cursor: pointer;
  padding: 0 2px;
}

.short-video-progress {
  flex: 1;
  height: 2px;
  background: rgba(255, 255, 255, 0.28);
  border-radius: 2px;
  overflow: hidden;
}

.short-video-progress__bar {
  height: 100%;
  background: #fff;
  border-radius: 2px;
  transition: width 0.1s linear;
}

.short-video-nav-arrows {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.32);
  backdrop-filter: blur(8px);
}

.short-video-nav-btn {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.short-video-nav-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.short-video-comment-sheet {
  position: absolute;
  inset: 0;
  z-index: 35;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  background: rgba(0, 0, 0, 0.45);
  animation: short-video-comment-fade-in 0.2s ease;
}

@keyframes short-video-comment-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.short-video-comment-drawer {
  display: flex;
  flex-direction: column;
  max-height: 68%;
  min-height: 38%;
  background: rgba(22, 22, 22, 0.96);
  color: #fff;
  border-radius: var(--lx-radius-lg) var(--lx-radius-lg) 0 0;
  overflow: hidden;
  animation: short-video-comment-slide-up 0.24s ease;
}

@keyframes short-video-comment-slide-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.short-video-comment-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 44px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.short-video-comment-header__title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

.short-video-comment-header__close {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.short-video-comment-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 14px 12px;
}

.short-video-comment-reply {
  color: rgba(255, 255, 255, 0.55);
}

.short-video-comment-btn {
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.55);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.short-video-comment-btn--danger {
  color: #ff8a8a;
}

.short-video-reply-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  padding: 0 2px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
}

.short-video-comment-empty {
  margin: 0;
  padding: 8px 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
}

.short-video-comment-empty--center {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}

.short-video-comment-load-more {
  display: block;
  width: 100%;
  margin-bottom: 4px;
  padding: 8px 0;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.55);
  font-size: 12px;
  cursor: pointer;
}

.short-video-comment-load-more:disabled {
  opacity: 0.6;
  cursor: default;
}

.short-video-comment-footer {
  flex-shrink: 0;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(22, 22, 22, 0.98);
}

.short-video-comment-image-preview {
  position: relative;
  display: inline-block;
  margin-bottom: 8px;
}

.short-video-comment-image-preview img {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  object-fit: cover;
}

.short-video-comment-image-preview__remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.short-video-comment-emoji-panel {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 4px;
  margin-bottom: 8px;
  padding: 8px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
}

.short-video-comment-emoji-btn {
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  padding: 4px;
  border-radius: 8px;
}

.short-video-comment-emoji-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.short-video-comment-compose {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 42px;
  padding: 4px 6px 4px 8px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.14);
}

.short-video-comment-compose__tool {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.short-video-comment-compose__tool:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.short-video-comment-compose__input-wrap {
  position: relative;
  flex: 1;
  min-width: 0;
}

.short-video-comment-compose__input {
  width: 100%;
  height: 34px;
  border: none;
  outline: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.95);
  font-size: 14px;
}

.short-video-comment-compose__input::placeholder {
  color: rgba(255, 255, 255, 0.45);
}

.short-video-comment-compose__send {
  flex-shrink: 0;
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.14);
  color: #6eb6ff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.short-video-comment-compose__send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.short-video-comment-image-input {
  display: none;
}

.notif-dismiss-layer {
  position: absolute;
  inset: 0;
  z-index: calc(var(--lx-z-critical) - 1);
  background: transparent;
  cursor: default;
}

.short-video-report-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.short-video-report-reasons {
  margin-bottom: 12px;
}

.short-video-report-reason {
  margin-bottom: 6px;
}

.short-video-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--lx-text-secondary);
  padding-top: 48px;
  background: var(--lx-bg-panel);
}

.short-video-favorites-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.short-video-empty-publish {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  border-radius: 999px;
  background: var(--lx-accent);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 20px;
  cursor: pointer;
}

.short-video-empty-retry {
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
}

.short-video-empty-retry:hover {
  color: var(--lx-accent);
}

.short-video-modal {
  position: absolute;
  inset: 0;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
  padding: var(--lx-space-lg);
  box-sizing: border-box;
}

.short-video-modal-card {
  width: min(420px, 100%);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-lg);
  padding: var(--lx-space-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-lg);
  box-shadow: var(--lx-shadow-soft);
}

.short-video-modal-card h3 {
  margin: 0;
  font-size: var(--lx-font-xl);
}

.short-video-mine-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--lx-space-lg) var(--lx-space-xl) var(--lx-space-4xl);
}

.short-video-mine-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--lx-space-sm);
}

.short-video-mine-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.short-video-mine-tile {
  position: relative;
  display: block;
  width: 100%;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: var(--lx-radius-sm);
  overflow: hidden;
  cursor: pointer;
  background: var(--lx-conf-bg-void);
  aspect-ratio: 9 / 16;
}

.short-video-mine-tile__cover {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.short-video-mine-tile__plays {
  position: absolute;
  right: 4px;
  bottom: 4px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  max-width: calc(100% - 8px);
  padding: var(--lx-space-2xs) var(--lx-space-xs);
  border-radius: var(--lx-radius-xs);
  background: rgba(0, 0, 0, 0.58);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  pointer-events: none;
  overflow: hidden;
  white-space: nowrap;
}

.short-video-mine-tile__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: rgba(255, 255, 255, 0.65);
}

.short-video-share-sheet {
  position: absolute;
  inset: 0;
  z-index: 70;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.short-video-share-panel {
  width: 100%;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-lg) var(--lx-radius-lg) 0 0;
  padding: var(--lx-space-lg) var(--lx-space-2xl) calc(var(--lx-space-2xl) + env(safe-area-inset-bottom, 0px));
}

.short-video-share-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 15px;
  font-weight: 600;
}

.short-video-share-panel__close {
  border: none;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  padding: 4px;
}

.short-video-share-panel__item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  margin-top: 8px;
  padding: 12px 10px;
  border: none;
  border-radius: var(--lx-radius-md);
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
  font-size: 14px;
  cursor: pointer;
}

.short-video-mine-empty--inline {
  padding: var(--lx-space) 0 var(--lx-space-lg);
  font-size: var(--lx-font-md);
}

.short-video-author-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px 8px;
  max-height: 400px;
  overflow-y: auto;
  padding: 2px;
}

.short-video-author-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.short-video-author-cell__plays {
  font-size: 11px;
  color: var(--lx-text-secondary);
  text-align: center;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.short-video-author-tile {
  position: relative;
  display: block;
  width: 100%;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: var(--lx-radius-md);
  overflow: hidden;
  cursor: pointer;
  background: #1a1a1a;
  aspect-ratio: 9 / 16;
}

.short-video-author-tile:hover {
  opacity: 0.92;
}

.short-video-author-tile__cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.short-video-author-tile__fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.55);
  background: linear-gradient(160deg, #2a2a2a, #111);
}

.short-video-mine-tile-wrap {
  position: relative;
}

.short-video-mine-tile-wrap:hover .short-video-mine-tile__actions {
  opacity: 1;
}

.short-video-mine-tile__actions {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.15s ease;
  pointer-events: none;
}

.short-video-mine-tile-wrap:hover .short-video-mine-tile__actions,
.short-video-mine-tile__actions:focus-within {
  pointer-events: auto;
}

.short-video-mine-tile__action {
  min-width: 64px;
  padding: 4px 8px;
  border: none;
  border-radius: var(--lx-radius-xs);
  background: rgba(255, 255, 255, 0.92);
  color: #222;
  font-size: 11px;
  cursor: pointer;
}

.short-video-mine-tile__action--danger {
  background: var(--lx-danger);
  color: var(--lx-text-on-accent);
}

.short-video-mine-empty {
  font-size: 13px;
  color: var(--lx-text-secondary);
  padding: 16px;
  text-align: center;
}

.short-video-mine-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 240px;
  overflow-y: auto;
}

.short-video-mine-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border-radius: var(--lx-radius-md);
  background: var(--lx-bg-hover);
}

.short-video-mine-item--clickable {
  cursor: pointer;
}

.short-video-mine-item--clickable:hover {
  background: var(--lx-bg-panel);
}

.short-video-mine-item__actions {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
}

.short-video-mine-item__main {
  flex: 1;
  min-width: 0;
}

.short-video-mine-item__desc {
  margin: 0 0 4px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.short-video-mine-item__meta {
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.short-video-publish-hint {
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.short-video-publish-limit {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.short-video-publish-progress-wrap {
  margin: 10px 0 8px;
}

.short-video-publish-progress {
  position: relative;
  height: 8px;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius-xs);
  overflow: hidden;
}

.short-video-publish-progress__bar {
  height: 100%;
  background: var(--lx-primary);
  transition: width 0.2s ease;
}

.short-video-publish-progress__text {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.short-video-visibility {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.short-video-visibility__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.short-video-visibility__options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.short-video-visibility__option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.short-video-visibility__desc {
  font-size: 12px;
  color: var(--lx-text-secondary);
  padding-left: 24px;
}

.short-video-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.hidden-input {
  display: none;
}
</style>
