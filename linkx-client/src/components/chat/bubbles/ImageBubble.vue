<script setup lang="ts">
/**
 * 图片消息气泡。
 * <p>
 * content 字段存储图片 URL 或 DataURL。
 * </p>
 */
import type { ChatMessage } from '../../../types'

defineProps<{ msg: ChatMessage }>()
</script>

<template>
  <!-- 图片气泡：点击由父组件处理预览 -->
  <div class="lx-bubble image-bubble" :class="{ self: msg.isSelf }">
    <img :src="msg.content" class="lx-bubble-image" alt="图片消息" />
  </div>
</template>
