<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import {
  MoonOutline,
  SunnyOutline,
  DesktopOutline,
  CheckmarkCircle
} from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../stores/app'
import { useAppSettingsStore } from '../../stores/appSettings'
import { applyAccentColor, ACCENT_PRESETS } from '../../utils/accentColor'
import {
  applyDocumentTheme,
  notifyElectronTheme,
  resolveThemePreference
} from '../../utils/themeSync'
import { useI18n } from '../../i18n'
import { LxGroupCard } from '../ui'

const appStore = useAppStore()
const appSettingsStore = useAppSettingsStore()
const { accentColor, themeMode } = storeToRefs(appSettingsStore)
const { t } = useI18n()

const accentPresets = computed(() =>
  ACCENT_PRESETS.map(c => ({
    ...c,
    label: t(`appearance.accentPresets.${c.id}` as 'appearance.accentPresets.cyan')
  }))
)

function applyThemeMode(mode: 'system' | 'light' | 'dark') {
  themeMode.value = mode
  const resolved = resolveThemePreference(mode)
  if (appStore.theme !== resolved) {
    appStore.theme = resolved
  }
  applyDocumentTheme(resolved)
  notifyElectronTheme(resolved)
}

function pickAccent(id: string) {
  accentColor.value = id
  applyAccentColor(id)
}
</script>

<template>
  <div class="settings-scroll">
    <LxGroupCard tag="section" variant="settings">
      <div class="group-head"><span>{{ t('appearance.themeMode') }}</span></div>
      <div class="theme-mode-row">
        <button
          type="button"
          class="theme-mode"
          :class="{ 'is-active': themeMode === 'system' }"
          @click="applyThemeMode('system')"
        >
          <n-icon :component="DesktopOutline" :size="20" />
          <span>{{ t('appearance.followSystem') }}</span>
        </button>
        <button
          type="button"
          class="theme-mode"
          :class="{ 'is-active': themeMode === 'light' }"
          @click="applyThemeMode('light')"
        >
          <n-icon :component="SunnyOutline" :size="20" />
          <span>{{ t('appearance.light') }}</span>
        </button>
        <button
          type="button"
          class="theme-mode"
          :class="{ 'is-active': themeMode === 'dark' }"
          @click="applyThemeMode('dark')"
        >
          <n-icon :component="MoonOutline" :size="20" />
          <span>{{ t('appearance.dark') }}</span>
        </button>
      </div>
    </LxGroupCard>

    <LxGroupCard tag="section" variant="settings">
      <div class="group-head"><span>{{ t('appearance.accent') }}</span></div>
      <div class="accent-row">
        <button
          v-for="c in accentPresets"
          :key="c.id"
          type="button"
          class="accent-dot"
          :class="{ 'is-active': accentColor === c.id, rainbow: c.id === 'rainbow' }"
          :style="c.id === 'rainbow' ? undefined : { background: c.color }"
          :title="c.label"
          @click="pickAccent(c.id)"
        >
          <n-icon
            v-if="accentColor === c.id"
            :component="CheckmarkCircle"
            :size="16"
            class="accent-check"
          />
        </button>
      </div>
    </LxGroupCard>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.theme-mode-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--lx-space-md);
  padding: var(--lx-space-xs) var(--lx-space-2xl) var(--lx-space-2xl);
}

.theme-mode {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lx-space);
  padding: var(--lx-space-xl) var(--lx-space);
  border: 1.5px solid var(--lx-border-light);
  border-radius: var(--lx-radius-xl);
  background: var(--lx-bg-card);
  color: var(--lx-text-secondary);
  cursor: pointer;
  transition: border-color var(--lx-duration-md), color var(--lx-duration-md), box-shadow var(--lx-duration-md);
}

.theme-mode:hover {
  border-color: var(--lx-accent);
  color: var(--lx-text-body);
}

.theme-mode.is-active {
  border-color: var(--lx-accent);
  color: var(--lx-accent);
  box-shadow: var(--lx-shadow-ring-accent);
}

.theme-mode span {
  font-size: var(--lx-font-sm);
  font-weight: 500;
}

.accent-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-xs) var(--lx-space-2xl) var(--lx-space-2xl);
}

.accent-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid transparent;
  padding: 0;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform var(--lx-duration), box-shadow var(--lx-duration);
}

.accent-dot:hover {
  transform: scale(1.08);
}

.accent-dot.is-active {
  box-shadow: 0 0 0 2px var(--lx-bg-card), 0 0 0 4px var(--lx-accent);
}

.accent-dot.rainbow {
  background: var(--lx-gradient-rainbow);
}

.accent-check {
  color: var(--lx-text-on-accent);
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.35));
}
</style>
