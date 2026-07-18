<script setup lang="ts">
// Vue 响应式 API、计算属性、生命周期与侦听器
import { ref, watch, computed, onMounted, onBeforeUnmount } from 'vue'
// Naive UI 模态框 + 图标 + 消息提示
import { NModal, NIcon, useMessage } from 'naive-ui'
// Ionicons5 设置相关图标
import {
  SettingsOutline,
  PersonOutline,
  ColorPaletteOutline,
  InformationCircleOutline,
  CloseOutline
} from '@vicons/ionicons5'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 设置弹窗 Store
import { useSettingsStore } from '../stores/settings'
// 偏好保存事件总线（统一在此订阅 toast，避免每个子页面重复订阅）
import { onPreferenceChange } from '../utils/preferenceEvents'

// 各设置子页面组件
import GeneralSettings from './settings/GeneralSettings.vue'
import AccountSettings from './settings/AccountSettings.vue'
import AppearanceSettings from './settings/AppearanceSettings.vue'
import AboutSettings from './settings/AboutSettings.vue'

// 设置 Store 实例
const settingsStore = useSettingsStore()
// 弹窗可见性与外部指定的激活标签
const { isSettingsModalVisible, settingsActiveTab } = storeToRefs(settingsStore)
// 关闭设置弹窗的方法
const { closeSettings } = settingsStore

// 当前激活的设置标签
const activeTab = ref('general')

// 根据激活标签计算右侧标题
const pageTitle = computed(() => {
  const map: Record<string, string> = {
    general: '通用设置',
    account: '账号与安全',
    appearance: '外观与显示',
    about: '关于 LinkX'
  }
  return map[activeTab.value] ?? '设置'
})

// 同步外部指定的标签到本地 activeTab
watch(settingsActiveTab, newVal => {
  if (newVal) activeTab.value = newVal
}, { immediate: true })

// ============================================================
// 统一 toast：订阅 store 的偏好保存事件总线
// - 任何设置子页面（通用 / 账号 / 外观）触发 scheduleSave 并成功后，
//   通过 onPreferenceChange 广播至此，由本组件统一翻译成中文 toast。
// - 避免每个子页面都各自订阅、避免重复弹两次。
// ============================================================
const message = useMessage()

/** 把本次保存的字段列表拼成中文 toast 文案 */
function summarize(fields: Record<string, unknown>, labels: Record<string, string>): string {
  const keys = Object.keys(fields)
  if (keys.length === 0) return '设置已更新'
  const names = keys.map(k => labels[k] || k)
  if (names.length === 1) return `${names[0]}已更新`
  if (names.length === 2) return `${names[0]}、${names[1]}已更新`
  return `${names.slice(0, -1).join('、')}与${names[names.length - 1]}已更新`
}

let unsubscribeToast: (() => void) | null = null

onMounted(() => {
  unsubscribeToast = onPreferenceChange.on(event => {
    if (event.kind === 'success') {
      message.success(summarize(event.fields as Record<string, unknown>, event.fieldLabel))
    } else {
      message.error('设置保存失败：' + event.message)
    }
  })
})

onBeforeUnmount(() => {
  if (unsubscribeToast) unsubscribeToast()
})

// 导航菜单项配置
const navItems = [
  { key: 'general', label: '通用设置', icon: SettingsOutline },
  { key: 'account', label: '账号与安全', icon: PersonOutline },
  { key: 'appearance', label: '外观与显示', icon: ColorPaletteOutline },
  { key: 'about', label: '关于 LinkX', icon: InformationCircleOutline }
] as const
</script>

