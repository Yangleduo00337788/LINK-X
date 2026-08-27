<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 灵伴 Agent 自动操作时的全局提示层（含内嵌二次确认、霓虹边框与虚拟鼠标）。
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
const isExecuting = computed(() => run.value.phase === 'executing')
const showNeon = computed(() => isExecuting.value || isConfirming.value)

const bannerTitle = computed(() =>
  isConfirming.value ? t('linkmateAgent.waitConfirm') : t('linkmateAgent.helpingOperation')
)

const bannerSubtitle = computed(() => currentStepLabel.value)

const cursorStyle = computed(() => ({
  left: `${run.value.cursor.x}px`,
  top: `${run.value.cursor.y}px`
}))

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
      :class="{ 'is-neon': showNeon, 'is-executing': isExecuting }"
      :style="{ zIndex: lxZ.dialog }"
      role="status"
      aria-live="polite"
    >
      <div v-if="showNeon" class="lm-agent-neon-frame" aria-hidden="true">
        <span class="lm-agent-neon-frame__edge lm-agent-neon-frame__edge--top" />
        <span class="lm-agent-neon-frame__edge lm-agent-neon-frame__edge--right" />
        <span class="lm-agent-neon-frame__edge lm-agent-neon-frame__edge--bottom" />
        <span class="lm-agent-neon-frame__edge lm-agent-neon-frame__edge--left" />
        <span class="lm-agent-neon-frame__glow" />
      </div>

      <div
        class="lm-agent-banner"
        :class="{ 'is-confirming': isConfirming, 'is-executing': isExecuting }"
      >
        <div class="lm-agent-banner__neon" aria-hidden="true" />
        <div class="lm-agent-banner__pulse" aria-hidden="true" />
        <NIcon class="lm-agent-banner__icon" :size="18">
          <SparklesOutline />
        </NIcon>
        <div class="lm-agent-banner__text">
          <p class="lm-agent-banner__title">{{ bannerTitle }}</p>
          <p v-if="bannerSubtitle" class="lm-agent-banner__step">{{ bannerSubtitle }}</p>
          <p v-if="run.thinkingText && (isExecuting || isConfirming)" class="lm-agent-banner__thinking">
            <span class="lm-agent-banner__thinking-dot" />
            {{ run.thinkingText }}
          </p>
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

      <Transition name="lm-agent-cursor">
        <div
          v-if="isExecuting && run.cursor.visible"
          class="lm-agent-cursor"
          :class="{ 'is-clicking': run.cursor.clicking }"
          :style="cursorStyle"
          aria-hidden="true"
        >
          <div v-if="run.thinkingText" class="lm-agent-cursor__thought">
            {{ run.thinkingText }}
          </div>
          <svg class="lm-agent-cursor__pointer" width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path
              d="M5.5 3.21V20.8c0 .45.54.67.85.35l4.86-4.86a.5.5 0 0 1 .35-.15h6.87a.5.5 0 0 0 .35-.85L6.35 2.86a.5.5 0 0 0-.85.35Z"
              fill="currentColor"
              stroke="#fff"
              stroke-width="1.2"
            />
          </svg>
          <span class="lm-agent-cursor__ripple" />
        </div>
      </Transition>
    </div>
  </Transition>
</template>

