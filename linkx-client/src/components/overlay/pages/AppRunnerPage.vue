<script setup lang="ts">
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 全屏覆盖层 Store
import { useOverlayStore } from '../../../stores/overlay'
// 内嵌 WebView 组件
import AppWebView from '../../AppWebView.vue'

// 覆盖层 Store 实例
const overlayStore = useOverlayStore()
// 当前要运行的应用信息
const { overlayApp } = storeToRefs(overlayStore)
</script>

<template>
  <!-- 应用运行页面：内嵌 WebView 或占位卡片 -->
  <div class="page-wrap app-runner-page" v-if="overlayApp">
    <div v-if="overlayApp.url" class="app-run-embed">
      <AppWebView :url="overlayApp.url" :title="overlayApp.name" />
    </div>
    <section v-else class="group-card app-run">
      <div class="app-icon-lg" :style="{ background: overlayApp.color }">{{ overlayApp.icon }}</div>
      <h2>{{ overlayApp.name }}</h2>
      <p>{{ overlayApp.desc }}</p>
      <p class="tip">该应用暂未配置内嵌 URL。</p>
    </section>
  </div>
</template>

<style scoped>
@import '../overlay-common.css';
</style>
