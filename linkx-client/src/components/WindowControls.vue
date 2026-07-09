<script setup lang="ts">
/**
 * Electron 窗口控制按钮组件。
 * <p>
 * 提供最小化、最大化/还原、关闭三个窗口操作按钮，
 * 仅在 Electron 环境下通过 preload 暴露的 API 控制原生窗口。
 * </p>
 */
// Vue 响应式、计算属性与生命周期钩子
import { ref, computed, onMounted, onUnmounted } from 'vue'
// Naive UI 图标组件
import { NIcon } from 'naive-ui'
// Ionicons5 窗口控制图标：最小化、最大化、关闭、还原
import { RemoveOutline, SquareOutline, CloseOutline, CopyOutline } from '@vicons/ionicons5'

// 窗口是否处于最大化状态的响应式标志
const isMaximized = ref(false)
// 最大化状态变化监听器的取消函数
let offMaxListener: (() => void) | undefined

// 根据最大化状态切换图标：已最大化显示还原图标，否则显示最大化图标
const maximizeIcon = computed(() => (isMaximized.value ? CopyOutline : SquareOutline))
// 根据最大化状态切换按钮提示文字
const maximizeTitle = computed(() => (isMaximized.value ? '向下还原' : '最大化'))

// 从 Electron API 同步当前窗口最大化状态
async function syncMaximized() {
  const api = window.electronAPI // 获取 preload 暴露的 Electron API
  if (!api?.isMaximized) return // 非 Electron 环境或无此 API 则跳过
  try {
    isMaximized.value = await api.isMaximized() // 异步查询窗口是否最大化
  } catch {
    /* ignore */ // 查询失败时静默忽略
  }
}

// 组件挂载时初始化最大化状态并注册变化监听
onMounted(() => {
  const api = window.electronAPI
  if (!api) return // 非 Electron 环境直接返回
  void syncMaximized() // 同步初始最大化状态
  // 注册最大化状态变化回调，返回取消函数
  offMaxListener = api.onMaximizedChange?.(maximized => {
    isMaximized.value = maximized // 窗口最大化状态变化时更新本地标志
  })
})

// 组件卸载时移除最大化状态监听
onUnmounted(() => {
  offMaxListener?.() // 调用取消函数清理监听器
})

// 执行窗口控制操作：最小化、最大化或关闭
function fire(action: 'minimize' | 'maximize' | 'close') {
  const api = window.electronAPI
  if (!api) {
    // 浏览器环境下提示用户需在 Electron 中运行
    alert('窗口控制需要在 Electron 中运行，请执行：npm run electron:dev')
    return
  }
  // 调用对应的 Electron API 方法
  void api[action]().then(() => {
    if (action === 'maximize') void syncMaximized() // 最大化操作后重新同步状态
  })
}

// 按钮点击处理：阻止事件冒泡后执行窗口操作
function onClick(action: 'minimize' | 'maximize' | 'close', e: MouseEvent) {
  e.stopPropagation() // 阻止冒泡，避免触发标题栏拖拽
  e.preventDefault() // 阻止默认行为
  fire(action) // 执行窗口控制
}
</script>

<template>
  <!-- 窗口控制按钮组 -->
  <div class="qq-window-btns" @mousedown.stop @dblclick.stop>
    <!-- 最小化按钮 -->
    <button type="button" class="qq-win-btn" title="最小化" @click="onClick('minimize', $event)">
      <n-icon class="ico" :component="RemoveOutline" :size="12" />
    </button>
    <!-- 最大化/还原按钮 -->
    <button
      type="button"
      class="qq-win-btn"
      :title="maximizeTitle"
      @click="onClick('maximize', $event)"
    >
      <n-icon class="ico ico-max" :component="maximizeIcon" :size="11" />
    </button>
    <!-- 关闭按钮 -->
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
  color: var(--lx-text-nav);
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
  background: var(--lx-border-light);
  color: var(--lx-text-body);
}

.qq-win-btn.close:hover {
  background: #e81123;
  color: var(--lx-bg-card);
}
</style>
