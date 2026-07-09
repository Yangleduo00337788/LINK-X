<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { MicOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'

defineProps<{ msg: ChatMessage; playing: boolean }>()

function formatVoiceDuration(sec?: number) {
  const s = sec ?? 0
  return s < 60 ? `${s}"` : `${Math.floor(s / 60)}'${s % 60}"`
}
</script>

<template>
  <div class="qq-bubble voice-bubble" :class="{ self: msg.isSelf, playing }">
    <n-icon :component="MicOutline" :size="16" class="voice-ico" />
    <span>{{ formatVoiceDuration(msg.voiceDuration) }}</span>
  </div>
</template>