<style scoped>
.lm-agent-overlay {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.lm-agent-overlay.is-neon {
  pointer-events: none;
}

.lm-agent-neon-frame {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.lm-agent-neon-frame__glow {
  position: absolute;
  inset: 0;
  box-shadow:
    inset 0 0 24px color-mix(in srgb, var(--lx-accent) 18%, transparent),
    inset 0 0 60px color-mix(in srgb, #7c5cff 10%, transparent);
  animation: lm-agent-frame-glow 2.4s ease-in-out infinite;
}

.lm-agent-neon-frame__edge {
  position: absolute;
  pointer-events: none;
  background: linear-gradient(
    90deg,
    transparent,
    color-mix(in srgb, var(--lx-accent) 85%, #7c5cff),
    color-mix(in srgb, #00d4ff 70%, var(--lx-accent)),
    transparent
  );
  background-size: 200% 100%;
  animation: lm-agent-neon-flow 2.8s linear infinite;
  filter: blur(0.2px);
}

.lm-agent-neon-frame__edge--top,
.lm-agent-neon-frame__edge--bottom {
  left: 0;
  right: 0;
  height: 3px;
}

.lm-agent-neon-frame__edge--top {
  top: 0;
}

.lm-agent-neon-frame__edge--bottom {
  bottom: 0;
  animation-direction: reverse;
}

.lm-agent-neon-frame__edge--left,
.lm-agent-neon-frame__edge--right {
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(
    180deg,
    transparent,
    color-mix(in srgb, var(--lx-accent) 85%, #7c5cff),
    color-mix(in srgb, #00d4ff 70%, var(--lx-accent)),
    transparent
  );
  background-size: 100% 200%;
}

.lm-agent-neon-frame__edge--left {
  left: 0;
}

.lm-agent-neon-frame__edge--right {
  right: 0;
  animation-direction: reverse;
}

.lm-agent-overlay.is-executing .lm-agent-neon-frame__edge {
  animation-duration: 1.8s;
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

.lm-agent-banner.is-executing {
  border-color: color-mix(in srgb, var(--lx-accent) 55%, #7c5cff);
  box-shadow:
    0 8px 32px color-mix(in srgb, var(--lx-shadow) 24%, transparent),
    0 0 0 1px color-mix(in srgb, var(--lx-accent) 20%, transparent),
    0 0 24px color-mix(in srgb, var(--lx-accent) 22%, transparent);
}

.lm-agent-banner__neon {
  position: absolute;
  inset: -1px;
  border-radius: inherit;
  opacity: 0;
  pointer-events: none;
  box-shadow:
    0 0 12px color-mix(in srgb, var(--lx-accent) 40%, transparent),
    0 0 28px color-mix(in srgb, #7c5cff 30%, transparent);
  animation: lm-agent-banner-neon 2s ease-in-out infinite;
}

.lm-agent-banner.is-executing .lm-agent-banner__neon,
.lm-agent-banner.is-confirming .lm-agent-banner__neon {
  opacity: 1;
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

.lm-agent-banner.is-executing .lm-agent-banner__pulse {
  background: linear-gradient(
    90deg,
    transparent 0%,
    color-mix(in srgb, var(--lx-accent) 18%, transparent) 40%,
    color-mix(in srgb, #7c5cff 14%, transparent) 60%,
    transparent 100%
  );
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

.lm-agent-banner__thinking {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 4px 0 0;
  font-size: 11px;
  color: color-mix(in srgb, var(--lx-accent) 75%, var(--lx-text-secondary));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lm-agent-banner__thinking-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--lx-accent);
  box-shadow: 0 0 8px color-mix(in srgb, var(--lx-accent) 60%, transparent);
  animation: lm-agent-thinking-pulse 1.2s ease-in-out infinite;
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

.lm-agent-cursor {
  position: fixed;
  z-index: 1;
  pointer-events: none;
  transform: translate(-4px, -4px);
  transition: transform 0.14s ease;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.28));
}

.lm-agent-cursor.is-clicking {
  transform: translate(-4px, -4px) scale(0.88);
}

.lm-agent-cursor__thought {
  position: absolute;
  left: 22px;
  top: -6px;
  max-width: 220px;
  padding: 6px 10px;
  border-radius: 10px;
  font-size: 11px;
  line-height: 1.35;
  color: var(--lx-text-primary);
  background: color-mix(in srgb, var(--lx-bg-elevated) 94%, transparent);
  border: 1px solid color-mix(in srgb, var(--lx-accent) 35%, var(--lx-border));
  box-shadow:
    0 6px 20px color-mix(in srgb, var(--lx-shadow) 20%, transparent),
    0 0 12px color-mix(in srgb, var(--lx-accent) 15%, transparent);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
}

.lm-agent-cursor__pointer {
  color: var(--lx-accent);
  display: block;
}

.lm-agent-cursor__ripple {
  position: absolute;
  left: 2px;
  top: 2px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid color-mix(in srgb, var(--lx-accent) 70%, #7c5cff);
  opacity: 0;
  transform: scale(0.3);
}

@keyframes lm-agent-click-ripple {
  0% {
    opacity: 0.75;
    transform: scale(0.3);
  }
  100% {
    opacity: 0;
    transform: scale(1.4);
  }
}

.lm-agent-cursor.is-clicking .lm-agent-cursor__ripple {
  animation: lm-agent-click-ripple 0.58s ease-out;
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

.lm-agent-cursor-enter-active,
.lm-agent-cursor-leave-active {
  transition: opacity 0.18s ease;
}

.lm-agent-cursor-enter-from,
.lm-agent-cursor-leave-to {
  opacity: 0;
}

@keyframes lm-agent-shimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

@keyframes lm-agent-neon-flow {
  0% {
    background-position: 0% 50%;
  }
  100% {
    background-position: 200% 50%;
  }
}

@keyframes lm-agent-frame-glow {
  0%,
  100% {
    opacity: 0.65;
  }
  50% {
    opacity: 1;
  }
}

@keyframes lm-agent-banner-neon {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 1;
  }
}

@keyframes lm-agent-thinking-pulse {
  0%,
  100% {
    transform: scale(0.85);
    opacity: 0.55;
  }
  50% {
    transform: scale(1.15);
    opacity: 1;
  }
}
</style>
