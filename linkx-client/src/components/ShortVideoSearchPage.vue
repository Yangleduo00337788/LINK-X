<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { CloseOutline } from '@vicons/ionicons5'
import { useI18n } from '../i18n'
import { shortVideoTopicLabel, type ShortVideoPost, type ShortVideoTopic } from '../api/shortVideo'
import { readableShortVideoText } from '../utils/shortVideoText'
import ShortVideoSubPageShell from './ShortVideoSubPageShell.vue'
import ShortVideoSearchNav from './ShortVideoSearchNav.vue'

const HISTORY_PREVIEW = 5

const props = defineProps<{
  open: boolean
  searchText: string
  searchHistory: string[]
  hotTopics: ShortVideoTopic[]
  hotTopicsLoading: boolean
  hotVideos: ShortVideoPost[]
  hotVideosLoading: boolean
}>()

const emit = defineEmits<{
  close: []
  'update:searchText': [value: string]
  submit: []
  pickHistory: [query: string]
  removeHistory: [query: string]
  clearHistory: []
  pickTopic: [tag: string]
  pickVideo: [post: ShortVideoPost]
}>()

const { t } = useI18n()

const historyExpanded = ref(false)

const visibleHistory = computed(() => {
  if (historyExpanded.value) return props.searchHistory
  return props.searchHistory.slice(0, HISTORY_PREVIEW)
})

const hasMoreHistory = computed(() => props.searchHistory.length > HISTORY_PREVIEW)

watch(
  () => props.open,
  open => {
    if (open) historyExpanded.value = false
  }
)

function hotVideoLabel(post: ShortVideoPost) {
  const text = readableShortVideoText(post.description)
  if (text) return text
  if (post.nickname) return post.nickname
  return t('shortVideo.empty')
}
</script>

<template>
  <ShortVideoSubPageShell
    v-if="open"
    nav-class="sv-subpage__nav--search"
    body-class="sv-subpage__body--search"
    @close="emit('close')"
  >
    <template #nav>
      <ShortVideoSearchNav
        :model-value="searchText"
        autofocus
        @update:model-value="emit('update:searchText', $event)"
        @back="emit('close')"
        @submit="emit('submit')"
      />
    </template>

    <section v-if="searchHistory.length > 0" class="sv-search-section">
      <div class="sv-search-section__head">
        <h3 class="sv-search-section__title">{{ t('shortVideo.searchHistoryTitle') }}</h3>
        <button type="button" class="sv-search-section__action" @click="emit('clearHistory')">
          {{ t('shortVideo.clearSearchHistory') }}
        </button>
      </div>
      <div class="sv-search-history">
        <div v-for="item in visibleHistory" :key="item" class="sv-search-history__tag">
          <button type="button" class="sv-search-history__text" @click="emit('pickHistory', item)">
            {{ item }}
          </button>
          <button
            type="button"
            class="sv-search-history__remove"
            :aria-label="t('shortVideo.deleteSearchHistory')"
            @click.stop="emit('removeHistory', item)"
          >
            <NIcon :component="CloseOutline" :size="12" />
          </button>
        </div>
        <button
          v-if="hasMoreHistory"
          type="button"
          class="sv-search-history__toggle"
          @click="historyExpanded = !historyExpanded"
        >
          {{ historyExpanded ? t('shortVideo.searchHistoryCollapse') : t('shortVideo.searchHistoryExpand') }}
        </button>
      </div>
    </section>

    <section class="sv-search-section">
      <h3 class="sv-search-section__title">{{ t('shortVideo.hotTopics') }}</h3>
      <div v-if="hotTopicsLoading" class="sv-search-section__empty">{{ t('common.loading') }}</div>
      <div v-else-if="hotTopics.length === 0" class="sv-search-section__empty">
        {{ t('shortVideo.hotTopicsEmpty') }}
      </div>
      <ol v-else class="sv-rank-list">
        <li v-for="(topic, index) in hotTopics" :key="topic.name" class="sv-rank-list__item">
          <button type="button" class="sv-rank-list__btn" @click="emit('pickTopic', topic.name)">
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
    </section>

    <section class="sv-search-section">
      <h3 class="sv-search-section__title">{{ t('shortVideo.hotVideos') }}</h3>
      <div v-if="hotVideosLoading" class="sv-search-section__empty">{{ t('common.loading') }}</div>
      <div v-else-if="hotVideos.length === 0" class="sv-search-section__empty">
        {{ t('shortVideo.hotVideosEmpty') }}
      </div>
      <ol v-else class="sv-rank-list">
        <li v-for="(post, index) in hotVideos" :key="post.id" class="sv-rank-list__item">
          <button type="button" class="sv-rank-list__btn" @click="emit('pickVideo', post)">
            <span class="sv-rank-list__rank" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
            <span class="sv-rank-list__main">
              <span class="sv-rank-list__name">{{ hotVideoLabel(post) }}</span>
            </span>
            <span class="sv-rank-list__meta">
              {{ t('shortVideo.playCount', { n: post.playCount ?? 0 }) }}
            </span>
          </button>
        </li>
      </ol>
    </section>
  </ShortVideoSubPageShell>
</template>
