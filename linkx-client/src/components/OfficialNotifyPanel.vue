<script setup lang="ts">
/**
 * 消息页「LinkX官方」主面板：以好友聊天气泡形式展示反馈进度与系统通知。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { HeadsetOutline, CheckmarkDoneOutline, TrashOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useNotificationsStore } from '../stores/notifications'
import { useAppStore } from '../stores/app'
import EmptyState from './common/EmptyState.vue'
import Avatar from './Avatar.vue'
import type { MessageNotification } from '../stores/notifications'
import { resolveNoteMediaUrl } from '../api/note'
import { normalizeMediaUrl } from '../utils/mediaUrl'
import { useI18n } from '../i18n'

const message = useMessage()
const { t } = useI18n()
const notificationsStore = useNotificationsStore()
const appStore = useAppStore()

const { officialNotifs } = storeToRefs(notificationsStore)
const { userProfile } = storeToRefs(appStore)
const {
  fetchMessageNotifications,
  markMessageAsRead,
  markOfficialNotifsAsRead,
  deleteMessageNotification
} = notificationsStore

onMounted(() => {
  void fetchMessageNotifications()
})

interface BodyPart {
  kind: 'text' | 'image'
  text?: string
  key?: string
}

interface OfficialChatMessage {
  id: string
  notifId: string
  time: string
  isSelf: boolean
  text: string
  images: BodyPart[]
  unread: boolean
  isSystemTip?: boolean
}

const EVIDENCE_KEY_RE = /^\d+\.\s*([\w./-]+\.(?:png|jpe?g|gif|webp|bmp))$/i
const resolvedEvidenceUrls = ref<Record<string, string>>({})

const officialAvatarProps = computed(() => ({
  text: t('chat.officialAvatar'),
  color: '#2f6fed',
  size: 36
}))

const selfAvatarProps = computed(() => ({
  text: t('chat.me'),
  color: 'var(--lx-success)',
  size: 36,
  imageUrl: userProfile.value.avatar || undefined
}))

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

function statusTitle(type?: string): string {
  switch (type) {
    case 'feedback_submitted':
      return t('chat.officialStepSubmitted')
    case 'feedback_replied':
      return t('chat.officialStepReplied')
    case 'feedback_closed':
      return t('chat.officialStepClosed')
    case 'feedback_reopened':
      return t('chat.officialStepReopened')
    case 'review_approved':
      return t('chat.officialStepApproved')
    case 'review_rejected':
      return t('chat.officialStepRejected')
    case 'notice_published':
      return t('chat.officialStepNotice')
    default:
      return t('chat.officialStepProgress')
  }
}

function extractValue(line: string, prefixes: string[]): string | null {
  for (const prefix of prefixes) {
    if (line.startsWith(prefix)) return line.slice(prefix.length).trim()
  }
  return null
}

/** 把通知正文拆成文本行 + 证据图 key */
function parseBodyParts(content?: string): BodyPart[] {
  if (!content) return []
  const parts: BodyPart[] = []
  for (const raw of content.split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || /^【.+】$/.test(line)) continue
    if (/^证据图片:\s*$/.test(line)) continue
    const m = line.match(EVIDENCE_KEY_RE)
    if (m) {
      parts.push({ kind: 'image', key: m[1] })
      continue
    }
    if (/^证据图片:\s*无$/.test(line)) continue
    parts.push({ kind: 'text', text: line })
  }
  return parts
}

function makeChatMessage(
  notif: MessageNotification,
  suffix: string,
  isSelf: boolean,
  text: string,
  images: BodyPart[] = []
): OfficialChatMessage {
  return {
    id: `${notif.id}:${suffix}`,
    notifId: notif.id,
    time: notif.createTime,
    isSelf,
    text,
    images,
    unread: notif.readStatus === 0
  }
}

function expandNotification(notif: MessageNotification): OfficialChatMessage[] {
  const parts = parseBodyParts(notif.content)
  const textLines = parts.filter(p => p.kind === 'text').map(p => p.text!)
  const imageParts = parts.filter(p => p.kind === 'image')

  if (notif.type === 'feedback_submitted') {
    const messages: OfficialChatMessage[] = []
    let userText = ''
    let officialText = ''
    for (const line of textLines) {
      const feedback = extractValue(line, ['你的反馈：', '你的举报：'])
      if (feedback) userText = feedback
      const detail = extractValue(line, ['详情：'])
      if (detail) officialText = detail
    }
    if (userText) {
      messages.push(makeChatMessage(notif, 'user', true, userText, imageParts))
    }
    if (officialText) {
      messages.push(makeChatMessage(notif, 'ack', false, officialText))
    }
    if (!messages.length) {
      messages.push(makeChatMessage(notif, 'fallback', false, notif.content || statusTitle(notif.type)))
    }
    return messages
  }

  if (
    notif.type === 'feedback_replied' ||
    notif.type === 'feedback_closed' ||
    notif.type === 'feedback_reopened'
  ) {
    for (const line of textLines) {
      const detail = extractValue(line, ['详情：'])
      if (detail) return [makeChatMessage(notif, 'reply', false, detail)]
    }
    return [makeChatMessage(notif, 'status', false, statusTitle(notif.type))]
  }

  const displayLines: string[] = []
  for (const line of textLines) {
    if (line.startsWith('类型：')) continue
    if (line.startsWith('你的反馈：') || line.startsWith('你的举报：')) continue
    const detail = extractValue(line, ['详情：'])
    if (detail) {
      displayLines.push(detail)
      continue
    }
    displayLines.push(line)
  }
  const text = displayLines.join('\n').trim() || statusTitle(notif.type)
  return [makeChatMessage(notif, 'official', false, text, imageParts)]
}

