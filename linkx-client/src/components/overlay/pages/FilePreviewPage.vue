<script setup lang="ts">
/**
 * 文件预览页面
 */
import { computed, ref } from 'vue'
import { NButton, NIcon, useMessage } from 'naive-ui'
import { CloudDownloadOutline, OpenOutline, CloseOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useOverlayStore } from '../../../stores/overlay'
import { useI18n } from '../../../i18n'
import { downloadFileWithSettings } from '../../../utils/downloadFile'

const overlayStore = useOverlayStore()
const { close } = overlayStore
const { filePreview } = storeToRefs(overlayStore)
const { t } = useI18n()
const message = useMessage()
const downloading = ref(false)

// 文件大小格式化
function formatFileSize(bytes: number | string | undefined): string {
  if (!bytes) return t('overlay.unknownSize')
  const size = typeof bytes === 'string' ? parseInt(bytes) : bytes
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

// 判断是否为图片
const isImage = computed(() => {
  return filePreview.value?.isImage || false
})

// 文件图标
const fileIcon = computed(() => {
  if (isImage.value) return null
  const name = filePreview.value?.fileName || ''
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (['pdf'].includes(ext)) return 'PDF'
  if (['doc', 'docx'].includes(ext)) return 'DOC'
  if (['xls', 'xlsx'].includes(ext)) return 'XLS'
  if (['ppt', 'pptx'].includes(ext)) return 'PPT'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'ZIP'
  if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) return t('overlay.audio')
  if (['mp4', 'avi', 'mkv', 'mov'].includes(ext)) return t('overlay.video')
  return t('overlay.fileLabel')
})

// 下载文件（遵守文件管理：下载目录 / 保存方式）
async function downloadFile() {
  const url = filePreview.value?.fileUrl
  if (!url || downloading.value) return

  downloading.value = true
  try {
    const result = await downloadFileWithSettings(
      url,
      filePreview.value?.fileName || t('overlay.downloadName')
    )
    if (result.canceled) return
    if (result.ok) {
      message.success(
        result.path ? t('files.downloadSaved', { path: result.path }) : t('files.downloadOk')
      )
    } else {
      message.error(result.message || t('files.downloadFail'))
    }
  } finally {
    downloading.value = false
  }
}

// 在新窗口打开
function openFile() {
  const url = filePreview.value?.fileUrl
  if (!url) return
  window.open(url, '_blank')
}
</script>

<template>
  <div class="page-wrap file-preview-page">
    <section class="group-card file-preview">
      <!-- 关闭按钮 -->
      <button type="button" class="close-btn" @click="close">
        <n-icon :component="CloseOutline" :size="20" />
      </button>

      <!-- 预览区域 -->
      <div v-if="filePreview?.fileUrl && isImage" class="preview-box preview-img-wrap">
        <img :src="filePreview.fileUrl" :alt="filePreview.fileName" class="preview-img" />
      </div>
      <div v-else class="preview-box">
        <div class="file-icon-large">{{ fileIcon }}</div>
      </div>

      <!-- 文件信息 -->
      <div class="file-info">
        <h3 class="file-name">{{ filePreview?.fileName || t('overlay.unknownFile') }}</h3>
        <p class="file-meta">{{ formatFileSize(filePreview?.fileSize) }}</p>
      </div>

      <!-- 操作按钮 -->
      <div class="file-actions">
        <n-button
          v-if="filePreview?.fileUrl"
          type="primary"
          :loading="downloading"
          :disabled="downloading"
          @click="downloadFile"
        >
          <template #icon>
            <n-icon :component="CloudDownloadOutline" />
          </template>
          {{ t('overlay.download') }}
        </n-button>
        <n-button
          v-if="filePreview?.fileUrl && !isImage"
          secondary
          @click="openFile"
        >
          <template #icon>
            <n-icon :component="OpenOutline" />
          </template>
          {{ t('overlay.openBrowser') }}
        </n-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';

.file-preview-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 20px;
}

.file-preview {
  width: 100%;
  max-width: 500px;
  padding: 24px;
  position: relative;
  text-align: center;
}

.close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.preview-box {
  margin: 20px auto;
}

.preview-img-wrap {
  max-height: 400px;
  overflow: hidden;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-panel);
}

.preview-img {
  max-width: 100%;
  max-height: 400px;
  object-fit: contain;
  border-radius: var(--lx-radius);
}

.file-icon-large {
  width: 120px;
  height: 120px;
  margin: 0 auto;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
}

.file-info {
  margin: 16px 0;
}

.file-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-text-body);
  margin: 0 0 8px;
  word-break: break-all;
}

.file-meta {
  font-size: 14px;
  color: var(--lx-text-muted);
  margin: 0;
}

.file-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 20px;
}
</style>
