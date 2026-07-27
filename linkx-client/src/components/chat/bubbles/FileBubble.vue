<script setup lang="ts">
/**
 * 文件消息卡片气泡。
 * <p>
 * 展示文件名、大小与传输状态条；上传中显示进度。
 * </p>
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { DocumentOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'
import { useI18n } from '../../../i18n'

const props = defineProps<{ msg: ChatMessage }>()
const { t } = useI18n()

const barText = computed(() => {
  const msg = props.msg
  if (msg.fileStatus) return msg.fileStatus
  if (msg.isSelf && msg.sendStatus === 'failed') return t('chat.fileStatusFailed')
  if (
    msg.isSelf &&
    msg.sendStatus === 'sending' &&
    msg.uploadProgress != null &&
    msg.uploadProgress < 100
  ) {
    return t('chat.fileStatusUploading', { n: msg.uploadProgress })
  }
  if (msg.isSelf && msg.sendStatus === 'sending') return t('chat.fileStatusSending')
  return msg.isSelf ? t('chat.fileStatusSent') : t('chat.fileStatusReceived')
})

const progressPercent = computed(() => {
  const msg = props.msg
  if (!msg.isSelf || msg.sendStatus !== 'sending') return null
  if (msg.uploadProgress == null) return null
  return Math.max(0, Math.min(100, msg.uploadProgress))
})
</script>

<template>
  <div class="lx-file-card" :class="{ self: msg.isSelf, uploading: progressPercent != null }">
    <div class="lx-file-main">
      <div class="lx-file-icon apk">
        <n-icon :component="DocumentOutline" :size="26" color="var(--lx-bg-card)" />
      </div>
      <div class="lx-file-meta">
        <div class="lx-file-name">{{ msg.fileName || msg.content || t('chat.fileFallback') }}</div>
        <div class="lx-file-size">{{ msg.fileSize || '' }}</div>
      </div>
    </div>
    <div class="lx-file-bar">
      <div
        v-if="progressPercent != null"
        class="lx-file-progress"
        :style="{ width: progressPercent + '%' }"
      />
      <span class="lx-file-bar-text">{{ barText }}</span>
    </div>
  </div>
</template>

<style scoped>
.lx-file-card {
  max-width: 300px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: 0 1px 3px var(--lx-bg-active);
  cursor: pointer;
}
.lx-file-main {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
}
.lx-file-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.lx-file-icon.apk {
  background: linear-gradient(145deg, #7ed56f 0%, #5cb85c 100%);
}
.lx-file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lx-file-size {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-top: 4px;
}
.lx-file-bar {
  position: relative;
  overflow: hidden;
  padding: 6px 14px;
  background: #4a4a4a;
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
}
.lx-file-progress {
  position: absolute;
  inset: 0 auto 0 0;
  background: rgba(18, 183, 245, 0.35);
  transition: width 0.2s ease;
}
.lx-file-bar-text {
  position: relative;
  z-index: 1;
}
.lx-file-card.uploading .lx-file-bar {
  color: #b8e9ff;
}
</style>
