<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { LinkOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'
import { computed } from 'vue'

const props = defineProps<{ msg: ChatMessage }>()

const isLinkMsg = computed(() => {
  const msg = props.msg
  return msg.type === 'link' || /https?:\/\//.test(msg.content) || msg.content.includes('抖音')
})
</script>

<template>
  <div class="qq-bubble" :class="{ self: msg.isSelf, link: isLinkMsg }">
    <div v-if="msg.replyTo" class="qq-bubble-reply">
      {{ msg.replyTo.senderName }}: {{ msg.replyTo.content }}
    </div>
    <p class="qq-bubble-text">{{ msg.content }}</p>
    <n-icon v-if="isLinkMsg" class="qq-link-ico" :component="LinkOutline" :size="14" />
  </div>
</template>
