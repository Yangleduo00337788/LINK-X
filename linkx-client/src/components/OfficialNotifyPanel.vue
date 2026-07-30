<script setup lang="ts">
/**
 * 消息页「LinkX官方」主面板：按反馈单分组展示详细进度时间线，并实时跟随推送刷新。
 */
import { computed, onMounted, watch } from 'vue'
import { NIcon, NTag, useMessage } from 'naive-ui'
import {
  HeadsetOutline,
  CheckmarkDoneOutline,
  TrashOutline,
  CreateOutline,
  ChatbubbleEllipsesOutline,
  CloseCircleOutline,
  RefreshOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../stores/notifications'
import EmptyState from './common/EmptyState.vue'
import type { MessageNotification } from '../stores/notifications'
import { useI18n } from '../i18n'

const message = useMessage()
const { t } = useI18n()
const notificationsStore = useNotificationsStore()

const { officialNotifs } = storeToRefs(notificationsStore)
const {
  fetchMessageNotifications,
  markMessageAsRead,
  markOfficialNotifsAsRead,
  deleteMessageNotification
} = notificationsStore

onMounted(() => {
  void fetchMessageNotifications()
})

watch(
  () => officialNotifs.value.length,
  () => {
    // 推送刷新后列表变化时保持最新
  }
)

interface ProgressStep {
  notif: MessageNotification
  title: string
  bodyLines: string[]
}

interface FeedbackTicket {
  id: string
  latestTime: string
  unread: boolean
  steps: ProgressStep[]
}

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

function statusMeta(type?: string): { title: string; tag: 'info' | 'success' | 'warning' | 'error' | 'default' } {
  switch (type) {
    case 'feedback_submitted':
      return { title: t('chat.officialStepSubmitted'), tag: 'info' }
    case 'feedback_replied':
      return { title: t('chat.officialStepReplied'), tag: 'success' }
    case 'feedback_closed':
      return { title: t('chat.officialStepClosed'), tag: 'default' }
    case 'feedback_reopened':
      return { title: t('chat.officialStepReopened'), tag: 'warning' }
    default:
      return { title: t('chat.officialStepProgress'), tag: 'info' }
  }
}

function stepIcon(type?: string) {
  switch (type) {
    case 'feedback_submitted':
      return CreateOutline
    case 'feedback_replied':
      return ChatbubbleEllipsesOutline
    case 'feedback_closed':
      return CloseCircleOutline
    case 'feedback_reopened':
      return RefreshOutline
    default:
      return HeadsetOutline
  }
}

/** 把通知正文拆成多行详细信息 */
function parseBodyLines(content?: string): string[] {
  if (!content) return []
  return content
    .split(/\r?\n/)
    .map(s => s.trim())
    .filter(Boolean)
    .filter(s => !/^【.+】$/.test(s))
}

const tickets = computed<FeedbackTicket[]>(() => {
  const map = new Map<string, FeedbackTicket>()
  for (const notif of officialNotifs.value) {
    const key = notif.relatedId || notif.id
    let ticket = map.get(key)
    if (!ticket) {
      ticket = {
        id: key,
        latestTime: notif.createTime,
        unread: false,
        steps: []
      }
      map.set(key, ticket)
    }
    const meta = statusMeta(notif.type)
    ticket.steps.push({
      notif,
      title: meta.title,
      bodyLines: parseBodyLines(notif.content)
    })
    if (notif.readStatus === 0) ticket.unread = true
    if (Date.parse(notif.createTime) >= Date.parse(ticket.latestTime)) {
      ticket.latestTime = notif.createTime
    }
  }
  // 每张工单内按时间正序（时间线从早到晚）
  for (const ticket of map.values()) {
    ticket.steps.sort(
      (a, b) => Date.parse(a.notif.createTime) - Date.parse(b.notif.createTime)
    )
  }
  // 工单按最新进度倒序
  return [...map.values()].sort(
    (a, b) => Date.parse(b.latestTime) - Date.parse(a.latestTime)
  )
})

async function onClickStep(notif: MessageNotification) {
  if (notif.readStatus === 0) {
    void markMessageAsRead(notif.id)
  }
}

async function markAllRead() {
  await markOfficialNotifsAsRead()
  message.success(t('chat.markedAllRead'))
}

async function clearOne(notif: MessageNotification, e: Event) {
  e.stopPropagation()
  await deleteMessageNotification(notif.id)
}

function latestStatus(ticket: FeedbackTicket) {
  const last = ticket.steps[ticket.steps.length - 1]
  return statusMeta(last?.notif.type)
}
</script>

<template>
  <div class="official-notify-panel">
    <header class="header">
      <div class="title-wrap">
        <n-icon :component="HeadsetOutline" :size="22" class="title-icon" />
        <div>
          <h2 class="title">{{ t('chat.officialSession') }}</h2>
          <p class="subtitle">{{ t('chat.officialSubtitle') }}</p>
        </div>
      </div>
      <div class="actions">
        <button type="button" class="action-btn" :title="t('chat.markRead')" @click="markAllRead">
          <n-icon :component="CheckmarkDoneOutline" :size="18" />
        </button>
      </div>
    </header>

    <div class="content">
      <EmptyState
        v-if="tickets.length === 0"
        :title="t('chat.noOfficial')"
        :description="t('chat.officialEmptyDesc')"
      />
      <div v-else class="ticket-list">
        <article
          v-for="ticket in tickets"
          :key="ticket.id"
          class="ticket"
          :class="{ unread: ticket.unread }"
        >
          <div class="ticket-head">
            <div class="ticket-title-row">
              <span class="ticket-label">{{ t('chat.officialTicket') }}</span>
              <NTag size="small" :type="latestStatus(ticket).tag" round>
                {{ latestStatus(ticket).title }}
              </NTag>
            </div>
            <span class="ticket-time">{{ formatTime(ticket.latestTime) }}</span>
          </div>

          <ol class="timeline">
            <li
              v-for="step in ticket.steps"
              :key="step.notif.id"
              class="timeline-item"
              :class="{ unread: step.notif.readStatus === 0 }"
              @click="onClickStep(step.notif)"
            >
              <div class="dot-col">
                <div class="dot">
                  <n-icon :component="stepIcon(step.notif.type)" :size="14" />
                </div>
              </div>
              <div class="step-body">
                <div class="step-top">
                  <span class="step-title">{{ step.title }}</span>
                  <span class="step-time">{{ formatTime(step.notif.createTime) }}</span>
                </div>
                <div v-if="step.bodyLines.length" class="step-detail">
                  <p v-for="(line, idx) in step.bodyLines" :key="idx" class="detail-line">
                    {{ line }}
                  </p>
                </div>
                <p v-else class="step-detail plain">{{ step.notif.content }}</p>
              </div>
              <button
                type="button"
                class="delete-btn"
                :title="t('common.delete')"
                @click="clearOne(step.notif, $event)"
              >
                <n-icon :component="TrashOutline" :size="15" />
              </button>
            </li>
          </ol>
        </article>
      </div>
    </div>
  </div>
</template>

<style scoped>
.official-notify-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-window, var(--lx-bg-panel));
}

