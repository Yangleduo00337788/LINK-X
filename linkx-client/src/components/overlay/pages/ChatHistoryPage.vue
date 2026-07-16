<script setup lang="ts">
/**
 * 聊天记录浏览与搜索页面
 */
import { computed, ref, watch } from 'vue'
import { NIcon, NInput, NEmpty, useMessage } from 'naive-ui'
import { ChatbubblesOutline, TimeOutline, SearchOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../../stores/app'
import EmptyState from '../../common/EmptyState.vue'
import Avatar from '../../Avatar.vue'

const appStore = useAppStore()
const message = useMessage()
const { sessions, messagesBySession, currentSessionId } = storeToRefs(appStore)
const { selectSession } = appStore

// 搜索关键词
const searchQuery = ref('')
const searchResults = ref<Array<{
  sessionId: string
  sessionName: string
  messages: Array<{ id: string; content: string; time: string; isSelf: boolean; type: string }>
}>>([])

// 所有消息（用于搜索）
const allMessages = computed(() => {
  const results: Array<{
    sessionId: string
    sessionName: string
    messages: Array<{ id: string; content: string; time: string; isSelf: boolean; type: string }>
  }> = []

  for (const session of sessions.value) {
    const msgs = messagesBySession.value[session.id] || []
    if (msgs.length > 0) {
      results.push({
        sessionId: session.id,
        sessionName: session.name,
        messages: msgs.filter(m => m.type !== 'system')
      })
    }
  }
  return results
})

// 搜索处理
watch(searchQuery, (q) => {
  if (!q.trim()) {
    searchResults.value = []
    return
  }
  const query = q.trim().toLowerCase()
  const results: typeof searchResults.value = []

  for (const session of allMessages.value) {
    const matched = session.messages.filter(m => {
      if (m.type === 'image' || m.type === 'file' || m.type === 'voice' || m.type === 'redPacket') {
        return false
      }
      return m.content.toLowerCase().includes(query)
    })
    if (matched.length > 0) {
      results.push({
        sessionId: session.sessionId,
        sessionName: session.sessionName,
        messages: matched
      })
    }
  }
  searchResults.value = results
})

// 当前会话消息
const currentMessages = computed(() => {
  if (!currentSessionId.value) return []
  return (messagesBySession.value[currentSessionId.value] || [])
    .filter(m => m.type !== 'system')
})

// 当前会话名称
const currentSessionName = computed(() => {
  return sessions.value.find(s => s.id === currentSessionId.value)?.name || '—'
})

// 历史消息预览
function historyPreview(msg: (typeof currentMessages.value)[number]) {
  if (msg.type === 'file') return `[文件] ${msg.fileName || msg.content}`
  if (msg.type === 'image' || msg.isImage) return '[图片]'
  if (msg.type === 'voice') return '[语音]'
  if (msg.type === 'redPacket') return `[红包] ${msg.redPacketGreeting || msg.content}`
  return msg.content
}

// 跳转到指定消息所在会话
function goToMessage(sessionId: string) {
  const session = sessions.value.find(s => s.id === sessionId)
  if (session) {
    selectSession(session)
    message.success('已跳转到对应会话')
  }
}
</script>

<template>
  <div class="page-wrap history-page">
    <!-- 搜索框 -->
    <div class="search-bar">
      <n-input
        v-model:value="searchQuery"
        placeholder="搜索聊天记录..."
        clearable
      >
        <template #prefix>
          <n-icon :component="SearchOutline" />
        </template>
      </n-input>
    </div>

    <!-- 搜索结果 -->
    <template v-if="searchQuery.trim()">
      <section class="panel-card history-card">
        <div class="history-hero">
          <div class="history-avatar">
            <n-icon :component="SearchOutline" :size="28" />
          </div>
          <div class="history-meta">
            <h2 class="history-name">搜索结果</h2>
            <p class="history-sub">
              共 {{ searchResults.reduce((sum, r) => sum + r.messages.length, 0) }} 条相关记录
            </p>
          </div>
        </div>

        <div v-if="searchResults.length === 0" class="empty-search">
          <n-empty description="未找到匹配的聊天记录" />
        </div>

        <div v-else class="search-results">
          <div v-for="result in searchResults" :key="result.sessionId" class="result-group">
            <div class="result-session" @click="goToMessage(result.sessionId)">
              <span class="session-name">{{ result.sessionName }}</span>
              <span class="result-count">{{ result.messages.length }} 条</span>
            </div>
            <div
              v-for="msg in result.messages.slice(0, 5)"
              :key="msg.id"
              class="result-item"
              :class="{ self: msg.isSelf }"
              @click="goToMessage(result.sessionId)"
            >
              <div class="result-bubble">
                <p class="result-text">{{ msg.content }}</p>
                <span class="result-time">{{ msg.time }}</span>
              </div>
            </div>
            <div v-if="result.messages.length > 5" class="result-more">
              还有 {{ result.messages.length - 5 }} 条结果...
            </div>
          </div>
        </div>
      </section>
    </template>

    <!-- 当前会话聊天记录 -->
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
              共 {{ currentMessages.length }} 条消息
            </p>
          </div>
        </div>

        <div v-if="currentMessages.length === 0" class="empty-history">
          <EmptyState
            title="暂无消息"
            description="当前会话还没有聊天记录"
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
