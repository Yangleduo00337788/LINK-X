<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
  NButton,
  NDrawer,
  NDrawerContent,
  NIcon,
  NInput,
  NSelect,
  NSwitch,
  NTooltip,
} from 'naive-ui'
import { CheckmarkOutline, ColorPaletteOutline } from '@vicons/ionicons5'
import { usePreferencesStore } from '@/stores/preferences'
import {
  THEME_COLOR_PRESETS,
  type AppearancePreset,
  type FormContainerStyle,
  type LayoutMode,
} from '@/theme/presets'

const { t } = useI18n()
const prefs = usePreferencesStore()
const show = ref(false)

const appearanceOptions: { key: AppearancePreset; nav: 'dark' | 'light'; body: 'light' | 'dark' }[] = [
  { key: 'mixed', nav: 'dark', body: 'light' },
  { key: 'light', nav: 'light', body: 'light' },
  { key: 'dark', nav: 'dark', body: 'dark' },
]

const layoutOptions: { key: LayoutMode }[] = [
  { key: 'side' },
  { key: 'top' },
  { key: 'mix' },
]

const formStyleOptions = computed(() => [
  { label: t('themeConfig.formDrawer'), value: 'drawer' },
  { label: t('themeConfig.formModal'), value: 'modal' },
])
</script>

