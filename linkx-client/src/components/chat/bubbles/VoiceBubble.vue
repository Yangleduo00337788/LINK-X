<script setup lang="ts">
/**
 * 语音消息气泡。
 * <p>
 * 展示麦克风图标与时长；playing 为 true 时高亮播放态。
 * </p>
 */
import { NIcon } from 'naive-ui'
import { MicOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'

defineProps<{ msg: ChatMessage; playing: boolean }>()

/**
 * 格式化语音时长显示。
 *
 * @param sec 秒数，缺省为 0
 * @returns 小于 60 秒显示 3" 形式，否则分秒形式
 */
function formatVoiceDuration(sec?: number) {
  const s = sec ?? 0
  return s < 60 ? `${s}"` : `${Math.floor(s / 60)}'${s % 60}"`
}
</script>

<template>
  <!-- 语音气泡：播放中附加 playing 类 -->
  <div class="qq-bubble voice-bubble" :class="{ self: msg.isSelf, playing }">
    <n-icon :component="MicOutline" :size="16" class="voice-ico" />
    <span>{{ formatVoiceDuration(msg.voiceDuration) }}</span>
  </div>
</template>
