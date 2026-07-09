<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import {
  NModal,
  NTabs,
  NTabPane,
  NIcon
} from 'naive-ui'
import {
  SettingsOutline,
  PersonOutline,
  ColorPaletteOutline,
  FlaskOutline,
  InformationCircleOutline,
  CloseOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useSettingsStore } from '../stores/settings'

import GeneralSettings from './settings/GeneralSettings.vue'
import AccountSettings from './settings/AccountSettings.vue'
import AppearanceSettings from './settings/AppearanceSettings.vue'
import PrototypeSettings from './settings/PrototypeSettings.vue'
import AboutSettings from './settings/AboutSettings.vue'

const settingsStore = useSettingsStore()
const { isSettingsModalVisible, settingsActiveTab } = storeToRefs(settingsStore)
const { closeSettings } = settingsStore

const activeTab = ref('general')

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

watch(settingsActiveTab, newVal => {
  if (newVal) activeTab.value = newVal
}, { immediate: true })
</script>

<template>
  <n-modal
    v-model:show="isSettingsModalVisible"
    class="settings-modal"
    preset="card"
    to="body"
    :bordered="false"
    :show-icon="false"
    :closable="false"
    :z-index="10001"
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

        <GeneralSettings v-show="activeTab === 'general'" />
        <AccountSettings v-show="activeTab === 'account'" />
        <AppearanceSettings v-show="activeTab === 'appearance'" />
        <PrototypeSettings v-show="activeTab === 'prototype'" />
        <AboutSettings v-show="activeTab === 'about'" />
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
</style>
