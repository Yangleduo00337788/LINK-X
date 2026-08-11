<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 文件 / 图片预览页面（图片参考图二：大图横幅 + 底部信息条）
 */
import { computed, ref } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { CloudDownloadOutline, OpenOutline, CloseOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useOverlayStore } from '../../../stores/overlay'
import { useI18n } from '../../../i18n'
import { downloadFileWithSettings } from '../../../utils/downloadFile'
import { LxButton, LxIconButton } from '../../ui'

const overlayStore = useOverlayStore()
const { close } = overlayStore
const { filePreview } = storeToRefs(overlayStore)
const { t } = useI18n()
const message = useMessage()
const downloading = ref(false)

/** 已是人类可读则原样展示；纯数字按字节格式化 */
function displayFileSize(raw: number | string | undefined): string {
  if (raw == null || raw === '') return t('overlay.unknownSize')
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    if (!trimmed) return t('overlay.unknownSize')
    // 已格式化：含单位字母
    if (/[a-zA-Z]/.test(trimmed) && !/^\d+(\.\d+)?$/.test(trimmed)) {
      return trimmed
    }
    const n = Number(trimmed)
    if (!Number.isFinite(n) || n < 0) return trimmed
    return formatBytes(n)
  }
  return formatBytes(raw)
}

function formatBytes(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

const isImage = computed(() => !!filePreview.value?.isImage)

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

function openFile() {
  const url = filePreview.value?.fileUrl
  if (!url) return
  if (!/^https?:\/\//i.test(url)) {
    console.warn('[FilePreviewPage] 拒绝非 HTTP(S) URL:', url)
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <div class="page-wrap file-preview-page" :class="{ 'is-image': isImage }">
    <section class="preview-card" :class="{ 'preview-card--image': isImage }">
      <LxIconButton
        variant="close"
        class="close-btn lx-close-btn--overlay"
        :title="t('common.close')"
        @click="close"
      >
        <n-icon :component="CloseOutline" :size="20" />
      </LxIconButton>

      <!-- 图片：横幅预览 + 底部信息叠层 -->
      <template v-if="filePreview?.fileUrl && isImage">
        <div class="banner">
          <img :src="filePreview.fileUrl" :alt="filePreview.fileName" class="banner-img" />
          <div class="banner-meta">
            <span class="banner-name">{{ filePreview.fileName || t('overlay.unknownFile') }}</span>
            <span class="banner-size">{{ displayFileSize(filePreview.fileSize) }}</span>
          </div>
        </div>
        <div class="file-actions">
          <LxButton variant="pill-primary" :disabled="downloading" @click="downloadFile">
            <n-icon :component="CloudDownloadOutline" :size="16" />
            {{ t('overlay.download') }}
          </LxButton>
        </div>
      </template>

      <!-- 普通文件 -->
      <template v-else>
        <div class="preview-box">
          <div class="file-icon-large">{{ fileIcon }}</div>
        </div>
        <div class="file-info">
          <h3 class="file-name">{{ filePreview?.fileName || t('overlay.unknownFile') }}</h3>
          <p class="file-meta">{{ displayFileSize(filePreview?.fileSize) }}</p>
        </div>
        <div class="file-actions">
          <LxButton
            v-if="filePreview?.fileUrl"
            variant="pill-primary"
            :disabled="downloading"
            @click="downloadFile"
          >
            <n-icon :component="CloudDownloadOutline" :size="16" />
            {{ t('overlay.download') }}
          </LxButton>
          <LxButton v-if="filePreview?.fileUrl" variant="outline" class="lx-btn--pill" @click="openFile">
            <n-icon :component="OpenOutline" :size="16" />
            {{ t('overlay.openBrowser') }}
          </LxButton>
        </div>
      </template>
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
  padding: var(--lx-space-4xl) var(--lx-space-3xl);
}

.preview-card {
  width: 100%;
  max-width: 520px;
  padding: var(--lx-space-5xl-minus) var(--lx-space-4xl) var(--lx-space-4xl);
  position: relative;
  text-align: center;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-2xl);
  box-shadow: var(--lx-shadow-modal);
}

.preview-card--image {
  max-width: 560px;
  padding: var(--lx-space-2xl) var(--lx-space-2xl) var(--lx-space-3xl);
}

.close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: var(--lx-z-raised-2);
  width: 32px;
  height: 32px;
  border: none;
  background: rgba(0, 0, 0, 0.35);
  color: var(--lx-text-on-accent);
  cursor: pointer;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);
}

.preview-card:not(.preview-card--image) .close-btn {
  background: transparent;
  color: var(--lx-text-muted);
}

.preview-card:not(.preview-card--image) .close-btn:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.close-btn:hover {
  filter: brightness(1.1);
}

.banner {
  position: relative;
  border-radius: var(--lx-radius-lg);
  overflow: hidden;
  background: var(--lx-preview-void);
  min-height: 220px;
  max-height: min(520px, 62vh);
  display: flex;
  align-items: center;
  justify-content: center;
}

.banner-img {
  display: block;
  width: 100%;
  max-height: min(520px, 62vh);
  object-fit: contain;
}

.banner-meta {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-lg);
  padding: var(--lx-space) var(--lx-space-lg);
  border-radius: var(--lx-radius-pill);
  background: rgba(20, 22, 28, 0.72);
  color: var(--lx-text-on-accent);
  backdrop-filter: blur(8px);
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-snug);
}

.banner-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  text-align: left;
}

.banner-size {
  flex-shrink: 0;
  opacity: 0.85;
}

.preview-box {
  margin: var(--lx-space-3xl) auto;
}

.file-icon-large {
  width: 120px;
  height: 120px;
  margin: 0 auto;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  border-radius: var(--lx-radius-4xl);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--lx-font-display);
  font-weight: 700;
}

.file-info {
  margin: var(--lx-space-2xl) 0;
}

.file-name {
  font-size: var(--lx-font-xl);
  font-weight: 600;
  color: var(--lx-text-body);
  margin: 0 0 var(--lx-space);
  word-break: break-all;
}

.file-meta {
  font-size: var(--lx-font);
  color: var(--lx-text-muted);
  margin: 0;
}

.file-actions {
  display: flex;
  gap: var(--lx-space-lg);
  justify-content: center;
  margin-top: var(--lx-space-2xl);
}
</style>
