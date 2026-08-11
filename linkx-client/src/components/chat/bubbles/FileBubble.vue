<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 文件消息卡片气泡（图三风格：左侧信息 + 右侧类型图标）。
 */
import { computed } from 'vue'
import type { ChatMessage } from '../../../types'
import { useI18n } from '../../../i18n'
import { fileStatusFromSendStatus } from '../../../utils/messageStatus'

const props = defineProps<{ msg: ChatMessage }>()
const { t } = useI18n()

const displayName = computed(
  () => props.msg.fileName || props.msg.content || t('chat.fileFallback')
)

const extLabel = computed(() => {
  const name = displayName.value
  const i = name.lastIndexOf('.')
  if (i < 0) return 'FILE'
  return name.slice(i + 1).toUpperCase().slice(0, 4) || 'FILE'
})

const iconTone = computed(() => {
  const ext = extLabel.value.toLowerCase()
  if (ext === 'pdf') return 'pdf'
  if (['doc', 'docx'].includes(ext)) return 'doc'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'xls'
  if (['ppt', 'pptx'].includes(ext)) return 'ppt'
  if (['zip', 'rar', '7z'].includes(ext)) return 'zip'
  if (['txt', 'md', 'log'].includes(ext)) return 'txt'
  return 'file'
})

const barText = computed(() => fileStatusFromSendStatus(props.msg, t))

const progressPercent = computed(() => {
  const msg = props.msg
  if (!msg.isSelf || msg.sendStatus !== 'sending') return null
  if (msg.uploadProgress == null) return null
  return Math.max(0, Math.min(100, msg.uploadProgress))
})

const sizeLine = computed(() => {
  const size = props.msg.fileSize || ''
  if (!size) return barText.value
  return `${size}  ${barText.value}`
})
</script>

<template>
  <div class="lx-file-card" :class="{ self: msg.isSelf, uploading: progressPercent != null }">
    <div class="lx-file-main">
      <div class="lx-file-meta">
        <div class="lx-file-name" :title="displayName">{{ displayName }}</div>
        <div class="lx-file-size">{{ sizeLine }}</div>
      </div>
      <div class="lx-file-icon" :class="iconTone">
        <span class="lx-file-ext">{{ extLabel }}</span>
      </div>
    </div>
    <div v-if="progressPercent != null" class="lx-file-bar">
      <div class="lx-file-progress" :style="{ width: progressPercent + '%' }" />
      <span class="lx-file-bar-text">{{ barText }}</span>
    </div>
  </div>
</template>

<style scoped>
.lx-file-card {
  max-width: 280px;
  min-width: 200px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius-xl);
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  transition: filter var(--lx-duration) ease;
}
.lx-file-card:hover {
  filter: brightness(1.03);
}
.lx-file-card.self {
  background: var(--lx-divider);
}
.lx-file-card.self .lx-file-name {
  color: var(--lx-text-on-accent);
}
.lx-file-card.self .lx-file-size {
  color: rgba(255, 255, 255, 0.65);
}
.lx-file-main {
  display: flex;
  align-items: center;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-lg) var(--lx-space-xl);
}
.lx-file-meta {
  flex: 1;
  min-width: 0;
  text-align: left;
}
.lx-file-icon {
  width: 48px;
  height: 56px;
  border-radius: var(--lx-radius-xs);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(180deg, var(--lx-bg-soft) 0%, var(--lx-file-bubble-track) 100%);
  box-shadow: var(--lx-shadow-inset-border);
  position: relative;
  clip-path: polygon(0 0, calc(100% - 12px) 0, 100% 12px, 100% 100%, 0 100%);
}
.lx-file-icon::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 12px;
  height: 12px;
  background: linear-gradient(135deg, transparent 50%, rgba(0, 0, 0, 0.08) 50%);
}
.lx-file-ext {
  font-size: var(--lx-font-xs);
  font-weight: 700;
  letter-spacing: 0.02em;
  padding-bottom: var(--lx-space);
  color: var(--lx-file-bubble-muted);
}
.lx-file-icon.pdf .lx-file-ext { color: var(--lx-danger); }
.lx-file-icon.doc .lx-file-ext { color: var(--lx-file-bubble-doc); }
.lx-file-icon.xls .lx-file-ext { color: var(--lx-file-bubble-xls); }
.lx-file-icon.ppt .lx-file-ext { color: var(--lx-file-bubble-ppt); }
.lx-file-icon.zip .lx-file-ext { color: var(--lx-file-bubble-zip); }
.lx-file-icon.txt .lx-file-ext { color: var(--lx-file-bubble-txt); }
.lx-file-name {
  font-size: var(--lx-font);
  font-weight: 500;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lx-file-size {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  margin-top: var(--lx-space-sm);
}
.lx-file-bar {
  position: relative;
  overflow: hidden;
  padding: var(--lx-space-sm) var(--lx-space-xl);
  background: var(--lx-file-preview);
  color: rgba(255, 255, 255, 0.9);
  font-size: var(--lx-font-sm);
}
.lx-file-progress {
  position: absolute;
  inset: 0 auto 0 0;
  background: rgba(18, 183, 245, 0.35);
  transition: width var(--lx-duration-md) ease;
}
.lx-file-bar-text {
  position: relative;
  z-index: var(--lx-z-raised);
}
.lx-file-card.uploading .lx-file-bar {
  color: var(--lx-file-preview-text);
}
</style>
