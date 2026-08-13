<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 收藏封面图：有 messageId 时走聊天鉴权加载，避免 OSS/MinIO 外链在 Electron 下失败。
 */
import { ref, watch, onBeforeUnmount } from 'vue'
import { resolveChatMediaSrcByMessageId } from '../../utils/chatMediaAccess'
import { normalizeMediaUrl } from '../../utils/mediaUrl'

const props = defineProps<{
  messageId: string
  fallbackUrl?: string
  imgClass?: string
}>()

const emit = defineEmits<{ (e: 'error'): void }>()

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
  const resolved = await resolveChatMediaSrcByMessageId(props.messageId, props.fallbackUrl)
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
  () => [props.messageId, props.fallbackUrl] as const,
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
    return
  }
  emit('error')
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
