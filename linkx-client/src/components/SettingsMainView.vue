<script setup lang="ts">
/**
 * 设置页右侧主内容：按分类切换卡片内容，并统一订阅偏好保存 toast。
 */
import { watch, onMounted, onBeforeUnmount, computed } from 'vue'
import { useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useSettingsStore } from '../stores/settings'
import { onPreferenceChange } from '../utils/preferenceEvents'
import { useI18n, getLocale } from '../i18n'
import AccountSettings from './settings/AccountSettings.vue'
import GeneralSettings from './settings/GeneralSettings.vue'
import NotificationsSettings from './settings/NotificationsSettings.vue'
import PrivacySettings from './settings/PrivacySettings.vue'
import ChatSettings from './settings/ChatSettings.vue'
import FilesSettings from './settings/FilesSettings.vue'
import ShortcutsSettings from './settings/ShortcutsSettings.vue'
import AppearanceSettings from './settings/AppearanceSettings.vue'
import AboutSettings from './settings/AboutSettings.vue'

const settingsStore = useSettingsStore()
const { settingsActiveTab } = storeToRefs(settingsStore)
const message = useMessage()
const { t } = useI18n()

const pageTitle = computed(() => {
  const key = `settings.tabs.${settingsActiveTab.value}`
  return t(key)
})

function summarize(fields: Record<string, unknown>): string {
  const keys = Object.keys(fields)
  if (keys.length === 0) return t('settings.saved')
  const names = keys.map(k => t(`settings.fields.${k}`))
  if (names.length === 1) return t('settings.fieldUpdated', { name: names[0] })
  if (names.length === 2) return t('settings.fieldsUpdated2', { a: names[0], b: names[1] })
  return t('settings.fieldsUpdatedMany', {
    list: names.slice(0, -1).join(getLocale() === 'en-US' ? ', ' : '、'),
    last: names[names.length - 1]
  })
}

let unsubscribeToast: (() => void) | null = null

onMounted(() => {
  unsubscribeToast = onPreferenceChange.on(event => {
    if (event.kind === 'success') {
      message.success(summarize(event.fields as Record<string, unknown>))
    } else {
      message.error(t('settings.saveFailed', { message: event.message }))
    }
  })
})

onBeforeUnmount(() => {
  if (unsubscribeToast) unsubscribeToast()
})

watch(settingsActiveTab, () => {
  const el = document.querySelector('.settings-main-body')
  if (el) el.scrollTop = 0
})
</script>

<template>
  <div class="settings-main">
    <header class="main-head">
      <h2>{{ pageTitle }}</h2>
    </header>
    <div class="settings-main-body">
      <AccountSettings v-show="settingsActiveTab === 'account'" />
      <GeneralSettings v-show="settingsActiveTab === 'general'" />
      <NotificationsSettings v-show="settingsActiveTab === 'notifications'" />
      <PrivacySettings v-show="settingsActiveTab === 'privacy'" />
      <ChatSettings v-show="settingsActiveTab === 'chat'" />
      <FilesSettings v-show="settingsActiveTab === 'files'" />
      <ShortcutsSettings v-show="settingsActiveTab === 'shortcuts'" />
      <AppearanceSettings v-show="settingsActiveTab === 'appearance'" />
      <AboutSettings v-show="settingsActiveTab === 'about'" />
    </div>
  </div>
</template>

<style scoped>
.settings-main {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-panel);
  min-width: 0;
}

.main-head {
  flex-shrink: 0;
  padding: 20px 28px 12px;
}

.main-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--lx-text);
}

.settings-main-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
