<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 网盘图片缩略图：优先鉴权加载 /cloud/files/{id}/content。
 */
import { resolveDriveImageDisplaySrc } from '../../utils/driveMediaAccess'
import { useAuthDisplayImage } from '../../utils/authDisplayImage'

const props = defineProps<{
  fileId: string
  fallbackUrl?: string
  alt?: string
  imgClass?: string
}>()

const { displaySrc, onImgErrorApplyFallback } = useAuthDisplayImage({
  watchKeys: () => [props.fileId, props.fallbackUrl] as const,
  getFallbackUrl: () => props.fallbackUrl,
  resolveSrc: () => resolveDriveImageDisplaySrc(props.fileId, props.fallbackUrl)
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
    :class="imgClass"
    loading="lazy"
    decoding="async"
    referrerpolicy="no-referrer"
    draggable="false"
    @error="onImgError"
  />
</template>
