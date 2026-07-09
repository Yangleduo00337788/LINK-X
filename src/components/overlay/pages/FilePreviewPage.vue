<script setup lang="ts">
import { NButton } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useOverlayStore } from '../../../stores/overlay'

const overlayStore = useOverlayStore()
const { filePreview } = storeToRefs(overlayStore)
</script>

<template>
  <div class="page-wrap file-preview-page">
    <section class="group-card file-preview">
      <div v-if="filePreview?.fileUrl && filePreview.isImage" class="preview-box preview-img-wrap">
        <img :src="filePreview.fileUrl" :alt="filePreview.fileName" class="preview-img" />
      </div>
      <div v-else class="preview-box">{{ filePreview?.isImage ? '🖼️' : '📄' }}</div>
      <p class="file-name">{{ filePreview?.fileName || '文件' }}</p>
      <p class="muted">{{ filePreview?.fileSize || '—' }} · 本地预览</p>
      <n-button
        v-if="filePreview?.fileUrl"
        tag="a"
        :href="filePreview.fileUrl"
        target="_blank"
        rel="noopener"
        type="primary"
        class="mt"
      >
        下载 / 打开
      </n-button>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
