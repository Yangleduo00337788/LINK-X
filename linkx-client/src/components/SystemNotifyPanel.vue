<script setup lang="ts">
/**
 * 消息页「日程提醒」主面板：展示站内日程提醒消息列表。
 */
import { onMounted } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { CalendarOutline, CheckmarkDoneOutline, TrashOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../stores/notifications'
import { useCalendarStore } from '../stores/calendar'
import { useAppStore } from '../stores/app'
import EmptyState from './common/EmptyState.vue'
import type { MessageNotification } from '../stores/notifications'
import { useI18n } from '../i18n'

const message = useMessage()
const { t } = useI18n()
const notificationsStore = useNotificationsStore()
const calendarStore = useCalendarStore()
const appStore = useAppStore()

const { calendarRemindNotifs } = storeToRefs(notificationsStore)
const {
  fetchMessageNotifications,
  markMessageAsRead,
  markCalendarRemindsAsRead,
  deleteMessageNotification
} = notificationsStore

onMounted(() => {
  void fetchMessageNotifications()
})

function formatTime(raw: string): string {
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw
  const now = Date.now()
  const diff = Math.max(0, now - date.getTime())
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return t('chat.justNow')
  if (minutes < 60) return t('chat.minutesAgo', { n: minutes })
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return t('chat.hoursAgo', { n: hours })
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${m}-${d} ${hh}:${mm}`
}

function formatRemindContent(content?: string): string {
  if (!content) return ''
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

async function onClickItem(notif: MessageNotification) {
  if (notif.readStatus === 0) {
    void markMessageAsRead(notif.id)
  }
  if (notif.type !== 'calendar_remind' || !notif.relatedId) return

  let dateKey = ''
  const local = calendarStore.events.find(e => e.id === notif.relatedId)
  if (local?.date) {
    dateKey = local.date
  } else {
    const ev = await calendarStore.fetchEventById(notif.relatedId)
    if (ev?.date) dateKey = ev.date
  }
  if (!dateKey) {
    // 从文案兜底解析「将于 YYYY-MM-DD」
    const m = notif.content?.match(/(\d{4}-\d{2}-\d{2})/)
    if (m) dateKey = m[1]
  }
  if (dateKey) {
    const [y, mo, d] = dateKey.split('-').map(Number)
    await calendarStore.setSelectedDate(new Date(y, mo - 1, d).getTime())
  }
  appStore.setNav('calendar')
}

async function markAllRead() {
  await markCalendarRemindsAsRead()
  message.success(t('chat.markedAllRead'))
}

async function clearOne(notif: MessageNotification, e: Event) {
  e.stopPropagation()
  await deleteMessageNotification(notif.id)
}
</script>

<template>
  <div class="system-notify-panel">
    <header class="header">
      <div class="title-wrap">
        <n-icon :component="CalendarOutline" :size="22" class="title-icon" />
        <h2 class="title">{{ t('chat.calendarRemind') }}</h2>
      </div>
      <div class="actions">
        <button type="button" class="action-btn" :title="t('chat.markRead')" @click="markAllRead">
          <n-icon :component="CheckmarkDoneOutline" :size="18" />
        </button>
      </div>
    </header>

    <div class="content">
      <EmptyState
        v-if="calendarRemindNotifs.length === 0"
        :title="t('chat.noRemind')"
        :description="t('chat.remindEmptyDesc')"
      />
      <ul v-else class="notif-list">
        <li
          v-for="notif in calendarRemindNotifs"
          :key="notif.id"
          class="notif-row"
          :class="{ unread: notif.readStatus === 0 }"
          @click="onClickItem(notif)"
        >
          <div class="icon-wrap">
            <n-icon :component="CalendarOutline" :size="22" />
          </div>
          <div class="info">
            <div class="top">
              <span class="name">{{ t('chat.calendarRemind') }}</span>
              <span class="time">{{ formatTime(notif.createTime) }}</span>
            </div>
            <div class="preview">{{ formatRemindContent(notif.content) }}</div>
          </div>
          <button
            type="button"
            class="delete-btn"
            :title="t('common.delete')"
            @click="clearOne(notif, $event)"
          >
            <n-icon :component="TrashOutline" :size="16" />
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.system-notify-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-window, var(--lx-bg-panel));
}

.header {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid var(--lx-divider);
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-icon {
  color: var(--lx-accent);
}

.title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  cursor: pointer;
  color: var(--lx-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-accent);
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.notif-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.notif-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 20px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.notif-row:hover {
  background: var(--lx-bg-hover);
}

.notif-row.unread {
  background: rgba(18, 183, 245, 0.06);
}

.icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(18, 183, 245, 0.12);
  color: var(--lx-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.info {
  flex: 1;
  min-width: 0;
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.name {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.time {
  font-size: 12px;
  color: var(--lx-text-tertiary, var(--lx-text-secondary));
  flex-shrink: 0;
}

.preview {
  font-size: 13px;
  color: var(--lx-text-secondary);
  line-height: 1.45;
  word-break: break-word;
}

.delete-btn {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  color: var(--lx-text-tertiary, var(--lx-text-secondary));
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
}

.notif-row:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background: var(--lx-danger-bg-soft, rgba(240, 64, 64, 0.08));
  color: var(--lx-danger);
}
</style>
