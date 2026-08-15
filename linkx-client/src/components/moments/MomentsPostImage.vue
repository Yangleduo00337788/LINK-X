<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 朋友圈帖子图片：本系统图片优先鉴权加载。
 */
import { resolveMomentsImageDisplaySrc } from '../../utils/momentsMediaAccess'
import { recoverMediaUrlOnError } from '../../utils/mediaUrl'
import { useAuthDisplayImage } from '../../utils/authDisplayImage'

const props = defineProps<{
  url?: string
  imageId?: string
  alt?: string
}>()

const { displaySrc, onImgErrorApplyFallback } = useAuthDisplayImage({
  watchKeys: () => [props.url, props.imageId] as const,
  getFallbackUrl: () => props.url,
  resolveSrc: () => resolveMomentsImageDisplaySrc(props.imageId, props.url)
})

async function onImgError() {
  if (onImgErrorApplyFallback()) return
  const next = await recoverMediaUrlOnError(displaySrc.value, async () => props.url || null)
  if (next) displaySrc.value = next
}
</script>

<template>
  <img
    v-if="displaySrc"
    :src="displaySrc"
    :alt="alt || ''"
    class="post-image"
    loading="lazy"
    referrerpolicy="no-referrer"
    @error="onImgError"
  />
</template>
