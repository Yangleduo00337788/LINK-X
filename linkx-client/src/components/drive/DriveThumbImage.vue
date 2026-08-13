<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 网盘图片缩略图：优先鉴权加载 /cloud/files/{id}/content。
 */
import { ref, watch, onBeforeUnmount } from 'vue'
import { resolveDriveImageDisplaySrc } from '../../utils/driveMediaAccess'
import { normalizeMediaUrl } from '../../utils/mediaUrl'

const props = defineProps<{
  fileId: string
  fallbackUrl?: string
  alt?: string
  imgClass?: string
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
  const resolved = await resolveDriveImageDisplaySrc(props.fileId, props.fallbackUrl)
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
  () => [props.fileId, props.fallbackUrl] as const,
  () => {
    void loadDisplaySrc()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  loadSeq += 1
  revokeAuthBlob()
})

function onImgError() {
  const fallback = normalizeMediaUrl(props.fallbackUrl || '') || props.fallbackUrl || ''
  if (fallback && fallback !== displaySrc.value) {
    revokeAuthBlob()
    displaySrc.value = fallback
  }
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