<template>
  <div class="theme-config-trigger">
    <NTooltip :disabled="show">
      <template #trigger>
        <NButton
          quaternary
          circle
          class="lx-float-btn header-action-btn"
          aria-label="theme-config"
          @click="show = true"
        >
          <template #icon>
            <NIcon :component="ColorPaletteOutline" />
          </template>
        </NButton>
      </template>
      {{ t('layout.themeConfig') }}
    </NTooltip>

    <NDrawer v-model:show="show" :width="340" placement="right" class="theme-config-drawer">
      <NDrawerContent :title="t('layout.themeConfig')" closable :native-scrollbar="false">
        <div class="theme-drawer-body">
          <section class="theme-section">
            <div class="theme-section-title">{{ t('themeConfig.appearance') }}</div>
            <div class="thumb-grid thumb-grid--3">
              <button
                v-for="item in appearanceOptions"
                :key="item.key"
                type="button"
                class="thumb-card"
                :class="{ active: prefs.appearancePreset === item.key }"
                :aria-label="t(`themeConfig.appearance_${item.key}`)"
                @click="prefs.setAppearancePreset(item.key)"
              >
                <div class="layout-thumb layout-thumb--appearance">
                  <div
                    class="layout-thumb-nav"
                    :class="item.nav === 'dark' ? 'is-dark' : 'is-light'"
                  />
                  <div class="layout-thumb-main">
                    <div
                      class="layout-thumb-header"
                      :class="item.body === 'dark' ? 'is-dark' : 'is-light'"
                    />
                    <div
                      class="layout-thumb-content"
                      :class="item.body === 'dark' ? 'is-dark' : 'is-light'"
                    />
                  </div>
                </div>
                <span v-if="prefs.appearancePreset === item.key" class="thumb-check">
                  <NIcon :component="CheckmarkOutline" />
                </span>
              </button>
            </div>
          </section>

          <section class="theme-section">
            <div class="theme-section-title">{{ t('themeConfig.layout') }}</div>
            <div class="thumb-grid thumb-grid--3">
              <button
                v-for="item in layoutOptions"
                :key="item.key"
                type="button"
                class="thumb-card"
                :class="{ active: prefs.layoutMode === item.key }"
                :aria-label="t(`themeConfig.layout_${item.key}`)"
                @click="prefs.setLayoutMode(item.key)"
              >
                <div class="layout-thumb" :class="`layout-thumb--${item.key}`">
                  <template v-if="item.key === 'side'">
                    <div class="layout-thumb-nav is-dark" />
                    <div class="layout-thumb-main">
                      <div class="layout-thumb-header is-light" />
                      <div class="layout-thumb-content is-light" />
                    </div>
                  </template>
                  <template v-else-if="item.key === 'top'">
                    <div class="layout-thumb-header is-dark full" />
                    <div class="layout-thumb-content is-light full" />
                  </template>
                  <template v-else>
                    <div class="layout-thumb-header is-dark full" />
                    <div class="layout-thumb-body-row">
                      <div class="layout-thumb-nav is-dark short" />
                      <div class="layout-thumb-content is-light" />
                    </div>
                  </template>
                </div>
                <span v-if="prefs.layoutMode === item.key" class="thumb-check">
                  <NIcon :component="CheckmarkOutline" />
                </span>
              </button>
            </div>
          </section>

          <section class="theme-section">
            <div class="theme-section-title">{{ t('themeConfig.primaryColor') }}</div>
            <div class="color-grid">
              <button
                v-for="preset in THEME_COLOR_PRESETS"
                :key="preset.key"
                type="button"
                class="color-swatch"
                :style="{ background: preset.color }"
                :aria-label="preset.key"
                @click="prefs.setPrimaryColor(preset.color)"
              >
                <NIcon
                  v-if="prefs.primaryColor === preset.color"
                  :component="CheckmarkOutline"
                  class="color-swatch-check"
                />
              </button>
            </div>
            <div class="switch-row">
              <span>{{ t('themeConfig.headerThemeColor') }}</span>
              <NSwitch
                :value="prefs.headerThemeColor"
                size="small"
                @update:value="prefs.setHeaderThemeColor"
              />
            </div>
            <div class="switch-row">
              <span>{{ t('themeConfig.headerThemeFull') }}</span>
              <NSwitch
                :value="prefs.headerThemeFull"
                size="small"
                :disabled="!prefs.headerThemeColor"
                @update:value="prefs.setHeaderThemeFull"
              />
            </div>
          </section>

          <section class="theme-section theme-section--switches">
            <div class="switch-row">
              <span>{{ t('themeConfig.moduleDock') }}</span>
              <NSwitch
                :value="prefs.moduleDockEnabled"
                size="small"
                @update:value="prefs.setModuleDockEnabled"
              />
            </div>
            <div class="switch-row">
              <span>{{ t('themeConfig.breadcrumb') }}</span>
              <NSwitch
                :value="prefs.breadcrumbEnabled"
                size="small"
                @update:value="prefs.setBreadcrumbEnabled"
              />
            </div>
            <div class="switch-row">
              <span>{{ t('themeConfig.multiTab') }}</span>
              <NSwitch
                :value="prefs.multiTabEnabled"
                size="small"
                @update:value="prefs.setMultiTabEnabled"
              />
            </div>
            <div class="switch-row">
              <span>{{ t('themeConfig.siderCollapsed') }}</span>
              <NSwitch
                :value="prefs.siderCollapsedDefault"
                size="small"
                @update:value="prefs.setSiderCollapsedDefault"
              />
            </div>
            <div class="switch-row">
              <span>{{ t('themeConfig.menuAccordion') }}</span>
              <NSwitch
                :value="prefs.menuAccordion"
                size="small"
                @update:value="prefs.setMenuAccordion"
              />
            </div>
            <div class="switch-row">
              <span>{{ t('setting.watermarkEnabled') }}</span>
              <NSwitch
                :value="prefs.watermarkEnabled"
                size="small"
                @update:value="prefs.setWatermarkEnabled"
              />
            </div>
            <div class="switch-row">
              <span>{{ t('themeConfig.footer') }}</span>
              <NSwitch
                :value="prefs.footerEnabled"
                size="small"
                @update:value="prefs.setFooterEnabled"
              />
            </div>
            <div v-if="prefs.footerEnabled" class="footer-text-block">
              <div class="theme-config-label">{{ t('themeConfig.footerTextLabel') }}</div>
              <NInput
                :value="prefs.footerText"
                size="small"
                type="textarea"
                :rows="2"
                :placeholder="t('themeConfig.footerTextPh')"
                @update:value="prefs.setFooterText"
              />
            </div>
            <div class="switch-row">
              <span>{{ t('themeConfig.rounded') }}</span>
              <NSwitch
                :value="prefs.roundedCorners"
                size="small"
                @update:value="prefs.setRoundedCorners"
              />
            </div>
            <div class="switch-row switch-row--select">
              <span>{{ t('themeConfig.formStyle') }}</span>
              <NSelect
                :value="prefs.formStyle"
                size="small"
                :options="formStyleOptions"
                class="form-style-select"
                @update:value="(v) => prefs.setFormStyle(v as FormContainerStyle)"
              />
            </div>
            <div class="switch-row">
              <span>{{ t('themeConfig.grayscale') }}</span>
              <NSwitch
                :value="prefs.grayscaleMode"
                size="small"
                @update:value="prefs.setGrayscaleMode"
              />
            </div>
          </section>

          <NAlert type="warning" :bordered="false" class="theme-tip">
            {{ t('themeConfig.tip') }}
          </NAlert>
        </div>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
