<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NForm,
  NFormItem,
  NInput,
  NSlider,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  useMessage,
} from 'naive-ui'
import { storeToRefs } from 'pinia'
import { fetchSettings, type AdminSetting } from '@/api/settings'
import { usePreferencesStore } from '@/stores/preferences'

const { t } = useI18n()
const message = useMessage()
const prefs = usePreferencesStore()
const { watermarkEnabled, watermarkFullscreen, watermarkLines, watermarkOpacity } =
  storeToRefs(prefs)

const loading = ref(false)
const settings = ref<AdminSetting | null>(null)
const linesText = ref(watermarkLines.value.join('\n'))

watch(watermarkLines, (lines) => {
  linesText.value = lines.join('\n')
})

const lineCount = computed(() =>
  linesText.value
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean).length,
)

const opacityPercent = computed({
  get: () => Math.round(watermarkOpacity.value * 100),
  set: (v: number) => prefs.setWatermarkOpacity(v / 100),
})

function formatBytes(bytes?: number) {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function saveWatermark() {
  const lines = linesText.value
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
  prefs.setWatermarkLines(lines)
  message.success(t('setting.watermarkSaved'))
}

function resetWatermark() {
  linesText.value = ''
  prefs.setWatermarkLines([])
  prefs.setWatermarkOpacity(0.12)
  message.success(t('setting.watermarkReset'))
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
    <div class="page-shell">
      <NSpin :show="loading">
        <NDescriptions v-if="settings" label-placement="left" :column="1" bordered>
          <NDescriptionsItem :label="t('setting.captcha')">
            <NTag :type="settings.captchaEnabled ? 'success' : 'default'" size="small">
              {{ settings.captchaEnabled ? t('common.on') : t('common.off') }}
            </NTag>
          </NDescriptionsItem>
          <NDescriptionsItem :label="t('setting.appVersion')">{{ settings.appVersion || '-' }}</NDescriptionsItem>
          <NDescriptionsItem :label="t('setting.channel')">{{ settings.appChannel || '-' }}</NDescriptionsItem>
          <NDescriptionsItem :label="t('setting.downloadUrl')">{{ settings.downloadUrl || '-' }}</NDescriptionsItem>
          <NDescriptionsItem :label="t('setting.maxUpload')">{{ formatBytes(settings.maxUploadBytes) }}</NDescriptionsItem>
          <NDescriptionsItem :label="t('setting.releaseNotes')">{{ settings.releaseNotes || '-' }}</NDescriptionsItem>
        </NDescriptions>
      </NSpin>
    </div>

    <div class="page-shell">
      <h3 class="section-title">{{ t('setting.watermarkTitle') }}</h3>
      <p class="section-hint">{{ t('setting.watermarkHint') }}</p>
      <NForm label-placement="left" label-width="120">
        <NFormItem :label="t('setting.watermarkEnabled')">
          <NSwitch
            :value="watermarkEnabled"
            @update:value="prefs.setWatermarkEnabled"
          />
        </NFormItem>
        <NFormItem :label="t('setting.watermarkFullscreen')">
          <NSwitch
            :value="watermarkFullscreen"
            :disabled="!watermarkEnabled"
            @update:value="prefs.setWatermarkFullscreen"
          />
        </NFormItem>
        <NFormItem :label="t('setting.watermarkOpacity')">
          <div class="opacity-row">
            <NSlider
              v-model:value="opacityPercent"
              :min="2"
              :max="50"
              :step="1"
              :disabled="!watermarkEnabled"
              style="flex: 1"
            />
            <span class="opacity-value">{{ opacityPercent }}%</span>
          </div>
        </NFormItem>
        <NFormItem :label="t('setting.watermarkLines')">
          <NInput
            v-model:value="linesText"
            type="textarea"
            :rows="4"
            :placeholder="t('setting.watermarkLinesPlaceholder')"
            :disabled="!watermarkEnabled"
          />
        </NFormItem>
        <NFormItem :label="t('setting.watermarkLineCount')">
          <span>{{ lineCount || t('setting.watermarkDefault') }}</span>
        </NFormItem>
        <NFormItem>
          <NSpace>
            <NButton type="primary" class="lx-float-btn" :disabled="!watermarkEnabled" @click="saveWatermark">
              {{ t('common.save') }}
            </NButton>
            <NButton class="lx-float-btn" :disabled="!watermarkEnabled" @click="resetWatermark">
              {{ t('setting.watermarkResetBtn') }}
            </NButton>
          </NSpace>
        </NFormItem>
      </NForm>
    </div>
  </div>
</template>

<style scoped>
.section-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
}
.section-hint {
  margin: 0 0 16px;
  color: var(--lx-text-3);
  font-size: 13px;
}
.opacity-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 420px;
}
.opacity-value {
  width: 42px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--lx-text-2);
}
</style>
