<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群聊智能总结侧边装饰条：默认半藏，悬停展开，点击触发总结。
 */
import { ref } from 'vue'
import { useMessage } from 'naive-ui'
import * as groupApi from '../../api/group'
import { useI18n } from '../../i18n'

const props = defineProps<{
  sessionId: string
}>()

const emit = defineEmits<{
  summarized: []
}>()

const { t } = useI18n()
const message = useMessage()
const loading = ref(false)
const hovered = ref(false)

async function onClick() {
  if (!props.sessionId || loading.value) return
  loading.value = true
  try {
    const res = await groupApi.triggerGroupAiSummary(props.sessionId)
    if (res.code === 200) {
      message.success(t('groupAi.summaryTriggered'))
      emit('summarized')
    } else {
      message.error(res.message || t('groupAi.summaryTriggerFail'))
    }
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('groupAi.summaryTriggerFail'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <button
    type="button"
    class="smart-summary-tab"
    :class="{ hovered, loading }"
    :disabled="loading"
    :title="t('groupAi.summaryTabHint')"
    :aria-label="t('groupAi.summaryTitle')"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
    @click="onClick"
  >
    <span class="tab-glow" aria-hidden="true" />
    <span class="tab-label">{{ t('groupAi.summaryTabLabel') }}</span>
  </button>
</template>

<style scoped>
.smart-summary-tab {
  position: absolute;
  top: 42%;
  right: 0;
  z-index: var(--lx-z-sticky, 8);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  min-height: 108px;
  padding: var(--lx-space-md) var(--lx-space-sm);
  border: 1px solid var(--lx-accent-soft);
  border-right: none;
  border-radius: var(--lx-radius) 0 0 var(--lx-radius);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--lx-accent) 12%, var(--lx-bg-panel)),
    var(--lx-bg-panel)
  );
  color: var(--lx-accent);
  box-shadow: -4px 0 16px color-mix(in srgb, var(--lx-accent) 18%, transparent);
  cursor: pointer;
  transform: translateX(50%);
  transition:
    transform 0.28s cubic-bezier(0.4, 0, 0.2, 1),
    box-shadow 0.28s ease,
    background 0.28s ease;
  overflow: hidden;
}

.smart-summary-tab.hovered,
.smart-summary-tab:focus-visible {
  transform: translateX(0);
  box-shadow: -6px 0 20px color-mix(in srgb, var(--lx-accent) 28%, transparent);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--lx-accent) 18%, var(--lx-bg-panel)),
    var(--lx-bg-panel)
  );
}

.smart-summary-tab.loading {
  opacity: 0.72;
  cursor: wait;
}

.smart-summary-tab:disabled {
  pointer-events: none;
}

.tab-glow {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent, color-mix(in srgb, var(--lx-accent) 8%, transparent));
  opacity: 0;
  transition: opacity 0.28s ease;
}

.smart-summary-tab.hovered .tab-glow {
  opacity: 1;
}

.tab-label {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: var(--lx-font-xs);
  font-weight: 600;
  letter-spacing: 0.12em;
  line-height: 1.2;
  user-select: none;
  white-space: nowrap;
}
</style>