.theme-config-trigger {
  display: inline-flex;
}


.theme-drawer-body {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding-bottom: 12px;
}

.theme-section-title {
  font-size: 13px;
  color: var(--lx-text-2);
  margin-bottom: 12px;
}

.thumb-grid {
  display: grid;
  gap: 12px;
}

.thumb-grid--3 {
  grid-template-columns: repeat(3, 1fr);
}

.thumb-card {
  position: relative;
  padding: 6px;
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  background: var(--lx-card);
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.thumb-card:hover:not(.disabled) {
  border-color: var(--lx-oa-blue);
}

.thumb-card.active {
  border-color: var(--lx-oa-blue);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--lx-oa-blue) 35%, transparent);
}

.thumb-card.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.thumb-check {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--lx-oa-blue);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.layout-thumb {
  display: flex;
  height: 48px;
  border-radius: 2px;
  overflow: hidden;
  background: #f0f2f5;
}

.layout-thumb--appearance {
  flex-direction: row;
}

.layout-thumb--top {
  flex-direction: column;
}

.layout-thumb--mix {
  flex-direction: column;
}

.layout-thumb-nav {
  width: 28%;
  min-width: 22px;
}

.layout-thumb-nav.short {
  width: 22%;
  min-width: 18px;
}

.layout-thumb-nav.is-dark {
  background: #001529;
}

.layout-thumb-nav.is-light {
  background: #ffffff;
  border-right: 1px solid #e8e8e8;
}

.layout-thumb-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.layout-thumb-body-row {
  flex: 1;
  display: flex;
  min-height: 0;
}

.layout-thumb-header {
  height: 10px;
  flex-shrink: 0;
}

.layout-thumb-header.full {
  width: 100%;
}

.layout-thumb-header.is-dark {
  background: #001529;
}

.layout-thumb-header.is-light {
  background: #ffffff;
  border-bottom: 1px solid #f0f0f0;
}

.layout-thumb-content {
  flex: 1;
  min-height: 0;
}

.layout-thumb-content.full {
  width: 100%;
}

.layout-thumb-content.is-light {
  background: #f5f5f5;
}

.layout-thumb-content.is-dark {
  background: #141414;
}

.color-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.color-swatch {
  width: 100%;
  aspect-ratio: 1;
  border: none;
  border-radius: var(--lx-radius);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: transform 0.12s ease;
}

.color-swatch:hover {
  transform: scale(1.06);
}

.color-swatch-check {
  font-size: 16px;
}

.theme-section--switches {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 36px;
  font-size: 13px;
  color: var(--lx-text);
}

.switch-row--select {
  align-items: center;
}

.form-style-select {
  width: 120px;
}

.footer-text-block {
  margin: 4px 0 10px;
}

.theme-config-label {
  font-size: 12px;
  color: var(--lx-text-2);
  margin-bottom: 8px;
}

.theme-tip {
  font-size: 12px;
  line-height: 1.6;
}
</style>