.header {
  min-height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  border-bottom: 1px solid var(--lx-divider);
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-icon {
  color: #2f6fed;
}

.title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--lx-text-primary);
}

.subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--lx-text-tertiary, #999);
}

.actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
}

.action-btn:hover {
  background: var(--lx-bg-hover, rgba(0, 0, 0, 0.06));
  color: var(--lx-text-primary);
}

.content {
  flex: 1;
  overflow: auto;
  padding: 12px 16px 20px;
}

.ticket-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ticket {
  border: 1px solid var(--lx-divider);
  border-radius: 12px;
  background: var(--lx-bg-panel, #fff);
  overflow: hidden;
}

.ticket.unread {
  border-color: rgba(47, 111, 237, 0.35);
  box-shadow: 0 0 0 1px rgba(47, 111, 237, 0.08);
}

.ticket-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(47, 111, 237, 0.04);
  border-bottom: 1px solid var(--lx-divider);
}

.ticket-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ticket-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text-primary);
}

.ticket-time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--lx-text-tertiary, #999);
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 8px 0;
}

.timeline-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  position: relative;
}

.timeline-item:hover {
  background: var(--lx-bg-hover, rgba(0, 0, 0, 0.03));
}

.timeline-item.unread .step-title {
  color: #2f6fed;
}

.dot-col {
  width: 24px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  padding-top: 2px;
}

.dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(47, 111, 237, 0.12);
  color: #2f6fed;
}

.step-body {
  flex: 1;
  min-width: 0;
}

.step-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.step-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-primary);
}

.step-time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--lx-text-tertiary, #999);
}

.step-detail {
  font-size: 13px;
  line-height: 1.55;
  color: var(--lx-text-secondary);
  word-break: break-word;
}

.step-detail.plain {
  margin: 0;
}

.detail-line {
  margin: 0 0 2px;
}

.delete-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--lx-text-tertiary, #999);
  cursor: pointer;
  opacity: 0;
}

.timeline-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: var(--lx-danger, #e34d59);
}
</style>
