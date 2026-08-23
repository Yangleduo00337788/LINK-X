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
  ChatbubbleOutline,
  EllipsisHorizontal,
  EyeOutline,
  Heart,
  HeartOutline,
  Pause,
  PersonOutline,
  Play,
  SearchOutline,
  ShareSocialOutline,
  VideocamOutline,
  VolumeHighOutline,
  VolumeMuteOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { LxButton } from './ui'
import { useShortVideoStore, type ShortVideoFeedTab } from '../stores/shortVideo'
import { useAppStore } from '../stores/app'
import { useI18n } from '../i18n'
import { resolveApiErrorMessage } from '../api/client'
import Avatar from './Avatar.vue'
import { resolveUserAvatarUrl } from '../utils/defaultAvatar'
import { resolveShortVideoDisplaySrc } from '../utils/shortVideoMediaAccess'
import { readableShortVideoText } from '../utils/shortVideoText'
import { copyText } from '../utils/clipboard'
import type { ShortVideoComment, ShortVideoPost } from '../api/shortVideo'

const { t } = useI18n()
const message = useMessage()
const dialog = useDialog()
const route = useRoute()
const router = useRouter()
const store = useShortVideoStore()
const appStore = useAppStore()
const { feedTab, posts, loading, publishing, activeIndex, myPosts, myPostsLoading, feedError } = storeToRefs(store)

const feedRef = ref<HTMLElement | null>(null)
const videoRefs = ref<Record<string, HTMLVideoElement | null>>({})
const videoSrcMap = ref<Record<string, string>>({})
const blobRevokeList = ref<string[]>([])
const progressMap = ref<Record<string, number>>({})
const pausedMap = ref<Record<string, boolean>>({})
const muted = ref(true)
const commentOpenFor = ref<string | null>(null)
const commentText = ref('')
const publishOpen = ref(false)
const publishDesc = ref('')
const publishVisibility = ref(0)
const fileInputRef = ref<HTMLInputElement | null>(null)
const pendingFile = ref<File | null>(null)
const searchOpen = ref(false)
const searchText = ref('')
const mineOpen = ref(false)
const editOpen = ref(false)
const editTarget = ref<ShortVideoPost | null>(null)
const editDesc = ref('')
const editVisibility = ref(0)
const replyToComment = ref<ShortVideoComment | null>(null)
const mediaSyncToken = ref(0)
const playbackRate = ref(1)
const PLAYBACK_RATES = [1, 1.5, 2, 0.75]

const feedTabs: Array<{ id: ShortVideoFeedTab; label: string }> = [
  { id: 'live', label: t('shortVideo.live') },
  { id: 'following', label: t('shortVideo.following') },
  { id: 'friends', label: t('shortVideo.friends') },
  { id: 'recommend', label: t('shortVideo.recommend') }
]

const activePost = computed(() => posts.value[activeIndex.value])
const showFeed = computed(() => posts.value.length > 0)
const isLiveTab = computed(() => feedTab.value === 'live')
const currentUserId = computed(() => String(appStore.userProfile.userId || ''))

const visibilityOptions = computed(() => [
  { value: 0, label: t('moments.public'), desc: t('moments.publicDesc') },
  { value: 1, label: t('moments.friendsOnly'), desc: t('moments.friendsOnlyDesc') },
  { value: 2, label: t('moments.private'), desc: t('moments.privateDesc') }
])

const commentPlaceholder = computed(() => {
  if (!replyToComment.value) return t('shortVideo.commentPh')
  return t('moments.replyTo', {
    name: replyToComment.value.nickname || t('shortVideo.author')
  })
})

function isOwnPost(post: ShortVideoPost) {
  return Boolean(post.userId && currentUserId.value && post.userId === currentUserId.value)
}

function isOwnComment(comment: ShortVideoComment) {
  return Boolean(comment.userId && currentUserId.value && comment.userId === currentUserId.value)
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
  () => posts.value.map(p => p.id).join(','),
  () => {
    void syncMediaSources()
  }
)

