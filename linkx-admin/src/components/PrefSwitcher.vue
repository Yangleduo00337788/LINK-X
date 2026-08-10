<!-- 作者：yangleduo -->
<script setup lang="ts">
import { NButton, NButtonGroup, NIcon, NTooltip } from 'naive-ui'
import { MoonOutline, SunnyOutline } from '@vicons/ionicons5'
import { useI18n } from 'vue-i18n'
import { usePreferencesStore } from '@/stores/preferences'

defineProps<{
  compact?: boolean
}>()

const { t } = useI18n()
const prefs = usePreferencesStore()
</script>

<template>
  <div class="pref-switcher">
    <NButtonGroup size="small">
      <NTooltip>
        <template #trigger>
          <NButton
            quaternary
            :type="prefs.theme === 'dark' ? 'primary' : 'default'"
            @click="prefs.setTheme('dark')"
          >
            <template #icon>
              <NIcon :component="MoonOutline" />
            </template>
            <span v-if="!compact">{{ t('layout.themeDark') }}</span>
          </NButton>
        </template>
        {{ t('layout.themeDark') }}
      </NTooltip>
      <NTooltip>
        <template #trigger>
          <NButton
            quaternary
            :type="prefs.theme === 'light' ? 'primary' : 'default'"
            @click="prefs.setTheme('light')"
          >
            <template #icon>
              <NIcon :component="SunnyOutline" />
            </template>
            <span v-if="!compact">{{ t('layout.themeLight') }}</span>
          </NButton>
        </template>
        {{ t('layout.themeLight') }}
      </NTooltip>
    </NButtonGroup>
    <NButtonGroup size="small">
      <NButton
        quaternary
        :type="prefs.locale === 'zh-CN' ? 'primary' : 'default'"
        @click="prefs.setLocale('zh-CN')"
      >
        {{ t('layout.langZh') }}
      </NButton>
      <NButton
        quaternary
        :type="prefs.locale === 'en-US' ? 'primary' : 'default'"
        @click="prefs.setLocale('en-US')"
      >
        {{ t('layout.langEn') }}
      </NButton>
    </NButtonGroup>
  </div>
</template>

<style scoped>
.pref-switcher {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
