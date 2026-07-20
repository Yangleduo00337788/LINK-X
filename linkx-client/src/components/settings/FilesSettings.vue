<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NSelect, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { useI18n } from '../../i18n'

const message = useMessage()
const appSettingsStore = useAppSettingsStore()
const { downloadPath, downloadAskEveryTime } = storeToRefs(appSettingsStore)
const isElectron = computed(() => !!window.electronAPI)
const { t } = useI18n()

const askMode = computed({
  get: () => (downloadAskEveryTime.value ? 'ask' : 'auto'),
  set: (v: string) => {
    downloadAskEveryTime.value = v === 'ask'
  }
})

const askOptions = computed(() => [
  { label: t('files.askEveryTime'), value: 'ask' },
  { label: t('files.autoSave'), value: 'auto' }
])

async function pickDownloadPath() {
  const path = await window.electronAPI?.pickDownloadPath?.()
  if (path) {
    downloadPath.value = path
    message.success(t('files.dirUpdated'))
  }
}

async function openDownloadFolder() {
  const ok = await window.electronAPI?.openDownloadPath?.(downloadPath.value || undefined)
  if (ok === false) {
    message.warning(t('files.openFail'))
  }
}

async function clearCache() {
  const result = await window.electronAPI?.clearAppCache?.()
  if (result?.ok) {
    message.success(result.message || t('files.cacheCleared'))
  } else {
    message.error(result?.message || t('files.cacheFail'))
  }
}
</script>

<template>
  <div class="settings-scroll">
    <section class="group-card">
      <div class="group-head"><span>{{ t('files.download') }}</span></div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('files.downloadDir') }}</span>
          <span class="setting-desc">{{ downloadPath || t('files.downloadDirDefault') }}</span>
        </div>
        <div class="row-actions">
          <n-button size="small" secondary :disabled="!isElectron" @click="pickDownloadPath">
            {{ t('files.change') }}
          </n-button>
          <n-button size="small" tertiary :disabled="!isElectron" @click="openDownloadFolder">
            {{ t('files.open') }}
          </n-button>
        </div>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('files.saveMode') }}</span>
        </div>
        <n-select
          v-model:value="askMode"
          :options="askOptions"
          size="small"
          style="width: 200px"
        />
      </div>
    </section>

    <section class="group-card">
      <div class="group-head"><span>{{ t('files.cache') }}</span></div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('files.clearCache') }}</span>
          <span class="setting-desc">{{ t('files.clearCacheDesc') }}</span>
        </div>
        <n-button size="small" secondary :disabled="!isElectron" @click="clearCache">
          {{ t('files.clearNow') }}
        </n-button>
      </div>
    </section>

    <p v-if="!isElectron" class="web-tip">{{ t('files.webTip') }}</p>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.row-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.web-tip {
  margin: 0 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
}
</style>
