<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { Play, VideocamOutline } from '@vicons/ionicons5'
import { useI18n } from '../i18n'
import {
  getShortVideoTopic,
  listShortVideos,
  shortVideoTopicLabel,
  type ShortVideoPost,
  type ShortVideoTopic
} from '../api/shortVideo'
import { resolveApiErrorMessage } from '../api/client'
import { buildShortVideoMediaApiUrl } from '../utils/shortVideoMediaAccess'
import { readableShortVideoText } from '../utils/shortVideoText'
import ShortVideoSubPageShell from './ShortVideoSubPageShell.vue'

const props = defineProps<{
  open: boolean
  topicName: string
}>()

const emit = defineEmits<{
  close: []
  play: [post: ShortVideoPost]
}>()

const { t } = useI18n()

const topic = ref<ShortVideoTopic | null>(null)
const posts = ref<ShortVideoPost[]>([])
const loading = ref(false)
const postsLoading = ref(false)
const postsLoadingMore = ref(false)
const error = ref('')
const postsError = ref('')
const hasMore = ref(true)
const coverFailed = ref<Record<string, boolean>>({})
const pageSize = 20

const title = computed(() => {
  if (!props.topicName) return t('shortVideo.topicPlaza')
  if (topic.value) return `#${shortVideoTopicLabel(topic.value)}`
  return `#${props.topicName}`
})

const subtitle = computed(() => {
  if (topic.value) {
    return t('shortVideo.topicPostCount', { n: topic.value.postCount ?? 0 })
  }
  return ''
})

function coverPoster(post: ShortVideoPost) {
  return buildShortVideoMediaApiUrl(post.id, 'cover')
}

function coverFailedFor(postId: string) {
  return Boolean(coverFailed.value[postId])
}

function onCoverError(postId: string) {
  coverFailed.value = { ...coverFailed.value, [postId]: true }
}

function normalizePosts(data: unknown): ShortVideoPost[] {
  if (!Array.isArray(data)) return []
  return data as ShortVideoPost[]
}

async function loadTopic() {
  const name = props.topicName.trim()
  if (!name) return
  loading.value = true
  error.value = ''
  try {
    const res = await getShortVideoTopic(name)
    if (res.code !== 200 || !res.data) {
      throw new Error(res.message || 'load topic failed')
    }
    topic.value = res.data
  } catch (e) {
    topic.value = null
    error.value = resolveApiErrorMessage(e, t('shortVideo.topicDetailLoadFail'))
  } finally {
    loading.value = false
  }
}

async function loadPosts(reset = false) {
  const name = props.topicName.trim()
  if (!name) return
  if (!reset && !hasMore.value) return

  if (reset) {
    posts.value = []
    hasMore.value = true
    postsError.value = ''
  }

  const isFirst = reset || posts.value.length === 0
  if (isFirst) postsLoading.value = true
  else postsLoadingMore.value = true

  try {
    const beforeId = reset ? undefined : posts.value[posts.value.length - 1]?.id
    const res = await listShortVideos({
      q: `#${name}`,
      beforeId,
      limit: pageSize
    })
    if (res.code !== 200) {
      throw new Error(res.message || 'load posts failed')
    }
    const rows = normalizePosts(res.data)
    if (reset) {
      posts.value = rows
    } else {
      const existing = new Set(posts.value.map(item => item.id))
      posts.value.push(...rows.filter(item => !existing.has(item.id)))
    }
    hasMore.value = rows.length >= pageSize
  } catch (e) {
    postsError.value = resolveApiErrorMessage(e, t('shortVideo.loadFail'))
    if (reset) posts.value = []
  } finally {
    postsLoading.value = false
    postsLoadingMore.value = false
  }
}

async function reloadAll() {
  coverFailed.value = {}
  await Promise.all([loadTopic(), loadPosts(true)])
}

function onScroll(event: Event) {
  const el = event.target as HTMLElement | null
  if (!el || postsLoading.value || postsLoadingMore.value || !hasMore.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 48) {
    void loadPosts(false)
  }
}

function pickPost(post: ShortVideoPost) {
  emit('play', post)
}

watch(
  () => [props.open, props.topicName] as const,
  ([open, name]) => {
    if (open && name) {
      void reloadAll()
    }
  }
)
</script>

<template>
  <ShortVideoSubPageShell
    v-if="open"
    :title="title"
    :subtitle="subtitle"
    body-class="sv-subpage__body--flush"
    @close="emit('close')"
  >
    <div class="sv-topic-detail">
      <div v-if="loading" class="sv-topic-detail__meta sv-subpage__empty">{{ t('common.loading') }}</div>
      <div v-else-if="error" class="sv-topic-detail__meta sv-subpage__empty sv-subpage__empty--error">
        {{ error }}
      </div>
      <div v-else-if="topic" class="sv-topic-detail__meta">
        <h3 class="sv-topic-detail__name">#{{ shortVideoTopicLabel(topic) }}</h3>
        <p class="sv-topic-detail__count">
          {{ t('shortVideo.topicPostCount', { n: topic.postCount ?? 0 }) }}
        </p>
        <span v-if="topic.pinned" class="sv-rank-list__badge">{{ t('shortVideo.topicPinned') }}</span>
      </div>

      <div class="sv-topic-detail__scroll" @scroll="onScroll">
        <div v-if="postsLoading" class="sv-subpage__empty">{{ t('common.loading') }}</div>
        <div v-else-if="postsError" class="sv-subpage__empty sv-subpage__empty--error">{{ postsError }}</div>
        <div v-else-if="posts.length === 0" class="sv-subpage__empty">{{ t('shortVideo.topicDetailEmpty') }}</div>
        <div v-else class="sv-video-grid">
          <div v-for="item in posts" :key="item.id" class="sv-video-grid__cell">
            <button
              type="button"
              class="sv-video-grid__tile"
              :title="readableShortVideoText(item.description) || t('shortVideo.empty')"
              @click="pickPost(item)"
            >
              <img
                v-if="!coverFailedFor(item.id)"
                :src="coverPoster(item)"
                class="sv-video-grid__cover"
                alt=""
                loading="lazy"
                @error="onCoverError(item.id)"
              />
              <div v-else class="sv-video-grid__fallback">
                <NIcon :component="VideocamOutline" :size="22" />
              </div>
              <span class="sv-video-grid__plays">
                <NIcon :component="Play" :size="10" />
                {{ item.playCount ?? 0 }}
              </span>
            </button>
          </div>
        </div>
        <div v-if="postsLoadingMore" class="sv-subpage__footer">{{ t('common.loading') }}</div>
      </div>
    </div>
  </ShortVideoSubPageShell>
</template>

<style scoped>
.sv-topic-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.sv-topic-detail__meta {
  flex-shrink: 0;
  padding: var(--lx-space-xl) var(--lx-space-xl) var(--lx-space-lg);
  border-bottom: 1px solid var(--lx-border-light);
}

.sv-topic-detail__name {
  margin: 0;
  font-size: var(--lx-font-2xl);
  font-weight: 700;
  color: var(--lx-text-body);
  word-break: break-word;
}

.sv-topic-detail__count {
  margin: var(--lx-space-sm) 0 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.sv-topic-detail__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--lx-space-lg) var(--lx-space-xl) var(--lx-space-4xl);
}
</style>