watch(activeIndex, () => {
  playActiveVideo()
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

async function syncMediaSources() {
  const token = ++mediaSyncToken.value
  revokeBlobUrls()
  const next: Record<string, string> = {}
  const revokes: string[] = []
  for (const post of posts.value) {
    const { src, blobUrlToRevoke } = await resolveShortVideoDisplaySrc(post.id, 'video', post.videoUrl)
    if (token !== mediaSyncToken.value) return
    if (src) next[post.id] = src
    if (blobUrlToRevoke) revokes.push(blobUrlToRevoke)
  }
  if (token !== mediaSyncToken.value) {
    for (const url of revokes) {
      try {
        URL.revokeObjectURL(url)
      } catch {
        /* ignore */
      }
    }
    return
  }
  videoSrcMap.value = next
  blobRevokeList.value = revokes
  await nextTick()
  playActiveVideo()
}

function setVideoRef(id: string, el: HTMLVideoElement | null) {
  videoRefs.value[id] = el
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
    void store.fetchFeed(false)
  }
}

function scrollToIndex(index: number) {
  const el = feedRef.value
  if (!el || index < 0 || index >= posts.value.length) return
  el.scrollTo({ top: index * el.clientHeight, behavior: 'smooth' })
  store.setActiveIndex(index)
}

function goPrev() {
  scrollToIndex(activeIndex.value - 1)
}

function goNext() {
  scrollToIndex(activeIndex.value + 1)
}

function playActiveVideo() {
  const current = activePost.value
  for (const [id, video] of Object.entries(videoRefs.value)) {
    if (!video) continue
    if (current && id === current.id) {
      video.muted = muted.value
      video.playbackRate = playbackRate.value
      video.play().catch(() => {
        pausedMap.value[id] = true
      })
      pausedMap.value[id] = false
      void store.markPlayed(current.id)
    } else {
      video.pause()
      video.currentTime = 0
      progressMap.value[id] = 0
      pausedMap.value[id] = false
    }
  }
}

function togglePlay(post: ShortVideoPost) {
  const video = videoRefs.value[post.id]
  if (!video) return
  if (video.paused) {
    video.play().catch(() => {})
    pausedMap.value[post.id] = false
  } else {
    video.pause()
    pausedMap.value[post.id] = true
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
  pausedMap.value[postId] = false
}

function onVideoPause(postId: string) {
  pausedMap.value[postId] = true
}

function isPaused(post: ShortVideoPost) {
  return pausedMap.value[post.id] ?? false
}

function formatCount(n: number) {
  if (n >= 100000) return '10万+'
  if (n >= 10000) return `${Math.floor(n / 10000)}万+`
  return String(n)
}

function openPublish() {
  publishOpen.value = true
  publishDesc.value = ''
  publishVisibility.value = 0
  pendingFile.value = null
  mineOpen.value = false
}

async function openMine() {
  mineOpen.value = true
  await store.fetchMyPosts()
}

function buildShareLink(post: ShortVideoPost) {
  const base = window.location.href.split('#')[0]
  return `${base}#/short-video?post=${post.id}`
}

async function sharePost(post: ShortVideoPost) {
  const ok = await copyText(buildShareLink(post))
  if (ok) {
    message.success(t('shortVideo.shareCopied'))
  } else {
    message.error(t('shortVideo.shareFail'))
  }
}

function moreOptions(post: ShortVideoPost): DropdownOption[] {
  const options: DropdownOption[] = [
    { label: t('viewer.copyLink'), key: 'copy' }
  ]
  if (isOwnPost(post)) {
    options.push({ type: 'divider', key: 'divider' })
    options.push({ label: t('moments.editPost'), key: 'edit' })
    options.push({ label: t('shortVideo.deletePost'), key: 'delete' })
  }
  return options
}

function onMoreSelect(key: string, post: ShortVideoPost) {
  if (key === 'copy') {
    void sharePost(post)
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
  pendingFile.value = file
}

async function submitPublish() {
  if (!pendingFile.value) {
    message.warning(t('shortVideo.needVideo'))
    return
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

function openComments(post: ShortVideoPost) {
  commentOpenFor.value = post.id
  commentText.value = ''
  replyToComment.value = null
}

function startReply(comment: ShortVideoComment) {
  replyToComment.value = comment
  commentText.value = ''
}

function cancelReply() {
  replyToComment.value = null
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
  const text = commentText.value.trim()
  if (!text) return
  try {
    await store.addComment(post.id, text, replyToComment.value?.id)
    commentText.value = ''
    replyToComment.value = null
    message.success(t('shortVideo.commentOk'))
  } catch {
    message.error(t('shortVideo.commentFail'))
  }
}

async function submitSearch() {
  const q = searchText.value.trim()
  searchOpen.value = false
  if (!q) {
    await store.fetchFeed(true)
  } else {
    await store.searchFeed(q)
  }
  await syncMediaSources()
}

function avatarUrl(post: ShortVideoPost) {
  return resolveUserAvatarUrl(post.avatar, post.userId)
}

function videoSrc(post: ShortVideoPost) {
  return videoSrcMap.value[post.id] || ''
}
</script>

<template>
  <div class="short-video-main" :class="{ 'short-video-main--feed': showFeed }">
    <div class="short-video-stage">
      <div v-if="loading && posts.length === 0 && !isLiveTab" class="short-video-empty">
        {{ t('common.loading') }}
      </div>

      <div v-else-if="isLiveTab" class="short-video-empty short-video-empty--live">
        <p>{{ t('shortVideo.liveSoon') }}</p>
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
          v-for="post in posts"
          :key="post.id"
          class="short-video-slide"
        >
          <video
            v-if="videoSrc(post)"
            :ref="el => setVideoRef(post.id, el as HTMLVideoElement | null)"
            class="short-video-player"
            :src="videoSrc(post)"
            :poster="post.coverUrl || undefined"
            playsinline
            loop
            :muted="muted"
            preload="auto"
            @click="togglePlay(post)"
            @timeupdate="onTimeUpdate(post.id, $event)"
            @play="onVideoPlay(post.id)"
            @pause="onVideoPause(post.id)"
          />
          <div v-else class="short-video-player short-video-player--loading">
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

          <div class="short-video-overlay">
            <div class="short-video-meta">
              <p v-if="readableShortVideoText(post.description)" class="short-video-desc">
                {{ readableShortVideoText(post.description) }}
              </p>
              <div class="short-video-author">
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
                  @click="toggleFollow(post)"
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
                  <span>{{ formatCount(post.comments.length) }}</span>
                </button>
                <button type="button" class="short-video-action" :title="t('shortVideo.playCount', { n: post.playCount || 0 })">
                  <NIcon :component="EyeOutline" :size="32" />
                  <span>{{ formatCount(post.playCount || 0) }}</span>
                </button>
                <button type="button" class="short-video-action" :title="t('shortVideo.share')" @click="sharePost(post)">
                  <NIcon :component="ShareSocialOutline" :size="32" />
                  <span>{{ t('shortVideo.share') }}</span>
                </button>
              </div>
            </div>
          </div>

          <div class="short-video-controls">
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

          <div v-if="commentOpenFor === post.id" class="short-video-comment-panel">
            <div class="short-video-comment-list">
              <div v-for="c in post.comments" :key="c.id" class="short-video-comment-item">
                <div class="short-video-comment-body">
                  <strong>{{ c.nickname || t('shortVideo.author') }}</strong>
                  <span v-if="c.replyToNickname" class="short-video-comment-reply">
                    {{ t('moments.reply') }} {{ c.replyToNickname }}
                  </span>
                  <span>{{ readableShortVideoText(c.content) }}</span>
                </div>
                <div class="short-video-comment-actions">
                  <button type="button" class="short-video-comment-btn" @click="startReply(c)">
                    {{ t('moments.reply') }}
                  </button>
                  <button
                    v-if="isOwnComment(c)"
                    type="button"
                    class="short-video-comment-btn short-video-comment-btn--danger"
                    @click="confirmDeleteComment(post, c)"
                  >
                    {{ t('common.delete') }}
                  </button>
                </div>
              </div>
              <p v-if="post.comments.length === 0" class="short-video-comment-empty">
                {{ t('shortVideo.noComments') }}
              </p>
            </div>
            <div v-if="replyToComment" class="short-video-reply-hint">
              <span>{{ t('moments.replyTo', { name: replyToComment.nickname || t('shortVideo.author') }) }}</span>
              <button type="button" class="short-video-comment-btn" @click="cancelReply">
                {{ t('common.cancel') }}
              </button>
            </div>
            <div class="short-video-comment-input">
              <NInput
                v-model:value="commentText"
                :placeholder="commentPlaceholder"
                size="small"
                @keyup.enter="submitComment(post)"
              />
              <LxButton size="small" variant="toolbar-primary" @click="submitComment(post)">
                {{ t('shortVideo.send') }}
              </LxButton>
            </div>
          </div>
        </section>
      </div>

      <header class="short-video-topbar" :class="{ 'short-video-topbar--overlay': showFeed }">
        <div class="short-video-topbar__side short-video-topbar__side--left">
          <button type="button" class="short-video-topbar__icon" :title="t('shortVideo.publish')" @click="openPublish">
            <NIcon :component="VideocamOutline" :size="22" />
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
          <button type="button" class="short-video-topbar__icon" :title="t('shortVideo.search')" @click="searchOpen = true">
            <NIcon :component="SearchOutline" :size="22" />
          </button>
          <button type="button" class="short-video-topbar__icon" :title="t('shortVideo.mine')" @click="openMine">
            <NIcon :component="PersonOutline" :size="22" />
          </button>
        </div>
      </header>
    </div>

    <div v-if="searchOpen" class="short-video-modal" @click.self="searchOpen = false">
      <div class="short-video-modal-card short-video-modal-card--search">
        <h3>{{ t('shortVideo.search') }}</h3>
        <NInput
          v-model:value="searchText"
          :placeholder="t('shortVideo.searchPh')"
          @keyup.enter="submitSearch"
        />
        <div class="short-video-modal-actions">
          <LxButton @click="searchOpen = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" @click="submitSearch">{{ t('shortVideo.search') }}</LxButton>
        </div>
      </div>
    </div>

    <div v-if="mineOpen" class="short-video-modal" @click.self="mineOpen = false">
      <div class="short-video-modal-card short-video-modal-card--mine">
        <h3>{{ t('shortVideo.mine') }}</h3>
        <div class="short-video-mine-profile">
          <Avatar
            :text="(appStore.userProfile.nickname || t('shortVideo.author')).slice(0, 1)"
            color="transparent"
            :size="48"
            :image-url="resolveUserAvatarUrl(appStore.userProfile.avatar, appStore.userProfile.userId)"
          />
          <span>{{ appStore.userProfile.nickname || t('shortVideo.author') }}</span>
        </div>
        <LxButton variant="modal-primary" @click="openPublish">
          <NIcon :component="VideocamOutline" :size="18" style="margin-right: 6px" />
          {{ t('shortVideo.publish') }}
        </LxButton>
        <h4 class="short-video-mine-section">{{ t('shortVideo.myVideos') }}</h4>
        <div v-if="myPostsLoading" class="short-video-mine-empty">{{ t('common.loading') }}</div>
        <div v-else-if="myPosts.length === 0" class="short-video-mine-empty">{{ t('shortVideo.noMyVideos') }}</div>
        <div v-else class="short-video-mine-list">
          <div v-for="item in myPosts" :key="item.id" class="short-video-mine-item">
            <div class="short-video-mine-item__main">
              <p class="short-video-mine-item__desc">{{ readableShortVideoText(item.description) || t('shortVideo.empty') }}</p>
              <span class="short-video-mine-item__meta">
                {{ t('shortVideo.playCount', { n: item.playCount || 0 }) }} · {{ formatCount(item.likes) }} {{ t('moments.like') }}
              </span>
            </div>
            <div class="short-video-mine-item__actions">
              <LxButton size="small" variant="sm" @click="openEdit(item)">
                {{ t('moments.editPost') }}
              </LxButton>
              <LxButton size="small" variant="sm" @click="confirmDeletePost(item)">
                {{ t('shortVideo.deletePost') }}
              </LxButton>
            </div>
          </div>
        </div>
        <div class="short-video-modal-actions">
          <LxButton @click="mineOpen = false">{{ t('common.close') }}</LxButton>
        </div>
      </div>
    </div>

    <div v-if="publishOpen" class="short-video-modal">
      <div class="short-video-modal-card">
        <h3>{{ t('shortVideo.publishTitle') }}</h3>
        <p class="short-video-publish-hint">{{ pendingFile?.name || t('shortVideo.pickVideo') }}</p>
        <LxButton size="small" @click="pickVideo">{{ t('shortVideo.pickVideo') }}</LxButton>
        <NInput
          v-model:value="publishDesc"
          type="textarea"
          :placeholder="t('shortVideo.descPh')"
          :autosize="{ minRows: 3, maxRows: 6 }"
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
          <LxButton @click="publishOpen = false">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="modal-primary" :loading="publishing" @click="submitPublish">
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
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  min-height: 48px;
  padding: 0 10px;
  flex-shrink: 0;
  background: var(--lx-bg-panel);
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

.short-video-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 56px 10px 52px 14px;
  pointer-events: none;
  z-index: 10;
}

.short-video-meta,
.short-video-rail {
  pointer-events: auto;
}

.short-video-meta {
  max-width: calc(100% - 64px);
  color: #fff;
}

.short-video-desc {
  margin: 0 0 10px;
  line-height: 1.45;
  font-size: 14px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.short-video-author {
  display: flex;
  align-items: center;
  gap: 8px;
}

.short-video-name {
  font-weight: 600;
  font-size: 15px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
}

.short-video-follow-pill {
  border: none;
  border-radius: 4px;
  background: #20d492;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  padding: 4px 10px;
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
  gap: 4px;
  color: #fff;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 12px;
  min-width: 48px;
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

.short-video-comment-panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
  padding: 12px;
  max-height: 42%;
  z-index: 18;
}

.short-video-comment-list {
  max-height: 140px;
  overflow-y: auto;
  margin-bottom: 8px;
}

.short-video-comment-item {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}

.short-video-comment-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.short-video-comment-reply {
  opacity: 0.75;
}

.short-video-comment-actions {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
}

.short-video-comment-btn {
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.82);
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
  font-size: 12px;
  opacity: 0.85;
}

.short-video-comment-empty {
  font-size: 12px;
  opacity: 0.8;
}

.short-video-comment-input {
  display: flex;
  gap: 8px;
  align-items: center;
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

.short-video-empty--live {
  color: rgba(255, 255, 255, 0.75);
  background: #111;
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
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.short-video-modal-card {
  width: min(420px, 92vw);
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius-lg);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.short-video-modal-card h3 {
  margin: 0;
  font-size: 16px;
}

.short-video-modal-card--search {
  width: min(480px, 92vw);
}

.short-video-modal-card--mine {
  width: min(480px, 92vw);
}

.short-video-mine-section {
  margin: 4px 0 0;
  font-size: 14px;
  font-weight: 600;
}

.short-video-mine-empty {
  font-size: 13px;
  color: var(--lx-text-secondary);
  padding: 8px 0;
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

.short-video-mine-profile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.short-video-publish-hint {
  font-size: 13px;
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
