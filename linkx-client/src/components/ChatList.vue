<script setup lang="ts">
/**
 * 聊天会话列表组件。
 * <p>
 * 展示会话列表，支持搜索过滤、虚拟滚动、右键菜单（置顶/免打扰/删除），
 * 以及通过添加按钮发起群聊或添加好友。
 * 列表中插入「日程提醒」站内通知虚拟会话（默认不置顶）。
 * </p>
 */
import { ref, computed, onMounted } from 'vue'
import { NIcon, NSkeleton, NDropdown, NVirtualList, useMessage, type DropdownOption } from 'naive-ui'
import {
  PhonePortraitOutline,
  NotificationsOffOutline,
  WarningOutline,
  CalendarOutline
} from '@vicons/ionicons5'
import PinIcon from './icons/PinIcon.vue'
import PanelSearchBar from './PanelSearchBar.vue'
import Avatar from './Avatar.vue'
import GroupAvatar from './GroupAvatar.vue'
import EmptyState from './common/EmptyState.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
import { useNotificationsStore } from '../stores/notifications'
import type { ChatSession } from '../types'
import { SYSTEM_NOTIFY_SESSION_ID } from '../types'
import { formatChatTime } from '../utils/chatTime'
import { useI18n } from '../i18n'

const message = useMessage()
const { t } = useI18n()
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const notificationsStore = useNotificationsStore()

const { sortedSessions, currentSessionId, isLoading, isOffline } = storeToRefs(appStore)
const { calendarRemindNotifs, calendarRemindUnreadCount } = storeToRefs(notificationsStore)
const { selectSession, toggleSessionPin, toggleSessionMute, deleteSession } = appStore
const { openCreateGroup, openComprehensiveSearch } = chatModalsStore
const { fetchMessageNotifications } = notificationsStore

const searchValue = ref('')

const contextSession = ref<ChatSession | null>(null)
const contextMenuShow = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)

onMounted(() => {
  void fetchMessageNotifications()
})

function formatNotifListTime(raw?: string): string {
  if (!raw) return ''
  const ms = Date.parse(raw)
  if (!Number.isFinite(ms)) return ''
  return formatChatTime(ms)
}

/** 列表预览：统一成「将于 …」开头，与标题「日」左缘视觉对齐 */
function formatRemindPreview(content?: string): string {
  if (!content) return t('chat.noRemind')
  const raw = content.replace(/^[「【\[]([^」】\]]*)[」】\]]\s*/, '$1 ').trim()
  const m = raw.match(/^(?:(.+?)\s+)?将于\s+(.+)$/)
  if (m?.[2]) {
    const title = (m[1] || '').trim()
    return title
      ? t('chat.remindAtWithTitle', { time: m[2], title })
      : t('chat.remindAt', { time: m[2] })
  }
  return raw
}

/** 消息页虚拟会话：日程提醒（默认不置顶） */
const systemNotifySession = computed<ChatSession>(() => {
  const list = calendarRemindNotifs.value
  const latest = list[0]
  return {
    id: SYSTEM_NOTIFY_SESSION_ID,
    name: t('chat.calendarRemind'),
    lastMessage: formatRemindPreview(latest?.content),
    time: formatNotifListTime(latest?.createTime),
    avatarText: t('chat.remindAvatar'),
    avatarColor: '#12b7f5',
    unread: calendarRemindUnreadCount.value || undefined,
    pinned: false,
    isReal: false,
    isSystemNotify: true
  }
})

const filteredSessions = computed(() => {
  const q = searchValue.value.trim().toLowerCase()
  const system = systemNotifySession.value
  const rest = sortedSessions.value
  // 不置顶：跟在普通会话后面，不插到列表最前
  const merged = [...rest, system]
  if (!q) return merged
  return merged.filter(
    s => s.name.toLowerCase().includes(q) || s.lastMessage.toLowerCase().includes(q)
  )
})

const contextMenuOptions = computed<DropdownOption[]>(() => {
  const s = contextSession.value
  if (!s || s.isSystemNotify) return []
  return [
    { label: s.pinned ? t('chat.unpin') : t('chat.pin'), key: 'pin' },
    { label: s.muted ? t('chat.unmute') : t('chat.mute'), key: 'mute' },
    { type: 'divider', key: 'd1' },
    { label: t('chat.deleteSession'), key: 'delete' }
  ]
})

const addOptions = computed(() => [
  { label: t('chat.createGroup'), key: 'group' },
  { label: t('chat.addFriendGroup'), key: 'friend' }
])

function isMyPhoneSession(name?: string): boolean {
  return name === '我的手机' || name === t('chat.myPhone')
}

