<script setup lang="ts">
import { NSwitch, NIcon, NRadioGroup, NRadio, NButton } from 'naive-ui'
import { MusicalNotesOutline, PlayCircleOutline } from '@vicons/ionicons5'
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
  notifyTone
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
  padding: 0 16px 16px;
}
</style>
