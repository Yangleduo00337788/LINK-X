<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 自绘 Win11 风格窗控。悬停为居中圆角块；关闭键 hover 红底白字。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from '../i18n'
import WindowPinButton from './WindowPinButton.vue'

const props = withDefaults(
  defineProps<{
    /** 是否显示最大化（登录/注册等固定窗为 false） */
    showMaximize?: boolean
    /** 是否显示最小化 */
    showMinimize?: boolean
    /** 是否显示窗口置顶（与 minimize 同列） */
    showPin?: boolean
    /** Win32 无边框登录窗等场景强制显示自绘窗控 */
    forceShow?: boolean
    /** 关闭前回调（如等待笔记保存完成） */
    beforeClose?: () => void | Promise<void>
  }>(),
  {
    showMaximize: true,
    showMinimize: true,
    showPin: false,
    forceShow: false
  }
)

const { t } = useI18n()
const isElectron = !!window.electronAPI?.isElectron
const showCustom = computed(
  () =>
    isElectron &&
    (!!window.electronAPI?.showCustomCaptionButtons || props.forceShow)
)
const showToolbar = computed(() => showCustom.value || (isElectron && props.showPin))
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
async function close() {
  try {
    await props.beforeClose?.()
  } catch {
    /* ignore */
  }
  window.electronAPI?.close?.()
}
</script>

<template>
  <div v-if="showToolbar" class="caption-btns" role="toolbar" aria-label="Window">
    <WindowPinButton v-if="showPin" />
    <button
      v-if="showCustom && showMinimize"
      type="button"
      class="lx-win-caption-btn"
      :title="t('shell.minimize')"
      @click="minimize"
    >
      <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
        <path d="M1 5h8" stroke="currentColor" stroke-width="1.1" fill="none" />
      </svg>
    </button>
    <button
      v-if="showCustom && showMaximize"
      type="button"
      class="lx-win-caption-btn"
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
    <button
      v-if="showCustom"
      type="button"
      class="lx-win-caption-btn lx-win-caption-btn--close"
      :title="t('shell.close')"
      @click="close"
    >
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
}
</style>
