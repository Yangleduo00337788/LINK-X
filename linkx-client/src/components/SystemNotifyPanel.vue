<script setup lang="ts">
/**
 * 消息页「日程提醒」主面板：与 LinkX 官方统一的卡片式通知流。
 */
import { computed, onMounted } from 'vue'
import { NIcon, NDropdown, useMessage, useDialog, type DropdownOption } from 'naive-ui'
import {
  CalendarOutline,
  CheckmarkDoneOutline,
  ChevronForwardOutline,
  EllipsisHorizontalOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../stores/notifications'
import { useCalendarStore } from '../stores/calendar'
import type { CalendarEvent } from '../stores/calendar'
import { useAppStore } from '../stores/app'
import EmptyState from './common/EmptyState.vue'
import type { MessageNotification } from '../stores/notifications'
import { useI18n } from '../i18n'
import {
  buildCalendarRemindViewModel,
  formatOfficialDividerTime,
  type CalendarRemindViewModel
} from '../utils/calendarRemindContent'
import '../styles/notifyFeed.css'

const message = useMessage()
const dialog = useDialog()
const { t } = useI18n()
const notificationsStore = useNotificationsStore()
const calendarStore = useCalendarStore()
const appStore = useAppStore()

const { calendarRemindNotifs } = storeToRefs(notificationsStore)
const { remindedUpcomingEvents } = storeToRefs(calendarStore)
const {
  fetchMessageNotifications,
  markMessageAsRead,
  markCalendarRemindsAsRead,
  deleteMessageNotification
} = notificationsStore

onMounted(() => {
  void fetchMessageNotifications()
  void calendarStore.ensureReminderWatch()
})

function resolveEventTitle(relatedId?: string): string | undefined {
  if (!relatedId) return undefined
  return calendarStore.events.find(e => e.id === relatedId)?.title
}

const feedItems = computed<CalendarRemindViewModel[]>(() =>
  calendarRemindNotifs.value.map(notif =>
    buildCalendarRemindViewModel(notif, t, resolveEventTitle(notif.relatedId))
  )
)

const showUpcoming = computed(
  () => calendarRemindNotifs.value.length === 0 && remindedUpcomingEvents.value.length > 0
)

function formatUpcomingBody(ev: CalendarEvent): string {
  const timePart = `${ev.date} ${ev.time || ''}`.trim()
  return t('chat.remindAtWithTitle', { time: timePart, title: ev.title })
}

function findNotifById(notifId: string): MessageNotification | undefined {
  return calendarRemindNotifs.value.find(n => n.id === notifId)
}

function cardMoreOptions(item: CalendarRemindViewModel): DropdownOption[] {
  const options: DropdownOption[] = []
  if (item.relatedId) {
    options.push({ label: t('chat.remindCancelSchedule'), key: 'cancelReminder' })
  }
  options.push({ label: t('chat.remindClose'), key: 'close' })
  return options
}

function onCardMoreSelect(notifId: string, key: string) {
  if (key === 'cancelReminder') {
    confirmCancelReminder(notifId)
    return
  }
  if (key === 'close') {
    void closeNotif(notifId)
  }
}

async function openCalendarFromNotif(notifId: string) {
  const notif = findNotifById(notifId)
  if (!notif) return
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
    const m = notif.content?.match(/(\d{4}-\d{2}-\d{2})/)
    if (m) dateKey = m[1]
  }
  if (dateKey) {
    const [y, mo, d] = dateKey.split('-').map(Number)
    await calendarStore.setSelectedDate(new Date(y, mo - 1, d).getTime())
  }
  appStore.setNav('calendar')
}

function openUpcomingEvent(ev: CalendarEvent) {
  const [y, mo, d] = ev.date.split('-').map(Number)
  void calendarStore.setSelectedDate(new Date(y, mo - 1, d).getTime())
  appStore.setNav('calendar')
}

async function markAllRead() {
  await markCalendarRemindsAsRead()
  message.success(t('chat.markedAllRead'))
}

/** 知道了：标已读并删除该条消息 */
async function acknowledgeNotif(notifId: string) {
  await closeNotif(notifId, { silent: true })
}

async function closeNotif(notifId: string, options?: { silent?: boolean }) {
  const notif = findNotifById(notifId)
  if (!notif) return
  if (notif.readStatus === 0) {
    await markMessageAsRead(notif.id)
  }
  await deleteMessageNotification(notif.id)
  if (!options?.silent) {
    message.success(t('chat.remindClosed'))
  }
}

function confirmCancelReminder(notifId: string) {
  dialog.warning({
    title: t('chat.remindCancelSchedule'),
    content: t('chat.remindCancelConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await cancelEventReminder(notifId)
    }
  })
}

async function cancelEventReminder(notifId: string) {
  const notif = findNotifById(notifId)
  if (!notif) return
  if (!notif.relatedId) {
    await closeNotif(notifId)
    return
  }
  await calendarStore.cancelReminderForEvent(notif.relatedId)
  await closeNotif(notifId, { silent: true })
  message.success(t('chat.remindCancelled'))
}

