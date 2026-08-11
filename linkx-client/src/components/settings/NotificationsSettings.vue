<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed } from 'vue'
import { NSwitch, NIcon, NRadioGroup, NRadio, NTimePicker } from 'naive-ui'
import { MusicalNotesOutline, PlayCircleOutline, MoonOutline, OptionsOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { listTones, playTone, unlockAudio, type ToneId } from '../../utils/notifyTone'
import { useI18n } from '../../i18n'
import { LxGroupCard, LxButton } from '../ui'

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
  notifySystem,
  notifyFriendOnline
} = storeToRefs(appSettingsStore)
const { t } = useI18n()

const tones = computed(() =>
  listTones().map(tone => ({
    id: tone.id,
    label: t(`notifications.tonePresets.${tone.id}.label` as 'notifications.tonePresets.default.label'),
    description: t(
      `notifications.tonePresets.${tone.id}.description` as 'notifications.tonePresets.default.description'
    )
  }))
)

const activeToneLabel = computed(
  () => tones.value.find(x => x.id === notifyTone.value)?.label || ''
)

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
    <LxGroupCard tag="section" variant="settings">
      <div class="group-head"><span>{{ t('notifications.title') }}</span></div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.soundNotify') }}</span>
          <span class="setting-desc">{{ t('notifications.soundNotifyDesc') }}</span>
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
          <span class="setting-desc">{{ t('notifications.messageDetailDesc') }}</span>
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
          <span class="setting-desc">{{ t('notifications.notifyAtMeDesc') }}</span>
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
          <span class="setting-desc">{{ t('notifications.notifySoundDesc') }}</span>
        </div>
        <n-switch
          v-model:value="notifySound"
          size="small"
          @update:value="toggleSwitch('notifySound')"
        />
      </div>
    </LxGroupCard>

    <LxGroupCard tag="section" variant="settings">
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
          <span class="setting-desc">{{ t('notifications.notifySocialDesc') }}</span>
        </div>
        <n-switch v-model:value="notifySocial" size="small" @update:value="toggleSwitch('notifySocial')" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.notifyFriendOnline') }}</span>
          <span class="setting-desc">{{ t('notifications.notifyFriendOnlineDesc') }}</span>
        </div>
        <n-switch
          v-model:value="notifyFriendOnline"
          size="small"
          @update:value="toggleSwitch('notifyFriendOnline')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.notifyMoments') }}</span>
          <span class="setting-desc">{{ t('notifications.notifyMomentsDesc') }}</span>
        </div>
        <n-switch v-model:value="notifyMoments" size="small" @update:value="toggleSwitch('notifyMoments')" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('notifications.notifySystem') }}</span>
          <span class="setting-desc">{{ t('notifications.notifySystemDesc') }}</span>
        </div>
        <n-switch v-model:value="notifySystem" size="small" @update:value="toggleSwitch('notifySystem')" />
      </div>
    </LxGroupCard>

    <LxGroupCard tag="section" variant="settings">
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
    </LxGroupCard>

    <LxGroupCard tag="section" variant="settings">
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
      <p class="local-note">{{ t('notifications.tipSync') }}</p>
      <div class="tone-preview-row">
        <LxButton variant="link-md" @click="playTone(notifyTone)">
          <n-icon :component="PlayCircleOutline" :size="16" />
          {{ t('notifications.previewTone', { label: activeToneLabel }) }}
        </LxButton>
      </div>
      <p class="local-note">{{ t('notifications.desktopLocalOnly') }}</p>
    </LxGroupCard>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.tone-row {
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lx-space-md);
}

.tone-preview-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-lg);
  padding: 0 var(--lx-space-2xl) var(--lx-space);
}

.quiet-range {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  padding: 0 var(--lx-space-2xl) var(--lx-space-2xl);
}

.quiet-sep {
  color: var(--lx-text-muted);
  font-size: var(--lx-font-md);
}

.local-note {
  margin: 0;
  padding: 0 var(--lx-space-2xl) var(--lx-space-2xl);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading);
}
</style>
