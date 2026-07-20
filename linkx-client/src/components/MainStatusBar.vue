<script setup lang="ts">
/**
 * 顶部主状态栏组件。
 * <p>
 * 显示品牌标识、会话标题、窗口置顶；最小化/最大化/关闭由 Windows 原生 titleBarOverlay 提供。
 * 置顶使用统一垂直 PinIcon，贴在原生窗控左侧。
 * 中间区域支持拖拽移动窗口。
 * </p>
 */
// Vue 响应式、计算属性与挂载钩子
import { ref, computed, onMounted } from 'vue'
// 统一垂直图钉图标
import PinIcon from './icons/PinIcon.vue'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
import { useI18n } from '../i18n'

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
const { t } = useI18n()
// 解构当前导航键与会话信息的响应式引用
const { navKey, currentSession } = storeToRefs(appStore)

// 窗口是否置顶的状态
const isPinned = ref(false)

// 计算中间标题栏显示的会话名称（仅在聊天导航且有当前会话时）
const centerTitle = computed(() => {
  if (navKey.value === 'chat' && currentSession.value) {
    return currentSession.value.name // 显示当前会话名称
  }
  return '' // 其他导航不显示中间标题
})

const pinTitle = computed(() => (isPinned.value ? t('shell.unpin') : t('shell.pin')))
const selectSessionHint = computed(() => t('chat.selectSession'))

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
          <span class="nickname single">{{ title || selectSessionHint }}</span>
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

    <!-- 右侧：置顶（统一垂直 PinIcon）；最小化/最大化/关闭由 titleBarOverlay 提供 -->
    <div class="status-right">
      <button
        type="button"
        class="win-caption-btn"
        :class="{ active: isPinned }"
        :title="pinTitle"
        :aria-pressed="isPinned"
        @click="togglePin"
      >
        <PinIcon :size="14" />
      </button>
    </div>
  </header>
</template>

<style scoped>
.main-status-bar {
  flex-shrink: 0;
  height: env(titlebar-area-height, 40px);
  min-height: env(titlebar-area-height, 40px);
  width: env(titlebar-area-width, 100%);
  margin-left: env(titlebar-area-x, 0px);
  box-sizing: border-box;
  display: flex;
  align-items: stretch;
  padding: 0 0 0 10px;
  background: transparent;
  border-bottom: none;
  position: relative;
  z-index: 50;
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
  align-items: stretch;
  flex-shrink: 0;
  height: 100%;
  padding: 0;
  -webkit-app-region: no-drag;
}

/* 对齐 Win11 Caption Buttons：46×标题栏高、直角、同色悬停 */
.win-caption-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 100%;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--lx-text);
  cursor: default;
  transition: background-color 83ms linear;
}

.win-caption-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.win-caption-btn:active {
  background: rgba(0, 0, 0, 0.04);
}

.win-caption-btn.active {
  color: var(--lx-accent);
}

.win-caption-btn.active:hover {
  background: rgba(0, 0, 0, 0.06);
}

:global([data-theme='dark']) .win-caption-btn:hover,
:global([data-theme='dark']) .win-caption-btn.active:hover {
  background: rgba(255, 255, 255, 0.06);
}

:global([data-theme='dark']) .win-caption-btn:active {
  background: rgba(255, 255, 255, 0.04);
}
</style>
