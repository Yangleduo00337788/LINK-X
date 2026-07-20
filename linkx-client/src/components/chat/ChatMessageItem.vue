<script setup lang="ts">
/**
 * 单条聊天消息行组件。
 * <p>
 * 根据消息类型渲染对应气泡子组件，并处理头像展示与事件向上传递。
 * </p>
 */
// Vue 计算属性
import { computed } from 'vue'
// 「我的手机」会话头像图标
import { PhonePortraitOutline } from '@vicons/ionicons5'
// 通用头像组件
import Avatar from '../Avatar.vue'
// 消息类型
import type { ChatMessage } from '../../types'
// 会话状态
import { useAppStore } from '../../stores/app'
import { storeToRefs } from 'pinia'
import { useI18n } from '../../i18n'

// 各类型消息气泡子组件
import FileBubble from './bubbles/FileBubble.vue'
import ImageBubble from './bubbles/ImageBubble.vue'
import VoiceBubble from './bubbles/VoiceBubble.vue'
import RedPacketBubble from './bubbles/RedPacketBubble.vue'
import TextBubble from './bubbles/TextBubble.vue'
import DataCardBubble from './bubbles/DataCardBubble.vue'

// 入参：消息体、当前正在播放的语音消息 id
defineProps<{
  msg: ChatMessage
  playingVoiceId: string | null
}>()

// 向父组件传递：右键菜单、播放语音、打开文件/图片、红包、资料卡
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
const { currentSession } = storeToRefs(appStore)

// 是否为「我的手机」会话
const isMyPhone = computed(() => {
  const name = currentSession.value?.name
  return name === '我的手机' || name === t('chat.myPhone')
})
// 是否已选中有效会话
const hasSession = computed(() => !!currentSession.value)
// 是否为单聊（非群、非我的手机）
const isFriendChat = computed(() => hasSession.value && !currentSession.value?.isGroup && !isMyPhone.value)

/**
 * 构造对方/系统侧头像 props。
 *
 * @param size 头像尺寸，默认 36
 */
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
  <!-- 消息行：左对齐（对方）或右对齐（自己） -->
  <div class="message-row" :class="msg.isSelf ? 'right' : 'left'">
    <!-- 单聊时对方头像可点击打开资料卡 -->
    <button v-if="!msg.isSelf && isFriendChat" type="button" class="avatar-btn" @click="emit('openPeerProfile', $event)">
      <Avatar v-bind="peerAvatarProps(36)" />
    </button>
    <Avatar v-else-if="!msg.isSelf" v-bind="peerAvatarProps(36)" />

    <!-- 气泡区域：按 type 分发到子组件 -->
    <div class="bubble-wrapper" @contextmenu="emit('contextmenu', $event, msg)">
      <FileBubble v-if="msg.type === 'file'" :msg="msg" @click="emit('openFileView', msg)" />
      <ImageBubble v-else-if="msg.type === 'image' || msg.isImage" :msg="msg" @click="emit('openImageView', msg)" />
      <VoiceBubble v-else-if="msg.type === 'voice'" :msg="msg" :playing="playingVoiceId === msg.id" @click="emit('playVoice', msg)" />
      <RedPacketBubble v-else-if="msg.type === 'redPacket'" :msg="msg" @click="emit('clickRedPacket', msg)" />
      <DataCardBubble v-else-if="msg.type === 'dataCard'" :msg="msg" />
      <TextBubble v-else :msg="msg" />
    </div>

    <!-- 自己侧头像：点击打开个人资料 -->
    <button v-if="msg.isSelf" type="button" class="avatar-btn" @click="emit('openSelfProfile', $event)">
      <Avatar :text="t('chat.me')" color="var(--lx-success)" :size="36" />
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
