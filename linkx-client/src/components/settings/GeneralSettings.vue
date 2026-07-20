<script setup lang="ts">
import { computed } from 'vue'
import { NSwitch, NSelect, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { setLocale, useI18n } from '../../i18n'
import { unlockAudio } from '../../utils/notifyTone'

const appSettingsStore = useAppSettingsStore()
const message = useMessage()
const { t } = useI18n()
const { autoStart, language, minimizeToTray, openOnStartup } = storeToRefs(appSettingsStore)

const languageOptions = computed(() => [
  { label: '简体中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
])

const startupOptions = computed(() => [
  { label: t('general.openMain'), value: 'main' },
  { label: t('general.openTray'), value: 'tray' }
])

async function applyAutoStart() {
  unlockAudio()
  try {
    const ok = await window.electronAPI?.setAutoStart?.(autoStart.value)
    if (ok === false) {
      message.error(t('general.autoStartFail'))
    }
  } catch (e) {
    message.error(
      t('general.autoStartFailDetail', {
        message: e instanceof Error ? e.message : 'unknown'
      })
    )
  }
  appSettingsStore.scheduleSave('autoStart')
  await appSettingsStore.syncDesktopPrefs()
}

async function onLanguageChange(value: string) {
  language.value = value
  const applied = setLocale(value)
  language.value = applied
  appSettingsStore.scheduleSave('language')
  await appSettingsStore.syncDesktopPrefs()
  message.success(t('general.languageApplied'))
}

async function onMinimizeToTrayChange() {
  await appSettingsStore.syncDesktopPrefs()
}

async function onOpenOnStartupChange() {
  await appSettingsStore.syncDesktopPrefs()
}
</script>

<template>
  <div class="settings-scroll">
    <section class="group-card">
      <div class="group-head"><span>{{ t('general.title') }}</span></div>

      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('general.language') }}</span>
        </div>
        <n-select
          :value="language"
          :options="languageOptions"
          size="small"
          style="width: 140px"
          @update:value="onLanguageChange"
        />
      </div>

      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('general.openOnStartup') }}</span>
        </div>
        <n-select
          v-model:value="openOnStartup"
          :options="startupOptions"
          size="small"
          style="width: 140px"
          @update:value="onOpenOnStartupChange"
        />
      </div>

      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('general.minimizeToTray') }}</span>
          <span class="setting-desc">{{ t('general.minimizeToTrayDesc') }}</span>
        </div>
        <n-switch
          v-model:value="minimizeToTray"
          size="small"
          @update:value="onMinimizeToTrayChange"
        />
      </div>

      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('general.autoStart') }}</span>
          <span class="setting-desc">{{ t('general.autoStartDesc') }}</span>
        </div>
        <n-switch
          v-model:value="autoStart"
          size="small"
          @update:value="applyAutoStart"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';
</style>
