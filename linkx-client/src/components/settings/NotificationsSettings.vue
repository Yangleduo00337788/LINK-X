<script setup lang="ts">
import { NSwitch, NIcon, NRadioGroup, NRadio, NButton, NTimePicker } from 'naive-ui'
import { MusicalNotesOutline, PlayCircleOutline, MoonOutline, OptionsOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { listTones, playTone, unlockAudio, type ToneId } from '../../utils/notifyTone'
import { useI18n } from '../../i18n'

const appSettingsStore = useAppSettingsStore()
const {
  soundNotify,
  messageDetail,
  notifyAtMe,
  notifySound,
  notifyTone,
  quietHoursEnabled,
  quietHoursStart,
  quietHoursEnd,
  notifyChat,
  notifySocial,
  notifyMoments,
  notifySystem
} = storeToRefs(appSettingsStore)
const { t } = useI18n()

const tones = listTones()

function toggleSwitch(key: Parameters<typeof appSettingsStore.scheduleSave>[0]) {
  unlockAudio()
  appSettingsStore.scheduleSave(key)
}

function pickTone(id: ToneId) {
  unlockAudio()
  appSettingsStore.setNotifyTone(id)
  playTone(id)
  appSettingsStore.scheduleSave('notifyTone')
}

function parseHm(hm: string): number | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec((hm || '').trim())
  if (!m) return null
  const h = Number(m[1])
  const min = Number(m[2])
  if (h > 23 || min > 59) return null
  const d = new Date()
  d.setHours(h, min, 0, 0)
  return d.getTime()
}

function formatHm(ts: number | null): string {
  if (ts == null) return '00:00'
  const d = new Date(ts)
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

function onQuietStart(v: number | null) {
  quietHoursStart.value = formatHm(v)
  appSettingsStore.scheduleSave('quietHoursStart')
}

function onQuietEnd(v: number | null) {
  quietHoursEnd.value = formatHm(v)
  appSettingsStore.scheduleSave('quietHoursEnd')
}
</script>

<template>
  <div class="settings-scroll">
    <section class="group-card">
      <div class="group-head"><span>{{ t('notifications.title') }}</span></div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.soundNotify') }}</span>
        </div>
        <n-switch
          v-model:value="soundNotify"
          size="small"
          @update:value="toggleSwitch('soundNotify')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.messageDetail') }}</span>
        </div>
        <n-switch
          v-model:value="messageDetail"
          size="small"
          @update:value="toggleSwitch('messageDetail')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.notifyAtMe') }}</span>
        </div>
        <n-switch
          v-model:value="notifyAtMe"
          size="small"
          @update:value="toggleSwitch('notifyAtMe')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.notifySound') }}</span>
        </div>
        <n-switch
          v-model:value="notifySound"
          size="small"
          @update:value="toggleSwitch('notifySound')"
        />
      </div>
    </section>

    <section class="group-card">
      <div class="group-head">
        <n-icon :component="OptionsOutline" :size="16" class="group-ico" />
        <span>{{ t('notifications.channelTitle') }}</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.notifyChat') }}</span>
          <span class="setting-desc">{{ t('notifications.notifyChatDesc') }}</span>
        </div>
        <n-switch v-model:value="notifyChat" size="small" @update:value="toggleSwitch('notifyChat')" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.notifySocial') }}</span>
        </div>
        <n-switch v-model:value="notifySocial" size="small" @update:value="toggleSwitch('notifySocial')" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.notifyMoments') }}</span>
        </div>
        <n-switch v-model:value="notifyMoments" size="small" @update:value="toggleSwitch('notifyMoments')" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.notifySystem') }}</span>
        </div>
        <n-switch v-model:value="notifySystem" size="small" @update:value="toggleSwitch('notifySystem')" />
      </div>
    </section>

    <section class="group-card">
      <div class="group-head">
        <n-icon :component="MoonOutline" :size="16" class="group-ico" />
        <span>{{ t('notifications.quietTitle') }}</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.quietEnabled') }}</span>
          <span class="setting-desc">{{ t('notifications.quietDesc') }}</span>
        </div>
        <n-switch
          v-model:value="quietHoursEnabled"
          size="small"
          @update:value="toggleSwitch('quietHoursEnabled')"
        />
      </div>
      <div v-if="quietHoursEnabled" class="quiet-range">
        <n-time-picker
          :value="parseHm(quietHoursStart)"
          format="HH:mm"
          size="small"
          :clearable="false"
          @update:value="onQuietStart"
        />
        <span class="quiet-sep">{{ t('notifications.quietTo') }}</span>
        <n-time-picker
          :value="parseHm(quietHoursEnd)"
          format="HH:mm"
          size="small"
          :clearable="false"
          @update:value="onQuietEnd"
        />
      </div>
    </section>

    <section class="group-card">
      <div class="group-head">
        <n-icon :component="MusicalNotesOutline" :size="16" class="group-ico" />
        <span>{{ t('notifications.toneTitle') }}</span>
      </div>
      <div class="setting-row tone-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.tone') }}</span>
          <span class="setting-desc">{{ t('notifications.toneDesc') }}</span>
        </div>
        <n-radio-group
          v-model:value="notifyTone"
          size="small"
          @update:value="(v: string) => pickTone(v as ToneId)"
        >
          <n-radio v-for="tone in tones" :key="tone.id" :value="tone.id">{{ tone.label }}</n-radio>
        </n-radio-group>
      </div>
      <div class="tone-preview-row">
        <n-button size="tiny" tertiary @click="playTone(notifyTone)">
          <template #icon>
            <n-icon :component="PlayCircleOutline" />
          </template>
          {{ t('notifications.previewTone', { label: tones.find(x => x.id === notifyTone)?.label || '' }) }}
        </n-button>
      </div>
      <p class="local-note">{{ t('notifications.desktopLocalOnly') }}</p>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.tone-row {
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.tone-preview-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px 8px;
}

.quiet-range {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px 16px;
}

.quiet-sep {
  color: var(--lx-text-muted);
  font-size: 13px;
}

.local-note {
  margin: 0;
  padding: 0 16px 16px;
  font-size: 12px;
  color: var(--lx-text-muted);
  line-height: 1.4;
}
</style>
