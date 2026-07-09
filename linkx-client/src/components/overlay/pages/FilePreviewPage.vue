<script setup lang="ts">
// Naive UI 按钮组件
import { NButton } from 'naive-ui'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 全屏覆盖层 Store
import { useOverlayStore } from '../../../stores/overlay'

// 覆盖层 Store 实例
const overlayStore = useOverlayStore()
// 文件预览参数（名称、URL、大小、是否图片）
const { filePreview } = storeToRefs(overlayStore)
</script>

<template>
  <!-- 文件预览页面 -->
  <div class="page-wrap file-preview-page">
    <!-- 预览区域与下载按钮 -->
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
