<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 灵伴右侧内嵌对话面板（消息页主内容区右缘）。
 * <p>
 * 由聊天列表 AI 按钮打开；左侧中部折叠按钮可收起，
 * 收起后可在左侧导航栏底部恢复。左缘可拖拽调整宽度。
 * </p>
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { NInput, NIcon, NSpin, useMessage } from 'naive-ui'
import { ChevronForwardOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useLinkMateStore } from '../stores/linkmate'
import type { LinkMateMessage } from '../api/linkmate'
import { useI18n } from '../i18n'
import LinkMateLogoMark from './LinkMateLogoMark.vue'

const { t } = useI18n()
const message = useMessage()
const linkMate = useLinkMateStore()
const {
  activeMessages,
  loadingMessages,
  streaming,
  inputDraft,
  enabled,
  status,
  panelWidth
} = storeToRefs(linkMate)

const inputRef = ref<InstanceType<typeof NInput> | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const booted = ref(false)
const isResizing = ref(false)
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)

const panelStyle = computed(() => ({ width: `${panelWidth.value}px` }))

const canChat = computed(() => enabled.value)

const streamingAssistant = computed(() => {
  if (!streaming.value) return null
  const last = activeMessages.value.at(-1)
  if (!last || last.role !== 'assistant' || !last.id.startsWith('temp-assistant')) return null
  return last
})

const showThinking = computed(
  () => !!streamingAssistant.value && !streamingAssistant.value.content.trim()
)

const showGeneratingHint = computed(
  () => !!streamingAssistant.value && !!streamingAssistant.value.content.trim()
)

function isThinkingMessage(msg: LinkMateMessage) {
  return (
    streaming.value &&
    msg.role === 'assistant' &&
    msg.id.startsWith('temp-assistant') &&
    !msg.content.trim()
  )
}

function collapse() {
  linkMate.collapsePanel()
}

async function ensureReady() {
  await linkMate.ensurePanelReady()
  booted.value = true
}

function scrollToBottom() {
  const el = messageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function startResize(e: MouseEvent) {
  isResizing.value = true
  resizeStartX.value = e.clientX
  resizeStartWidth.value = panelWidth.value
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function onResize(e: MouseEvent) {
  if (!isResizing.value) return
  const delta = resizeStartX.value - e.clientX
  linkMate.setPanelWidth(resizeStartWidth.value + delta)
}

function stopResize() {
  isResizing.value = false
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

async function handleSend() {
  const text = inputDraft.value.trim()
  if (!text || streaming.value) return
  inputDraft.value = ''
  try {
    await linkMate.sendMessage(text)
  } catch (err) {
    message.error(err instanceof Error ? err.message : t('linkmate.sendFailed'))
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void handleSend()
  }
}

watch(
  () => activeMessages.value.length,
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

watch(
  () => activeMessages.value.at(-1)?.content,
  async () => {
    if (streaming.value) {
      await nextTick()
      scrollToBottom()
    }
  }
)

watch(showThinking, async visible => {
  if (visible) {
    await nextTick()
    scrollToBottom()
  }
})

onMounted(async () => {
  await ensureReady()
  await nextTick()
  scrollToBottom()
  inputRef.value?.focus()
})

onUnmounted(() => {
  stopResize()
})
</script>

<template>
  <aside class="linkmate-side" :style="panelStyle">
    <div
      class="linkmate-resizer"
      :class="{ dragging: isResizing }"
      :title="t('linkmate.resizePanel')"
      @mousedown="startResize"
    />

    <div class="collapse-hover-zone" aria-hidden="true" />
    <button
      type="button"
      class="lx-collapse-handle"
      :title="t('linkmate.collapsePanel')"
      @click="collapse"
    >
      <NIcon :component="ChevronForwardOutline" :size="14" />
    </button>

    <div class="linkmate-side-body">
      <header class="linkmate-side-hdr">
        <div class="linkmate-side-hdr-left">
          <LinkMateLogoMark size="hdr" />
          <div>
            <div class="linkmate-side-title">{{ t('linkmate.dialogTitle') }}</div>
            <div v-if="status" class="linkmate-side-sub">{{ status.model }}</div>
          </div>
        </div>
      </header>

      <div v-if="!enabled" class="linkmate-side-disabled">
        <p>{{ t('linkmate.disabledHint') }}</p>
      </div>

      <template v-else>
        <div ref="messageListRef" class="linkmate-side-messages">
          <div v-if="loadingMessages || !booted" class="linkmate-side-empty">
            {{ t('common.loading') }}
          </div>
          <div v-else-if="activeMessages.length === 0" class="linkmate-side-empty">
            {{ t('linkmate.chatHint') }}
          </div>
          <template v-else>
            <template v-for="msg in activeMessages" :key="msg.id">
              <div v-if="msg.role === 'user'" class="linkmate-side-msg is-user">
                <div class="linkmate-side-msg-content">
                  {{ msg.content }}
                </div>
              </div>
              <div v-else-if="isThinkingMessage(msg)" class="linkmate-status-hint">
                <NSpin :size="16" />
                <span>{{ t('linkmate.thinking') }}</span>
              </div>
              <div v-else-if="msg.content" class="linkmate-side-msg is-assistant">
                <div class="linkmate-side-msg-content">
                  {{ msg.content }}
                  <span
                    v-if="streaming && msg.id.startsWith('temp-assistant')"
                    class="linkmate-cursor"
                  >▍</span>
                </div>
              </div>
            </template>
          </template>
        </div>

        <footer class="linkmate-side-footer">
          <div v-if="showGeneratingHint" class="linkmate-generating-hint">
            <NSpin :size="14" />
            <span>{{ t('linkmate.generating') }}</span>
          </div>
          <div class="linkmate-side-input-row">
            <div class="linkmate-side-input-wrap">
              <NInput
                ref="inputRef"
                v-model:value="inputDraft"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                :placeholder="t('linkmate.inputPlaceholder')"
                :disabled="streaming || !canChat"
                @keydown="handleKeydown"
              />
            </div>
            <button
              v-if="streaming"
              type="button"
              class="linkmate-side-btn linkmate-side-btn--stop"
              @click="linkMate.abortStream()"
            >
              {{ t('linkmate.stop') }}
            </button>
            <button
              v-else
              type="button"
              class="linkmate-side-btn linkmate-side-btn--send"
              :disabled="!inputDraft.trim() || !canChat"
              @click="handleSend"
            >
              {{ t('linkmate.send') }}
            </button>
          </div>
        </footer>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.linkmate-side {
  position: relative;
  flex-shrink: 0;
  height: 100%;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.linkmate-side-body {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--lx-bg-panel);
}

.linkmate-resizer {
  position: absolute;
  left: 0;
  top: 0;
  width: 1px;
  height: 100%;
  cursor: col-resize;
  z-index: var(--lx-z-dropdown);
  transition: background var(--lx-duration-md);
}

.linkmate-resizer::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: -3px;
  right: -3px;
  cursor: col-resize;
}

.linkmate-resizer:hover,
.linkmate-resizer.dragging {
  background: var(--lx-separator-fade, rgba(0, 0, 0, 0.06));
}

.collapse-hover-zone {
  position: absolute;
  left: -14px;
  top: 0;
  width: 20px;
  height: 100%;
  z-index: var(--lx-z-raised-4);
}

.linkmate-side:hover .lx-collapse-handle,
.lx-collapse-handle:focus-visible {
  opacity: 1;
  pointer-events: auto;
}

.linkmate-side-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-sm);
  padding: 12px 14px;
  border-bottom: 1px solid var(--lx-border-light);
  background: linear-gradient(180deg, rgba(18, 183, 245, 0.06), transparent);
  flex-shrink: 0;
}

.linkmate-side-hdr-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.linkmate-side-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-primary);
}

