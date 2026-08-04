<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NTooltip } from 'naive-ui'
import { usePreferencesStore } from '@/stores/preferences'

const { t } = useI18n()
const prefs = usePreferencesStore()

const label = computed(() => (prefs.locale === 'zh-CN' ? '中' : 'EN'))

function toggle() {
  prefs.toggleLocale()
}
</script>

<template>
  <NTooltip>
    <template #trigger>
      <NButton
        quaternary
        circle
        class="lx-float-btn header-action-btn lang-switch-btn"
        aria-label="language"
        @click="toggle"
      >
        {{ label }}
      </NButton>
    </template>
    {{ prefs.locale === 'zh-CN' ? t('layout.langEn') : t('layout.langZh') }}
  </NTooltip>
</template>

<style scoped>
.lang-switch-btn {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
</style>
