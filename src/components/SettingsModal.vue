<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import {
  NModal,
  NTabs,
  NTabPane,
  NSwitch,
  NButton,
  NAvatar,
  NIcon,
  useMessage
} from 'naive-ui'
import {
  SettingsOutline,
  PersonOutline,
  ColorPaletteOutline,
  FlaskOutline,
  InformationCircleOutline,
  CheckmarkCircle,
  CloseOutline,
  MoonOutline,
  SunnyOutline,
  NotificationsOutline,
  ShieldCheckmarkOutline,
  DesktopOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../stores/appSettings'
import { useSettingsStore } from '../stores/settings'
import { useAppStore } from '../stores/app'
import type { ChatBackgroundId } from '../types'

const settingsStore = useSettingsStore()
const appSettingsStore = useAppSettingsStore()
const appStore = useAppStore()
const { isSettingsModalVisible, settingsActiveTab } = storeToRefs(settingsStore)
const { userProfile, theme, isOffline } = storeToRefs(appStore)
const {
  autoStart,
  soundNotify,
  messageDetail,
  notifyAtMe,
  notifySound,
  privacyVerifyFriend,
  privacyAllowStranger,
  privacyShowOnline,
  chatBackground
} = storeToRefs(appSettingsStore)
const { toggleTheme, toggleOffline, simulateIncomingMessage } = appStore
const { setChatBackground } = appSettingsStore
const { closeSettings } = settingsStore

const activeTab = ref('general')
const message = useMessage()

const chatBackgrounds: { id: ChatBackgroundId; label: string; style: string }[] = [
  { id: 'default', label: '默认纯色', style: 'linear-gradient(180deg, #f5f6f7 0%, #eceff1 100%)' },
  { id: 'purple', label: '梦幻紫', style: 'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)' },
  { id: 'orange', label: '落日橘', style: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)' }
]

const pageTitle = computed(() => {
  const map: Record<string, string> = {
    general: '通用设置',
    account: '账号与安全',
    appearance: '外观与显示',
    prototype: '原型演示工具',
    about: '关于 LinkX'
  }
  return map[activeTab.value] ?? '设置'
})

function pickChatBackground(id: ChatBackgroundId) {
  setChatBackground(id)
  message.success('聊天背景已更新')
}

function checkUpdate() {
  message.success('当前已是最新版本 1.0.0')
}

function exportLogs() {
  console.info('[LinkX] 系统日志导出', { theme: theme.value, chatBackground: chatBackground.value })
  message.success('日志已输出到开发者控制台')
}

function changePassword() {
  message.info('修改密码需对接后端账号服务')
}

function manageDevices() {
  message.info('当前设备：本机 · Windows（本地演示）')
}

watch(settingsActiveTab, newVal => {
  if (newVal) activeTab.value = newVal
}, { immediate: true })

function applyAutoStart() {
  window.electronAPI?.setAutoStart?.(autoStart.value)
}
</script>

<template>
  <n-modal
    v-model:show="isSettingsModalVisible"
    class="settings-modal"
    preset="card"
    :bordered="false"
    :show-icon="false"
    :closable="false"
    style="width: 760px; max-width: 94vw; border-radius: 12px; overflow: hidden; padding: 0;"
  >
    <div class="settings-shell">
      <aside class="settings-nav">
        <div class="nav-brand">
          <img src="../assets/logo-linkx.svg" alt="" class="nav-logo" />
          <div>
            <div class="nav-title">设置</div>
            <div class="nav-sub">LinkX 偏好选项</div>
          </div>
        </div>

        <n-tabs
          v-model:value="activeTab"
          placement="left"
          type="bar"
          class="nav-tabs"
          :bar-width="3"
        >
          <n-tab-pane name="general">
            <template #tab>
              <span class="nav-tab">
                <n-icon :component="SettingsOutline" :size="18" />
                <span>通用</span>
              </span>
            </template>
          </n-tab-pane>
          <n-tab-pane name="account">
            <template #tab>
              <span class="nav-tab">
                <n-icon :component="PersonOutline" :size="18" />
                <span>账号</span>
              </span>
            </template>
          </n-tab-pane>
          <n-tab-pane name="appearance">
            <template #tab>
              <span class="nav-tab">
                <n-icon :component="ColorPaletteOutline" :size="18" />
                <span>外观</span>
              </span>
            </template>
          </n-tab-pane>
          <n-tab-pane name="prototype">
            <template #tab>
              <span class="nav-tab">
                <n-icon :component="FlaskOutline" :size="18" />
                <span>演示</span>
              </span>
            </template>
          </n-tab-pane>
          <n-tab-pane name="about">
            <template #tab>
              <span class="nav-tab">
                <n-icon :component="InformationCircleOutline" :size="18" />
                <span>关于</span>
              </span>
            </template>
          </n-tab-pane>
        </n-tabs>
      </aside>

      <main class="settings-main">
        <header class="main-head">
          <h2>{{ pageTitle }}</h2>
          <button type="button" class="close-btn" aria-label="关闭" @click="closeSettings">
            <n-icon :component="CloseOutline" :size="18" />
          </button>
        </header>

        <div v-show="activeTab === 'general'" class="settings-scroll">
          <section class="group-card">
            <div class="group-head">
              <n-icon :component="DesktopOutline" :size="18" class="group-ico" />
              <span>系统</span>
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">开机自动启动</span>
                <span class="setting-desc">登录 Windows 后自动打开 LinkX</span>
              </div>
              <n-switch v-model:value="autoStart" size="small" @update:value="applyAutoStart" />
            </div>
          </section>

          <section class="group-card">
            <div class="group-head">
              <n-icon :component="NotificationsOutline" :size="18" class="group-ico" />
              <span>消息与通知</span>
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">新消息声音提示</span>
              </div>
              <n-switch v-model:value="soundNotify" size="small" />
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">通知显示消息详情</span>
              </div>
              <n-switch v-model:value="messageDetail" size="small" />
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">群聊 @ 我</span>
              </div>
              <n-switch v-model:value="notifyAtMe" size="small" />
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">通知声音</span>
              </div>
              <n-switch v-model:value="notifySound" size="small" />
            </div>
          </section>
        </div>

        <div v-show="activeTab === 'account'" class="settings-scroll">
          <section class="profile-card">
            <n-avatar
              round
              :size="72"
              src="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user"
              class="profile-avatar"
            />
            <div class="profile-meta">
              <div class="profile-name">{{ userProfile.nickname }}</div>
              <div class="profile-id">LinkX ID · linkx_888888</div>
              <div class="profile-badge">已登录</div>
            </div>
          </section>

          <section class="group-card">
            <div class="group-head">
              <n-icon :component="ShieldCheckmarkOutline" :size="18" class="group-ico" />
              <span>安全设置</span>
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">登录密码</span>
                <span class="setting-desc">定期更换密码保护账号安全</span>
              </div>
              <n-button size="small" secondary @click="changePassword">修改</n-button>
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">设备管理</span>
                <span class="setting-desc">查看已登录的设备与会话</span>
              </div>
              <n-button size="small" secondary @click="manageDevices">查看</n-button>
            </div>
          </section>

          <section class="group-card">
            <div class="group-head">
              <span>隐私</span>
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">加好友需验证</span>
              </div>
              <n-switch v-model:value="privacyVerifyFriend" size="small" />
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">允许陌生人会话</span>
              </div>
              <n-switch v-model:value="privacyAllowStranger" size="small" />
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">在线状态可见</span>
              </div>
              <n-switch v-model:value="privacyShowOnline" size="small" />
            </div>
          </section>
        </div>

        <div v-show="activeTab === 'appearance'" class="settings-scroll">
          <section class="group-card">
            <div class="group-head">
              <n-icon :component="theme === 'dark' ? MoonOutline : SunnyOutline" :size="18" class="group-ico" />
              <span>主题</span>
            </div>
            <div class="theme-mode-row">
              <button
                type="button"
                class="theme-mode"
                :class="{ active: theme === 'light' }"
                @click="theme !== 'light' && toggleTheme()"
              >
                <n-icon :component="SunnyOutline" :size="22" />
                <span>浅色</span>
              </button>
              <button
                type="button"
                class="theme-mode"
                :class="{ active: theme === 'dark' }"
                @click="theme !== 'dark' && toggleTheme()"
              >
                <n-icon :component="MoonOutline" :size="22" />
                <span>深色</span>
              </button>
            </div>
          </section>

          <section class="group-card">
            <div class="group-head">
              <span>聊天背景</span>
            </div>
            <p class="group-tip">选择会话区域的背景样式</p>
            <div class="bg-grid">
              <button
                v-for="bg in chatBackgrounds"
                :key="bg.id"
                type="button"
                class="bg-tile"
                :class="{ active: chatBackground === bg.id }"
                @click="pickChatBackground(bg.id)"
              >
                <div class="bg-preview" :style="{ background: bg.style }">
                  <div class="bg-mock-bubble left" />
                  <div class="bg-mock-bubble right" />
                </div>
                <span class="bg-label">{{ bg.label }}</span>
                <n-icon
                  v-if="chatBackground === bg.id"
                  :component="CheckmarkCircle"
                  :size="18"
                  class="bg-check"
                />
              </button>
            </div>
          </section>
        </div>

        <div v-show="activeTab === 'prototype'" class="settings-scroll">
          <section class="group-card group-card--warn">
            <div class="group-head">
              <n-icon :component="FlaskOutline" :size="18" class="group-ico" />
              <span>边缘状态触发</span>
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">模拟网络断开</span>
                <span class="setting-desc">开启后将在聊天列表顶部显示断网提示</span>
              </div>
              <n-switch :value="isOffline" size="small" @update:value="toggleOffline" />
            </div>
          </section>

          <section class="group-card">
            <div class="group-head">
              <span>系统通知</span>
            </div>
            <div class="setting-row">
              <div class="setting-text">
                <span class="setting-name">模拟接收新消息</span>
                <span class="setting-desc">触发桌面通知与未读角标（Electron）</span>
              </div>
              <n-button size="small" type="primary" @click="simulateIncomingMessage">
                触发
              </n-button>
            </div>
          </section>
        </div>

        <div v-show="activeTab === 'about'" class="settings-scroll about-scroll">
          <section class="about-card">
            <div class="about-glow" />
            <img src="../assets/logo-linkx.svg" alt="LinkX" class="about-logo" />
            <h3 class="about-name">LinkX</h3>
            <p class="about-ver">Version 1.0.0 · Beta</p>
            <p class="about-desc">极简、高效、安全的企业级即时通讯工具</p>
            <div class="about-actions">
              <n-button type="primary" @click="checkUpdate">检查更新</n-button>
              <n-button secondary @click="exportLogs">系统日志</n-button>
            </div>
            <p class="about-copy">© 2026 LinkX Team</p>
          </section>
        </div>
      </main>
    </div>
  </n-modal>
</template>

<style scoped>
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
  height: 520px;
  background: var(--lx-bg-card);
}

