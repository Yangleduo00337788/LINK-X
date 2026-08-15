<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 图片消息气泡：无边框直出，双击进入预览。
 * 已入库消息优先鉴权加载（Web Cookie / Electron blob），减少预签名 URL 暴露。
 */
import { ref, watch, onBeforeUnmount } from 'vue'
import type { ChatMessage } from '../../../types'
import * as chatApi from '../../../api/chat'
import { normalizeMediaUrl, recoverMediaUrlOnError } from '../../../utils/mediaUrl'
import { buildChatMessageMediaApiUrl } from '../../../utils/authDownload'
import {
  getCachedElectronMediaBlob,
  canUseAuthenticatedChatMedia,
  isLocalChatMediaPreview,
  resolveChatImageDisplaySrc
} from '../../../utils/chatMediaAccess'
import {
  getCachedMediaPath,
  saveMediaBytes,
  toMediaFileUrl
} from '../../../services/chatMessageStore'
import { isWebEnvironment } from '../../../utils/tokenStorage'
import { useI18n } from '../../../i18n'

const props = defineProps<{ msg: ChatMessage }>()
const emit = defineEmits<{
  (e: 'preview', msg: ChatMessage): void
  (e: 'contentLoaded'): void
}>()
const { t } = useI18n()

const displaySrc = ref('')
let authBlobUrl: string | null = null
let loadSeq = 0

function getImmediateSrc(msg: ChatMessage): string {
  if (isLocalChatMediaPreview(msg)) {
    return (msg.fileUrl || msg.content || '').trim()
  }
  if (canUseAuthenticatedChatMedia(msg) && isWebEnvironment()) {
    return buildChatMessageMediaApiUrl(msg.id)
  }
  if (canUseAuthenticatedChatMedia(msg)) {
    const cached = getCachedElectronMediaBlob(msg.id)
    if (cached) return cached
  }
  const raw = (msg.fileUrl || msg.content || '').trim()
  if (!raw || raw.startsWith('blob:') || raw.startsWith('data:')) return raw
  return normalizeMediaUrl(raw) || raw
}

function revokeAuthBlob() {
  if (authBlobUrl) {
    URL.revokeObjectURL(authBlobUrl)
    authBlobUrl = null
  }
}

async function loadDisplaySrc() {
  const seq = ++loadSeq
  const disk = canUseAuthenticatedChatMedia(props.msg)
    ? await getCachedMediaPath(props.msg.id, 'thumb')
    : null
  if (disk) {
    displaySrc.value = toMediaFileUrl(disk)
    return
  }
  const immediate = getImmediateSrc(props.msg)
  if (immediate) {
    displaySrc.value = immediate
  }
  revokeAuthBlob()
  const resolved = await resolveChatImageDisplaySrc(props.msg)
  if (seq !== loadSeq) {
    if (resolved.blobUrlToRevoke) {
      URL.revokeObjectURL(resolved.blobUrlToRevoke)
    }
    return
  }
  if (resolved.blobUrlToRevoke) {
    authBlobUrl = resolved.blobUrlToRevoke
    try {
      const res = await fetch(resolved.src)
      const buf = await res.arrayBuffer()
      const saved = await saveMediaBytes(props.msg.id, buf, { kind: 'thumb', ext: 'jpg' })
      if (saved) {
        revokeAuthBlob()
        displaySrc.value = toMediaFileUrl(saved)
        return
      }
    } catch {
      /* keep blob url */
    }
  }
  if (resolved.src) {
    displaySrc.value = resolved.src
  }
}

watch(
  () => props.msg.id,
  () => {
    displaySrc.value = getImmediateSrc(props.msg)
    revokeAuthBlob()
  }
)

watch(
  () => [props.msg.id, props.msg.content, props.msg.fileUrl, props.msg.sendStatus] as const,
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
    const res = await chatApi.refreshMessageMediaUrl(props.msg.id)
    if (res.code === 200 && res.data?.url) return res.data.url
    return null
  })
  if (next) {
    revokeAuthBlob()
    displaySrc.value = normalizeMediaUrl(next) || next
  }
}

function onDblClick(e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  emit('preview', props.msg)
}

function onImgLoad() {
  emit('contentLoaded')
}
</script>

<template>
  <div class="image-bubble" :class="{ self: msg.isSelf }" @dblclick="onDblClick">
    <img
      v-if="displaySrc"
      :src="displaySrc"
      class="lx-bubble-image"
      :alt="msg.fileName || t('chat.imageMessage')"
      :title="t('chat.imageDblClickHint')"
      loading="lazy"
      decoding="async"
      referrerpolicy="no-referrer"
      draggable="false"
      @error="onImgError"
      @load="onImgLoad"
      @dblclick="onDblClick"
    />
  </div>
</template>

<style scoped>
.image-bubble {
  padding: 0;
  background: transparent;
  border: none;
  box-shadow: none;
  line-height: 0;
}

.lx-bubble-image {
  max-width: 220px;
  max-height: 280px;
  border-radius: var(--lx-bubble-radius);
  object-fit: cover;
  cursor: zoom-in;
  display: block;
  user-select: none;
}
</style>
