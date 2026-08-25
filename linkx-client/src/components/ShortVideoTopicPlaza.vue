<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from '../i18n'
import { listShortVideoTopics, shortVideoTopicLabel, type ShortVideoTopic } from '../api/shortVideo'
import { resolveApiErrorMessage } from '../api/client'
import ShortVideoSubPageShell from './ShortVideoSubPageShell.vue'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
  select: [tag: string]
}>()

const { t } = useI18n()

const topics = ref<ShortVideoTopic[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const page = ref(1)
const total = ref(0)
const error = ref('')
const pageSize = 20

const hasMore = computed(() => topics.value.length < total.value)

async function loadTopics(reset = false) {
  if (reset) {
    page.value = 1
    topics.value = []
    total.value = 0
    error.value = ''
  }
  if (!reset && !hasMore.value) return

  const isFirst = reset || page.value === 1
  if (isFirst) loading.value = true
  else loadingMore.value = true

  try {
    const res = await listShortVideoTopics({ page: page.value, limit: pageSize })
    if (res.code !== 200 || !res.data) {
      throw new Error(res.message || 'load topics failed')
    }
    const rows = Array.isArray(res.data.items) ? res.data.items : []
    total.value = typeof res.data.total === 'number' ? res.data.total : rows.length
    if (reset) {
      topics.value = rows
    } else {
      const existing = new Set(topics.value.map(item => item.name))
      topics.value.push(...rows.filter(item => !existing.has(item.name)))
    }
    if (rows.length >= pageSize) {
      page.value += 1
    }
  } catch (e) {
    error.value = resolveApiErrorMessage(e, t('shortVideo.topicPlazaLoadFail'))
    if (reset) topics.value = []
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function onScroll(event: Event) {
  const el = event.target as HTMLElement | null
  if (!el || loading.value || loadingMore.value || !hasMore.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 48) {
    void loadTopics(false)
  }
}

function pickTopic(topic: ShortVideoTopic) {
  emit('select', topic.name)
}

watch(
  () => props.open,
  open => {
    if (open) {
      void loadTopics(true)
    }
  }
)
</script>

<template>
  <ShortVideoSubPageShell
    v-if="open"
    :title="t('shortVideo.topicPlaza')"
    :subtitle="t('shortVideo.topicPlazaHint')"
    @close="emit('close')"
  >
    <div class="topic-plaza-scroll" @scroll="onScroll">
      <div v-if="loading" class="sv-subpage__empty">{{ t('common.loading') }}</div>
      <div v-else-if="error" class="sv-subpage__empty sv-subpage__empty--error">{{ error }}</div>
      <div v-else-if="topics.length === 0" class="sv-subpage__empty">{{ t('shortVideo.hotTopicsEmpty') }}</div>
      <ol v-else class="sv-rank-list">
        <li v-for="(topic, index) in topics" :key="topic.name" class="sv-rank-list__item">
          <button type="button" class="sv-rank-list__btn" @click="pickTopic(topic)">
            <span class="sv-rank-list__rank" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
            <span class="sv-rank-list__main">
              <span class="sv-rank-list__name">#{{ shortVideoTopicLabel(topic) }}</span>
              <span v-if="topic.pinned" class="sv-rank-list__badge">{{ t('shortVideo.topicPinned') }}</span>
            </span>
            <span class="sv-rank-list__meta">
              {{ t('shortVideo.topicPostCount', { n: topic.postCount ?? 0 }) }}
            </span>
          </button>
        </li>
      </ol>
      <div v-if="loadingMore" class="sv-subpage__footer">{{ t('common.loading') }}</div>
    </div>
  </ShortVideoSubPageShell>
</template>

<style scoped>
.topic-plaza-scroll {
  height: 100%;
  overflow-y: auto;
  margin: calc(-1 * var(--lx-space-lg)) calc(-1 * var(--lx-space-xl));
  padding: var(--lx-space-lg) var(--lx-space-xl);
}
</style>
