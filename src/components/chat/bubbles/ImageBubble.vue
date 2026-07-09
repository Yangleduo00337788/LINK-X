<script setup lang="ts">
import type { ChatMessage } from '../../../types'

defineProps<{ msg: ChatMessage }>()
</script>

<template>
  <div class="qq-bubble image-bubble" :class="{ self: msg.isSelf }">
    <img :src="msg.content" class="qq-bubble-image" alt="图片消息" />
  </div>
</template>