/* ---- 左侧导航 ---- */
.settings-nav {
  width: 168px;
  flex-shrink: 0;
  background: var(--lx-bg-panel);
  border-right: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  padding: 20px 0 16px;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px 20px;
  border-bottom: 1px solid var(--lx-border-light);
  margin-bottom: 8px;
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
}

.nav-tabs {
  flex: 1;
}

.nav-tabs :deep(.n-tabs-nav) {
  width: 100% !important;
  background: transparent !important;
}

.nav-tabs :deep(.n-tabs-tab) {
  justify-content: flex-start !important;
  padding: 10px 16px !important;
  margin: 2px 8px !important;
  border-radius: 8px !important;
  color: var(--lx-text-secondary) !important;
  transition: background 0.18s, color 0.18s;
}

.nav-tabs :deep(.n-tabs-tab:hover) {
  background: var(--lx-bg-hover) !important;
  color: var(--lx-text-body) !important;
}

.nav-tabs :deep(.n-tabs-tab--active) {
  background: var(--lx-accent-soft) !important;
  color: var(--lx-accent) !important;
  font-weight: 500;
}

.nav-tabs :deep(.n-tabs-tab-pad),
.nav-tabs :deep(.n-tabs-pane-wrapper) {
  display: none !important;
}

.nav-tabs :deep(.n-tabs-bar) {
  background: var(--lx-accent) !important;
  border-radius: 2px;
}

