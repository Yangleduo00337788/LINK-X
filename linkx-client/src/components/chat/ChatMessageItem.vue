<script setup lang="ts">
/**
 * 单条聊天消息行组件。
 * <p>
 * 根据消息类型渲染对应气泡子组件，并处理头像展示与事件向上传递。
 * </p>
 */
import { computed } from 'vue'
import { PhonePortraitOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import type { ChatMessage } from '../../types'
import { useAppStore } from '../../stores/app'
import { storeToRefs } from 'pinia'
import { useI18n } from '../../i18n'

import FileBubble from './bubbles/FileBubble.vue'
import ImageBubble from './bubbles/ImageBubble.vue'
import VoiceBubble from './bubbles/VoiceBubble.vue'
import RedPacketBubble from './bubbles/RedPacketBubble.vue'
import TextBubble from './bubbles/TextBubble.vue'
import DataCardBubble from './bubbles/DataCardBubble.vue'

const props = defineProps<{
  msg: ChatMessage
  /** 当前是否正在播放该条语音 */
  playing?: boolean
}>()

const emit = defineEmits<{
  (e: 'contextmenu', event: MouseEvent, msg: ChatMessage): void
  (e: 'playVoice', msg: ChatMessage): void
  (e: 'openFileView', msg: ChatMessage): void
  (e: 'openImageView', msg: ChatMessage): void
  (e: 'clickRedPacket', msg: ChatMessage): void
  (e: 'openPeerProfile', event: MouseEvent): void
  (e: 'openSelfProfile', event: MouseEvent): void
}>()

const { t } = useI18n()
const appStore = useAppStore()
const { currentSession, userProfile } = storeToRefs(appStore)

const isMyPhone = computed(() => {
  const name = currentSession.value?.name
  return name === '我的手机' || name === t('chat.myPhone')
})
const hasSession = computed(() => !!currentSession.value)
const isFriendChat = computed(() => hasSession.value && !currentSession.value?.isGroup && !isMyPhone.value)

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
</script>

<template>
  <div v-if="isSystemTip || isRecall" class="recall-tip-row">
    <span class="recall-tip">{{ tipText }}</span>
  </div>
  <div v-else class="message-row" :class="msg.isSelf ? 'right' : 'left'">
    <button v-if="!msg.isSelf && isFriendChat" type="button" class="avatar-btn" @click="emit('openPeerProfile', $event)">
      <Avatar v-bind="peerAvatarProps" />
    </button>
    <Avatar v-else-if="!msg.isSelf" v-bind="peerAvatarProps" />

    <div class="bubble-wrapper" @contextmenu="emit('contextmenu', $event, msg)">
      <FileBubble v-if="msg.type === 'file'" :msg="msg" @click="emit('openFileView', msg)" />
      <ImageBubble v-else-if="msg.type === 'image' || msg.isImage" :msg="msg" @click="emit('openImageView', msg)" />
      <VoiceBubble v-else-if="msg.type === 'voice'" :msg="msg" :playing="!!props.playing" @click="emit('playVoice', msg)" />
      <RedPacketBubble v-else-if="msg.type === 'redPacket'" :msg="msg" @click="emit('clickRedPacket', msg)" />
      <DataCardBubble v-else-if="msg.type === 'dataCard'" :msg="msg" />
      <TextBubble v-else :msg="msg" />
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
  padding: 4px 12px;
}
.recall-tip {
  font-size: 12px;
  line-height: 1.5;
  color: var(--lx-text-muted, #999);
  user-select: none;
}
.message-row {
  display: flex;
  gap: 8px;
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
}
</style>
<style>
/* Global styles for bubbles to avoid duplicating them */
.lx-bubble {
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
.lx-bubble.self {
  background: #4facfe;
  color: #ffffff;
  box-shadow: 0 1px 2px rgba(79, 172, 254, 0.3);
  border: none;
}
.lx-bubble.self .lx-bubble-text {
  color: #ffffff;
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
  color: #ffffff;
  text-decoration: underline;
  text-underline-offset: 2px;
  font-weight: 700;
}
.lx-file-card {
  max-width: 300px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: 0 1px 3px var(--lx-bg-active);
  cursor: pointer;
}
.lx-file-main { display: flex; align-items: center; gap: 12px; padding: 12px 14px; }
.lx-file-icon { width: 44px; height: 44px; border-radius: var(--lx-radius); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.lx-file-icon.apk { background: linear-gradient(145deg, #7ed56f 0%, #5cb85c 100%); }
.lx-file-name { font-size: 14px; font-weight: 500; color: var(--lx-text-body); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lx-file-size { font-size: 12px; color: var(--lx-text-muted); margin-top: 4px; }
.lx-file-bar { padding: 6px 14px; background: #4a4a4a; color: rgba(255, 255, 255, 0.9); font-size: 12px; }
.lx-bubble-reply {
  font-size: 12px; color: var(--lx-text-secondary); background: var(--lx-bg-hover);
  padding: 4px 8px; border-radius: var(--lx-radius); margin-bottom: 6px;
  border-left: 2px solid var(--lx-accent); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%;
}
.lx-bubble-image { max-width: 200px; max-height: 200px; border-radius: var(--lx-radius); object-fit: cover; cursor: pointer; display: block; }
.image-bubble { padding: 4px; background: var(--lx-bg-card); cursor: pointer; }
.voice-bubble { display: inline-flex; align-items: center; gap: 8px; min-width: 72px; cursor: pointer; }
.voice-bubble.playing { color: var(--lx-accent); }
.voice-ico { flex-shrink: 0; }
.red-packet-card {
  display: flex; align-items: center; gap: 10px; min-width: 200px; max-width: 260px;
  padding: 12px 14px; border-radius: var(--lx-radius);
  background: linear-gradient(135deg, #e84c3d, #c0392b); color: var(--lx-text-on-accent);
  cursor: pointer; box-shadow: 0 2px 8px rgba(232, 76, 61, 0.35);
}
.red-packet-card.opened { opacity: 0.85; }
.rp-icon { width: 36px; height: 36px; border-radius: 50%; background: rgba(255, 255, 255, 0.2); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 16px; flex-shrink: 0; }
.rp-title { font-size: 14px; font-weight: 600; line-height: 1.3; }
.rp-sub { font-size: 12px; opacity: 0.85; margin-top: 2px; }
</style>