function onSelect(session: ChatSession) {
  if (session.isSystemNotify) {
    appStore.currentSessionId = SYSTEM_NOTIFY_SESSION_ID
    return
  }
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
  if (session.isSystemNotify) return
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
    message.success(wasPinned ? t('chat.unpinnedOk') : t('chat.pinnedOk'))
  } else if (key === 'mute') {
    const wasMuted = s.muted
    toggleSessionMute(s.id)
    message.success(wasMuted ? t('chat.unmutedOk') : t('chat.mutedOk'))
  } else if (key === 'delete') {
    deleteSession(s.id)
    message.success(t('chat.sessionDeleted'))
  }
  contextMenuShow.value = false
}
</script>

<template>
  <div class="chat-list">
    <PanelSearchBar
      v-model="searchValue"
      :placeholder="t('chat.search')"
      :add-options="addOptions"
      @add-select="onAddSelect"
    />

    <div v-if="isOffline" class="offline-banner">
      <n-icon :component="WarningOutline" :size="16" />
      <span>{{ t('chat.offlineBanner') }}</span>
    </div>

    <div class="session-list">
      <template v-if="isLoading">
        <div class="skeleton-item" v-for="i in 8" :key="i">
          <n-skeleton size="large" class="skeleton-avatar" />
          <div class="skeleton-info">
            <n-skeleton text width="60%" height="16px" class="skeleton-title" />
            <n-skeleton text width="80%" height="14px" class="skeleton-desc" />
          </div>
        </div>
      </template>

      <template v-else-if="filteredSessions.length === 0">
        <EmptyState :title="t('chat.noMatchSession')" :description="t('chat.tryOtherKeyword')" />
      </template>

      <template v-else>
        <n-virtual-list
          style="max-height: 100%; height: 100%"
          :item-size="68"
          :items="filteredSessions"
          item-key="id"
        >
          <template #default="{ item: session }">
            <div
              class="session-item"
              :class="{ active: currentSessionId === session.id, pinned: session.pinned }"
              @click="onSelect(session)"
              @contextmenu="onSessionContext($event, session)"
            >
              <div class="avatar-wrapper">
                <GroupAvatar
                  v-if="session.isGroup"
                  :text="session.avatarText"
                  :color="session.avatarColor"
                  :size="44"
                  :image-url="session.avatarUrl"
                  :faces="session.memberAvatars"
                />
                <Avatar
                  v-else
                  :text="session.avatarText"
                  :color="session.avatarColor"
                  :size="44"
                  :image-url="session.avatarUrl"
                  :icon="
                    session.isSystemNotify
                      ? CalendarOutline
                      : isMyPhoneSession(session.name)
                        ? PhonePortraitOutline
                        : undefined
                  "
                />
                <div
                  v-if="session.unread && !session.muted"
                  class="unread-badge"
                >
                  {{ session.unread > 99 ? '99+' : session.unread }}
                </div>
                <div
                  v-else-if="session.atMe && session.muted"
                  class="unread-dot"
                  :title="t('chat.someoneAtMe')"
                />
              </div>

              <div class="session-content">
                <div class="session-name">
                  <PinIcon v-if="session.pinned" :size="12" class="pin-icon" /><span
                    class="session-name-text"
                    >{{ session.name }}</span
                  >
                </div>
                <span class="session-meta">
                  <n-icon
                    v-if="session.muted"
                    :component="NotificationsOffOutline"
                    :size="14"
                    class="mute-icon"
                  />
                  <span class="session-time">{{ session.time }}</span>
                </span>
                <span class="last-message">
                  <span v-if="session.atMe" class="at-me-hint">{{ t('chat.someoneAtMe') }}</span>
                  {{ session.lastMessage }}
                </span>
              </div>
            </div>
          </template>
        </n-virtual-list>
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
  background: var(--lx-danger);
  color: var(--lx-bg-card);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--lx-bg-panel);
  box-shadow: 0 1px 3px rgba(240, 64, 64, 0.35);
}

.unread-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--lx-danger);
  border: 2px solid var(--lx-bg-panel);
}

.session-content {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'name meta'
    'msg msg';
  column-gap: 8px;
  row-gap: 6px;
  align-items: center;
}

.session-name {
  grid-area: name;
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  margin: 0;
  padding: 0;
}

.session-name-text,
.last-message {
  margin: 0;
  padding: 0;
  text-indent: 0;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
}

.session-name-text {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.pin-icon {
  flex-shrink: 0;
  color: var(--lx-accent);
}

.session-meta {
  grid-area: meta;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.mute-icon {
  color: var(--lx-text-secondary);
}

.session-time {
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.last-message {
  grid-area: msg;
  display: block;
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.at-me-hint {
  color: var(--lx-danger);
  margin-right: 4px;
  flex-shrink: 0;
}

.skeleton-item {
  height: 68px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  gap: 12px;
}

.skeleton-avatar {
  width: 44px !important;
  height: 44px !important;
  border-radius: 8px;
  flex-shrink: 0;
}

.skeleton-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-title,
.skeleton-desc {
  margin: 0;
}
</style>
