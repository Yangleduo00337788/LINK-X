<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群相册缩略图：优先鉴权加载群资源内容。
 */
import { resolveGroupAssetDisplaySrc } from '../../utils/groupMediaAccess'
import { useAuthDisplayImage } from '../../utils/authDisplayImage'

const props = defineProps<{
  conversationId: string
  assetId: string
  fallbackUrl?: string
  alt?: string
}>()

const { displaySrc, onImgErrorApplyFallback } = useAuthDisplayImage({
  watchKeys: () => [props.conversationId, props.assetId, props.fallbackUrl] as const,
  getFallbackUrl: () => props.fallbackUrl,
  resolveSrc: () =>
    resolveGroupAssetDisplaySrc(props.conversationId, props.assetId, props.fallbackUrl)
})

function onImgError() {
  onImgErrorApplyFallback()
}
</script>

<template>
  <img
    v-if="displaySrc"
    :src="displaySrc"
    :alt="alt || ''"
    loading="lazy"
    decoding="async"
    referrerpolicy="no-referrer"
    draggable="false"
    @error="onImgError"
  />
</template>
