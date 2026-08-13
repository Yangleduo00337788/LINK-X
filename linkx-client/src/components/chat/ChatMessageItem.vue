<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 单条聊天消息行组件。
 * <p>
 * 根据消息类型渲染对应气泡子组件，并处理头像展示与事件向上传递。
 * </p>
 */
import { computed, ref, onMounted } from 'vue'
import { NIcon } from 'naive-ui'
import { FolderOutline, PhonePortraitOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import type { ChatMessage } from '../../types'
import { useAppStore } from '../../stores/app'
import { storeToRefs } from 'pinia'
import { useI18n } from '../../i18n'
import { isMyPhoneSessionName } from '../../utils/myPhoneSession'
import * as chatApi from '../../api/chat'

import FileBubble from './bubbles/FileBubble.vue'
import ImageBubble from './bubbles/ImageBubble.vue'
import VoiceBubble from './bubbles/VoiceBubble.vue'
import RedPacketBubble from './bubbles/RedPacketBubble.vue'
import LocationBubble from './bubbles/LocationBubble.vue'
import TextBubble from './bubbles/TextBubble.vue'
import DataCardBubble from './bubbles/DataCardBubble.vue'
import CallBubble from './bubbles/CallBubble.vue'
import { groupReadCountLabel, privateStatusLabel } from '../../utils/messageStatus'

const props = defineProps<{
  msg: ChatMessage
  /** 跳转到 @我 时短暂高亮 */
  highlight?: boolean
}>()

const emit = defineEmits<{
  (e: 'contextmenu', event: MouseEvent, msg: ChatMessage): void
  (e: 'openFileView', msg: ChatMessage): void
  (e: 'openChatFile', msg: ChatMessage): void
  (e: 'openImageView', msg: ChatMessage): void
  (e: 'clickRedPacket', msg: ChatMessage): void
  (e: 'clickConference', msg: ChatMessage): void
  (e: 'openPeerProfile', event: MouseEvent): void
  (e: 'openSelfProfile', event: MouseEvent): void
  (e: 'retry', msg: ChatMessage): void
  (e: 'messageContentLoaded', msg: ChatMessage): void
}>()

const fileHover = ref(false)

const { t } = useI18n()
const appStore = useAppStore()
const { currentSession, userProfile } = storeToRefs(appStore)

const isMyPhone = computed(() => isMyPhoneSessionName(currentSession.value?.name))
const hasSession = computed(() => !!currentSession.value)
const isFriendChat = computed(() => hasSession.value && !currentSession.value?.isGroup && !isMyPhone.value)
const isGroupChat = computed(() => !!currentSession.value?.isGroup)

const isRecall = computed(() => props.msg.type === 'recall')
const isSystemTip = computed(
  () => props.msg.type === 'system' || props.msg.type === 'time'
)

/** 撤回提示：你撤回了一条消息 / XXX撤回了一条消息 */
const recallTip = computed(() => {
  if (props.msg.isSelf) return t('chat.youRecalled')
  const name = props.msg.senderName || currentSession.value?.name || t('chat.messageFallback')
  return t('chat.peerRecalled', { name })
})

const tipText = computed(() => {
  if (isRecall.value) return recallTip.value
  return props.msg.content || ''
})

/** 单聊：全部用文字展示发送状态 */
const statusLabel = computed(() => {
  if (!props.msg.isSelf || isGroupChat.value) return ''
  return privateStatusLabel(props.msg, t)
})

const sensitiveAlertText = computed(() => {
  if (!props.msg.isSelf || !props.msg.sensitiveAlert) return ''
  return t('chat.sensitiveAlertTip')
})

/** 群聊仅在最新一条己方消息展示已读人数 */
const isLatestSelfMessage = computed(() => {
  if (!props.msg.isSelf || !isGroupChat.value) return false
  const sessionId = props.msg.sessionId || currentSession.value?.id
  if (!sessionId) return true
  const list = appStore.messagesBySession[sessionId]
  if (!list?.length) return true
  for (let i = list.length - 1; i >= 0; i--) {
    const m = list[i]
    if (!m.isSelf) continue
    if (m.type === 'time' || m.type === 'system' || m.type === 'recall') continue
    return m.id === props.msg.id
  }
  return false
})

const readCountText = computed(() => {
  if (!props.msg.isSelf || !isGroupChat.value || !isLatestSelfMessage.value) return ''
  return groupReadCountLabel(props.msg, t)
})

const fetchingRead = ref(false)

async function maybeFetchReadCount() {
  if (!isLatestSelfMessage.value) return
  if (props.msg.sendStatus === 'sending' || props.msg.sendStatus === 'failed') return
  const sessionId = props.msg.sessionId || currentSession.value?.id
  if (!sessionId || !props.msg.id || props.msg.id.includes('-')) return
  if (fetchingRead.value) return
  fetchingRead.value = true
  try {
    const res = await chatApi.getMessageReadCount(sessionId, props.msg.id)
    if (res.code === 200 && res.data) {
      appStore.setMessageReadCount(
        sessionId,
        props.msg.id,
        Number(res.data.readCount) || 0,
        Number(res.data.totalMembers) || 0
      )
    }
  } catch {
    // ignore — 已读统计非关键功能
  } finally {
    fetchingRead.value = false
  }
}

onMounted(() => {
  maybeFetchReadCount()
})

/**
 * 对方头像 props（computed，避免滚动时每帧重复算 + 误触发群成员请求）。
 * 群聊直接用消息自带的 senderAvatar，不再在渲染期查 groupMeta。
 */
const peerAvatarProps = computed(() => {
  const s = currentSession.value
  const size = 36
  if (s?.isGroup) {
    const name = props.msg.senderName || s.avatarText || '?'
    return {
      text: name.charAt(0),
      color: s.avatarColor || 'var(--lx-accent)',
      size,
      imageUrl: props.msg.senderAvatar || undefined,
      icon: undefined as undefined
    }
  }
  return {
    text: s?.avatarText || '?',
    color: s?.avatarColor || 'var(--lx-accent)',
    size,
    imageUrl: s?.avatarUrl,
    icon: isMyPhone.value ? PhonePortraitOutline : undefined
  }
})

const selfAvatarProps = computed(() => ({
  text: t('chat.me'),
  color: 'var(--lx-success)',
  size: 36,
  imageUrl: userProfile.value.avatar || undefined
}))

function onStatusClick() {
  if (!props.msg.isSelf || props.msg.sendStatus !== 'failed') return
  // 业务拦截（如敏感词）不提供重试
  if (props.msg.sendFailReason) return
  emit('retry', props.msg)
}
</script>

<template>
  <div v-if="isSystemTip || isRecall" class="recall-tip-row">
    <span class="recall-tip">{{ tipText }}</span>
  </div>
  <div
    v-else
    class="message-row"
    :class="[msg.isSelf ? 'right' : 'left', { 'is-at-me-flash': highlight }]"
  >
    <button v-if="!msg.isSelf && isFriendChat" type="button" class="avatar-btn" @click="emit('openPeerProfile', $event)">
      <Avatar v-bind="peerAvatarProps" />
    </button>
    <Avatar v-else-if="!msg.isSelf" v-bind="peerAvatarProps" />

    <div
      class="bubble-wrapper"
      @contextmenu="emit('contextmenu', $event, msg)"
      @mouseenter="maybeFetchReadCount"
    >
      <div
        v-if="msg.type === 'file'"
        class="file-msg-wrap"
        @mouseenter="fileHover = true"
        @mouseleave="fileHover = false"
      >
        <button
          v-show="fileHover"
          type="button"
          class="file-side-open"
          :title="t('chat.openFile')"
          @click.stop="emit('openChatFile', msg)"
        >
          <n-icon :component="FolderOutline" :size="18" />
        </button>
        <FileBubble :msg="msg" @click="emit('openChatFile', msg)" />
      </div>
      <ImageBubble
        v-else-if="msg.type === 'image' || msg.isImage"
        :msg="msg"
        @preview="emit('openImageView', msg)"
        @content-loaded="emit('messageContentLoaded', msg)"
      />
      <VoiceBubble v-else-if="msg.type === 'voice'" :msg="msg" />
      <RedPacketBubble v-else-if="msg.type === 'redPacket'" :msg="msg" @click="emit('clickRedPacket', msg)" />
      <LocationBubble v-else-if="msg.type === 'location'" :msg="msg" />
      <CallBubble v-else-if="msg.type === 'conference'" :msg="msg" @click="emit('clickConference', msg)" />
      <DataCardBubble v-else-if="msg.type === 'dataCard'" :msg="msg" />
      <TextBubble v-else :msg="msg" />
      <div
        v-if="msg.isSelf && (statusLabel || readCountText || msg.edited || sensitiveAlertText)"
        class="msg-meta"
      >
        <button
          v-if="statusLabel"
          type="button"
          class="msg-status"
          :class="{
            failed: msg.sendStatus === 'failed',
            read: msg.sendStatus === 'read',
            delivered: msg.sendStatus === 'delivered',
            'no-retry': !!msg.sendFailReason
          }"
          @click="onStatusClick"
        >
          {{ statusLabel }}
        </button>
        <span v-if="msg.edited" class="msg-edited">{{ t('chat.editedLabel') }}</span>
        <span v-if="readCountText" class="msg-read">{{ readCountText }}</span>
        <span v-if="sensitiveAlertText" class="msg-sensitive-alert">{{ sensitiveAlertText }}</span>
      </div>
    </div>

    <button v-if="msg.isSelf" type="button" class="avatar-btn" @click="emit('openSelfProfile', $event)">
      <Avatar v-bind="selfAvatarProps" />
    </button>
  </div>
