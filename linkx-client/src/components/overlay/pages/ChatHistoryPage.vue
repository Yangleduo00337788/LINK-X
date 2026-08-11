<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 聊天记录浏览与搜索页面（服务端搜索 + 时间范围 + 高亮 + 当前会话本地预览）
 */
import { computed, ref, watch } from 'vue'
import { NIcon, NInput, NEmpty, NDatePicker, useMessage } from 'naive-ui'
import { ChatbubblesOutline, TimeOutline, SearchOutline, CloseOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../../stores/app'
import { useOverlayStore } from '../../../stores/overlay'
import EmptyState from '../../common/EmptyState.vue'
import { useI18n } from '../../../i18n'
import DOMPurify from 'dompurify'
import * as chatApi from '../../../api/chat'
import { LxButton } from '../../ui'

const appStore = useAppStore()
const overlayStore = useOverlayStore()
const message = useMessage()
const { t } = useI18n()
const { sessions, messagesBySession, currentSessionId } = storeToRefs(appStore)
const { openSessionAtMessage } = appStore
const { closeAll: closeOverlay } = overlayStore

const searchQuery = ref('')
const searching = ref(false)
const timeRange = ref<[number, number] | null>(null)
const searchResults = ref<
  Array<{
    sessionId: string
    sessionName: string
    messages: Array<{
      id: string
      content: string
      highlight?: string
      time: string
      isSelf: boolean
      type: string
    }>
  }>
>([])

let searchTimer: ReturnType<typeof setTimeout> | null = null

watch([searchQuery, timeRange], () => {
  if (searchTimer) clearTimeout(searchTimer)
  if (!searchQuery.value.trim()) {
    searchResults.value = []
    return
  }
  searchTimer = setTimeout(() => {
    void runServerSearch(searchQuery.value.trim())
  }, 300)
})

async function runServerSearch(query: string) {
  searching.value = true
  try {
    const fromTime = timeRange.value?.[0]
    const toTime = timeRange.value
      ? timeRange.value[1] + 24 * 60 * 60 * 1000 - 1
      : undefined
    const res = await chatApi.searchMessages(query, {
      limit: 50,
      fromTime,
      toTime
    })
    if (res.code !== 200 || !res.data) {
      searchResults.value = []
      return
    }
    const map = new Map<
      string,
      {
        sessionId: string
        sessionName: string
        messages: Array<{
          id: string
          content: string
          highlight?: string
          time: string
          isSelf: boolean
          type: string
        }>
      }
    >()
    for (const hit of res.data) {
      const sid = String(hit.conversationId)
      if (!map.has(sid)) {
        map.set(sid, {
          sessionId: sid,
          sessionName:
            hit.conversationName || sessions.value.find(s => s.id === sid)?.name || t('modals.chat'),
          messages: []
        })
      }
      const time = hit.createTime
        ? new Date(hit.createTime).toLocaleString('zh-CN', {
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
          })
        : ''
      map.get(sid)!.messages.push({
        id: String(hit.messageId),
        content: hit.content || hit.fileName || '',
        highlight: hit.highlight || undefined,
        time,
        isSelf: false,
        type: hit.type || 'text'
      })
    }
    searchResults.value = [...map.values()]
  } catch (e) {
    console.error('服务端搜索失败:', e)
    message.error(t('extra.searchFail'))
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

const currentMessages = computed(() => {
  if (!currentSessionId.value) return []
  return (messagesBySession.value[currentSessionId.value] || []).filter(m => m.type !== 'time')
})

const currentSessionName = computed(() => {
  return sessions.value.find(s => s.id === currentSessionId.value)?.name || '—'
})

const totalSearchCount = computed(() =>
  searchResults.value.reduce((sum, r) => sum + r.messages.length, 0)
)

function historyPreview(msg: (typeof currentMessages.value)[number]) {
  if (msg.type === 'file') return `${t('overlay.file')} ${msg.fileName || msg.content}`
  if (msg.type === 'image' || msg.isImage) return t('overlay.image')
  if (msg.type === 'voice') return t('overlay.voice')
  if (msg.type === 'redPacket')
    return `${t('overlay.redPacket')} ${msg.redPacketGreeting || msg.content}`
  if (msg.type === 'conference')
    return `${t('overlay.conference')} ${msg.conferenceTitle || msg.fileName || msg.content}`
  return msg.content
}

function goToMessage(sessionId: string, messageId?: string) {
  if (messageId) {
    const ok = openSessionAtMessage(sessionId, messageId)
    if (ok) {
      closeOverlay()
      return
    }
  }
  const session = sessions.value.find(s => s.id === sessionId)
  if (session) {
    appStore.selectSession(session)
    appStore.navKey = 'chat'
    closeOverlay()
    message.success(t('overlay.jumpedToSession'))
  }
}

function clearTimeRange() {
  timeRange.value = null
}
</script>

<template>
  <div class="page-wrap history-page">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <n-input
        v-model:value="searchQuery"
        :placeholder="t('overlay.searchHistory')"
        clearable
        size="medium"
        class="search-input"
      >
        <template #prefix>
          <n-icon :component="SearchOutline" />
        </template>
      </n-input>
      <div class="time-filters">
        <n-date-picker
          v-model:value="timeRange"
          type="daterange"
          clearable
          size="small"
          :start-placeholder="t('overlay.searchFrom')"
          :end-placeholder="t('overlay.searchTo')"
          class="time-picker"
        />
        <LxButton
          v-if="timeRange"
          variant="link-md"
          class="clear-time"
          @click="clearTimeRange"
        >
          <n-icon :component="CloseOutline" :size="12" />
          {{ t('overlay.clearTimeRange') }}
        </LxButton>
      </div>
    </div>

    <template v-if="searchQuery.trim()">
      <section class="panel-card history-card">
        <div class="history-hero">
          <div class="history-avatar">
            <n-icon :component="SearchOutline" :size="26" />
          </div>
          <div class="history-meta">
            <h2 class="history-name">{{ t('overlay.searchResults') }}</h2>
            <p class="history-sub">
              {{ t('overlay.resultCount', { n: totalSearchCount }) }}
            </p>
          </div>
          <span v-if="searching" class="searching-tag">{{ t('overlay.searching') }}</span>
        </div>

        <div v-if="searchResults.length === 0 && !searching" class="empty-search">
          <n-empty :description="t('overlay.noMatchHistory')" />
        </div>

        <div v-else class="search-results">
          <div v-for="result in searchResults" :key="result.sessionId" class="result-group">
            <div class="result-session" @click="goToMessage(result.sessionId)">
              <span class="session-name">{{ result.sessionName }}</span>
              <span class="result-count">
                {{ t('overlay.countUnit', { n: result.messages.length }) }}
              </span>
            </div>
            <div
              v-for="msg in result.messages.slice(0, 5)"
              :key="msg.id"
              class="result-item"
              :class="{ self: msg.isSelf }"
              @click="goToMessage(result.sessionId, msg.id)"
            >
              <div class="result-bubble">
                <p
                  v-if="msg.highlight"
                  class="result-text"
                  v-html="DOMPurify.sanitize(msg.highlight)"
                />
                <p v-else class="result-text">{{ msg.content }}</p>
                <span class="result-time">{{ msg.time }}</span>
              </div>
            </div>
            <div v-if="result.messages.length > 5" class="result-more">
              {{ t('overlay.moreResults', { n: result.messages.length - 5 }) }}
            </div>
          </div>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="panel-card history-card">
        <div class="history-hero">
          <div class="history-avatar">
            <n-icon :component="ChatbubblesOutline" :size="26" />
          </div>
          <div class="history-meta">
            <h2 class="history-name">{{ currentSessionName }}</h2>
            <p class="history-sub">
              <n-icon :component="TimeOutline" :size="14" />
              {{ t('overlay.msgCount', { n: currentMessages.length }) }}
            </p>
          </div>
        </div>

        <div v-if="currentMessages.length === 0" class="empty-history">
          <EmptyState
            :title="t('overlay.noMessages')"
            :description="t('overlay.noMessagesDesc')"
          />
        </div>
        <div v-else class="history-scroll">
          <div
            v-for="msg in currentMessages"
            :key="msg.id"
            class="history-item"
            :class="{ self: msg.isSelf }"
          >
            <div class="history-bubble">
              <p class="history-text">{{ historyPreview(msg) }}</p>
              <span class="history-time">{{ msg.time }}</span>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';

.history-page {
  padding: var(--lx-space-2xl) var(--lx-space-xs) var(--lx-space-xs);
}

/* 搜索栏 —— 与上方 hero 同一节奏 */
.search-bar {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-md);
  padding: var(--lx-space-xl) var(--lx-space-2xl);
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-card);
  box-shadow: var(--lx-shadow-soft, 0 2px 12px rgba(0, 0, 0, 0.03));
}

.search-input :deep(.n-input__input-el),
.search-input :deep(.n-input__placeholder) {
  font-size: var(--lx-font);
}

.time-filters {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  flex-wrap: wrap;
}

.time-picker :deep(.n-input) {
  border-radius: var(--lx-radius-sm);
}

.clear-time {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  border: none;
  background: var(--lx-bg-panel);
  color: var(--lx-text-secondary);
  cursor: pointer;
  font-size: var(--lx-font-sm);
  padding: var(--lx-space-xs) var(--lx-space-md);
  border-radius: var(--lx-radius-pill);
  transition: background var(--lx-duration) ease, color var(--lx-duration) ease;
}

.clear-time:hover {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
}

/* hero 头 —— 更柔和 */
.history-card {
  display: flex;
  flex-direction: column;
  min-height: min(560px, calc(100vh - 200px));
  padding: 0;
  overflow: hidden;
}

.history-hero {
  display: flex;
  align-items: center;
  gap: var(--lx-space-xl);
  padding: var(--lx-space-2xl) var(--lx-space-3xl);
  background: linear-gradient(
    135deg,
    var(--lx-accent-soft),
    color-mix(in srgb, var(--lx-accent-soft) 50%, transparent)
  );
  border-bottom: 1px solid var(--lx-border-light);
}

.history-avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--lx-avatar-radius);
  background: var(--lx-bg-card);
  color: var(--lx-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--lx-accent) 18%, transparent);
}

