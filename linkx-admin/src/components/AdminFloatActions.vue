<!-- 作者：yangleduo -->
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NFloatButton, NFloatButtonGroup, NIcon, NTooltip } from 'naive-ui'
import {
  ArrowUpOutline,
  EllipsisHorizontalOutline,
  RefreshOutline,
  WaterOutline,
} from '@vicons/ionicons5'
import { usePreferencesStore } from '@/stores/preferences'

const router = useRouter()
const { t } = useI18n()
const prefs = usePreferencesStore()
const refreshing = ref(false)

function scrollTop() {
  const el = document.querySelector('.main-content .n-scrollbar-container')
  if (el) {
    el.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  const content = document.querySelector('.main-content')
  if (content) {
    content.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function refresh() {
  if (refreshing.value) return
  refreshing.value = true
  router.go(0)
}

function toggleWatermark() {
  prefs.setWatermarkEnabled(!prefs.watermarkEnabled)
}
</script>

<template>
  <NFloatButtonGroup class="admin-fab" :right="20" :bottom="20" shape="circle">
    <NFloatButton type="primary" menu-trigger="click">
      <NIcon :component="EllipsisHorizontalOutline" />
      <template #menu>
        <NTooltip placement="left" trigger="hover">
          <template #trigger>
            <NFloatButton type="primary" @click="scrollTop">
              <NIcon :component="ArrowUpOutline" />
            </NFloatButton>
          </template>
          {{ t('layout.backTop') }}
        </NTooltip>
        <NTooltip placement="left" trigger="hover">
          <template #trigger>
            <NFloatButton @click="refresh">
              <NIcon :component="RefreshOutline" />
            </NFloatButton>
          </template>
          {{ t('common.refresh') }}
        </NTooltip>
        <NTooltip placement="left" trigger="hover">
          <template #trigger>
            <NFloatButton
              :type="prefs.watermarkEnabled ? 'primary' : 'default'"
              @click="toggleWatermark"
            >
              <NIcon :component="WaterOutline" />
            </NFloatButton>
          </template>
          {{ prefs.watermarkEnabled ? t('layout.watermarkOff') : t('layout.watermarkOn') }}
        </NTooltip>
      </template>
    </NFloatButton>
  </NFloatButtonGroup>
</template>

<style scoped>
.admin-fab {
  z-index: 55 !important;
}
</style>
