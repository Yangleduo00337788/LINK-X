<script setup lang="ts">
/**
 * Electron 窗口控制按钮。
 * default：最小化 / 最大化 / 关闭；login：菜单 / 关闭；close：仅关闭。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { NIcon } from 'naive-ui'
import {
  RemoveOutline,
  SquareOutline,
  CloseOutline,
  CopyOutline,
  MenuOutline
} from '@vicons/ionicons5'

const props = withDefaults(
  defineProps<{
    variant?: 'default' | 'login' | 'close'
  }>(),
  { variant: 'default' }
)

const emit = defineEmits<{
  menu: []
}>()

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
  if (props.variant === 'login') return
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

function onMenu(e: MouseEvent) {
  e.stopPropagation()
  e.preventDefault()
  emit('menu')
}
</script>

<template>
  <div
    class="lx-window-btns"
    :class="{ 'lx-window-btns--login': variant === 'login' || variant === 'close' }"
    @mousedown.stop
    @dblclick.stop
  >
    <template v-if="variant === 'login'">
      <button type="button" class="lx-win-btn menu" title="菜单" @click="onMenu">
        <n-icon class="ico" :component="MenuOutline" :size="16" />
      </button>
      <button type="button" class="lx-win-btn close" title="关闭" @click="onClick('close', $event)">
        <n-icon class="ico" :component="CloseOutline" :size="14" />
      </button>
    </template>
    <template v-else-if="variant === 'close'">
      <button type="button" class="lx-win-btn close" title="关闭" @click="onClick('close', $event)">
        <n-icon class="ico" :component="CloseOutline" :size="14" />
      </button>
    </template>
    <template v-else>
      <button type="button" class="lx-win-btn" title="最小化" @click="onClick('minimize', $event)">
        <n-icon class="ico" :component="RemoveOutline" :size="12" />
      </button>
      <button
        type="button"
        class="lx-win-btn"
        :title="maximizeTitle"
        @click="onClick('maximize', $event)"
      >
        <n-icon class="ico ico-max" :component="maximizeIcon" :size="11" />
      </button>
      <button type="button" class="lx-win-btn close" title="关闭" @click="onClick('close', $event)">
        <n-icon class="ico" :component="CloseOutline" :size="12" />
      </button>
    </template>
  </div>
</template>

<style scoped>
.lx-window-btns {
  display: flex;
  align-items: stretch;
  height: 40px;
  flex-shrink: 0;
  -webkit-app-region: no-drag;
  position: relative;
  z-index: 10000;
}

.lx-window-btns--login {
  height: 36px;
  align-items: center;
  gap: 2px;
  padding-right: 6px;
}

.lx-win-btn {
  width: 46px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-nav, #5c6370);
  cursor: pointer;
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  -webkit-app-region: no-drag;
  outline: none;
}

.lx-window-btns--login .lx-win-btn {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  color: #5c6370;
}

.lx-window-btns--login .lx-win-btn.menu {
  background: rgba(0, 0, 0, 0.04);
}

.lx-win-btn .ico {
  pointer-events: none;
}

.lx-win-btn .ico-max {
  transform: scale(0.95);
}

.lx-win-btn:hover {
  background: var(--lx-border-light, rgba(0, 0, 0, 0.06));
  color: var(--lx-text-body, #1f2329);
}

.lx-window-btns--login .lx-win-btn.menu:hover {
  background: rgba(0, 0, 0, 0.08);
}

.lx-win-btn.close:hover {
  background: #e81123;
  color: #fff;
}
</style>
