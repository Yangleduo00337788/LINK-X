<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 举报面板（好友 / 群聊共用）。
 * <p>
 * 选择举报原因、填写说明并可选上传截图证据，提交至反馈接口。
 * </p>
 */
import { ref, computed, onUnmounted } from 'vue'
import { NRadio, NRadioGroup, useMessage } from 'naive-ui'
import * as feedbackApi from '../../api/feedback'
import * as momentsApi from '../../api/moments'
import { useI18n } from '../../i18n'
import { LxButton, LxIconButton } from '../ui'

const props = withDefaults(
  defineProps<{
    /** group：群聊举报；user：好友举报 */
    targetKind?: 'group' | 'user'
    targetId?: string
    targetName?: string
  }>(),
  { targetKind: 'group', targetId: '', targetName: '' }
)

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'submitted'): void
}>()

const { t } = useI18n()
const message = useMessage()

interface EvidenceItem {
  /** MinIO object key（提交用） */
  key: string
  /** 本地预览地址（blob:） */
  previewUrl: string
}

const reason = ref('spam')
const detail = ref('')
const evidenceItems = ref<EvidenceItem[]>([])
const uploading = ref(false)
const submitting = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

onUnmounted(() => {
  for (const item of evidenceItems.value) {
    if (item.previewUrl.startsWith('blob:')) URL.revokeObjectURL(item.previewUrl)
  }
})

const isGroup = computed(() => props.targetKind !== 'user')

const reasonOptions = computed(() => [
  { value: 'spam', label: t('modals.reportReasonSpam') },
  { value: 'harassment', label: t('modals.reportReasonHarassment') },
  { value: 'fraud', label: t('modals.reportReasonFraud') },
  { value: 'porn', label: t('modals.reportReasonPorn') },
  { value: 'illegal', label: t('modals.reportReasonIllegal') },
  { value: 'other', label: t('modals.reportReasonOther') }
])

const reasonLabel = computed(() => {
  return reasonOptions.value.find(o => o.value === reason.value)?.label || reason.value
})

const panelTitle = computed(() =>
  isGroup.value ? t('modals.reportTitle') : t('modals.reportUserTitle')
)

const hintText = computed(() =>
  isGroup.value
    ? t('modals.reportGroupHint', { name: props.targetName || t('modals.groupChat') })
    : t('modals.reportUserHint', { name: props.targetName || t('modals.userFallback') })
)

function pickEvidence() {
  fileInputRef.value?.click()
}

async function onEvidenceSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return
  if (evidenceItems.value.length + files.length > 6) {
    message.warning(t('modals.reportEvidenceMax'))
    return
  }
  uploading.value = true
  try {
    for (const file of files) {
      if (!file.type.startsWith('image/')) {
        message.warning(t('modals.reportEvidenceImageOnly'))
        continue
      }
      if (file.size > 8 * 1024 * 1024) {
        message.warning(t('modals.reportEvidenceTooLarge'))
        continue
      }
      const res = await momentsApi.uploadMomentsImage(file)
      if (res.code === 200 && res.data) {
        // 上传接口只返回 object key，预览用本地 blob，避免裂图
        evidenceItems.value.push({
          key: res.data,
          previewUrl: URL.createObjectURL(file)
        })
      } else {
        message.error(res.message || t('modals.reportEvidenceFail'))
      }
    }
  } catch {
    message.error(t('modals.reportEvidenceFail'))
  } finally {
    uploading.value = false
  }
}

function removeEvidence(idx: number) {
  const item = evidenceItems.value[idx]
  if (item?.previewUrl.startsWith('blob:')) URL.revokeObjectURL(item.previewUrl)
  evidenceItems.value.splice(idx, 1)
}

