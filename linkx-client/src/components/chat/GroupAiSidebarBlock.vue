<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群聊侧栏「群聊小助手」设置：群主/管理员可开启接入、主动发言与智能总结。
 */
import { computed, ref } from 'vue'
import { NInput, NSwitch, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'
import LinkMateLogoMark from '../LinkMateLogoMark.vue'

const props = defineProps<{
  canManage: boolean
  /** 嵌入群资料抽屉时去掉侧栏专用底边与背景 */
  embedded?: boolean
}>()

const { t } = useI18n()
const message = useMessage()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const { currentSessionId } = storeToRefs(appStore)

const enableLoading = ref(false)
const proactiveLoading = ref(false)
const summaryLoading = ref(false)
const topicsDraft = ref('')
const summaryDraft = ref('')
let topicsTimer: ReturnType<typeof setTimeout> | undefined
let summaryTimer: ReturnType<typeof setTimeout> | undefined

const sessionId = computed(() => currentSessionId.value || '')

const prefs = computed(() => groupMetaStore.groupAiPrefsFor(sessionId.value))

const assistantEnabled = computed(() => {
  const id = sessionId.value
  if (!id) return false
  if (!groupMetaStore.linkmateStateLoaded(id)) return true
  return groupMetaStore.linkmateEnabledFor(id)
})

function extractError(e: unknown): string {
  const ax = e as { response?: { data?: { message?: string } }; message?: string }
  return ax.response?.data?.message || ax.message || t('groupAi.groupUpdateFail')
}

async function onEnableToggle(enabled: boolean) {
  const id = sessionId.value
  if (!id || !props.canManage || enableLoading.value) return
  enableLoading.value = true
  try {
    await groupMetaStore.updateLinkmateEnabled(id, enabled)
    message.success(enabled ? t('groupAi.groupOn') : t('groupAi.groupOff'))
  } catch (e: unknown) {
    message.error(extractError(e))
  } finally {
    enableLoading.value = false
  }
}

async function onProactiveToggle(enabled: boolean) {
  const id = sessionId.value
  if (!id || !props.canManage || proactiveLoading.value) return
  proactiveLoading.value = true
  try {
    await groupMetaStore.updateGroupAiFeatures(id, { proactiveSpeak: enabled })
    message.success(t('groupAi.settingsSaved'))
  } catch (e: unknown) {
    message.error(extractError(e))
  } finally {
    proactiveLoading.value = false
  }
}

async function onSummaryToggle(enabled: boolean) {
  const id = sessionId.value
  if (!id || !props.canManage || summaryLoading.value) return
  summaryLoading.value = true
  try {
    await groupMetaStore.updateGroupAiFeatures(id, { smartSummary: enabled })
    message.success(t('groupAi.settingsSaved'))
  } catch (e: unknown) {
    message.error(extractError(e))
  } finally {
    summaryLoading.value = false
  }
}

function onTopicsInput(value: string) {
  topicsDraft.value = value
  const id = sessionId.value
  if (!id || !props.canManage) return
  if (topicsTimer) clearTimeout(topicsTimer)
  topicsTimer = setTimeout(async () => {
    try {
      await groupMetaStore.updateGroupAiFeatures(id, { interestTopics: value })
    } catch (e: unknown) {
      message.error(extractError(e))
    }
  }, 600)
}

function onSummaryInput(value: string) {
  summaryDraft.value = value
  const id = sessionId.value
  if (!id || !props.canManage) return
  if (summaryTimer) clearTimeout(summaryTimer)
  summaryTimer = setTimeout(async () => {
    try {
      await groupMetaStore.updateGroupAiFeatures(id, { summaryInstruction: value })
    } catch (e: unknown) {
      message.error(extractError(e))
    }
  }, 600)
}
</script>

<template>
  <section class="group-ai-block" :class="{ embedded }">
    <div class="ai-hero">
      <LinkMateLogoMark size="sm" />
      <div class="ai-copy">
        <span class="official-badge">{{ t('groupAi.official') }}</span>
        <h3 class="ai-title">{{ t('groupAi.assistantName') }}</h3>
      </div>
    </div>
    <p class="ai-intro">{{ t('groupAi.assistantIntro') }}</p>
    <p v-if="!canManage" class="ai-hint">{{ t('groupAi.memberReadonly') }}</p>

    <div class="ai-row">
      <div class="ai-text">
        <span class="ai-name">{{ t('groupAi.enableTitle') }}</span>
        <span class="ai-desc">{{ t('groupAi.enableDesc') }}</span>
      </div>
      <n-switch
        size="small"
        :value="assistantEnabled"
        :disabled="!canManage || enableLoading"
        :loading="enableLoading"
        @update:value="onEnableToggle"
      />
    </div>

    <template v-if="assistantEnabled">
      <div class="ai-row">
        <div class="ai-text">
          <span class="ai-name">{{ t('groupAi.proactiveTitle') }}</span>
          <span class="ai-desc">{{ t('groupAi.proactiveDesc') }}</span>
        </div>
        <n-switch
          size="small"
          :value="prefs.proactiveSpeak"
          :disabled="!canManage || proactiveLoading"
          :loading="proactiveLoading"
          @update:value="onProactiveToggle"
        />
      </div>
      <div v-if="prefs.proactiveSpeak" class="ai-extra">
        <n-input
          :value="topicsDraft || prefs.interestTopics"
          type="textarea"
          size="small"
          :disabled="!canManage"
          :placeholder="t('groupAi.topicsPh')"
          :maxlength="groupMetaStore.interestTopicsLimit()"
          :autosize="{ minRows: 2, maxRows: 3 }"
          @update:value="onTopicsInput"
        />
      </div>
      <div class="ai-row">
        <div class="ai-text">
          <span class="ai-name">{{ t('groupAi.summaryTitle') }}</span>
          <span class="ai-desc">{{ t('groupAi.summaryDesc') }}</span>
        </div>
        <n-switch
          size="small"
          :value="prefs.smartSummary"
          :disabled="!canManage || summaryLoading"
          :loading="summaryLoading"
          @update:value="onSummaryToggle"
        />
      </div>
      <div v-if="prefs.smartSummary" class="ai-extra">
        <n-input
          :value="summaryDraft || prefs.summaryInstruction"
          type="textarea"
          size="small"
          :disabled="!canManage"
          :placeholder="t('groupAi.summaryPh')"
          :maxlength="groupMetaStore.summaryInstructionLimit()"
          :autosize="{ minRows: 2, maxRows: 3 }"
          @update:value="onSummaryInput"
        />
      </div>
    </template>
  </section>
</template>

<style scoped>
.group-ai-block {
  flex-shrink: 0;
  padding: var(--lx-space-xl) var(--lx-space-lg);
  border-bottom: 1px solid var(--lx-bg-panel-deep);
  background: var(--lx-bg-panel);
}

.group-ai-block.embedded {
  padding: 0;
  border-bottom: none;
  background: transparent;
}

.ai-hero {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
}

.ai-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-2xs);
}

.official-badge {
  display: inline-flex;
  align-self: flex-start;
  padding: 0 var(--lx-space-sm);
  height: 18px;
  border-radius: var(--lx-radius-pill);
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-size: var(--lx-font-2xs);
  font-weight: 600;
  line-height: 18px;
}

.ai-title {
  margin: 0;
  font-size: var(--lx-font-md);
  font-weight: 600;
  color: var(--lx-text-body);
}

.ai-intro,
.ai-hint {
  margin: var(--lx-space-sm) 0 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading-normal);
}

.ai-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lx-space);
  margin-top: var(--lx-space-lg);
}

.ai-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-name {
  font-size: var(--lx-font-sm);
  font-weight: 500;
  color: var(--lx-text-body);
}

.ai-desc {
  font-size: var(--lx-font-2xs);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading-normal);
}

.ai-extra {
  margin-top: var(--lx-space-sm);
}
</style>
