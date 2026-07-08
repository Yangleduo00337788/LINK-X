<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon, NSkeleton, NDropdown, useMessage, type DropdownOption } from 'naive-ui'
import { PhonePortraitOutline, NotificationsOffOutline, WarningOutline, PinOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import Avatar from './Avatar.vue'
import EmptyState from './common/EmptyState.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
import type { ChatSession } from '../types'

const message = useMessage()
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const { sortedSessions, currentSessionId, isLoading, isOffline } = storeToRefs(appStore)
const { selectSession, toggleSessionPin, toggleSessionMute, deleteSession } = appStore
const { openCreateGroup, openComprehensiveSearch } = chatModalsStore
const searchValue = ref('')

const contextSession = ref<ChatSession | null>(null)
const contextMenuShow = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)

const filteredSessions = computed(() => {
  const q = searchValue.value.trim().toLowerCase()
  if (!q) return sortedSessions.value
  return sortedSessions.value.filter(
    s => s.name.toLowerCase().includes(q) || s.lastMessage.toLowerCase().includes(q)
  )
})

const contextMenuOptions = computed<DropdownOption[]>(() => {
  const s = contextSession.value
  if (!s) return []
  return [
    { label: s.pinned ? '取消置顶' : '置顶', key: 'pin' },
    { label: s.muted ? '取消免打扰' : '免打扰', key: 'mute' },
    { type: 'divider', key: 'd1' },
    { label: '删除会话', key: 'delete' }
  ]
})

const addOptions = [
  { label: '发起群聊', key: 'group' },
  { label: '添加好友', key: 'friend' }
]

function onSelect(session: ChatSession) {
  selectSession(session)
}

function onAddSelect(key: string) {
  if (key === 'group') {
    openCreateGroup()
    return
  }
  if (key === 'friend') {
    openComprehensiveSearch()
  }
}

function onSessionContext(e: MouseEvent, session: ChatSession) {
  e.preventDefault()
  contextSession.value = session
  contextMenuX.value = e.clientX
  contextMenuY.value = e.clientY
  contextMenuShow.value = true
}

function onContextMenuSelect(key: string) {
  const s = contextSession.value
  if (!s) return
  if (key === 'pin') {
    const wasPinned = s.pinned
    toggleSessionPin(s.id)
    message.success(wasPinned ? '已取消置顶' : '已置顶')
  } else if (key === 'mute') {
    const wasMuted = s.muted
    toggleSessionMute(s.id)
    message.success(wasMuted ? '已取消免打扰' : '已开启免打扰')
  } else if (key === 'delete') {
    deleteSession(s.id)
    message.success('已删除会话')
  }
  contextMenuShow.value = false
}
</script>

<template>
  <div class="chat-list">
    <PanelSearchBar
      v-model="searchValue"
      placeholder="搜索"
      :add-options="addOptions"
      @add-select="onAddSelect"
    />

    <div v-if="isOffline" class="offline-banner">
      <n-icon :component="WarningOutline" :size="16" />
      <span>网络连接已断开，请检查网络设置</span>
    </div>

    <div class="session-list">
      <template v-if="isLoading">
        <div class="skeleton-item" v-for="i in 8" :key="i">
          <n-skeleton circle size="large" class="skeleton-avatar" />
          <div class="skeleton-info">
            <n-skeleton text width="60%" height="16px" class="skeleton-title" />
            <n-skeleton text width="80%" height="14px" class="skeleton-desc" />
          </div>
        </div>
      </template>

      <template v-else-if="filteredSessions.length === 0">
        <EmptyState title="无匹配的会话" description="请尝试其他关键词" />
      </template>

      <template v-else>
        <div
          v-for="session in filteredSessions"
          :key="session.id"
          class="session-item"
          :class="{ active: currentSessionId === session.id, pinned: session.pinned }"
          @click="onSelect(session)"
          @contextmenu="onSessionContext($event, session)"
        >
          <div class="avatar-wrapper">
            <Avatar
              :text="session.avatarText"
              :color="session.avatarColor"
              :size="44"
              :icon="session.name === '我的手机' ? PhonePortraitOutline : undefined"
            />
            <div v-if="session.unread && !session.muted" class="unread-badge">
              {{ session.unread > 99 ? '99+' : session.unread }}
            </div>
          </div>

          <div class="session-content">
            <div class="session-top">
              <span class="session-name">
                <n-icon v-if="session.pinned" :component="PinOutline" :size="12" class="pin-icon" />
                {{ session.name }}
              </span>
              <span class="session-meta">
                <n-icon
                  v-if="session.muted"
                  :component="NotificationsOffOutline"
                  :size="14"
                  class="mute-icon"
                />
                <span class="session-time">{{ session.time }}</span>
              </span>
            </div>
            <div class="session-bottom">
              <span class="last-message">{{ session.lastMessage }}</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <n-dropdown
      trigger="manual"
      placement="bottom-start"
      :show="contextMenuShow"
      :x="contextMenuX"
      :y="contextMenuY"
      :options="contextMenuOptions"
      @select="onContextMenuSelect"
      @clickoutside="contextMenuShow = false"
    />
  </div>
</template>

<style scoped>
.chat-list {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  border-right: none;
  flex-shrink: 0;
}

.offline-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: var(--lx-danger-bg-soft);
  color: var(--lx-danger);
  padding: 8px;
  font-size: 12px;
  border-bottom: 1px solid #ffccc7;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  background: var(--lx-bg-panel);
  padding: 4px 0;
}

.session-item {
  height: 68px;
  display: flex;
  align-items: center;
  padding: 0 10px 0 12px;
  margin: 0 6px;
  gap: 12px;
  border-radius: var(--lx-radius);
  cursor: pointer;
  transition: background 0.16s ease;
}

.session-item.pinned {
  background: rgba(18, 183, 245, 0.06);
}

.session-item:hover {
  background: var(--lx-bg-hover);
}

.session-item.active {
  background: rgba(18, 183, 245, 0.14);
}

.avatar-wrapper {
  position: relative;
  flex-shrink: 0;
}

.unread-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--lx-radius);
  background: linear-gradient(180deg, #ff6b6b 0%, #f04040 100%);
  color: var(--lx-bg-card);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--lx-bg-panel);
  box-shadow: 0 1px 3px rgba(240, 64, 64, 0.35);
}

.session-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.session-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-name {
  font-size: 14px;
  color: var(--lx-text-body);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 4px;
}

.pin-icon {
  color: var(--lx-accent);
  flex-shrink: 0;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.mute-icon {
  color: #b0b0b0;
}

.session-time {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.last-message {
  font-size: 12px;
  color: var(--lx-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skeleton-item {
  display: flex;
  padding: 12px 14px;
  gap: 10px;
  align-items: center;
}

.skeleton-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