<template>
  <!-- 设置弹窗：Naive UI Modal -->
  <n-modal
    v-model:show="isSettingsModalVisible"
    class="settings-modal"
    preset="card"
    to="body"
    :bordered="false"
    :show-icon="false"
    :closable="false"
    :z-index="10001"
    style="width: 840px; max-width: 94vw; border-radius: 14px; overflow: hidden; padding: 0;"
  >
    <div class="settings-shell">
      <!-- 左侧导航栏 -->
      <aside class="settings-nav">
        <div class="nav-brand">
          <img src="../assets/logo-linkx.svg" alt="" class="nav-logo" />
          <div class="nav-brand-text">
            <div class="nav-title">设置</div>
            <div class="nav-sub">LinkX 偏好选项</div>
          </div>
        </div>

        <nav class="nav-list" aria-label="设置导航">
          <button
            v-for="item in navItems"
            :key="item.key"
            type="button"
            class="nav-item"
            :class="{ 'is-active': activeTab === item.key }"
            @click="activeTab = item.key"
          >
            <n-icon :component="item.icon" :size="18" class="nav-item-ico" />
            <span class="nav-item-text">{{ item.label }}</span>
            <span class="nav-item-bar" aria-hidden="true"></span>
          </button>
        </nav>

        <div class="nav-footer">
          <span class="nav-footer-tip">LinkX · v1.0.0</span>
        </div>
      </aside>

      <!-- 右侧设置内容区 -->
      <main class="settings-main">
        <header class="main-head">
          <h2>{{ pageTitle }}</h2>
          <button type="button" class="close-btn" aria-label="关闭" @click="closeSettings">
            <n-icon :component="CloseOutline" :size="18" />
          </button>
        </header>

        <div class="main-body">
          <GeneralSettings v-show="activeTab === 'general'" />
          <AccountSettings v-show="activeTab === 'account'" />
          <AppearanceSettings v-show="activeTab === 'appearance'" />
          <AboutSettings v-show="activeTab === 'about'" />
        </div>
      </main>
    </div>
  </n-modal>
</template>

<style scoped>
/* ---- 弹窗容器 ---- */
.settings-modal :deep(.n-card) {
  background: var(--lx-bg-card) !important;
  color: var(--lx-text-body);
  border: 1px solid var(--lx-border-light) !important;
  box-shadow: var(--lx-shadow-modal) !important;
}

.settings-modal :deep(.n-card-header) {
  display: none;
}

.settings-modal :deep(.n-card__content) {
  padding: 0 !important;
  background: var(--lx-bg-card) !important;
}

.settings-shell {
  display: flex;
  /* 关键：固定高度 + 内容区可滚动，避免溢出 */
  height: 560px;
  max-height: calc(100vh - 80px);
  background: var(--lx-bg-card);
  overflow: hidden;
  border-radius: 14px;
  /* 让内部左侧 nav 的 panel 也有圆角左侧 */
}

/* ---- 左侧导航 ---- */
.settings-nav {
  width: 200px;
  flex-shrink: 0;
  /* 与弹窗外壳圆角（14px）保持一致：左侧两角继承，右侧直角 */
  border-radius: 14px 0 0 14px;
  background: var(--lx-bg-panel);
  border-right: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  padding: 20px 0 12px;
  overflow: hidden;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px 18px;
  border-bottom: 1px solid var(--lx-border-light);
  margin-bottom: 12px;
}

.nav-brand-text {
  min-width: 0;
  flex: 1;
}

.nav-logo {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
}

.nav-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-text-body);
  line-height: 1.2;
}

.nav-sub {
  font-size: 11px;
  color: var(--lx-text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 自定义导航列表（替换 naive-ui tabs） */
.nav-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 12px;
  overflow-y: auto;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 14px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: var(--lx-text-secondary);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background 0.18s, color 0.18s;
}

.nav-item:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.nav-item.is-active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-weight: 500;
}

.nav-item-ico {
  flex-shrink: 0;
}

.nav-item-text {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-item-bar {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 2px;
  background: var(--lx-accent);
  opacity: 0;
  transition: opacity 0.18s;
}

.nav-item.is-active .nav-item-bar {
  opacity: 1;
}

.nav-footer {
  padding: 12px 20px 0;
  border-top: 1px solid var(--lx-border-light);
  margin-top: 8px;
}

.nav-footer-tip {
  font-size: 11px;
  color: var(--lx-text-muted);
  letter-spacing: 0.04em;
}

/* ---- 右侧内容 ---- */
.settings-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  overflow: hidden;
  /* 右侧两个角也跟弹窗外壳对齐 */
  border-radius: 0 14px 14px 0;
}

.main-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 22px 32px 14px;
  border-bottom: 1px solid var(--lx-border-light);
  flex-shrink: 0;
}

/* 主体可滚动容器 */
.main-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  /* 让子组件的 .settings-scroll 不必再自带滚动 */
  display: flex;
  flex-direction: column;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: var(--lx-bg-panel);
  color: var(--lx-text-muted);
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.2s, color 0.2s;
}

.close-btn:hover {
  color: var(--lx-text-body);
  background: var(--lx-bg-hover);
}

.main-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--lx-text-body);
  letter-spacing: 0.02em;
}
</style>