.nav-tab {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

/* ---- 右侧内容 ---- */
.settings-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
}

.main-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 22px 28px 12px;
  border-bottom: 1px solid var(--lx-border-light);
  flex-shrink: 0;
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

.settings-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 20px 28px 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ---- 分组卡片 ---- */
.group-card {
  background: var(--lx-bg-panel);
  border: 1px solid var(--lx-border-light);
  border-radius: 10px;
  padding: 4px 0;
  overflow: hidden;
}

.group-card--warn {
  border-color: rgba(250, 173, 20, 0.25);
}

.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--lx-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.group-ico {
  color: var(--lx-accent);
}

.group-tip {
  margin: 0 16px 12px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border-top: 1px solid var(--lx-border-light);
  transition: background 0.15s;
}

.setting-row:first-of-type {
  border-top: none;
}

.setting-row:hover {
  background: var(--lx-bg-hover);
}

.setting-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.setting-name {
  font-size: 14px;
  color: var(--lx-text-body);
  font-weight: 500;
}

.setting-desc {
  font-size: 12px;
  color: var(--lx-text-muted);
  line-height: 1.4;
}

/* ---- 账号卡片 ---- */
.profile-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--lx-accent-soft) 0%, var(--lx-bg-panel) 60%);
  border: 1px solid var(--lx-border-light);
}

