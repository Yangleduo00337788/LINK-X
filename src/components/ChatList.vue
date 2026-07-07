<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon } from 'naive-ui'
import { PhonePortraitOutline, NotificationsOffOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'
import Avatar from './Avatar.vue'
import { useAppState } from '../composables/useAppState'
import { useChatModals } from '../composables/useChatModals'
import type { ChatSession } from '../types'

const { sessions, currentSessionId, selectSession } = useAppState()
const { openCreateGroup, openComprehensiveSearch } = useChatModals()
const searchValue = ref('')

const filteredSessions = computed(() => {
  const q = searchValue.value.trim().toLowerCase()
  if (!q) return sessions.value
  return sessions.value.filter(
    s => s.name.toLowerCase().includes(q) || s.lastMessage.toLowerCase().includes(q)
  )
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
</script>

<template>
  <div class="chat-list">
    <PanelSearchBar
      v-model="searchValue"
      placeholder="搜索"
      :add-options="addOptions"
      @add-select="onAddSelect"
    />

    <div class="session-list">
      <div
        v-for="session in filteredSessions"
        :key="session.id"
        class="session-item"
        :class="{ active: currentSessionId === session.id }"
        @click="onSelect(session)"
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
            <span class="session-name">{{ session.name }}</span>
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
    </div>
  </div>
</template>

<style scoped>
.chat-list {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel, #f3f3f3);
  display: flex;
  flex-direction: column;
  border-right: none;
  flex-shrink: 0;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  background: var(--lx-bg-panel, #f3f3f3);
  padding: 4px 0;
}

.session-item {
  height: 68px;
  display: flex;
  align-items: center;
  padding: 0 10px 0 12px;
  margin: 0 6px;
  gap: 12px;
  border-radius: var(--lx-radius-md, 10px);
  cursor: pointer;
  transition: background 0.16s ease;
}

.session-item:hover {
  background: rgba(0, 0, 0, 0.04);
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
  border-radius: 10px;
  background: linear-gradient(180deg, #ff6b6b 0%, #f04040 100%);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--lx-bg-panel, #f3f3f3);
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
  color: #333;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  color: #999;
}

.last-message {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>