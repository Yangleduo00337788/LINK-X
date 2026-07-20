<script setup lang="ts">
/**
 * 设置页左侧分类导航 + 全局语言切换。
 */
import { computed, type Component } from 'vue'
import { NIcon, NSelect, useMessage } from 'naive-ui'
import {
  PersonOutline,
  SettingsOutline,
  NotificationsOutline,
  LockClosedOutline,
  ChatbubblesOutline,
  FolderOutline,
  KeyOutline,
  ColorPaletteOutline,
  InformationCircleOutline
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useSettingsStore } from '../stores/settings'
import { useAppSettingsStore } from '../stores/appSettings'
import type { SettingsTab } from '../types'
import { setLocale, useI18n } from '../i18n'

const settingsStore = useSettingsStore()
const appSettingsStore = useAppSettingsStore()
const { settingsActiveTab } = storeToRefs(settingsStore)
const { language } = storeToRefs(appSettingsStore)
const { t } = useI18n()
const message = useMessage()

const languageOptions = [
  { label: '简体中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
]

const navItems = computed(() => {
  const items: { key: SettingsTab; labelKey: string; icon: Component }[] = [
    { key: 'account', labelKey: 'settings.tabs.account', icon: PersonOutline },
    { key: 'general', labelKey: 'settings.tabs.general', icon: SettingsOutline },
    { key: 'notifications', labelKey: 'settings.tabs.notifications', icon: NotificationsOutline },
    { key: 'privacy', labelKey: 'settings.tabs.privacy', icon: LockClosedOutline },
    { key: 'chat', labelKey: 'settings.tabs.chat', icon: ChatbubblesOutline },
    { key: 'files', labelKey: 'settings.tabs.files', icon: FolderOutline },
    { key: 'shortcuts', labelKey: 'settings.tabs.shortcuts', icon: KeyOutline },
    { key: 'appearance', labelKey: 'settings.tabs.appearance', icon: ColorPaletteOutline },
    { key: 'about', labelKey: 'settings.tabs.about', icon: InformationCircleOutline }
  ]
  return items.map(item => ({ ...item, label: t(item.labelKey) }))
})

function selectTab(key: SettingsTab) {
  settingsStore.setTab(key)
}

async function onLanguageChange(value: string) {
  const applied = setLocale(value)
  language.value = applied
  appSettingsStore.scheduleSave('language')
  await appSettingsStore.syncDesktopPrefs()
  message.success(t('general.languageApplied'))
}
</script>

<template>
  <div class="settings-panel">
    <div class="panel-head">
      <h1 class="panel-title">{{ t('settings.title') }}</h1>
      <div class="lang-row">
        <span class="lang-label">{{ t('general.language') }}</span>
        <n-select
          :value="language"
          :options="languageOptions"
          size="small"
          class="lang-select"
          @update:value="onLanguageChange"
        />
      </div>
    </div>
    <nav class="nav-list" :aria-label="t('settings.title')">
      <button
        v-for="item in navItems"
        :key="item.key"
        type="button"
        class="nav-item"
        :class="{ active: settingsActiveTab === item.key }"
        @click="selectTab(item.key)"
      >
        <n-icon :component="item.icon" :size="18" class="nav-ico" />
        <span class="nav-label">{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<style scoped>
.settings-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
  min-width: 0;
}

.panel-head {
  flex-shrink: 0;
  padding: 20px 20px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--lx-text);
  letter-spacing: -0.02em;
}

.lang-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.lang-label {
  font-size: 12px;
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.lang-select {
  width: 128px;
  flex-shrink: 0;
}

.nav-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 11px 14px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  text-align: left;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: var(--lx-bg-hover);
  color: var(--lx-text);
}

.nav-item.active {
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-weight: 600;
}

.nav-ico {
  flex-shrink: 0;
}

.nav-label {
  font-size: 14px;
}
</style>
