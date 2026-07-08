<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  NModal,
  NTabs,
  NTabPane,
  NSwitch,
  NButton,
  NAvatar,
  NDivider,
  NIcon,
  useMessage
} from 'naive-ui'
import { CheckmarkCircleOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../stores/appSettings'
import { useSettingsStore } from '../stores/settings'
import { useAppStore } from '../stores/app'

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

const activeTab = ref('general')
const message = useMessage()

function pickChatBackground(id: 'default' | 'purple' | 'orange') {
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

watch(settingsActiveTab, (newVal) => {
  if (newVal) {
    activeTab.value = newVal
  }
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
    title="设置"
    style="width: 720px; border-radius: var(--lx-radius); overflow: hidden; padding: 0;"
  >
    <div class="settings-container">
      <n-tabs
        v-model:value="activeTab"
        placement="left"
        type="line"
        animated
        class="settings-tabs"
      >
        <n-tab-pane name="general">
          <template #tab>
            <div class="tab-label">
              <span>通用设置</span>
            </div>
          </template>
          <div class="settings-content">
            <h3 class="section-title">系统</h3>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">开机自动启动</span>
              </div>
              <n-switch v-model:value="autoStart" size="small" @update:value="applyAutoStart" />
            </div>

            <n-divider style="margin: 8px 0" />

            <h3 class="section-title">消息与通知</h3>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">新消息声音提示</span>
              </div>
              <n-switch v-model:value="soundNotify" size="small" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">通知显示消息详情</span>
              </div>
              <n-switch v-model:value="messageDetail" size="small" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">群聊 @ 我</span>
              </div>
              <n-switch v-model:value="notifyAtMe" size="small" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">通知声音</span>
              </div>
              <n-switch v-model:value="notifySound" size="small" />
            </div>
          </div>
        </n-tab-pane>

        <n-tab-pane name="account">
          <template #tab>
            <div class="tab-label">
              <span>账号与安全</span>
            </div>
          </template>
          <div class="settings-content">
            <div class="account-profile">
              <n-avatar :size="64" src="https://07akioni.oss-cn-beijing.aliyuncs.com/07akioni.jpeg" round />
              <div class="account-info">
                <div class="nickname">{{ userProfile.nickname }}</div>
                <div class="linkx-id">LinkX ID: linkx_888888</div>
              </div>
            </div>

            <n-divider style="margin: 8px 0" />

            <h3 class="section-title">安全设置</h3>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">登录密码</span>
              </div>
              <n-button size="small" tertiary @click="changePassword">修改</n-button>
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">设备管理</span>
              </div>
              <n-button size="small" tertiary @click="manageDevices">查看</n-button>
            </div>

            <n-divider style="margin: 8px 0" />

            <h3 class="section-title">隐私</h3>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">加好友需验证</span>
              </div>
              <n-switch v-model:value="privacyVerifyFriend" size="small" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">允许陌生人会话</span>
              </div>
              <n-switch v-model:value="privacyAllowStranger" size="small" />
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">在线状态可见</span>
              </div>
              <n-switch v-model:value="privacyShowOnline" size="small" />
            </div>
          </div>
        </n-tab-pane>

        <n-tab-pane name="appearance">
          <template #tab>
            <div class="tab-label">
              <span>外观与显示</span>
            </div>
          </template>
          <div class="settings-content">
            <h3 class="section-title">主题设置</h3>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">深色模式</span>
              </div>
              <n-switch :value="theme === 'dark'" @update:value="toggleTheme" size="small" />
            </div>

            <n-divider style="margin: 8px 0" />

            <h3 class="section-title">聊天背景</h3>
            <div class="theme-preview-list">
              <div
                class="theme-preview"
                :class="{ active: chatBackground === 'default' }"
                @click="pickChatBackground('default')"
              >
                <div class="preview-color" style="background: #f5f6f7;"></div>
                <span>默认纯色</span>
                <n-icon v-if="chatBackground === 'default'" class="check-icon" :component="CheckmarkCircleOutline" />
              </div>
              <div
                class="theme-preview"
                :class="{ active: chatBackground === 'purple' }"
                @click="pickChatBackground('purple')"
              >
                <div class="preview-color" style="background: linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%);"></div>
                <span>梦幻紫</span>
                <n-icon v-if="chatBackground === 'purple'" class="check-icon" :component="CheckmarkCircleOutline" />
              </div>
              <div
                class="theme-preview"
                :class="{ active: chatBackground === 'orange' }"
                @click="pickChatBackground('orange')"
              >
                <div class="preview-color" style="background: linear-gradient(135deg, #f6d365 0%, #fda085 100%);"></div>
                <span>落日橘</span>
                <n-icon v-if="chatBackground === 'orange'" class="check-icon" :component="CheckmarkCircleOutline" />
              </div>
            </div>
          </div>
        </n-tab-pane>

        <n-tab-pane name="prototype">
          <template #tab>
            <div class="tab-label">
              <span>原型演示工具</span>
            </div>
          </template>
          <div class="settings-content">
            <h3 class="section-title">边缘状态触发</h3>
            
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">模拟网络断开</span>
                <span class="setting-desc" style="font-size: 12px; color: var(--lx-text-muted); display: block;">开启后将在聊天列表顶部显示断网提示</span>
              </div>
              <n-switch :value="isOffline" @update:value="toggleOffline" size="small" />
            </div>

            <n-divider style="margin: 8px 0" />

            <h3 class="section-title">系统通知</h3>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">模拟接收新消息</span>
                <span class="setting-desc" style="font-size: 12px; color: var(--lx-text-muted); display: block;">将触发 Electron 桌面系统通知和未读角标</span>
              </div>
              <n-button size="small" type="primary" @click="simulateIncomingMessage">触发通知</n-button>
            </div>
          </div>
        </n-tab-pane>

        <n-tab-pane name="about">
          <template #tab>
            <div class="tab-label">
              <span>关于</span>
            </div>
          </template>
          <div class="settings-content about-content">
            <img src="../assets/logo-linkx.svg" alt="LinkX Logo" class="about-logo" />
            <h2 class="app-name">LinkX</h2>
            <p class="app-version">Version 1.0.0 (Beta)</p>
            <p class="app-desc">一款极简、高效、安全的企业级即时通讯工具。</p>
            
            <div class="about-actions">
              <n-button type="primary" secondary @click="checkUpdate">检查更新</n-button>
              <n-button tertiary @click="exportLogs">系统日志</n-button>
            </div>
            
            <p class="copyright">© 2026 LinkX Team. All rights reserved.</p>
          </div>
        </n-tab-pane>
      </n-tabs>
    </div>
  </n-modal>
</template>

<style scoped>
.settings-modal {
  /* 覆盖默认的内边距 */
  --n-padding-bottom: 0 !important;
  --n-padding-left: 0 !important;
  --n-padding-right: 0 !important;
}

.settings-container {
  display: flex;
  height: 480px;
  background: var(--lx-bg-card);
}

:deep(.n-tabs) {
  height: 100%;
}

:deep(.n-tabs-nav) {
  width: 140px !important;
  background: var(--lx-bg-panel) !important;
  border-right: none !important;
  padding: 24px 0;
}

:deep(.n-tabs-pad) {
  display: none !important;
}

:deep(.n-tabs-tab) {
  padding: 8px 16px !important;
  justify-content: center !important;
  border-radius: 6px !important;
  transition: all 0.2s ease;
  margin: 4px 12px;
  color: var(--lx-text-body) !important;
}

:deep(.n-tabs-tab:hover) {
  background: var(--lx-bg-input);
}

:deep(.n-tabs-tab--active) {
  background: var(--lx-bg-hover) !important;
  color: var(--lx-text-body) !important;
  font-weight: 500;
}

:deep(.n-tabs-tab__label) {
  width: 100%;
  text-align: center;
}

.tab-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.settings-content {
  padding: 32px 40px;
  height: 100%;
  overflow-y: auto;
  box-sizing: border-box;
  background: var(--lx-bg-card);
}

.section-title {
  font-size: 13px;
  color: var(--lx-text-muted);
  font-weight: normal;
  margin-bottom: 12px;
  margin-top: 0;
  letter-spacing: 0.5px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  margin: 0 -16px;
  border-radius: var(--lx-radius);
  transition: background 0.2s;
}

.setting-item:hover {
  background: var(--lx-bg-panel);
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.setting-name {
  font-size: 14px;
  color: var(--lx-text-body);
}

.setting-desc {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.account-profile {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 0;
}

.account-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nickname {
  font-size: 18px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.linkx-id {
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.theme-preview-list {
  display: flex;
  gap: 20px;
  margin-top: 12px;
}

.theme-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  position: relative;
}

.preview-color {
  width: 80px;
  height: 120px;
  border-radius: var(--lx-radius);
  border: 2px solid transparent;
  box-shadow: 0 2px 8px var(--lx-bg-hover);
  transition: all 0.2s ease;
}

.theme-preview:hover .preview-color {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--lx-bg-active);
}

.theme-preview.active .preview-color {
  border-color: var(--lx-accent);
}

.theme-preview span {
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.check-icon {
  position: absolute;
  top: -8px;
  right: -8px;
  color: var(--lx-accent);
  font-size: 20px;
  background: var(--lx-bg-card);
  border-radius: 50%;
}

.about-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.about-logo {
  width: 80px;
  height: 80px;
  margin-bottom: 16px;
}

.app-name {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--lx-text-body);
}

.app-version {
  font-size: 14px;
  color: var(--lx-text-secondary);
  margin: 0 0 16px;
}

.app-desc {
  font-size: 14px;
  color: var(--lx-text-muted);
  max-width: 280px;
  line-height: 1.5;
  margin: 0 0 32px;
}

.about-actions {
  display: flex;
  gap: 16px;
  margin-bottom: 48px;
}

.copyright {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-top: auto;
}
</style>