.linkmate-side-sub {
  font-size: 11px;
  color: var(--lx-text-secondary);
  margin-top: 2px;
}

.linkmate-side-disabled {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  text-align: center;
  color: var(--lx-text-secondary);
  font-size: 13px;
}

.linkmate-side-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--lx-bg-panel);
}

.linkmate-side-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-secondary);
  font-size: 13px;
  text-align: center;
  padding: 24px;
}

.linkmate-side-msg.is-user {
  align-self: flex-end;
  max-width: 88%;
}

.linkmate-side-msg.is-assistant {
  align-self: stretch;
  max-width: 100%;
}

.linkmate-side-msg-content {
  word-break: break-word;
  white-space: pre-wrap;
}

.linkmate-side-msg.is-user .linkmate-side-msg-content {
  padding: 8px 12px;
  border-radius: var(--lx-radius-lg);
  font-size: 13px;
  line-height: 1.5;
  background: var(--lx-accent);
  color: #fff;
}

.linkmate-side-msg.is-assistant .linkmate-side-msg-content {
  padding: 2px 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--lx-text-primary);
}

.linkmate-status-hint,
.linkmate-generating-hint {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--lx-text-secondary);
  font-size: 13px;
}

.linkmate-status-hint {
  align-self: flex-start;
  padding: 4px 0;
}

.linkmate-generating-hint {
  margin-bottom: 8px;
}

.linkmate-cursor {
  animation: linkmate-cursor 1s step-end infinite;
}

@keyframes linkmate-cursor {
  50% {
    opacity: 0;
  }
}

.linkmate-side-footer {
  padding: 10px 14px 14px;
  border-top: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  flex-shrink: 0;
}

.linkmate-side-input-row {
  display: flex;
  align-items: stretch;
  gap: 8px;
  min-height: 36px;
}

.linkmate-side-input-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  min-height: 36px;
  padding: 6px 10px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-lg);
  background: var(--lx-bg-panel);
  box-sizing: border-box;
  transition: border-color var(--lx-duration);
}

.linkmate-side-input-wrap:focus-within {
  border-color: var(--lx-accent);
}

.linkmate-side-input-wrap :deep(.n-input) {
  background: transparent;
}

.linkmate-side-input-wrap :deep(.n-input__border),
.linkmate-side-input-wrap :deep(.n-input__state-border) {
  display: none;
}

.linkmate-side-input-wrap :deep(.n-input__textarea-el),
.linkmate-side-input-wrap :deep(.n-input__placeholder),
.linkmate-side-input-wrap :deep(.n-input__textarea-mirror) {
  min-height: 22px !important;
  padding: 0 !important;
  line-height: 22px !important;
  font-size: 13px;
}

.linkmate-side-btn {
  flex-shrink: 0;
  min-width: 64px;
  min-height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: var(--lx-radius-lg);
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  box-sizing: border-box;
  transition: background var(--lx-duration), filter var(--lx-duration);
}

.linkmate-side-btn--send {
  background: var(--lx-accent);
  color: #fff;
}

.linkmate-side-btn--send:hover:not(:disabled) {
  background: var(--lx-accent-hover);
}

.linkmate-side-btn--send:disabled {
  background: #e9e9e9;
  color: rgba(255, 255, 255, 0.9);
  cursor: not-allowed;
}

.linkmate-side-btn--stop {
  background: var(--lx-danger);
  color: #fff;
}

.linkmate-side-btn--stop:hover {
  filter: brightness(1.06);
}
</style>
