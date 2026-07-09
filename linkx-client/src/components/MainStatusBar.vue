<script setup lang="ts">
/**
 * 顶部主状态栏组件。
 * <p>
 * 显示品牌标识、会话标题、窗口置顶按钮与 Electron 窗口控制，
 * 中间区域支持拖拽移动窗口。
 * </p>
 */
// Vue 响应式、计算属性与挂载钩子
import { ref, computed, onMounted } from 'vue'
// 置顶图标组件
import PinIcon from './icons/PinIcon.vue'
// Electron 窗口控制按钮
import WindowControls from './WindowControls.vue'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'

// 定义组件属性及默认值
withDefaults(
  defineProps<{
    variant?: 'profile' | 'chat' | 'module' // 状态栏变体类型
    title?: string // 左侧标题
    subtitle?: string // 左侧副标题
    showTheme?: boolean // 是否显示主题切换（预留）
  }>(),
  {
    variant: 'profile', // 默认 profile 变体
    title: '', // 默认空标题
    subtitle: '', // 默认空副标题
    showTheme: true // 默认显示主题
  }
)

// 获取应用 Store 实例
const appStore = useAppStore()
// 解构当前导航键与会话信息的响应式引用
const { navKey, currentSession } = storeToRefs(appStore)

// 计算中间标题栏显示的会话名称（仅在聊天导航且有当前会话时）
const centerTitle = computed(() => {
  if (navKey.value === 'chat' && currentSession.value) {
    return currentSession.value.name // 显示当前会话名称
  }
  return '' // 其他导航不显示中间标题
})

// 窗口是否置顶的状态
const isPinned = ref(false)

// 挂载时从 Electron API 读取窗口置顶状态
onMounted(async () => {
  if (window.electronAPI && window.electronAPI.isPinned) {
    isPinned.value = await window.electronAPI.isPinned() // 异步获取置顶状态
  }
})

// 切换窗口置顶状态
async function togglePin() {
  if (window.electronAPI && window.electronAPI.togglePin) {
    isPinned.value = await window.electronAPI.togglePin() // 调用 API 切换并更新本地状态
  }
}
</script>

<template>
  <!-- 顶部状态栏 -->
  <header class="main-status-bar">
    <!-- 左侧：品牌与标题 -->
    <div class="status-left">
      <div class="brand-block" title="LinkX">
        <svg class="brand-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="24" height="24" rx="6" fill="var(--lx-text)"/>
          <path d="M8 8L16 16M16 8L8 16" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span class="brand-text">LinkX</span>
      </div>

      <!-- chat 变体标题区 -->
      <template v-if="variant === 'chat'">
        <div class="profile-col">
          <span class="nickname single">{{ title || '请选择会话' }}</span>
          <span v-if="subtitle" class="signature-link static">{{ subtitle }}</span>
        </div>
      </template>

      <!-- 其他变体标题区 -->
      <template v-else>
        <div class="profile-col">
          <span class="nickname single">{{ title }}</span>
          <span v-if="subtitle" class="signature-link static">{{ subtitle }}</span>
        </div>
      </template>
    </div>

    <!-- 中间：可拖拽区域，显示当前会话标题 -->
    <div class="status-center title-bar-drag">
      <span v-if="centerTitle" class="session-title">{{ centerTitle }}</span>
    </div>

    <!-- 右侧：窗口置顶按钮 -->
    <div class="status-right">
      <button type="button" class="pin-btn" :class="{ active: isPinned }" title="置顶窗口" @click="togglePin">
        <PinIcon :size="16" />
      </button>
    </div>

    <!-- 窗口控制按钮（最小化/最大化/关闭） -->
    <div class="win-controls-slot">
      <WindowControls />
    </div>
  </header>
</template>

<style scoped>
.main-status-bar {
  flex-shrink: 0;
  height: 40px;
  min-height: 40px;
  display: flex;
  align-items: stretch;
  padding: 0 0 0 10px;
  background: transparent;
  border-bottom: none;
  position: relative;
  z-index: 50;
}

.win-controls-slot {
  flex-shrink: 0;
  position: relative;
  z-index: 10001;
  -webkit-app-region: no-drag;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 0;
  min-width: 0;
  flex-shrink: 0;
  height: 40px;
  -webkit-app-region: no-drag;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin-right: 33px;
  user-select: none;
}

.brand-icon {
  flex-shrink: 0;
}

.brand-text {
  font-size: 16px;
  font-weight: 700;
  color: var(--lx-text);
  letter-spacing: -0.01em;
  line-height: 1;
}

.profile-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
  min-width: 0;
  max-width: 200px;
  line-height: 1.2;
  padding: 2px 0;
}

.nickname {
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nickname.single {
  font-size: 14px;
  font-weight: 500;
}

.signature-link {
  border: none;
  background: none;
  padding: 0;
  margin: 0;
  font-size: 11px;
  color: #a8a8a8;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
  text-align: left;
  line-height: 1.3;
}

.signature-link:hover {
  color: var(--lx-accent);
}

.signature-link.static {
  cursor: default;
  pointer-events: none;
}

.status-center {
  flex: 1;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  -webkit-app-region: drag;
  cursor: default;
}

.session-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--lx-text-body);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  pointer-events: none;
  user-select: none;
}

.status-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding-right: 4px;
  -webkit-app-region: no-drag;
}

.pin-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8c8c8c;
  cursor: pointer;
  border: none;
  background: none;
  width: 28px;
  height: 28px;
  border-radius: var(--lx-radius);
  transition: all 0.2s;
}

.pin-btn:hover {
  color: var(--lx-text-secondary);
  background: var(--lx-bg-hover);
}

.pin-btn.active {
  color: var(--lx-accent);
  background: none;
}

.pin-btn.active:hover {
  color: var(--lx-accent);
  background: var(--lx-bg-hover);
}
</style>
