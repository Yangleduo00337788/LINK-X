<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NDescriptions, NDescriptionsItem, NSpin, NTag } from 'naive-ui'
import { fetchSettings, type AdminSetting } from '@/api/settings'

const { t } = useI18n()
const loading = ref(false)
const settings = ref<AdminSetting | null>(null)

onMounted(async () => {
  loading.value = true
  try {
    settings.value = await fetchSettings()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpin :show="loading">
        <template v-if="settings">
          <NDescriptions label-placement="left" :column="1" bordered>
            <NDescriptionsItem :label="t('version.currentVersion')">{{ settings.appVersion || '-' }}</NDescriptionsItem>
            <NDescriptionsItem :label="t('version.channel')">
              <NTag size="small" type="info">{{ settings.appChannel || '-' }}</NTag>
            </NDescriptionsItem>
            <NDescriptionsItem :label="t('version.downloadUrl')">{{ settings.downloadUrl || '-' }}</NDescriptionsItem>
            <NDescriptionsItem :label="t('version.releaseNotes')">{{ settings.releaseNotes || '-' }}</NDescriptionsItem>
          </NDescriptions>
          <p class="hint">{{ t('version.hint') }}</p>
        </template>
      </NSpin>
    </div>
  </div>
</template>

<style scoped>
.hint {
  margin-top: 16px;
  color: var(--lx-text-3);
  font-size: 13px;
  line-height: 1.5;
}
</style>
