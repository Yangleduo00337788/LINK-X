<script setup lang="ts">
import { computed } from 'vue'
import { PhonePortraitOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import type { ChatMessage } from '../../types'
import { useAppStore } from '../../stores/app'
import { storeToRefs } from 'pinia'

import FileBubble from './bubbles/FileBubble.vue'
import ImageBubble from './bubbles/ImageBubble.vue'
import VoiceBubble from './bubbles/VoiceBubble.vue'
import RedPacketBubble from './bubbles/RedPacketBubble.vue'
import TextBubble from './bubbles/TextBubble.vue'
import DataCardBubble from './bubbles/DataCardBubble.vue'

defineProps<{
  msg: ChatMessage
  playingVoiceId: string | null
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

const appStore = useAppStore()
const { currentSession } = storeToRefs(appStore)

const isMyPhone = computed(() => currentSession.value?.name === '我的手机')
const hasSession = computed(() => !!currentSession.value)
const isFriendChat = computed(() => hasSession.value && !currentSession.value?.isGroup && !isMyPhone.value)

function peerAvatarProps(size = 36) {
  const s = currentSession.value
  return {
    text: s?.avatarText || '?',
    color: s?.avatarColor || 'var(--lx-accent)',
    size,
    imageUrl: s?.avatarUrl,
    icon: isMyPhone.value ? PhonePortraitOutline : undefined
  }
}
</script>

<template>
  <div class="message-row" :class="msg.isSelf ? 'right' : 'left'">
    <button v-if="!msg.isSelf && isFriendChat" type="button" class="avatar-btn" @click="emit('openPeerProfile', $event)">
      <Avatar v-bind="peerAvatarProps(36)" />
    </button>
    <Avatar v-else-if="!msg.isSelf" v-bind="peerAvatarProps(36)" />

    <div class="bubble-wrapper" @contextmenu="emit('contextmenu', $event, msg)">
      <FileBubble v-if="msg.type === 'file'" :msg="msg" @click="emit('openFileView', msg)" />
      <ImageBubble v-else-if="msg.type === 'image' || msg.isImage" :msg="msg" @click="emit('openImageView', msg)" />
      <VoiceBubble v-else-if="msg.type === 'voice'" :msg="msg" :playing="playingVoiceId === msg.id" @click="emit('playVoice', msg)" />
      <RedPacketBubble v-else-if="msg.type === 'redPacket'" :msg="msg" @click="emit('clickRedPacket', msg)" />
      <DataCardBubble v-else-if="msg.type === 'dataCard'" :msg="msg" />
      <TextBubble v-else :msg="msg" />
    </div>

    <button v-if="msg.isSelf" type="button" class="avatar-btn" @click="emit('openSelfProfile', $event)">
      <Avatar text="我" color="var(--lx-success)" :size="36" />
    </button>
  </div>
</template>

<style scoped>
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
.qq-bubble {
  position: relative;
  background: var(--lx-bg-card);
  padding: 10px 12px;
  border-radius: var(--lx-radius);
  font-size: 14px;
  line-height: 1.55;
  color: var(--lx-text);
  box-shadow: 0 1px 2px var(--lx-border-light);
}
.qq-bubble.self {
  background: #c9e7ff;
  border-radius: var(--lx-radius);
}
.qq-bubble.link .qq-bubble-text { margin: 0; word-break: break-all; }
.qq-link-ico { display: none; }
.qq-bubble-text { margin: 0; white-space: pre-wrap; word-break: break-word; }
.qq-file-card {
  max-width: 300px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: 0 1px 3px var(--lx-bg-active);
  cursor: pointer;
}
.qq-file-main { display: flex; align-items: center; gap: 12px; padding: 12px 14px; }
.qq-file-icon { width: 44px; height: 44px; border-radius: var(--lx-radius); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.qq-file-icon.apk { background: linear-gradient(145deg, #7ed56f 0%, #5cb85c 100%); }
.qq-file-name { font-size: 14px; font-weight: 500; color: var(--lx-text-body); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qq-file-size { font-size: 12px; color: var(--lx-text-muted); margin-top: 4px; }
.qq-file-bar { padding: 6px 14px; background: #4a4a4a; color: rgba(255, 255, 255, 0.9); font-size: 12px; }
.qq-bubble-reply {
  font-size: 12px; color: var(--lx-text-secondary); background: var(--lx-bg-hover);
  padding: 4px 8px; border-radius: var(--lx-radius); margin-bottom: 6px;
  border-left: 2px solid var(--lx-accent); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%;
}
.qq-bubble-image { max-width: 200px; max-height: 200px; border-radius: var(--lx-radius); object-fit: cover; cursor: pointer; display: block; }
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
