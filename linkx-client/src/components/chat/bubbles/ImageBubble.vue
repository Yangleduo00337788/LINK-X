<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 图片消息气泡：无边框直出，双击进入预览。
 * 已入库消息优先鉴权加载（Web Cookie / Electron blob），减少预签名 URL 暴露。
 * 虚拟列表会回收节点，展示地址写入内存缓存以避免滚动时灰块闪烁。
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
  resolveChatImageDisplaySrc,
  cacheElectronMediaBlob
} from '../../../utils/chatMediaAccess'
import {
  getCachedMediaPath,
  saveMediaBytes,
  toMediaFileUrl
} from '../../../services/chatMessageStore'
import { isWebEnvironment } from '../../../utils/tokenStorage'
import { useI18n } from '../../../i18n'
import {
  getChatImageDisplayCache,
  setChatImageDisplayCache
} from '../../../utils/chatImageDisplayCache'

const props = defineProps<{ msg: ChatMessage }>()
const emit = defineEmits<{
  (e: 'preview', msg: ChatMessage): void
  (e: 'contentLoaded'): void
}>()
const { t } = useI18n()

let loadSeq = 0

function getSyncDisplaySrc(msg: ChatMessage): string {
  const cached = getChatImageDisplayCache(msg.id)
  if (cached) return cached

  if (isLocalChatMediaPreview(msg)) {
    return (msg.fileUrl || msg.content || '').trim()
  }
  if (canUseAuthenticatedChatMedia(msg)) {
    const blob = getCachedElectronMediaBlob(msg.id)
    if (blob) return blob
  }
  if (canUseAuthenticatedChatMedia(msg) && isWebEnvironment()) {
    return buildChatMessageMediaApiUrl(msg.id)
  }
  const raw = (msg.fileUrl || msg.content || '').trim()
  if (!raw || raw.startsWith('blob:') || raw.startsWith('data:')) return raw
  return normalizeMediaUrl(raw) || raw
}

const displaySrc = ref(getSyncDisplaySrc(props.msg))

function commitDisplaySrc(src: string) {
  const trimmed = src?.trim()
  if (!trimmed) return
  displaySrc.value = trimmed
  setChatImageDisplayCache(props.msg.id, trimmed)
  if (trimmed.startsWith('blob:')) {
    cacheElectronMediaBlob(props.msg.id, trimmed)
  }
}

async function loadDisplaySrc() {
  const seq = ++loadSeq

  const disk = canUseAuthenticatedChatMedia(props.msg)
    ? await getCachedMediaPath(props.msg.id, 'thumb')
    : null
  if (disk) {
    if (seq !== loadSeq) return
    commitDisplaySrc(toMediaFileUrl(disk))
    return
  }

  const syncSrc = getSyncDisplaySrc(props.msg)
  if (syncSrc) {
    commitDisplaySrc(syncSrc)
    if (isLocalChatMediaPreview(props.msg)) return
  }

  const resolved = await resolveChatImageDisplaySrc(props.msg)
  if (seq !== loadSeq) {
    if (resolved.blobUrlToRevoke) {
      URL.revokeObjectURL(resolved.blobUrlToRevoke)
    }
    return
  }

  if (resolved.src) {
    commitDisplaySrc(resolved.src)
    if (resolved.blobUrlToRevoke) {
      const msgId = props.msg.id
      const src = resolved.src
      const persistThumb = async () => {
        if (seq !== loadSeq) return
        try {
          const res = await fetch(src)
          const buf = await res.arrayBuffer()
          const saved = await saveMediaBytes(msgId, buf, { kind: 'thumb', ext: 'jpg' })
          if (saved && seq === loadSeq) {
            commitDisplaySrc(toMediaFileUrl(saved))
          }
        } catch {
          /* keep blob / current src */
        }
      }
      if (typeof requestIdleCallback !== 'undefined') {
        requestIdleCallback(() => {
          void persistThumb()
        }, { timeout: 2500 })
      } else {
        window.setTimeout(() => {
          void persistThumb()
        }, 0)
      }
    }
  }
}

watch(
  () => props.msg.id,
  () => {
    displaySrc.value = getSyncDisplaySrc(props.msg)
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
})

async function onImgError() {
  const next = await recoverMediaUrlOnError(displaySrc.value, async () => {
    const res = await chatApi.refreshMessageMediaUrl(props.msg.id)
    if (res.code === 200 && res.data?.url) return res.data.url
    return null
  })
  if (next) {
    commitDisplaySrc(normalizeMediaUrl(next) || next)
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
