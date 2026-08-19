<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 语音消息气泡：内置鉴权播放逻辑。
 */
import { computed } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { MicOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'
import { useChatVoicePlayer } from '../../../utils/useChatVoicePlayer'
import { formatVoiceDurationLabel } from '../../../utils/voiceRecorder'

const props = defineProps<{ msg: ChatMessage }>()
const message = useMessage()
const { playingVoiceId, playVoice } = useChatVoicePlayer({
  onInfo: text => message.info(text),
  onError: text => message.error(text)
})

const playing = computed(() => playingVoiceId.value === props.msg.id)

function onClick() {
  void playVoice(props.msg)
}
</script>

<template>
  <div
    class="lx-bubble voice-bubble"
    :class="{ self: msg.isSelf, playing }"
    @click="onClick"
  >
    <n-icon :component="MicOutline" :size="16" class="voice-ico" />
    <span>{{ formatVoiceDurationLabel(msg.voiceDuration) }}</span>
  </div>
</template>
