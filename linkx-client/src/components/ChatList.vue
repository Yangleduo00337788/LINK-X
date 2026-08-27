<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 聊天会话列表组件。
 * <p>
 * 展示会话列表，支持搜索过滤、虚拟滚动、右键菜单（置顶/免打扰/删除），
 * 以及通过添加按钮发起群聊或添加好友。
 * 列表中插入「日程提醒」「LinkX官方」站内通知虚拟会话（默认不置顶）。
 * </p>
 */
import { ref, computed, onMounted, h, type Component } from 'vue'
import { NIcon, NSkeleton, NDropdown, NVirtualList, useMessage, type DropdownOption } from 'naive-ui'
import {
  PhonePortraitOutline,
  NotificationsOffOutline,
  WarningOutline,
  CalendarOutline,
  SparklesOutline,
  PeopleOutline,
  PersonAddOutline
} from '@vicons/ionicons5'
import PinIcon from './icons/PinIcon.vue'
import PanelSearchBar from './PanelSearchBar.vue'
import Avatar from './Avatar.vue'
import GroupAvatar from './GroupAvatar.vue'
import EmptyState from './common/EmptyState.vue'
import OpsRecommendCarousel from './ops/OpsRecommendCarousel.vue'

import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
import { useNotificationsStore } from '../stores/notifications'
import { useCalendarStore } from '../stores/calendar'
import { useLinkMateStore } from '../stores/linkmate'
import type { CalendarEvent } from '../stores/calendar'
import { lxColorHex } from '../theme/vars'
import type { ChatSession } from '../types'
import { SYSTEM_NOTIFY_SESSION_ID, OFFICIAL_NOTIFY_SESSION_ID } from '../types'
import { DEFAULT_AVATAR_URL } from '../utils/defaultAvatar'
import { formatChatTime } from '../utils/chatTime'
import { useI18n } from '../i18n'
import { isMyPhoneSessionName } from '../utils/myPhoneSession'
import { virtualListScrollbarProps, useNaiveVirtualListNativeScrollbar } from '../utils/virtualListScrollbar'

const message = useMessage()
const { t } = useI18n()
const showSidebarOps = ref(false)
const appStore = useAppStore()
const chatModalsStore = useChatModalsStore()
const notificationsStore = useNotificationsStore()
const calendarStore = useCalendarStore()
const linkMateStore = useLinkMateStore()

const { sortedSessions, currentSessionId, isLoading, isOffline } = storeToRefs(appStore)
const {
  calendarRemindNotifs,
  calendarRemindUnreadCount,
  officialNotifs,
  officialUnreadCount
} = storeToRefs(notificationsStore)
const { remindedUpcomingEvents } = storeToRefs(calendarStore)
const { selectSession, toggleSessionPin, toggleSessionImportant, toggleSessionMute, deleteSession, setNav } =
  appStore
const { openCreateGroup, openComprehensiveSearch } = chatModalsStore
const { fetchMessageNotifications } = notificationsStore

const searchValue = ref('')
const sessionListRef = ref<InstanceType<typeof NVirtualList> | null>(null)
useNaiveVirtualListNativeScrollbar(sessionListRef)

const contextSession = ref<ChatSession | null>(null)
const contextMenuShow = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)

onMounted(() => {
  void fetchMessageNotifications()
  void calendarStore.ensureReminderWatch()
})

function formatUpcomingEventPreview(ev: CalendarEvent): string {
  const timePart = `${ev.date} ${ev.time || ''}`.trim()
  return t('chat.remindAtWithTitle', { time: timePart, title: ev.title })
}

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

/** 会话预览是否为红包消息（列表用红色强调） */
function isRedPacketPreview(text?: string) {
  return !!text && text.includes('[红包]')
}

/** 消息页虚拟会话：日程提醒（默认不置顶） */
const systemNotifySession = computed<ChatSession>(() => {
  const list = calendarRemindNotifs.value
  const latest = list[0]
  const upcoming = remindedUpcomingEvents.value[0]
  const preview = latest
    ? formatRemindPreview(latest.content)
    : upcoming
      ? formatUpcomingEventPreview(upcoming)
      : t('chat.noRemind')
  return {
    id: SYSTEM_NOTIFY_SESSION_ID,
    name: t('chat.calendarRemind'),
    lastMessage: preview,
    time: formatNotifListTime(latest?.createTime),
    avatarText: t('chat.remindAvatar'),
    avatarColor: lxColorHex.accent,
    unread: calendarRemindUnreadCount.value || undefined,
    pinned: false,
    isReal: false,
    isSystemNotify: true
  }
})

