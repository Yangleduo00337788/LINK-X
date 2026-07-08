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
  NIcon
} from 'naive-ui'
import { CheckmarkCircleOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useSettingsStore } from '../stores/settings'
import { useAppStore } from '../stores/app'

const settingsStore = useSettingsStore()
const appStore = useAppStore()
const { isSettingsModalVisible, settingsActiveTab } = storeToRefs(settingsStore)
const { userProfile, theme, isOffline } = storeToRefs(appStore)
const { toggleTheme, toggleOffline, simulateIncomingMessage } = appStore

const activeTab = ref('general')

// 监听从外部传进来的 Tab 激活状态
watch(settingsActiveTab, (newVal) => {
  if (newVal) {
    activeTab.value = newVal
  }
}, { immediate: true })

// 模拟的设置状态
const autoStart = ref(true)
const soundNotify = ref(true)
const messageDetail = ref(true)
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
              <n-switch v-model:value="autoStart" size="small" />
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
              <n-button size="small" tertiary>修改</n-button>
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">设备管理</span>
              </div>
              <n-button size="small" tertiary>查看</n-button>
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
              <div class="theme-preview active">
                <div class="preview-color" style="background: #f5f6f7;"></div>
                <span>默认纯色</span>
                <n-icon class="check-icon" :component="CheckmarkCircleOutline" />
              </div>
              <div class="theme-preview">
                <div class="preview-color" style="background: linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%);"></div>
                <span>梦幻紫</span>
              </div>
              <div class="theme-preview">
                <div class="preview-color" style="background: linear-gradient(135deg, #f6d365 0%, #fda085 100%);"></div>
                <span>落日橘</span>
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
                <span class="setting-desc" style="font-size: 12px; color: #999; display: block;">开启后将在聊天列表顶部显示断网提示</span>
              </div>
              <n-switch :value="isOffline" @update:value="toggleOffline" size="small" />
            </div>

            <n-divider style="margin: 8px 0" />

            <h3 class="section-title">系统通知</h3>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">模拟接收新消息</span>
                <span class="setting-desc" style="font-size: 12px; color: #999; display: block;">将触发 Electron 桌面系统通知和未读角标</span>
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
              <n-button type="primary" secondary>检查更新</n-button>
              <n-button tertiary>系统日志</n-button>
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
  background: #ffffff;
}

:deep(.n-tabs) {
  height: 100%;
}

:deep(.n-tabs-nav) {
  width: 140px !important;
  background: #f7f7f7 !important;
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
  color: #333 !important;
}

:deep(.n-tabs-tab:hover) {
  background: #ebebeb;
}

:deep(.n-tabs-tab--active) {
  background: #e2e2e2 !important;
  color: #000 !important;
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
  background: #ffffff;
}

.section-title {
  font-size: 13px;
  color: #999;
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
  background: #f5f5f5;
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.setting-name {
  font-size: 14px;
  color: #333;
}

.setting-desc {
  font-size: 12px;
  color: #999;
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
  color: #333;
}

.linkx-id {
  font-size: 13px;
  color: #666;
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
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.theme-preview:hover .preview-color {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.theme-preview.active .preview-color {
  border-color: #12b7f5;
}

.theme-preview span {
  font-size: 13px;
  color: #666;
}

.check-icon {
  position: absolute;
  top: -8px;
  right: -8px;
  color: #12b7f5;
  font-size: 20px;
  background: #fff;
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
  color: #333;
}

.app-version {
  font-size: 14px;
  color: #666;
  margin: 0 0 16px;
}

.app-desc {
  font-size: 14px;
  color: #999;
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
  color: #bbb;
  margin-top: auto;
}
</style>
