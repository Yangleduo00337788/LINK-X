<script setup lang="ts">
/**
 * 自绘 Win11 风格窗控。关闭键右上角圆角与窗口 --lx-window-radius 一致，
 * 悬停红底可被裁进 20px 圆角（系统 titleBarOverlay 做不到）。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from '../i18n'

withDefaults(
  defineProps<{
    /** 是否显示最大化（登录/注册等固定窗为 false） */
    showMaximize?: boolean
    /** 是否显示最小化 */
    showMinimize?: boolean
  }>(),
  {
    showMaximize: true,
    showMinimize: true
  }
)

const { t } = useI18n()
const isElectron = !!window.electronAPI?.isElectron
const showCustom = computed(
  () => isElectron && !!window.electronAPI?.showCustomCaptionButtons
)
const isMaximized = ref(false)
let offMaximized: (() => void) | undefined

onMounted(async () => {
  if (!showCustom.value) return
  try {
    isMaximized.value = !!(await window.electronAPI?.isMaximized?.())
  } catch {
    /* ignore */
  }
  offMaximized = window.electronAPI?.onMaximizedChange?.(v => {
    isMaximized.value = !!v
  })
})

onUnmounted(() => {
  offMaximized?.()
})

function minimize() {
  window.electronAPI?.minimize?.()
}
function maximize() {
  window.electronAPI?.maximize?.()
}
function close() {
  window.electronAPI?.close?.()
}
</script>

<template>
  <div v-if="showCustom" class="caption-btns" role="toolbar" aria-label="Window">
    <button
      v-if="showMinimize"
      type="button"
      class="caption-btn"
      :title="t('shell.minimize')"
      @click="minimize"
    >
      <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
        <path d="M1 5h8" stroke="currentColor" stroke-width="1.1" fill="none" />
      </svg>
    </button>
    <button
      v-if="showMaximize"
      type="button"
      class="caption-btn"
      :title="isMaximized ? t('shell.restore') : t('shell.maximize')"
      @click="maximize"
    >
      <svg v-if="!isMaximized" width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
        <rect x="1.2" y="1.2" width="7.6" height="7.6" rx="0.6" stroke="currentColor" stroke-width="1.1" fill="none" />
      </svg>
      <svg v-else width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
        <path
          d="M3 3.2h4.2V7.4H3zM2.2 4.5V2.2H6.5"
          stroke="currentColor"
          stroke-width="1.1"
          fill="none"
        />
      </svg>
    </button>
    <button type="button" class="caption-btn caption-btn--close" :title="t('shell.close')" @click="close">
      <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
        <path d="M2 2l6 6M8 2L2 8" stroke="currentColor" stroke-width="1.2" fill="none" stroke-linecap="round" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.caption-btns {
  display: flex;
  align-items: stretch;
  flex-shrink: 0;
  height: 100%;
  -webkit-app-region: no-drag;
}

.caption-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 100%;
  min-height: 36px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--lx-text);
  cursor: default;
  transition: background-color 83ms linear, color 83ms linear;
}

.caption-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.caption-btn:active {
  background: rgba(0, 0, 0, 0.04);
}

/* 关闭键：右上角与窗口圆角一致，红底不会穿出 20px 弧线 */
.caption-btn--close {
  border-top-right-radius: var(--lx-window-radius, 20px);
}

.caption-btn--close:hover {
  background: #e81123;
  color: #fff;
}

.caption-btn--close:active {
  background: #f1707a;
  color: #fff;
}

:global([data-theme='dark']) .caption-btn:hover {
  background: rgba(255, 255, 255, 0.06);
}

:global([data-theme='dark']) .caption-btn:active {
  background: rgba(255, 255, 255, 0.04);
}

:global(html.is-maximized) .caption-btn--close {
  border-top-right-radius: 0;
}
</style>
