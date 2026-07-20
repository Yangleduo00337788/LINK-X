<script setup lang="ts">
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

const appStore = useAppStore()
const appSettingsStore = useAppSettingsStore()
const { accentColor, themeMode } = storeToRefs(appSettingsStore)
const { t } = useI18n()

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
    <section class="group-card">
      <div class="group-head"><span>{{ t('appearance.themeMode') }}</span></div>
      <div class="theme-mode-row">
        <button
          type="button"
          class="theme-mode"
          :class="{ active: themeMode === 'system' }"
          @click="applyThemeMode('system')"
        >
          <n-icon :component="DesktopOutline" :size="20" />
          <span>{{ t('appearance.followSystem') }}</span>
        </button>
        <button
          type="button"
          class="theme-mode"
          :class="{ active: themeMode === 'light' }"
          @click="applyThemeMode('light')"
        >
          <n-icon :component="SunnyOutline" :size="20" />
          <span>{{ t('appearance.light') }}</span>
        </button>
        <button
          type="button"
          class="theme-mode"
          :class="{ active: themeMode === 'dark' }"
          @click="applyThemeMode('dark')"
        >
          <n-icon :component="MoonOutline" :size="20" />
          <span>{{ t('appearance.dark') }}</span>
        </button>
      </div>
    </section>

    <section class="group-card">
      <div class="group-head"><span>{{ t('appearance.accent') }}</span></div>
      <div class="accent-row">
        <button
          v-for="c in ACCENT_PRESETS"
          :key="c.id"
          type="button"
          class="accent-dot"
          :class="{ active: accentColor === c.id, rainbow: c.id === 'rainbow' }"
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
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.theme-mode-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 4px 16px 16px;
}

.theme-mode {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 8px;
  border: 1.5px solid var(--lx-border-light);
  border-radius: 10px;
  background: var(--lx-bg-card);
  color: var(--lx-text-secondary);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, box-shadow 0.2s;
}

.theme-mode:hover {
  border-color: var(--lx-accent);
  color: var(--lx-text-body);
}

.theme-mode.active {
  border-color: var(--lx-accent);
  color: var(--lx-accent);
  box-shadow: 0 0 0 3px var(--lx-accent-soft);
}

.theme-mode span {
  font-size: 12px;
  font-weight: 500;
}

.accent-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 4px 18px 18px;
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
  transition: transform 0.15s, box-shadow 0.15s;
}

.accent-dot:hover {
  transform: scale(1.08);
}

.accent-dot.active {
  box-shadow: 0 0 0 2px var(--lx-bg-card), 0 0 0 4px var(--lx-accent);
}

.accent-dot.rainbow {
  background: conic-gradient(
    #ff6b6b,
    #feca57,
    #48dbfb,
    #ff9ff3,
    #54a0ff,
    #ff6b6b
  );
}

.accent-check {
  color: #fff;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.35));
}
</style>
