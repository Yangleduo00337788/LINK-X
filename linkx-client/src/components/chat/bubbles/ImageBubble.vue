<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 图片消息气泡：无边框直出，双击进入预览。
 */
import { ref, watch } from 'vue'
import type { ChatMessage } from '../../../types'
import * as chatApi from '../../../api/chat'
import { recoverMediaUrlOnError } from '../../../utils/mediaUrl'
import { useI18n } from '../../../i18n'

const props = defineProps<{ msg: ChatMessage }>()
const emit = defineEmits<{ (e: 'preview', msg: ChatMessage): void }>()
const { t } = useI18n()
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

function onDblClick(e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  emit('preview', props.msg)
}
</script>

<template>
  <div class="image-bubble" :class="{ self: msg.isSelf }" @dblclick="onDblClick">
    <img
      :src="displaySrc"
      class="lx-bubble-image"
      :alt="msg.fileName || t('chat.imageMessage')"
      :title="t('chat.imageDblClickHint')"
      loading="lazy"
      decoding="async"
      referrerpolicy="no-referrer"
      draggable="false"
      @error="onImgError"
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
  border-radius: 8px;
  object-fit: cover;
  cursor: zoom-in;
  display: block;
  user-select: none;
}
</style>
