<script setup lang="ts">
/**
 * 聊天记录浏览与搜索页面（服务端搜索 + 时间范围 + 高亮 + 当前会话本地预览）
 */
import { computed, ref, watch } from 'vue'
import { NIcon, NInput, NEmpty, NDatePicker, useMessage } from 'naive-ui'
import { ChatbubblesOutline, TimeOutline, SearchOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../../stores/app'
import EmptyState from '../../common/EmptyState.vue'
import { useI18n } from '../../../i18n'
import * as chatApi from '../../../api/chat'

const appStore = useAppStore()
const message = useMessage()
const { t } = useI18n()
const { sessions, messagesBySession, currentSessionId } = storeToRefs(appStore)
const { selectSession } = appStore

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
            hit.conversationName || sessions.value.find(s => s.id === sid)?.name || '会话',
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

function historyPreview(msg: (typeof currentMessages.value)[number]) {
  if (msg.type === 'file') return `${t('overlay.file')} ${msg.fileName || msg.content}`
  if (msg.type === 'image' || msg.isImage) return t('overlay.image')
  if (msg.type === 'voice') return t('overlay.voice')
  if (msg.type === 'redPacket')
    return `${t('overlay.redPacket')} ${msg.redPacketGreeting || msg.content}`
  return msg.content
}

function goToMessage(sessionId: string) {
  const session = sessions.value.find(s => s.id === sessionId)
  if (session) {
    selectSession(session)
    message.success(t('overlay.jumpedToSession'))
  }
}

function clearTimeRange() {
  timeRange.value = null
}
</script>

<template>
  <div class="page-wrap history-page">
    <div class="search-bar">
      <n-input v-model:value="searchQuery" :placeholder="t('overlay.searchHistory')" clearable>
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
        />
        <button
          v-if="timeRange"
          type="button"
          class="clear-time"
          @click="clearTimeRange"
        >
          {{ t('overlay.clearTimeRange') }}
        </button>
      </div>
    </div>

    <template v-if="searchQuery.trim()">
      <section class="panel-card history-card">
        <div class="history-hero">
          <div class="history-avatar">
            <n-icon :component="SearchOutline" :size="28" />
          </div>
          <div class="history-meta">
            <h2 class="history-name">{{ t('overlay.searchResults') }}</h2>
            <p class="history-sub">
              {{
                t('overlay.resultCount', {
                  n: searchResults.reduce((sum, r) => sum + r.messages.length, 0)
                })
              }}
            </p>
          </div>
        </div>

        <div v-if="searchResults.length === 0" class="empty-search">
          <n-empty :description="t('overlay.noMatchHistory')" />
        </div>

        <div v-else class="search-results">
          <div v-for="result in searchResults" :key="result.sessionId" class="result-group">
            <div class="result-session" @click="goToMessage(result.sessionId)">
              <span class="session-name">{{ result.sessionName }}</span>
              <span class="result-count">{{
                t('overlay.countUnit', { n: result.messages.length })
              }}</span>
            </div>
            <div
              v-for="msg in result.messages.slice(0, 5)"
              :key="msg.id"
              class="result-item"
              :class="{ self: msg.isSelf }"
              @click="goToMessage(result.sessionId)"
            >
              <div class="result-bubble">
                <p
                  v-if="msg.highlight"
                  class="result-text"
                  v-html="msg.highlight"
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
            <n-icon :component="ChatbubblesOutline" :size="28" />
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
  padding: 16px;
}

.search-bar {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.time-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.clear-time {
  border: none;
  background: transparent;
  color: var(--lx-accent);
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}

.history-card {
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
}

.history-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border-bottom: 1px solid var(--lx-border-light);
}

.history-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--lx-accent-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-accent);
}

.history-meta {
  flex: 1;
}

.history-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--lx-text-body);
  margin: 0;
}

.history-sub {
  font-size: 13px;
  color: var(--lx-text-muted);
  margin: 4px 0 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.empty-search,
.empty-history {
  padding: 40px 20px;
  text-align: center;
}

.history-scroll,
.search-results {
  max-height: 500px;
  overflow-y: auto;
  padding: 12px;
}

.result-group {
  margin-bottom: 16px;
}

.result-session {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  cursor: pointer;
  margin-bottom: 8px;
}

.result-session:hover {
  background: var(--lx-accent-soft);
}

.session-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.result-count {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.result-item {
  display: flex;
  margin-bottom: 8px;
}

.result-item.self {
  justify-content: flex-end;
}

.result-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--lx-bg-panel);
}

.result-item.self .result-bubble {
  background: var(--lx-accent-soft);
}

.result-text {
  font-size: 14px;
  color: var(--lx-text-body);
  margin: 0;
  line-height: 1.4;
  word-break: break-word;
}

.result-text :deep(mark) {
  background: color-mix(in srgb, var(--lx-accent) 28%, transparent);
  color: inherit;
  padding: 0 2px;
  border-radius: 2px;
}

.result-time {
  font-size: 11px;
  color: var(--lx-text-muted);
  margin-top: 4px;
  display: block;
}

.history-item {
  display: flex;
  margin-bottom: 8px;
}

.history-item.self {
  justify-content: flex-end;
}

.history-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--lx-bg-panel);
}

.history-item.self .history-bubble {
  background: var(--lx-accent-soft);
}

.history-text {
  font-size: 14px;
  color: var(--lx-text-body);
  margin: 0;
  line-height: 1.4;
  word-break: break-word;
}

.history-time {
  font-size: 11px;
  color: var(--lx-text-muted);
  margin-top: 4px;
  display: block;
}

.result-more {
  text-align: center;
  font-size: 12px;
  color: var(--lx-text-muted);
  padding: 4px;
}
</style>
