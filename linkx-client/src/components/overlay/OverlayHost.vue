<script setup lang="ts">
// Vue 计算属性与异步组件
import { computed, defineAsyncComponent } from 'vue'
// Naive UI 按钮与图标
import { NButton, NIcon } from 'naive-ui'
// Ionicons5 返回箭头图标
import { ArrowBackOutline } from '@vicons/ionicons5'
// 窗口控制按钮组件
import WindowControls from '../WindowControls.vue'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 全屏覆盖层 Store
import { useOverlayStore } from '../../stores/overlay'
// 覆盖层页面类型
import type { OverlayPage } from '../../types'

// 覆盖层 Store 实例
const overlayStore = useOverlayStore()
// 当前覆盖层页面
const { currentPage } = storeToRefs(overlayStore)
// 关闭覆盖层的方法
const { close } = overlayStore

// 异步加载各覆盖层子页面（按需加载）
const HelpPage = defineAsyncComponent(() => import('./pages/HelpPage.vue'))
const FilePreviewPage = defineAsyncComponent(() => import('./pages/FilePreviewPage.vue'))
const ChatHistoryPage = defineAsyncComponent(() => import('./pages/ChatHistoryPage.vue'))

// 各覆盖层页面的默认标题映射
const titleMap: Record<OverlayPage, string> = {
  help: '帮助与反馈',
  'file-preview': '文件预览',
  'chat-history': '聊天记录'
}

// 根据当前页面计算标题栏文案
const pageTitle = computed(() => {
  const p = currentPage.value
  if (!p) return ''
  return titleMap[p]
})
</script>

<template>
  <!-- 全屏覆盖层容器：有页面时显示 -->
  <div v-if="currentPage" class="overlay-host">
    <!-- 顶部导航栏：返回按钮、标题、窗口控制 -->
    <div class="overlay-header">
      <div class="left">
        <n-button quaternary circle @click="close">
          <template #icon>
            <n-icon :component="ArrowBackOutline" />
          </template>
        </n-button>
        <span class="title">{{ pageTitle }}</span>
      </div>
      <WindowControls />
    </div>

    <!-- 覆盖层主体：按 currentPage 动态渲染子页面 -->
    <div class="overlay-body">
      <HelpPage v-if="currentPage === 'help'" />
      <FilePreviewPage v-else-if="currentPage === 'file-preview'" />
      <ChatHistoryPage v-else-if="currentPage === 'chat-history'" />
    </div>
  </div>
</template>

<style scoped>
.overlay-host {
  position: absolute;
  inset: 0;
  z-index: 100;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
}

.overlay-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 0 4px;
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-panel);
  -webkit-app-region: drag;
}

.left {
  display: flex;
  align-items: center;
  gap: 8px;
  -webkit-app-region: no-drag;
}

.title {
  font-size: 16px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.overlay-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: var(--lx-bg-list, var(--lx-bg-panel));
  display: flex;
  justify-content: center;
}
</style>
