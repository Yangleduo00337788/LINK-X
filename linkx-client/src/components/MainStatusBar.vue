<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 顶部主状态栏组件。
 * <p>
 * 显示会话标题、窗口置顶；最小化/最大化/关闭为自绘 Win11 风格窗控，
 * 关闭键右上角圆角与窗口 --lx-window-radius 一致。
 * </p>
 */
import { computed } from 'vue'
import WindowCaptionButtons from './WindowCaptionButtons.vue'
import { storeToRefs } from 'pinia'
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

// 计算中间标题栏：聊天主区已有顶栏时不重复显示会话名
const centerTitle = computed(() => {
  if (navKey.value !== 'chat' || !currentSession.value) return ''
  const s = currentSession.value
  if (s.isSystemNotify || s.isOfficialNotify) return ''
  return ''
})

const selectSessionHint = computed(() => t('chat.selectSession'))
</script>

<template>
  <!-- 顶部状态栏 -->
  <header class="main-status-bar">
    <!-- 左侧：可拖拽留白与标题 -->
    <div class="status-left title-bar-drag">
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

    <!-- 右侧：置顶 + 自绘窗控（关闭键圆角与窗口一致） -->
    <div class="status-right">
      <WindowCaptionButtons show-pin />
    </div>
  </header>
</template>

<style scoped>
.main-status-bar {
  flex-shrink: 0;
  height: 40px;
  min-height: 40px;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  align-items: stretch;
  padding: 0 0 0 var(--lx-space-md);
  padding-right: calc(100% - env(titlebar-area-width, 100%));
  background: var(--lx-bg-window);
  border-bottom: none;
  position: relative;
  z-index: var(--lx-z-fab);
}

.status-left {
  display: flex;
  align-items: center;
  gap: 0;
  min-width: 12px;
  flex-shrink: 0;
  height: 40px;
  -webkit-app-region: drag;
}

.profile-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
  min-width: 0;
  max-width: 200px;
  line-height: var(--lx-leading-tight);
  padding: var(--lx-space-2xs) 0;
}

.nickname {
  font-size: var(--lx-font-md);
  font-weight: 600;
  color: var(--lx-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nickname.single {
  font-size: var(--lx-font);
  font-weight: 500;
}

.signature-link {
  border: none;
  background: none;
  padding: 0;
  margin: 0;
  font-size: var(--lx-font-xs);
  color: var(--lx-status-muted);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
  text-align: left;
  line-height: var(--lx-leading-snug);
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
  padding: 0 var(--lx-space-lg);
  margin-top: 6px;
  height: calc(100% - 6px);
  -webkit-app-region: drag;
  cursor: default;
}

.session-title {
  font-size: var(--lx-font-md);
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
</style>