async function submit() {
  if (!reason.value) {
    message.warning(t('modals.reportNeedReason'))
    return
  }
  const text = detail.value.trim()
  if (!text && evidenceItems.value.length === 0) {
    message.warning(t('modals.reportNeedEvidence'))
    return
  }

  const keys = evidenceItems.value.map(i => i.key)
  const lines = isGroup.value
    ? [
        `[举报群聊]`,
        `群ID: ${props.targetId}`,
        `群名称: ${props.targetName || '-'}`,
        `原因: ${reasonLabel.value}`,
        `说明: ${text || '-'}`,
        keys.length
          ? `证据图片:\n${keys.map((u, i) => `${i + 1}. ${u}`).join('\n')}`
          : '证据图片: 无'
      ]
    : [
        `[举报用户]`,
        `用户ID: ${props.targetId}`,
        `用户名称: ${props.targetName || '-'}`,
        `原因: ${reasonLabel.value}`,
        `说明: ${text || '-'}`,
        keys.length
          ? `证据图片:\n${keys.map((u, i) => `${i + 1}. ${u}`).join('\n')}`
          : '证据图片: 无'
      ]

  submitting.value = true
  try {
    const res = await feedbackApi.submitFeedback({
      type: 'other',
      content: lines.join('\n')
    })
    if (res.code === 200) {
      message.success(t('modals.reportOk'))
      emit('submitted')
    } else {
      message.error(res.message || t('modals.reportFail'))
    }
  } catch (e) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('modals.reportFail'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="report-panel">
    <div class="report-head">
      <LxIconButton class="report-back" :title="t('common.back')" @click="emit('back')">‹</LxIconButton>
      <h3>{{ panelTitle }}</h3>
    </div>

    <div class="report-scroll">
      <p class="hint">{{ hintText }}</p>

      <section class="section">
        <h4>{{ t('modals.reportReason') }}</h4>
        <n-radio-group v-model:value="reason" name="report-reason" class="reason-list">
          <n-radio
            v-for="opt in reasonOptions"
            :key="opt.value"
            :value="opt.value"
            class="reason-item"
          >
            {{ opt.label }}
          </n-radio>
        </n-radio-group>
      </section>

      <section class="section">
        <h4>{{ t('modals.reportDetail') }}</h4>
        <textarea
          v-model="detail"
          class="detail-input"
          rows="4"
          maxlength="500"
          :placeholder="t('modals.reportDetailPh')"
        />
      </section>

      <section class="section">
        <h4>{{ t('modals.reportEvidence') }}</h4>
        <p class="sub-hint">{{ t('modals.reportEvidenceHint') }}</p>
        <div class="evidence-grid">
          <div v-for="(item, idx) in evidenceItems" :key="item.key" class="evidence-item">
            <img :src="item.previewUrl" alt="" />
            <button type="button" class="lx-remove-badge lx-remove-badge--sm" @click="removeEvidence(idx)">×</button>
          </div>
          <button
            v-if="evidenceItems.length < 6"
            type="button"
            class="add-evidence"
            :disabled="uploading"
            @click="pickEvidence"
          >
            {{ uploading ? t('modals.reportUploading') : '+' }}
          </button>
        </div>
        <input
          ref="fileInputRef"
          type="file"
          accept="image/*"
          multiple
          class="hidden-input"
          @change="onEvidenceSelected"
        />
      </section>

      <LxButton
        variant="submit-block"
        :disabled="submitting || uploading"
        @click="submit"
      >
        {{ submitting ? t('modals.reportSubmitting') : t('modals.reportSubmit') }}
      </LxButton>
    </div>
  </div>
</template>

<style scoped>
.report-panel {
  position: absolute;
  inset: 0;
  z-index: var(--lx-z-raised-2);
  display: flex;
  flex-direction: column;
  background: var(--lx-bg-card);
}

.report-head {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  padding: var(--lx-space-xl) var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border);
  flex-shrink: 0;
}

.report-head h3 {
  margin: 0;
  font-size: var(--lx-font-xl);
  font-weight: 600;
}

.report-back {
  font-size: var(--lx-font-5xl);
  width: 28px;
  height: 28px;
  color: var(--lx-text-primary);
}

.report-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-xl) var(--lx-space-2xl) var(--lx-space-4xl);
}

.hint {
  margin: 0 0 var(--lx-space-2xl);
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading);
}

.section {
  margin-bottom: var(--lx-space-2xl);
}

.section h4 {
  margin: 0 0 var(--lx-space-md);
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-primary);
}

.reason-list {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-md);
}

.reason-item {
  font-size: var(--lx-font);
}

.detail-input {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  min-height: 88px;
  padding: var(--lx-space-md) var(--lx-space-lg);
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius-sm);
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-primary);
  background: var(--lx-bg-card);
  font-family: inherit;
}

.sub-hint {
  margin: 0 0 var(--lx-space-md);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.evidence-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lx-space);
}

.evidence-item {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: var(--lx-radius-sm);
  overflow: hidden;
  background: var(--lx-bg-panel);
}

.evidence-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.add-evidence {
  width: 72px;
  height: 72px;
  border: 1px dashed var(--lx-border);
  border-radius: var(--lx-radius-sm);
  background: transparent;
  color: var(--lx-text-muted);
  font-size: var(--lx-font-6xl);
  cursor: pointer;
}

.add-evidence:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.hidden-input {
  display: none;
}
</style>
