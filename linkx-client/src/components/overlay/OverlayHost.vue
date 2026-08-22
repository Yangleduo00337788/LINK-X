<!-- 作者：yangleduo -->
<script setup lang="ts">
// Vue 计算属性与异步组件
import { computed, defineAsyncComponent } from 'vue'
// Naive UI 按钮与图标
import { NIcon } from 'naive-ui'
import { ArrowBackOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useOverlayStore } from '../../stores/overlay'
import type { OverlayPage } from '../../types'
import { useI18n } from '../../i18n'
import { LxIconButton } from '../ui'
import ModalOverlayCaption from '../ModalOverlayCaption.vue'

// 覆盖层 Store 实例
const overlayStore = useOverlayStore()
// 当前覆盖层页面
const { currentPage } = storeToRefs(overlayStore)
// 关闭覆盖层的方法
const { close } = overlayStore
const { t } = useI18n()

// 异步加载各覆盖层子页面（按需加载）
const FilePreviewPage = defineAsyncComponent(() => import('./pages/FilePreviewPage.vue'))

// 根据当前页面计算标题栏文案
const pageTitle = computed(() => {
  const p = currentPage.value
  if (!p) return ''
  const titleMap: Record<OverlayPage, string> = {
    'file-preview': t('overlay.filePreview')
  }
  return titleMap[p]
})
</script>

<template>
  <!-- 全屏覆盖层容器：有页面时显示 -->
  <div v-if="currentPage" class="overlay-host">
    <ModalOverlayCaption />
    <!-- 顶部导航栏：返回按钮、标题（窗控由系统原生提供） -->
    <div class="overlay-header">
      <div class="left">
        <LxIconButton :title="t('common.back')" @click="close">
          <n-icon :component="ArrowBackOutline" />
        </LxIconButton>
        <span class="title">{{ pageTitle }}</span>
      </div>
    </div>

    <!-- 覆盖层主体：按 currentPage 动态渲染子页面 -->
    <div class="overlay-body">
      <FilePreviewPage v-if="currentPage === 'file-preview'" />
    </div>
  </div>
</template>

<style scoped>
.overlay-host {
  position: absolute;
  inset: 0;
  z-index: var(--lx-z-header);
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
}

.overlay-header {
  height: env(titlebar-area-height, 60px);
  min-height: 48px;
  width: env(titlebar-area-width, 100%);
  margin-left: env(titlebar-area-x, 0);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--lx-space) 0 var(--lx-space-xs);
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-panel);
  -webkit-app-region: drag;
}

.left {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  -webkit-app-region: no-drag;
}

.title {
  font-size: var(--lx-font-xl);
  font-weight: 500;
  color: var(--lx-text-body);
}

.overlay-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-4xl);
  background: var(--lx-bg-list, var(--lx-bg-panel));
  display: flex;
  justify-content: center;
}
</style>
