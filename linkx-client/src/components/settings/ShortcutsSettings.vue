<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NInput, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { useI18n } from '../../i18n'

const message = useMessage()
const appSettingsStore = useAppSettingsStore()
const { shortcutToggleWindow, shortcutLock } = storeToRefs(appSettingsStore)
const isElectron = computed(() => !!window.electronAPI)
const { t } = useI18n()

const recording = ref<'toggle' | 'lock' | null>(null)
const draftToggle = ref(shortcutToggleWindow.value)
const draftLock = ref(shortcutLock.value)

onMounted(() => {
  draftToggle.value = shortcutToggleWindow.value
  draftLock.value = shortcutLock.value
})

function formatAccel(e: KeyboardEvent): string | null {
  const parts: string[] = []
  if (e.ctrlKey || e.metaKey) parts.push('CommandOrControl')
  if (e.altKey) parts.push('Alt')
  if (e.shiftKey) parts.push('Shift')
  const key = e.key
  if (!key || ['Control', 'Shift', 'Alt', 'Meta'].includes(key)) return null
  const normalized = key.length === 1 ? key.toUpperCase() : key
  parts.push(normalized === ' ' ? 'Space' : normalized)
  if (parts.length < 2) return null
  return parts.join('+')
}

function startRecord(which: 'toggle' | 'lock') {
  recording.value = which
}

function onKeydown(e: KeyboardEvent) {
  if (!recording.value) return
  e.preventDefault()
  e.stopPropagation()
  const accel = formatAccel(e)
  if (!accel) return
  if (recording.value === 'toggle') draftToggle.value = accel
  else draftLock.value = accel
  recording.value = null
}

async function saveShortcuts() {
  shortcutToggleWindow.value = draftToggle.value
  shortcutLock.value = draftLock.value
  const ok = await window.electronAPI?.setShortcuts?.({
    toggleWindow: draftToggle.value,
    lock: draftLock.value
  })
  if (ok === false) {
    message.error(t('shortcuts.saveFail'))
    return
  }
  message.success(t('shortcuts.saved'))
}

function resetDefaults() {
  draftToggle.value = 'CommandOrControl+Shift+L'
  draftLock.value = 'CommandOrControl+Shift+K'
}
</script>

<template>
  <div class="settings-scroll" tabindex="0" @keydown="onKeydown">
    <section class="group-card">
      <div class="group-head"><span>{{ t('shortcuts.title') }}</span></div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('shortcuts.toggleWindow') }}</span>
          <span class="setting-desc">{{ t('shortcuts.toggleWindowDesc') }}</span>
        </div>
        <div class="shortcut-edit">
          <n-input
            :value="draftToggle"
            readonly
            size="small"
            style="width: 200px"
            :placeholder="recording === 'toggle' ? t('shortcuts.pressKeys') : ''"
            @click="startRecord('toggle')"
          />
        </div>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('shortcuts.lock') }}</span>
          <span class="setting-desc">{{ t('shortcuts.lockDesc') }}</span>
        </div>
        <div class="shortcut-edit">
          <n-input
            :value="draftLock"
            readonly
            size="small"
            style="width: 200px"
            :placeholder="recording === 'lock' ? t('shortcuts.pressKeys') : ''"
            @click="startRecord('lock')"
          />
        </div>
      </div>
      <div class="actions-row">
        <n-button size="small" tertiary @click="resetDefaults">{{ t('shortcuts.reset') }}</n-button>
        <n-button size="small" type="primary" :disabled="!isElectron" @click="saveShortcuts">
          {{ t('shortcuts.save') }}
        </n-button>
      </div>
    </section>
    <p v-if="!isElectron" class="web-tip">{{ t('shortcuts.webTip') }}</p>
    <p v-if="recording" class="record-tip">{{ t('shortcuts.recording') }}</p>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.shortcut-edit {
  flex-shrink: 0;
}

.actions-row {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 8px 16px 16px;
}

.web-tip,
.record-tip {
  margin: 0 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.record-tip {
  color: var(--lx-accent);
}
</style>