.profile-avatar {
  box-shadow: 0 4px 12px var(--lx-shadow-color);
  border: 2px solid var(--lx-bg-card);
}

.profile-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.profile-id {
  font-size: 13px;
  color: var(--lx-text-secondary);
  margin-top: 4px;
}

.profile-badge {
  display: inline-block;
  margin-top: 8px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-weight: 500;
}

/* ---- 主题切换 ---- */
.theme-mode-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 8px 16px 16px;
}

.theme-mode {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border: 2px solid var(--lx-border-light);
  border-radius: 10px;
  background: var(--lx-bg-card);
  color: var(--lx-text-secondary);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, box-shadow 0.2s;
}

.theme-mode:hover {
  border-color: var(--lx-accent);
  color: var(--lx-text-body);
}

.theme-mode.active {
  border-color: var(--lx-accent);
  color: var(--lx-accent);
  box-shadow: 0 0 0 3px var(--lx-accent-soft);
}

.theme-mode span {
  font-size: 13px;
  font-weight: 500;
}

/* ---- 聊天背景 ---- */
.bg-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 0 16px 16px;
}

.bg-tile {
  position: relative;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  text-align: center;
}

.bg-preview {
  height: 88px;
  border-radius: 8px;
  border: 2px solid var(--lx-border-light);
  overflow: hidden;
  position: relative;
  transition: border-color 0.2s, transform 0.2s;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 12px;
}

.bg-tile:hover .bg-preview {
  transform: translateY(-2px);
  border-color: var(--lx-accent);
}

.bg-tile.active .bg-preview {
  border-color: var(--lx-accent);
  box-shadow: 0 0 0 3px var(--lx-accent-soft);
}

.bg-mock-bubble {
  height: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.75);
}

.bg-mock-bubble.left {
  width: 55%;
  align-self: flex-start;
}

.bg-mock-bubble.right {
  width: 40%;
  align-self: flex-end;
  background: rgba(18, 183, 245, 0.35);
}

.bg-label {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.bg-check {
  position: absolute;
  top: 6px;
  right: 6px;
  color: var(--lx-accent);
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.2));
}

/* ---- 关于 ---- */
.about-scroll {
  justify-content: center;
}

.about-card {
  position: relative;
  text-align: center;
  padding: 36px 24px 28px;
  border-radius: 12px;
  background: var(--lx-bg-panel);
  border: 1px solid var(--lx-border-light);
  overflow: hidden;
}

.about-glow {
  position: absolute;
  top: -40px;
  left: 50%;
  transform: translateX(-50%);
  width: 200px;
  height: 120px;
  background: radial-gradient(ellipse, var(--lx-accent-soft) 0%, transparent 70%);
  pointer-events: none;
}

.about-logo {
  width: 72px;
  height: 72px;
  position: relative;
  z-index: 1;
}

.about-name {
  margin: 16px 0 4px;
  font-size: 22px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.about-ver {
  margin: 0;
  font-size: 13px;
  color: var(--lx-text-muted);
}

.about-desc {
  margin: 12px 0 24px;
  font-size: 14px;
  color: var(--lx-text-secondary);
  line-height: 1.5;
}

.about-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 20px;
}

.about-copy {
  margin: 0;
  font-size: 11px;
  color: var(--lx-text-muted);
}
</style>
