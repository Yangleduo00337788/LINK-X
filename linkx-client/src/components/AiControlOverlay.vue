<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 灵伴 Agent 自动操作时的全局提示层（含内嵌二次确认）。
 */
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { NIcon } from 'naive-ui'
import { SparklesOutline, CloseOutline } from '@vicons/ionicons5'
import { useLinkMateAgentStore } from '../stores/linkmateAgent'
import { useI18n } from '../i18n'
import { lxZ } from '../theme/vars'

const { t } = useI18n()
const agentStore = useLinkMateAgentStore()
const { run, currentStepLabel } = storeToRefs(agentStore)

const visible = computed(() => run.value.phase !== 'idle')
const isConfirming = computed(() => run.value.phase === 'confirming')

function cancelAll() {
  agentStore.cancelRun()
}

function approve() {
  agentStore.approvePendingConfirm()
}

function reject() {
  agentStore.rejectPendingConfirm()
}
</script>

<template>
  <Transition name="lm-agent-fade">
    <div
      v-if="visible"
      class="lm-agent-overlay"
      :style="{ zIndex: lxZ.dialog }"
      role="status"
      aria-live="polite"
    >
      <div class="lm-agent-banner" :class="{ 'is-confirming': isConfirming }">
        <div class="lm-agent-banner__pulse" aria-hidden="true" />
        <NIcon class="lm-agent-banner__icon" :size="18">
          <SparklesOutline />
        </NIcon>
        <div class="lm-agent-banner__text">
          <p class="lm-agent-banner__title">
            {{ isConfirming ? t('linkmateAgent.waitConfirm') : t('linkmateAgent.executing') }}
          </p>
          <p v-if="currentStepLabel" class="lm-agent-banner__step">{{ currentStepLabel }}</p>
        </div>
        <div v-if="isConfirming" class="lm-agent-banner__actions">
          <button type="button" class="lm-agent-banner__btn lm-agent-banner__btn--primary" @click="approve">
            {{ t('linkmateAgent.confirmExecute') }}
          </button>
          <button type="button" class="lm-agent-banner__btn" @click="reject">
            {{ t('linkmateAgent.skipAction') }}
          </button>
        </div>
        <button v-else type="button" class="lm-agent-banner__cancel" @click="cancelAll">
          <NIcon :size="16"><CloseOutline /></NIcon>
          <span>{{ t('linkmateAgent.cancel') }}</span>
        </button>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.lm-agent-overlay {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.lm-agent-banner {
  pointer-events: auto;
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: min(560px, calc(100vw - 32px));
  max-width: calc(100vw - 32px);
  padding: 10px 14px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--lx-bg-elevated) 92%, transparent);
  border: 1px solid color-mix(in srgb, var(--lx-accent) 35%, var(--lx-border));
  box-shadow: 0 8px 32px color-mix(in srgb, var(--lx-shadow) 24%, transparent);
  backdrop-filter: blur(12px);
  overflow: hidden;
}

.lm-agent-banner.is-confirming {
  border-color: color-mix(in srgb, var(--lx-warning, #f0a020) 45%, var(--lx-border));
}

.lm-agent-banner__pulse {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent 0%,
    color-mix(in srgb, var(--lx-accent) 12%, transparent) 50%,
    transparent 100%
  );
  animation: lm-agent-shimmer 2.2s ease-in-out infinite;
}

.lm-agent-banner__icon {
  flex-shrink: 0;
  color: var(--lx-accent);
  position: relative;
  z-index: 1;
}

.lm-agent-banner__text {
  flex: 1;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.lm-agent-banner__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text-primary);
}

.lm-agent-banner__step {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--lx-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lm-agent-banner__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.lm-agent-banner__btn {
  padding: 5px 12px;
  border-radius: 8px;
  border: 1px solid var(--lx-border);
  background: var(--lx-bg-muted);
  color: var(--lx-text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.lm-agent-banner__btn:hover {
  color: var(--lx-text-primary);
}

.lm-agent-banner__btn--primary {
  border-color: color-mix(in srgb, var(--lx-accent) 50%, var(--lx-border));
  background: color-mix(in srgb, var(--lx-accent) 18%, transparent);
  color: var(--lx-accent);
  font-weight: 600;
}

.lm-agent-banner__btn--primary:hover {
  background: color-mix(in srgb, var(--lx-accent) 28%, transparent);
}

.lm-agent-banner__cancel {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: none;
  border-radius: 8px;
  background: color-mix(in srgb, var(--lx-bg-muted) 80%, transparent);
  color: var(--lx-text-secondary);
  font-size: 12px;
  cursor: pointer;
  position: relative;
  z-index: 1;
}

.lm-agent-banner__cancel:hover {
  color: var(--lx-text-primary);
  background: var(--lx-bg-muted);
}

.lm-agent-fade-enter-active,
.lm-agent-fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.lm-agent-fade-enter-from,
.lm-agent-fade-leave-to {
  opacity: 0;
}

.lm-agent-fade-enter-from .lm-agent-banner,
.lm-agent-fade-leave-to .lm-agent-banner {
  transform: translateX(-50%) translateY(-8px);
}

@keyframes lm-agent-shimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}
</style>
