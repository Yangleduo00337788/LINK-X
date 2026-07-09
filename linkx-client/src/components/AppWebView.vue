<script setup lang="ts">
/**
 * 内嵌 Web 应用容器。
 * <p>
 * Electron 环境使用 webview 标签；浏览器环境降级为 iframe。
 * 加载失败时展示「在浏览器中打开」兜底页。
 * </p>
 */
import { ref, computed, watch } from 'vue'
import { useMediaPlaybackStore } from '../stores/mediaPlayback'
import type { AppItem } from '../types'

const props = defineProps<{
  url: string
  title?: string
  appKind?: AppItem['appKind']
  appId?: string
}>()

const mediaStore = useMediaPlaybackStore()
// 是否在 Electron 桌面环境
const isElectron = computed(() => !!window.electronAPI?.isElectron)
// webview/iframe 是否加载失败
const loadFailed = ref(false)

/**
 * 部分站点（如抖音）需自定义 User-Agent 才能正常嵌入。
 */
const userAgent = computed(() => {
  if (props.appKind === 'douyin') {
    return 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  }
  return undefined
})

/** webview 页面标题更新：同步到媒体播放条（如音乐应用） */
function onTitleUpdated(e: Event) {
  const title = (e as Event & { title?: string }).title ?? ''
  if (!props.appKind || !props.appId) return
  mediaStore.updateFromPageTitle(props.appKind, props.appId, title)
}

/** 内嵌页加载失败标记 */
function onWebviewFail() {
  loadFailed.value = true
}

/** 在新标签页打开原始 URL */
function openExternal() {
  window.open(props.url, '_blank', 'noopener')
}

// URL 变化时重置失败状态，允许重新加载
watch(
  () => props.url,
  () => {
    loadFailed.value = false
  }
)
</script>

<template>
  <!-- 应用 Web 容器：Electron webview / 浏览器 iframe / 失败兜底 -->
  <div class="app-webview-wrap">
    <webview
      v-if="isElectron && !loadFailed"
      :src="url"
      class="app-webview"
      allowpopups
      webpreferences="contextIsolation=yes, javascript=yes"
      :useragent="userAgent"
      @page-title-updated="onTitleUpdated"
      @did-fail-load="onWebviewFail"
    />
    <iframe
      v-else-if="!loadFailed"
      :src="url"
      :title="title || '应用'"
      class="app-webview"
      referrerpolicy="no-referrer-when-downgrade"
      @error="onWebviewFail"
    />
    <div v-else class="fallback">
      <p>无法在应用内加载「{{ title || '页面' }}」，可能被目标站点限制嵌入。</p>
      <button type="button" class="fallback-btn" @click="openExternal">在浏览器中打开</button>
    </div>
  </div>
</template>

<style scoped>
.app-webview-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  overflow: hidden;
}

.app-webview {
  flex: 1;
  width: 100%;
  border: none;
  background: var(--lx-bg-card);
}

.fallback {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
  text-align: center;
  color: var(--lx-text-secondary);
  font-size: 14px;
}

.fallback-btn {
  border: none;
  background: var(--lx-accent);
  color: var(--lx-text-on-accent);
  padding: 8px 16px;
  border-radius: var(--lx-radius);
  cursor: pointer;
  font-size: 13px;
}

.fallback-btn:hover {
  background: var(--lx-accent-hover);
}
</style>