/** 消息页虚拟会话：LinkX官方（反馈进度） */
const officialNotifySession = computed<ChatSession>(() => {
  const list = officialNotifs.value
  const latest = list[0]
  const preview = (latest?.content || '')
    .split(/\r?\n/)
    .map(s => s.trim())
    .find(Boolean)
  return {
    id: OFFICIAL_NOTIFY_SESSION_ID,
    name: t('chat.officialSession'),
    lastMessage: preview || t('chat.noOfficial'),
    time: formatNotifListTime(latest?.createTime),
    avatarText: t('chat.officialAvatar'),
    avatarColor: 'var(--lx-bg-logo)',
    avatarUrl: DEFAULT_AVATAR_URL,
    unread: officialUnreadCount.value || undefined,
    pinned: false,
    isReal: false,
    isOfficialNotify: true
  }
})

const filteredSessions = computed(() => {
  const q = searchValue.value.trim().toLowerCase()
  const system = systemNotifySession.value
  const official = officialNotifySession.value
  const rest = sortedSessions.value
  // 不置顶：跟在普通会话后面；官方在日程提醒下方
  const merged = [...rest, system, official]
  if (!q) return merged
  return merged.filter(
    s => s.name.toLowerCase().includes(q) || s.lastMessage.toLowerCase().includes(q)
  )
})

const contextMenuOptions = computed<DropdownOption[]>(() => {
  const s = contextSession.value
  if (!s || s.isSystemNotify || s.isOfficialNotify) return []
  return [
    { label: s.important ? t('chat.unmarkImportant') : t('chat.markImportant'), key: 'important' },
    { label: s.pinned ? t('chat.unpin') : t('chat.pin'), key: 'pin' },
    { label: s.muted ? t('chat.unmute') : t('chat.mute'), key: 'mute' },
    { type: 'divider', key: 'd1' },
    { label: t('chat.deleteSession'), key: 'delete' }
  ]
})

function renderAddIcon(icon: Component) {
  return () => h(NIcon, { size: 16 }, { default: () => h(icon) })
}

const addOptions = computed(() => [
  { label: t('chat.createGroup'), key: 'group', icon: renderAddIcon(PeopleOutline) },
  { label: t('chat.addFriendGroup'), key: 'friend', icon: renderAddIcon(PersonAddOutline) },
  { label: t('chat.openLinkmate'), key: 'linkmate', icon: renderAddIcon(SparklesOutline) }
])

function isMyPhoneSession(name?: string): boolean {
  return isMyPhoneSessionName(name)
}

function onSelect(session: ChatSession) {
  if (session.isSystemNotify) {
    appStore.currentSessionId = SYSTEM_NOTIFY_SESSION_ID
    return
  }
  if (session.isOfficialNotify) {
    appStore.currentSessionId = OFFICIAL_NOTIFY_SESSION_ID
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
    return
  }
  if (key === 'linkmate') {
    setNav('chat')
    void (async () => {
      await linkMateStore.ensurePanelReady()
      linkMateStore.openPanel()
    })()
  }
}

function onSessionContext(e: MouseEvent, session: ChatSession) {
  if (session.isSystemNotify || session.isOfficialNotify) return
  e.preventDefault()
  contextSession.value = session
  contextMenuX.value = e.clientX
  contextMenuY.value = e.clientY
  contextMenuShow.value = true
}

function onContextMenuSelect(key: string) {
  const s = contextSession.value
  if (!s) return
  if (key === 'important') {
    const was = s.important
    void toggleSessionImportant(s.id).then(() => {
      message.success(was ? t('chat.unimportantOk') : t('chat.importantOk'))
    })
  } else if (key === 'pin') {
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

    <div v-show="showSidebarOps" class="ops-slot">
      <OpsRecommendCarousel
        slot-code="chat_sidebar"
        :height="76"
        :radius="10"
        :show-caption="true"
        @loaded="(p) => (showSidebarOps = p.count > 0)"
      />
    </div>

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
          ref="sessionListRef"
          class="session-vl"
          style="max-height: 100%; height: 100%"
          :item-size="68"
          :items="filteredSessions"
          item-key="id"
          :scrollbar-props="virtualListScrollbarProps"
        >
          <template #default="{ item: session }">
            <div
              class="session-item"
              :class="{
                active: currentSessionId === session.id,
                pinned: session.pinned,
                important: session.important
              }"
              :data-lm-session-id="session.id"
              :data-lm-session-name="session.name"
              @click="onSelect(session)"
              @contextmenu="onSessionContext($event, session)"
            >
              <div class="avatar-wrapper">
                <GroupAvatar
                  v-if="session.isGroup"
                  :size="44"
                  :image-url="session.avatarUrl"
                  :default-image-url="session.ownerAvatarUrl"
                />
                <Avatar
                  v-else
                  :color="session.avatarColor"
                  :size="44"
                  :image-url="session.avatarUrl"
                  :icon="
                    session.isSystemNotify
                      ? CalendarOutline
                      : session.isOfficialNotify
                        ? undefined
                        : isMyPhoneSession(session.name)
                          ? PhonePortraitOutline
                          : undefined
                  "
                />
                <div
                  v-if="!session.isGroup && session.isReal && session.online"
                  class="session-online-dot"
                  :title="t('chat.online')"
                />
                <div
                  v-if="session.unread && !session.muted"
                  class="unread-badge"
                >
                  {{ session.unread > 99 ? '99+' : session.unread }}
                </div>
                <div
                  v-else-if="(session.atMe || session.atMeMessageId) && session.muted"
                  class="unread-dot"
                  :title="t('chat.someoneAtMe')"
                />
              </div>

              <div class="session-content">
                <div class="session-name">
                  <span class="session-name-text">
                    <span v-if="session.important" class="important-mark" :title="t('chat.important')">★</span>
                    {{ session.name }}
                  </span>
                </div>
                <span class="session-meta">
                  <PinIcon v-if="session.pinned" :size="10" filled class="pin-icon" />
                  <n-icon
                    v-if="session.muted"
                    :component="NotificationsOffOutline"
                    :size="14"
                    class="mute-icon"
                  />
                  <span class="session-time">{{ session.time }}</span>
                </span>
                <span class="last-message">
                  <span
                    v-if="session.atMe || session.atMeMessageId"
                    class="at-me-hint"
                  >{{ t('chat.someoneAtMe') }}</span>
                  <span
                    v-if="isRedPacketPreview(session.lastMessage) && !!session.unread && !session.muted"
                    class="red-packet-preview"
                  >{{ session.lastMessage }}</span>
                  <template v-else>{{ session.lastMessage }}</template>
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

.ops-slot {
  padding: 0 var(--lx-space-lg) var(--lx-space);
  flex-shrink: 0;
}

.offline-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lx-space-sm);
  background: var(--lx-danger-bg-soft);
  color: var(--lx-danger);
  padding: var(--lx-space);
  font-size: var(--lx-font-sm);
  border-bottom: 1px solid var(--lx-danger-soft-border);
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--lx-bg-panel);
  padding: var(--lx-space-xs) 0;
  display: flex;
  flex-direction: column;
}