async function snoozeNotifyOnly(notifId: string, minutes = 10) {
  const notif = findNotifById(notifId)
  if (!notif) return
  if (!notif.relatedId) {
    await acknowledgeNotif(notifId)
    return
  }
  const ok = calendarStore.snoozeReminderNotification(notif.relatedId, minutes)
  await closeNotif(notifId, { silent: true })
  if (ok) {
    message.success(t('chat.remindSnoozeNotifyDone', { n: minutes }))
  } else {
    message.warning(t('chat.remindSnoozeNotifyFailed'))
  }
}

async function postponeEventReminder(notifId: string, minutes = 10) {
  const notif = findNotifById(notifId)
  if (!notif) return
  if (!notif.relatedId) {
    await acknowledgeNotif(notifId)
    return
  }
  const ok = await calendarStore.postponeEventByMinutes(notif.relatedId, minutes)
  await closeNotif(notifId, { silent: true })
  if (ok) {
    message.success(t('chat.remindSnoozed', { n: minutes }))
  } else {
    message.warning(t('chat.remindSnoozeFailed'))
  }
}

async function onCardMainClick(notifId: string) {
  const notif = findNotifById(notifId)
  if (!notif || notif.readStatus !== 0) return
  await markMessageAsRead(notif.id)
}
</script>

<template>
  <div class="notify-feed-panel">
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
        v-if="feedItems.length === 0 && !showUpcoming"
        :title="t('chat.noRemind')"
        :description="t('chat.remindEmptyDesc')"
      />

      <div v-else class="notify-feed-scroll">
        <template v-if="showUpcoming">
          <div class="notify-feed-block">
            <div class="notify-feed-divider-time">{{ t('chat.upcomingRemindTitle') }}</div>
            <div class="notify-feed-card">
              <div class="notify-feed-card-main">
                <h3 class="notify-feed-card-title">{{ t('chat.upcomingRemindTitle') }}</h3>
                <p class="notify-feed-card-body">{{ t('chat.upcomingRemindDesc') }}</p>
              </div>
            </div>
          </div>
          <div v-for="ev in remindedUpcomingEvents" :key="`upcoming-${ev.id}`" class="notify-feed-block">
            <div class="notify-feed-divider-time">{{ ev.date }} {{ ev.time || '' }}</div>
            <div class="notify-feed-card">
              <div class="notify-feed-card-main">
                <h3 class="notify-feed-card-title">{{ ev.title }}</h3>
                <p v-if="ev.date" class="notify-feed-card-date">{{ ev.date }}</p>
                <p class="notify-feed-card-body">{{ formatUpcomingBody(ev) }}</p>
              </div>
              <button type="button" class="notify-feed-detail-row" @click="openUpcomingEvent(ev)">
                <span>{{ t('chat.remindViewCalendar') }}</span>
                <n-icon :component="ChevronForwardOutline" :size="16" />
              </button>
            </div>
          </div>
        </template>

        <div v-for="item in feedItems" :key="item.id" class="notify-feed-block">
          <div class="notify-feed-divider-time">{{ formatOfficialDividerTime(item.time) }}</div>
          <div class="notify-feed-card" :class="{ unread: item.unread }">
            <div class="notify-feed-card-main" @click="onCardMainClick(item.notifId)">
              <h3 class="notify-feed-card-title">{{ item.title }}</h3>
              <p v-if="item.dateLabel" class="notify-feed-card-date">{{ item.dateLabel }}</p>
              <p v-if="item.body" class="notify-feed-card-body">{{ item.body }}</p>
            </div>
            <button
              type="button"
              class="notify-feed-detail-row"
              @click.stop="acknowledgeNotif(item.notifId)"
            >
              <span>{{ t('chat.remindDismiss') }}</span>
            </button>
            <button
              v-if="item.relatedId"
              type="button"
              class="notify-feed-detail-row"
              @click.stop="snoozeNotifyOnly(item.notifId, 10)"
            >
              <span>{{ t('chat.remindSnoozeNotify') }}</span>
            </button>
            <button
              v-if="item.relatedId"
              type="button"
              class="notify-feed-detail-row"
              @click.stop="postponeEventReminder(item.notifId, 10)"
            >
              <span>{{ t('chat.remindPostpone') }}</span>
            </button>
            <button
              v-if="item.relatedId"
              type="button"
              class="notify-feed-detail-row"
              @click.stop="openCalendarFromNotif(item.notifId)"
            >
              <span>{{ t('chat.remindViewCalendar') }}</span>
              <n-icon :component="ChevronForwardOutline" :size="16" />
            </button>
            <n-dropdown
              trigger="click"
              :options="cardMoreOptions(item)"
              @select="key => onCardMoreSelect(item.notifId, key as string)"
            >
              <button type="button" class="card-more-btn" :title="t('chat.officialMore')">
                <n-icon :component="EllipsisHorizontalOutline" :size="16" />
              </button>
            </n-dropdown>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.title-icon {
  color: var(--lx-accent);
}

.card-more-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.9);
  color: #b2b2b2;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.notify-feed-card:hover .card-more-btn {
  opacity: 1;
}

.card-more-btn:hover {
  color: var(--lx-text-primary);
}

[data-theme='dark'] .card-more-btn {
  background: rgba(42, 42, 42, 0.95);
}
</style>
