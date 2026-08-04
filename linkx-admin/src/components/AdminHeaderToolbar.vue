<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { NButton, NIcon, NTooltip } from 'naive-ui'
import { ContractOutline, ExpandOutline } from '@vicons/ionicons5'
import { useFullscreen } from '@/composables/useFullscreen'
import LangSwitcher from '@/components/LangSwitcher.vue'
import ThemeConfigDrawer from '@/components/ThemeConfigDrawer.vue'

const { t } = useI18n()
const { isFullscreen, toggle: toggleFullscreen } = useFullscreen()
</script>

<template>
  <div class="admin-header-toolbar">
    <NTooltip>
      <template #trigger>
        <NButton
          quaternary
          circle
          class="lx-float-btn header-action-btn"
          aria-label="fullscreen"
          @click="toggleFullscreen"
        >
          <template #icon>
            <NIcon :component="isFullscreen ? ContractOutline : ExpandOutline" />
          </template>
        </NButton>
      </template>
      {{ isFullscreen ? t('layout.exitFullscreen') : t('layout.fullscreen') }}
    </NTooltip>
    <LangSwitcher />
    <ThemeConfigDrawer />
  </div>
</template>

<style scoped>
.admin-header-toolbar {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
