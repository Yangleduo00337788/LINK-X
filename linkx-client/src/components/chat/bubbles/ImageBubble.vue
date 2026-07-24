<script setup lang="ts">
/**
 * 图片消息气泡。
 * <p>
 * content 字段存储图片 URL 或 DataURL；预签名过期时自动刷新。
 * </p>
 */
import { ref, watch } from 'vue'
import type { ChatMessage } from '../../../types'
import * as chatApi from '../../../api/chat'
import { recoverMediaUrlOnError } from '../../../utils/mediaUrl'

const props = defineProps<{ msg: ChatMessage }>()
const displaySrc = ref(props.msg.content || props.msg.fileUrl || '')

watch(
  () => [props.msg.content, props.msg.fileUrl, props.msg.id] as const,
  ([content, fileUrl]) => {
    displaySrc.value = content || fileUrl || ''
  }
)

async function onImgError() {
  const next = await recoverMediaUrlOnError(displaySrc.value, async () => {
    const res = await chatApi.refreshMessageMediaUrl(props.msg.id)
    if (res.code === 200 && res.data?.url) return res.data.url
    return null
  })
  if (next) {
    displaySrc.value = next
  }
}
</script>

<template>
  <!-- 图片气泡：点击由父组件处理预览 -->
  <div class="lx-bubble image-bubble" :class="{ self: msg.isSelf }">
    <img
      :src="displaySrc"
      class="lx-bubble-image"
      alt="图片消息"
      loading="lazy"
      decoding="async"
      @error="onImgError"
    />
  </div>
</template>
