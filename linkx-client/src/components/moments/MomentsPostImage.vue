<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 朋友圈帖子图片：本系统图片优先鉴权加载。
 */
import { ref, watch, onBeforeUnmount } from 'vue'
import { resolveMomentsImageDisplaySrc } from '../../utils/momentsMediaAccess'
import { normalizeMediaUrl, recoverMediaUrlOnError } from '../../utils/mediaUrl'

const props = defineProps<{
  url?: string
  imageId?: string
  alt?: string
}>()

const displaySrc = ref('')
let authBlobUrl: string | null = null
let loadSeq = 0

function revokeAuthBlob() {
  if (authBlobUrl) {
    URL.revokeObjectURL(authBlobUrl)
    authBlobUrl = null
  }
}

async function loadDisplaySrc() {
  const seq = ++loadSeq
  revokeAuthBlob()
  const resolved = await resolveMomentsImageDisplaySrc(props.imageId, props.url)
  if (seq !== loadSeq) {
    if (resolved.blobUrlToRevoke) {
      URL.revokeObjectURL(resolved.blobUrlToRevoke)
    }
    return
  }
  if (resolved.blobUrlToRevoke) {
    authBlobUrl = resolved.blobUrlToRevoke
  }
  displaySrc.value = resolved.src
}

watch(
  () => [props.url, props.imageId] as const,
  () => {
    void loadDisplaySrc()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  loadSeq += 1
  revokeAuthBlob()
})

async function onImgError() {
  const next = await recoverMediaUrlOnError(displaySrc.value, async () => {
    const fallback = normalizeMediaUrl(props.url || '') || props.url || ''
    return fallback || null
  })
  if (next) {
    revokeAuthBlob()
    displaySrc.value = normalizeMediaUrl(next) || next
  }
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
