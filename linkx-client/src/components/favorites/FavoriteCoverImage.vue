<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 收藏封面图：有 messageId 时走聊天鉴权加载，避免 OSS/MinIO 外链在 Electron 下失败。
 */
import { resolveChatMediaSrcByMessageId } from '../../utils/chatMediaAccess'
import { useAuthDisplayImage } from '../../utils/authDisplayImage'

const props = defineProps<{
  messageId: string
  fallbackUrl?: string
  imgClass?: string
}>()

const emit = defineEmits<{ (e: 'error'): void }>()

const { displaySrc, onImgErrorApplyFallback } = useAuthDisplayImage({
  watchKeys: () => [props.messageId, props.fallbackUrl] as const,
  getFallbackUrl: () => props.fallbackUrl,
  resolveSrc: () => resolveChatMediaSrcByMessageId(props.messageId, props.fallbackUrl)
})

function onImgError() {
  if (!onImgErrorApplyFallback()) emit('error')
}
</script>

<template>
  <img
    v-if="displaySrc"
    :src="displaySrc"
    alt=""
    :class="imgClass"
    loading="lazy"
    decoding="async"
    referrerpolicy="no-referrer"
    draggable="false"
    @error="onImgError"
  />
</template>