const chatMessages = computed<OfficialChatMessage[]>(() => {
  const sorted = [...officialNotifs.value].sort(
    (a, b) => Date.parse(a.createTime) - Date.parse(b.createTime)
  )
  return sorted.flatMap(expandNotification)
})

watch(
  chatMessages,
  list => {
    const parts = list.flatMap(m => m.images)
    void resolveEvidenceKeys(parts)
  },
  { immediate: true, deep: true }
)

async function resolveEvidenceKeys(parts: BodyPart[]) {
  const keys = parts
    .filter(p => p.kind === 'image' && p.key && !resolvedEvidenceUrls.value[p.key])
    .map(p => p.key!)
  if (!keys.length) return
  await Promise.all(
    keys.map(async key => {
      try {
        const res = await resolveNoteMediaUrl(key)
        const url = normalizeMediaUrl(res.data) || res.data || ''
        if (res.code === 200 && url) {
          resolvedEvidenceUrls.value = { ...resolvedEvidenceUrls.value, [key]: url }
        }
      } catch {
        /* ignore single key */
      }
    })
  )
}

async function onClickMessage(msg: OfficialChatMessage) {
  if (msg.unread) {
    void markMessageAsRead(msg.notifId)
  }
}

async function markAllRead() {
  await markOfficialNotifsAsRead()
  message.success(t('chat.markedAllRead'))
}

async function clearOne(msg: OfficialChatMessage, e: Event) {
  e.stopPropagation()
  await deleteMessageNotification(msg.notifId)
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
        v-if="chatMessages.length === 0"
        :title="t('chat.noOfficial')"
        :description="t('chat.officialEmptyDesc')"
      />
      <div v-else class="chat-scroll">
        <div
          v-for="msg in chatMessages"
          :key="msg.id"
          class="message-row"
          :class="[msg.isSelf ? 'right' : 'left', { unread: msg.unread }]"
          @click="onClickMessage(msg)"
        >
          <Avatar v-if="!msg.isSelf" v-bind="officialAvatarProps" />

          <div class="bubble-wrapper">
            <div class="lx-bubble" :class="{ self: msg.isSelf }">
              <p class="lx-bubble-text">{{ msg.text }}</p>
              <div v-if="msg.images.length" class="bubble-images">
                <a
                  v-for="(img, idx) in msg.images"
                  :key="idx"
                  class="evidence-thumb"
                  :href="img.key ? resolvedEvidenceUrls[img.key] : undefined"
                  target="_blank"
                  rel="noopener noreferrer"
                  @click.stop
                >
                  <img
                    v-if="img.key && resolvedEvidenceUrls[img.key]"
                    :src="resolvedEvidenceUrls[img.key]"
                    alt=""
                  />
                </a>
              </div>
            </div>
            <span class="msg-time">{{ formatTime(msg.time) }}</span>
          </div>

          <Avatar v-if="msg.isSelf" v-bind="selfAvatarProps" />

          <button
            type="button"
            class="delete-btn"
            :title="t('common.delete')"
            @click="clearOne(msg, $event)"
          >
            <n-icon :component="TrashOutline" :size="15" />
          </button>
        </div>
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
  padding: 16px 20px 24px;
}

.chat-scroll {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  position: relative;
  padding-right: 28px;
}

.message-row.left {
  justify-content: flex-start;
}

.message-row.right {
  justify-content: flex-end;
}

.message-row.unread .lx-bubble:not(.self) {
  box-shadow: 0 0 0 1px rgba(47, 111, 237, 0.35);
}

.bubble-wrapper {
  max-width: min(420px, 72%);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-row.left .bubble-wrapper {
  align-items: flex-start;
}

.message-row.right .bubble-wrapper {
  align-items: flex-end;
}

.msg-time {
  font-size: 11px;
  color: var(--lx-text-tertiary, #999);
  padding: 0 4px;
}

.bubble-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.evidence-thumb {
  display: inline-block;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.25);
  line-height: 0;
}

.message-row.left .evidence-thumb {
  border-color: var(--lx-divider);
}

.evidence-thumb img {
  width: 120px;
  height: 120px;
  object-fit: cover;
  display: block;
}

.delete-btn {
  position: absolute;
  top: 4px;
  right: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--lx-text-tertiary, #999);
  cursor: pointer;
  opacity: 0;
}

.message-row:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background: var(--lx-bg-hover, rgba(0, 0, 0, 0.06));
  color: var(--lx-danger, #e34d59);
}
</style>

<style>
/* 与 ChatMessageItem 保持一致的气泡样式（官方会话独立渲染，需自带） */
.official-notify-panel .lx-bubble {
  position: relative;
  background: #ffffff;
  padding: 10px 12px;
  border-radius: var(--lx-radius);
  font-size: 14px;
  line-height: 1.55;
  color: var(--lx-text);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.official-notify-panel .lx-bubble.self {
  background: #4facfe;
  color: #ffffff;
  box-shadow: 0 1px 2px rgba(79, 172, 254, 0.3);
  border: none;
}

.official-notify-panel .lx-bubble.self .lx-bubble-text {
  color: #ffffff;
}

.official-notify-panel .lx-bubble-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--lx-text);
}
</style>
