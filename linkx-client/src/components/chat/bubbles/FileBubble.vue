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
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  transition: filter 0.15s ease;
}
.lx-file-card:hover {
  filter: brightness(1.03);
}
.lx-file-card.self {
  background: #3a3a3a;
}
.lx-file-card.self .lx-file-name {
  color: #fff;
}
.lx-file-card.self .lx-file-size {
  color: rgba(255, 255, 255, 0.65);
}
.lx-file-main {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
}
.lx-file-meta {
  flex: 1;
  min-width: 0;
  text-align: left;
}
.lx-file-icon {
  width: 48px;
  height: 56px;
  border-radius: 6px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(180deg, #f5f7fa 0%, #e8ecf1 100%);
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.06);
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
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  padding-bottom: 8px;
  color: #5a6a7a;
}
.lx-file-icon.pdf .lx-file-ext { color: #e34d59; }
.lx-file-icon.doc .lx-file-ext { color: #2b6cb0; }
.lx-file-icon.xls .lx-file-ext { color: #2f855a; }
.lx-file-icon.ppt .lx-file-ext { color: #c05621; }
.lx-file-icon.zip .lx-file-ext { color: #805ad5; }
.lx-file-icon.txt .lx-file-ext { color: #4a5568; }
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
  margin-top: 6px;
}
.lx-file-bar {
  position: relative;
  overflow: hidden;
  padding: 6px 14px;
  background: #2f2f2f;
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
