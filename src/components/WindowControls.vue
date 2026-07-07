<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { NIcon } from 'naive-ui'
import { RemoveOutline, SquareOutline, CloseOutline, CopyOutline } from '@vicons/ionicons5'

const isMaximized = ref(false)
let offMaxListener: (() => void) | undefined

const maximizeIcon = computed(() => (isMaximized.value ? CopyOutline : SquareOutline))
const maximizeTitle = computed(() => (isMaximized.value ? '向下还原' : '最大化'))

async function syncMaximized() {
  const api = window.electronAPI
  if (!api?.isMaximized) return
  try {
    isMaximized.value = await api.isMaximized()
  } catch {
    /* ignore */
  }
}

onMounted(() => {
  const api = window.electronAPI
  if (!api) return
  void syncMaximized()
  offMaxListener = api.onMaximizedChange?.(maximized => {
    isMaximized.value = maximized
  })
})

onUnmounted(() => {
  offMaxListener?.()
})

function fire(action: 'minimize' | 'maximize' | 'close') {
  const api = window.electronAPI
  if (!api) {
    alert('窗口控制需要在 Electron 中运行，请执行：npm run electron:dev')
    return
  }
  void api[action]().then(() => {
    if (action === 'maximize') void syncMaximized()
  })
}

function onClick(action: 'minimize' | 'maximize' | 'close', e: MouseEvent) {
  e.stopPropagation()
  e.preventDefault()
  fire(action)
}
</script>

<template>
  <div class="qq-window-btns" @mousedown.stop @dblclick.stop>
    <button type="button" class="qq-win-btn" title="最小化" @click="onClick('minimize', $event)">
      <n-icon class="ico" :component="RemoveOutline" :size="12" />
    </button>
    <button
      type="button"
      class="qq-win-btn"
      :title="maximizeTitle"
      @click="onClick('maximize', $event)"
    >
      <n-icon class="ico ico-max" :component="maximizeIcon" :size="11" />
    </button>
    <button type="button" class="qq-win-btn close" title="关闭" @click="onClick('close', $event)">
      <n-icon class="ico" :component="CloseOutline" :size="12" />
    </button>
  </div>
</template>

<style scoped>
.qq-window-btns {
  display: flex;
  align-items: stretch;
  height: 40px;
  flex-shrink: 0;
  -webkit-app-region: no-drag;
  position: relative;
  z-index: 10000;
}

.qq-win-btn {
  width: 46px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5c5c5c;
  cursor: pointer;
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  -webkit-app-region: no-drag;
  outline: none;
}

.qq-win-btn .ico {
  pointer-events: none;
}

.qq-win-btn .ico-max {
  transform: scale(0.95);
}

.qq-win-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #333;
}

.qq-win-btn.close:hover {
  background: #e81123;
  color: #fff;
}
</style>