<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * Win32 无边框窗：透明区无法命中鼠标；自绘外圈热区 + IPC setBounds 作为兜底。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { startWindowEdgeResize, type WindowResizeEdge } from '../utils/windowEdgeResize'

const EDGE = 10

const { isLoggedIn } = storeToRefs(useAppStore())
const isElectron = !!window.electronAPI?.isElectron
const isWindows = window.electronAPI?.getPlatform?.() === 'windows'
const isMaximized = ref(false)

const show = computed(() => isElectron && isWindows && isLoggedIn.value && !isMaximized.value)

const edges: Array<{ edge: WindowResizeEdge; className: string }> = [
  { edge: 'n', className: 'lx-win-resize__edge--top' },
  { edge: 's', className: 'lx-win-resize__edge--bottom' },
  { edge: 'w', className: 'lx-win-resize__edge--left' },
  { edge: 'e', className: 'lx-win-resize__edge--right' },
  { edge: 'nw', className: 'lx-win-resize__edge--nw' },
  { edge: 'ne', className: 'lx-win-resize__edge--ne' },
  { edge: 'sw', className: 'lx-win-resize__edge--sw' },
  { edge: 'se', className: 'lx-win-resize__edge--se' }
]

let offMaximized: (() => void) | undefined

async function getBounds() {
  const bounds = await window.electronAPI?.getWindowBounds?.()
  if (!bounds) throw new Error('window bounds unavailable')
  return bounds
}

async function setBounds(bounds: { x: number; y: number; width: number; height: number }) {
  await window.electronAPI?.setWindowBounds?.(bounds)
}

function onEdgeMouseDown(edge: WindowResizeEdge, event: MouseEvent) {
  if (!show.value) return
  startWindowEdgeResize(event, edge, getBounds, setBounds)
}

onMounted(async () => {
  if (!isElectron) return
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
</script>

<template>
  <div v-if="show" class="lx-win-resize" aria-hidden="true" :style="{ '--lx-edge': `${EDGE}px` }">
    <span
      v-for="item in edges"
      :key="item.edge"
      :class="['lx-win-resize__edge', item.className]"
      @mousedown="onEdgeMouseDown(item.edge, $event)"
    />
  </div>
</template>

<style scoped>
.lx-win-resize {
  pointer-events: none;
}

.lx-win-resize__edge {
  position: fixed;
  z-index: 2147483647;
  -webkit-app-region: no-drag;
  background: transparent;
  pointer-events: auto;
  touch-action: none;
}

.lx-win-resize__edge--top {
  top: 0;
  left: 0;
  right: 0;
  height: var(--lx-edge, 10px);
  cursor: ns-resize;
}

.lx-win-resize__edge--bottom {
  bottom: 0;
  left: 0;
  right: 0;
  height: var(--lx-edge, 10px);
  cursor: ns-resize;
}

.lx-win-resize__edge--left {
  left: 0;
  top: 0;
  bottom: 0;
  width: var(--lx-edge, 10px);
  cursor: ew-resize;
}

.lx-win-resize__edge--right {
  right: 0;
  top: 0;
  bottom: 0;
  width: var(--lx-edge, 10px);
  cursor: ew-resize;
}

.lx-win-resize__edge--nw {
  top: 0;
  left: 0;
  width: var(--lx-edge, 10px);
  height: var(--lx-edge, 10px);
  cursor: nwse-resize;
}

.lx-win-resize__edge--ne {
  top: 0;
  right: 0;
  width: var(--lx-edge, 10px);
  height: var(--lx-edge, 10px);
  cursor: nesw-resize;
}

.lx-win-resize__edge--sw {
  bottom: 0;
  left: 0;
  width: var(--lx-edge, 10px);
  height: var(--lx-edge, 10px);
  cursor: nesw-resize;
}

.lx-win-resize__edge--se {
  bottom: 0;
  right: 0;
  width: var(--lx-edge, 10px);
  height: var(--lx-edge, 10px);
  cursor: nwse-resize;
}
</style>