</template>

<style scoped>
.recall-tip-row {
  display: flex;
  justify-content: center;
  padding: var(--lx-space-xs) var(--lx-space-lg);
}
.recall-tip {
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-muted);
  user-select: none;
}
.message-row {
  display: flex;
  gap: var(--lx-space);
  align-items: flex-start;
}
.message-row.left { justify-content: flex-start; }
.message-row.right { justify-content: flex-end; }
.avatar-btn {
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  cursor: pointer;
  border-radius: var(--lx-avatar-radius);
  flex-shrink: 0;
  line-height: 0;
}
.avatar-btn:hover { opacity: 0.88; }
.avatar-btn:focus-visible { outline: 2px solid var(--lx-accent); outline-offset: 2px; }
.bubble-wrapper {
  max-width: min(420px, 72%);
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.message-row.left .bubble-wrapper {
  align-items: flex-start;
}
.file-msg-wrap {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--lx-space-sm);
}
.file-side-open {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--lx-radius-sm);
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.18);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  padding: 0;
}
.file-side-open:hover {
  background: var(--lx-accent, var(--lx-accent));
  color: var(--lx-text-on-accent);
}
.message-row.right .file-msg-wrap {
  flex-direction: row;
}
.message-row.left .file-msg-wrap {
  flex-direction: row;
}
.msg-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--lx-space-sm);
  margin-top: var(--lx-space-2xs);
  font-size: var(--lx-font-xs);
  line-height: var(--lx-leading-snug);
  color: var(--lx-text-muted);
}
.msg-status {
  border: none;
  padding: 0;
  background: transparent;
  color: inherit;
  font-size: inherit;
  cursor: default;
}
.msg-status.failed {
  color: var(--lx-danger, var(--lx-danger));
  cursor: pointer;
}
.msg-status.failed.no-retry {
  cursor: default;
}
.msg-status.read {
  color: var(--lx-accent, var(--lx-accent));
}
.msg-status.delivered {
  color: var(--lx-text-muted);
}
.msg-sensitive-alert {
  color: var(--lx-warning);
  max-width: 240px;
}
.msg-edited,
.msg-read {
  opacity: 0.9;
}
.message-row.is-at-me-flash .bubble-wrapper {
  animation: at-me-flash 1.6s ease;
}
.message-row.is-at-me-flash {
  animation: at-me-row-flash 1.6s ease;
}
@keyframes at-me-flash {
  0%,
  100% {
    box-shadow: none;
  }
  20%,
  55% {
    box-shadow: 0 0 0 3px rgba(18, 183, 245, 0.55);
    border-radius: var(--lx-bubble-radius);
  }
}
@keyframes at-me-row-flash {
  0%,
  100% {
    background: transparent;
  }
  20%,
  55% {
    background: rgba(18, 183, 245, 0.12);
    border-radius: var(--lx-radius-md);
  }
}
</style>
<style>
/* Global styles for bubbles to avoid duplicating them */
.lx-bubble {
  position: relative;
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  width: fit-content;
  max-width: 100%;
  background: var(--lx-bg-card);
  padding: 10px 14px;
  border-radius: var(--lx-bubble-radius);
  font-size: var(--lx-font);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
  border: 1px solid var(--lx-border-light);
}
.lx-bubble.self {
  background: var(--lx-accent);
  color: var(--lx-text-on-accent);
  box-shadow: 0 1px 3px color-mix(in srgb, var(--lx-accent) 32%, transparent);
  border: none;
}
.lx-bubble.self .lx-bubble-text {
  color: var(--lx-text-on-accent);
}
.lx-bubble.link .lx-bubble-text { margin: 0; word-break: break-all; }
.lx-link-ico { display: none; }
.lx-bubble-text { margin: 0; white-space: pre-wrap; word-break: break-word; color: var(--lx-text); }
.lx-mention {
  color: var(--lx-accent);
  font-weight: 500;
}
.lx-mention--me {
  color: var(--lx-accent);
  font-weight: 700;
}
.lx-bubble.self .lx-mention,
.lx-bubble.self .lx-mention--me {
  color: var(--lx-text-on-accent);
  text-decoration: underline;
  text-underline-offset: 2px;
  font-weight: 700;
}
.lx-file-card {
  max-width: 300px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-bubble-radius);
  overflow: hidden;
  box-shadow: 0 1px 3px var(--lx-bg-active);
  cursor: pointer;
}
.lx-file-main { display: flex; align-items: center; gap: var(--lx-space-lg); padding: var(--lx-space-lg) var(--lx-space-xl); }
.lx-file-icon { width: 44px; height: 44px; border-radius: var(--lx-radius); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.lx-file-icon.apk { background: var(--lx-file-apk-gradient); }
.lx-file-name { font-size: var(--lx-font); font-weight: 500; color: var(--lx-text-body); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lx-file-size { font-size: var(--lx-font-sm); color: var(--lx-text-muted); margin-top: var(--lx-space-xs); }
.lx-file-bar { padding: var(--lx-space-sm) var(--lx-space-xl); background: var(--lx-file-bar); color: rgba(255, 255, 255, 0.9); font-size: var(--lx-font-sm); }
.lx-bubble-image { max-width: 220px; max-height: 280px; border-radius: var(--lx-bubble-radius); object-fit: cover; cursor: zoom-in; display: block; }
.image-bubble { padding: 0; background: transparent; border: none; box-shadow: none; }
.voice-bubble { display: inline-flex; align-items: center; gap: var(--lx-space); min-width: 72px; cursor: pointer; }
.voice-bubble.playing { color: var(--lx-accent); }
.voice-bubble.self.playing { color: var(--lx-text-on-accent); opacity: 0.92; }
.voice-ico { flex-shrink: 0; }
.red-packet-card {
  display: flex; align-items: center; gap: var(--lx-space-md); min-width: 200px; max-width: 260px;
  padding: var(--lx-space-lg) var(--lx-space-xl); border-radius: var(--lx-radius);
  background: linear-gradient(135deg, var(--lx-danger), var(--lx-danger-deep)); color: var(--lx-text-on-accent);
  cursor: pointer; box-shadow: 0 2px 8px rgba(232, 76, 61, 0.35);
}
.red-packet-card.opened { opacity: 0.85; }
.rp-icon { width: 36px; height: 36px; border-radius: 50%; background: rgba(255, 255, 255, 0.2); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: var(--lx-font-xl); flex-shrink: 0; }
.rp-title { font-size: var(--lx-font); font-weight: 600; line-height: var(--lx-leading-snug); }
.rp-sub { font-size: var(--lx-font-sm); opacity: 0.85; margin-top: var(--lx-space-2xs); }
</style>
