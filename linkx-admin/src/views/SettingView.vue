<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NDescriptions, NDescriptionsItem, NSpin, NTag } from 'naive-ui'
import { fetchSettings, type AdminSetting } from '@/api/settings'

const loading = ref(false)
const settings = ref<AdminSetting | null>(null)

function formatBytes(bytes?: number) {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

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
    <div class="page-header">
      <h1 class="page-title">系统配置</h1>
    </div>
    <NSpin :show="loading">
      <div v-if="settings" class="page-card">
        <NDescriptions label-placement="left" :column="1" bordered>
          <NDescriptionsItem label="验证码">
            <NTag :type="settings.captchaEnabled ? 'success' : 'default'" size="small">
              {{ settings.captchaEnabled ? '已开启' : '已关闭' }}
            </NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="应用版本">{{ settings.appVersion || '-' }}</NDescriptionsItem>
          <NDescriptionsItem label="渠道">{{ settings.appChannel || '-' }}</NDescriptionsItem>
          <NDescriptionsItem label="下载地址">{{ settings.downloadUrl || '-' }}</NDescriptionsItem>
          <NDescriptionsItem label="最大上传">{{ formatBytes(settings.maxUploadBytes) }}</NDescriptionsItem>
          <NDescriptionsItem label="更新说明">{{ settings.releaseNotes || '-' }}</NDescriptionsItem>
        </NDescriptions>
      </div>
    </NSpin>
  </div>
</template>