.history-meta {
  flex: 1;
  min-width: 0;
}

.history-name {
  margin: 0;
  font-size: var(--lx-font-2xl);
  font-weight: 600;
  color: var(--lx-text-body);
  letter-spacing: 0.2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-sub {
  margin: var(--lx-space-xs) 0 0;
  display: flex;
  align-items: center;
  gap: var(--lx-space-xs);
  font-size: var(--lx-font-md);
  color: var(--lx-text-secondary);
}

.searching-tag {
  font-size: var(--lx-font-sm);
  color: var(--lx-accent);
  background: var(--lx-bg-card);
  padding: var(--lx-space-xs) var(--lx-space-md);
  border-radius: var(--lx-radius-pill);
  flex-shrink: 0;
}

/* 空状态 */
.empty-search,
.empty-history {
  padding: var(--lx-space-6xl) var(--lx-space-3xl);
  text-align: center;
}

/* 滚动容器 */
.history-scroll,
.search-results {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-2xl) var(--lx-space-3xl);
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-lg);
}

/* 搜索结果分组 */
.result-group {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space);
}

.result-session {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lx-space-md) var(--lx-space-xl);
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius-xl);
  cursor: pointer;
  transition: background var(--lx-duration) ease, transform var(--lx-duration-fast) ease;
  font-weight: 500;
}