.session-vl {
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
}

.session-item {
  height: 68px;
  display: flex;
  align-items: center;
  padding: 0 var(--lx-space-md) 0 var(--lx-space-lg);
  margin: 0 var(--lx-space-sm);
  gap: var(--lx-space-lg);
  border-radius: var(--lx-radius);
  cursor: pointer;
  transition: background var(--lx-duration) ease;
}

.session-item.important {
  background: linear-gradient(90deg, rgba(250, 173, 20, 0.12), transparent 72%);
}

.session-item.important.pinned {
  background: linear-gradient(90deg, rgba(250, 173, 20, 0.16), transparent 72%);
}

.session-item:hover {
  background: var(--lx-bg-hover);
}

.session-item.important:hover {
  background: linear-gradient(90deg, rgba(250, 173, 20, 0.2), var(--lx-bg-hover) 60%);
}

.session-item.active {
  background: rgba(18, 183, 245, 0.14);
}

.avatar-wrapper {
  position: relative;
  flex-shrink: 0;
}

.session-online-dot {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--lx-success, var(--lx-success));
  border: 2px solid var(--lx-bg-panel);
  box-shadow: 0 0 0 1px rgba(82, 196, 26, 0.35);
}

.unread-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  min-width: 18px;
  height: 18px;
  padding: 0 var(--lx-space-xs);
  border-radius: var(--lx-radius);
  background: var(--lx-danger);
  color: var(--lx-bg-card);
  font-size: var(--lx-font-2xs);
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
  column-gap: var(--lx-space);
  row-gap: var(--lx-space-sm);
  align-items: center;
}

.session-name {
  grid-area: name;
  display: flex;
  align-items: center;
  gap: var(--lx-space-xs);
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
  line-height: var(--lx-leading-snug);
}

.session-name-text {
  display: block;
  font-size: var(--lx-font);
  font-weight: 500;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pin-icon,
.important-mark {
  display: inline;
  vertical-align: baseline;
}

.pin-icon {
  flex-shrink: 0;
  color: var(--lx-text-tertiary, var(--lx-text-secondary));
  opacity: 0.88;
}

.important-mark {
  color: var(--lx-warning);
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-none);
  margin-right: var(--lx-space-2xs);
  flex-shrink: 0;
}

.session-meta {
  grid-area: meta;
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  flex-shrink: 0;
}

.mute-icon {
  color: var(--lx-text-secondary);
}

.session-time {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-secondary);
}

.last-message {
  grid-area: msg;
  display: block;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-secondary);
}

.last-message .red-packet-preview {
  color: var(--lx-danger);
  font-weight: 500;
}

.at-me-hint {
  color: var(--lx-danger);
  margin-right: var(--lx-space-xs);
  flex-shrink: 0;
}

.red-packet-preview {
  color: var(--lx-danger);
}

.skeleton-item {
  height: 68px;
  display: flex;
  align-items: center;
  padding: 0 var(--lx-space-lg);
  gap: var(--lx-space-lg);
}

.skeleton-avatar {
  width: 44px !important;
  height: 44px !important;
  border-radius: var(--lx-radius-sm);
  flex-shrink: 0;
}

.skeleton-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space);
}

.skeleton-title,
.skeleton-desc {
  margin: 0;
}
</style>
