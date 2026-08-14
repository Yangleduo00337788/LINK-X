<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 灵伴右侧内嵌对话面板（消息页主内容区右缘）。
 * <p>
 * 由聊天列表 AI 按钮打开；左侧中部折叠按钮可收起，
 * 收起后可在左侧导航栏底部恢复。
 * </p>
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { NInput, NIcon, useMessage } from 'naive-ui'
import { ChevronForwardOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useLinkMateStore } from '../stores/linkmate'
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
  status
} = storeToRefs(linkMate)

const inputRef = ref<InstanceType<typeof NInput> | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const booted = ref(false)

const canChat = computed(() => enabled.value)

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

onMounted(async () => {
  await ensureReady()
  await nextTick()
  scrollToBottom()
  inputRef.value?.focus()
})
</script>

<template>
  <aside class="linkmate-side">
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
            <div
              v-for="msg in activeMessages"
              :key="msg.id"
              class="linkmate-side-msg"
              :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
            >
              <div class="linkmate-side-msg-content">
                {{ msg.content }}
                <span
                  v-if="streaming && msg.id.startsWith('temp-assistant')"
                  class="linkmate-cursor"
                >▍</span>
              </div>
            </div>
          </template>
        </div>

        <footer class="linkmate-side-footer">
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
  width: 380px;
  flex-shrink: 0;
  height: 100%;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.linkmate-side-body {
  width: 380px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--lx-bg-panel);
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
  gap: 8px;
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

.linkmate-side-msg {
  max-width: 88%;
}

.linkmate-side-msg.is-user {
  align-self: flex-end;
}

.linkmate-side-msg.is-assistant {
  align-self: flex-start;
}

.linkmate-side-msg-content {
  padding: 8px 12px;
  border-radius: var(--lx-radius-lg);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.linkmate-side-msg.is-user .linkmate-side-msg-content {
  background: var(--lx-accent);
  color: #fff;
}

.linkmate-side-msg.is-assistant .linkmate-side-msg-content {
  background: var(--lx-bg-soft);
  color: var(--lx-text-primary);
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