.result-session:hover {
  background: var(--lx-accent-soft);
}

.result-session:active {
  transform: scale(0.99);
}

.session-name {
  font-size: var(--lx-font);
  color: var(--lx-text-body);
  font-weight: 500;
}

.result-count {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  background: var(--lx-bg-card);
  padding: var(--lx-space-2xs) var(--lx-space);
  border-radius: var(--lx-radius-pill);
  border: 1px solid var(--lx-border-light);
}

/* 消息项 */
.result-item,
.history-item {
  display: flex;
}

.result-item.self,
.history-item.self {
  justify-content: flex-end;
}

.result-bubble,
.history-bubble {
  max-width: min(86%, 480px);
  padding: var(--lx-space-md) var(--lx-space-xl);
  border-radius: var(--lx-radius-lg);
  background: var(--lx-bg-panel);
  border: 1px solid var(--lx-border-light);
  cursor: pointer;
  transition: border-color var(--lx-duration) ease, transform var(--lx-duration-fast) ease;
}

.result-bubble:hover,
.history-bubble:hover {
  border-color: color-mix(in srgb, var(--lx-accent) 35%, transparent);
}

.result-item.self .result-bubble,
.history-item.self .history-bubble {
  background: var(--lx-accent-soft);
  border-color: color-mix(in srgb, var(--lx-accent) 25%, transparent);
}

.result-text,
.history-text {
  font-size: var(--lx-font);
  color: var(--lx-text-body);
  margin: 0;
  line-height: var(--lx-leading-normal);
  word-break: break-word;
}

.result-text :deep(mark) {
  background: color-mix(in srgb, var(--lx-accent) 28%, transparent);
  color: inherit;
  padding: 0 var(--lx-space-2xs);
  border-radius: var(--lx-radius-hair);
}

.result-time,
.history-time {
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
  margin-top: var(--lx-space-sm);
  display: block;
  text-align: right;
}

.result-more {
  text-align: center;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  padding: var(--lx-space-xs);
}
</style>