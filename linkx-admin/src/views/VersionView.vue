<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NDescriptions, NDescriptionsItem, NSpin, NTag } from 'naive-ui'
import { fetchSettings, type AdminSetting } from '@/api/settings'

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
    <div class="page-header">
      <h1 class="page-title">版本管理</h1>
    </div>
    <NSpin :show="loading">
      <div v-if="settings" class="page-card">
        <NDescriptions label-placement="left" :column="1" bordered>
          <NDescriptionsItem label="当前版本">{{ settings.appVersion || '-' }}</NDescriptionsItem>
          <NDescriptionsItem label="发布渠道">
            <NTag size="small" type="info">{{ settings.appChannel || '-' }}</NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="下载地址">{{ settings.downloadUrl || '-' }}</NDescriptionsItem>
          <NDescriptionsItem label="更新说明">{{ settings.releaseNotes || '-' }}</NDescriptionsItem>
        </NDescriptions>
        <p class="hint">版本信息来自服务端配置（linkx.app.*），修改需更新配置并重启服务。</p>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.hint {
  margin-top: 16px;
  color: var(--n-text-color-3, #999);
  font-size: 13px;
  line-height: 1.5;
}
</style>
