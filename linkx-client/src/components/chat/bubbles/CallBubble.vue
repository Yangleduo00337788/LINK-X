<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 音视频通话 / 会议提示气泡（微信风格：图标 + 文案，可点击回拨或入会）。
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { CallOutline, VideocamOutline, PeopleOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'
import { useI18n } from '../../../i18n'

const props = defineProps<{ msg: ChatMessage }>()
const emit = defineEmits<{ (e: 'click', msg: ChatMessage): void }>()
const { t } = useI18n()

const isCall = computed(() => {
  if (props.msg.conferenceScene === 'call') return true
  if (props.msg.conferenceScene === 'meeting') return false
  const c = props.msg.content || ''
  const n = props.msg.fileName || ''
  return /语音通话|视频通话/.test(c) || n === '语音通话' || n === '视频通话'
})

const isVoice = computed(() => {
  if (props.msg.conferenceType === 'voice') return true
  if (props.msg.conferenceType === 'video') return false
  return /语音通话/.test(props.msg.content || '') || props.msg.fileName === '语音通话'
})

const icon = computed(() => {
  if (!isCall.value) return PeopleOutline
  return isVoice.value ? CallOutline : VideocamOutline
})

const label = computed(() => {
  const c = (props.msg.content || '').trim()
  if (c) return c
  if (isCall.value) {
    return isVoice.value ? t('chat.voiceCall') : t('chat.videoCall')
  }
  return props.msg.conferenceTitle || props.msg.fileName || t('conference.defaultTitle')
})
</script>

<template>
  <button
    type="button"
    class="lx-bubble call-bubble"
    :class="{ self: msg.isSelf }"
    @click="emit('click', msg)"
  >
    <n-icon :component="icon" :size="22" class="call-ico" />
    <span class="call-label">{{ label }}</span>
  </button>
</template>

<style scoped>
.call-bubble {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-md);
  max-width: min(260px, 70vw);
  min-width: 132px;
  padding: var(--lx-space-lg) var(--lx-space-xl);
  cursor: pointer;
  text-align: left;
  font: inherit;
}
.call-ico {
  flex-shrink: 0;
  opacity: 0.95;
}
.call-label {
  flex: 1;
  word-break: break-word;
  white-space: pre-wrap;
  line-height: var(--lx-leading-snug);
}
.call-bubble:hover {
  filter: brightness(0.98);
}
.call-bubble:active {
  filter: brightness(0.95);
}
</style>
