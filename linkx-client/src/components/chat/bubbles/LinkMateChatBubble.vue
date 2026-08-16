<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群聊内灵伴 AI 回复气泡：Markdown 渲染 + AI 免责声明。
 */
import { computed, reactive, ref, watch } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { ChevronDownOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'
import { renderLinkMateMarkdownLazy, copyCodeFromButtonLazy } from '../../../utils/linkmateMarkdownLazy'
import { copyText } from '../../../utils/clipboard'
import { useI18n } from '../../../i18n'

const COPY_PIN_MIN_LENGTH = 120

const props = defineProps<{ msg: ChatMessage }>()

const { t } = useI18n()
const message = useMessage()
const collapsedReasoning = reactive<Record<string, boolean>>({})

const renderedHtml = ref('')

watch(
  () => [props.msg.content, props.msg.streaming] as const,
  async ([content, streaming]) => {
    if (streaming) {
      renderedHtml.value = ''
      return
    }
    renderedHtml.value = await renderLinkMateMarkdownLazy(content || '')
  },
  { immediate: true }
)

const shouldPinCopy = computed(() => {
  const text = props.msg.content?.trim() || ''
  if (!text) return false
  return text.length >= COPY_PIN_MIN_LENGTH || /```/.test(text) || text.includes('\n')
})

function isReasoningCollapsed(id: string) {
  return collapsedReasoning[id] ?? false
}

function toggleReasoning(id: string) {
  collapsedReasoning[id] = !isReasoningCollapsed(id)
}

watch(
  () => props.msg.content,
  content => {
    if (props.msg.streaming && content?.trim() && props.msg.reasoningContent?.trim()) {
      collapsedReasoning[props.msg.id] = true
    }
  }
)

async function handleMarkdownClick(e: MouseEvent) {
  const btn = (e.target as HTMLElement | null)?.closest('.lm-code-copy') as HTMLElement | null
  if (!btn) return
  e.preventDefault()
  e.stopPropagation()
  const ok = await copyCodeFromButtonLazy(btn)
  if (ok) {
    message.success(t('linkmate.codeCopied'))
  } else {
    message.error(t('linkmate.copyCodeFailed'))
  }
}

async function copyContent() {
  const text = props.msg.content?.trim()
  if (!text) return
  const ok = await copyText(text)
  if (ok) {
    message.success(t('linkmate.messageCopied'))
  } else {
    message.error(t('linkmate.copyCodeFailed'))
  }
}
</script>

<template>
  <div class="linkmate-chat-stack">
    <div class="lx-bubble linkmate-chat-bubble" @click="handleMarkdownClick">
      <div v-if="msg.reasoningContent?.trim()" class="linkmate-reasoning-block">
        <button type="button" class="linkmate-reasoning-toggle" @click.stop="toggleReasoning(msg.id)">
          <NIcon
            :component="ChevronDownOutline"
            :size="12"
            class="linkmate-reasoning-chev"
            :class="{ collapsed: isReasoningCollapsed(msg.id) }"
          />
          <span>{{ t('linkmate.deepThinking') }}</span>
        </button>
        <div v-show="!isReasoningCollapsed(msg.id)" class="linkmate-reasoning-stream">
          {{ msg.reasoningContent }}
        </div>
      </div>
      <div v-if="msg.streaming" class="linkmate-stream-text">
        {{ msg.content || (msg.reasoningContent ? t('linkmate.deepThinkingGenerating') : t('linkmate.thinking')) }}
        <span v-if="msg.content" class="linkmate-cursor">▍</span>
      </div>
      <div v-else class="linkmate-md" v-html="renderedHtml" />
    </div>
    <div v-if="!msg.streaming && msg.content?.trim()" class="linkmate-chat-footer">
      <p class="linkmate-ai-disclaimer">{{ t('linkmate.aiDisclaimer') }}</p>
      <button
        type="button"
        class="linkmate-copy-btn"
        :class="{ 'is-pinned': shouldPinCopy }"
        @click="copyContent"
      >
        {{ t('linkmate.copyMessage') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.linkmate-chat-stack {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
  max-width: 100%;
}

.linkmate-chat-bubble {
  max-width: 100%;
}

.linkmate-chat-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 18px;
}

.linkmate-ai-disclaimer {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: var(--lx-font-xs);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-muted);
  user-select: none;
}

.linkmate-copy-btn {
  flex-shrink: 0;
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  color: var(--lx-text-secondary);
  font-size: var(--lx-font-xs);
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--lx-duration), color var(--lx-duration);
}

.linkmate-chat-stack:hover .linkmate-copy-btn,
.linkmate-copy-btn.is-pinned {
  opacity: 1;
  pointer-events: auto;
}

.linkmate-copy-btn:hover {
  color: var(--lx-accent);
}

.linkmate-reasoning-block {
  margin-bottom: 0.5em;
}

.linkmate-reasoning-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 4px;
  padding: 0;
  border: none;
  background: none;
  color: var(--lx-text-secondary);
  font-size: var(--lx-font-xs);
  cursor: pointer;
}

.linkmate-reasoning-chev {
  transition: transform var(--lx-duration);
}

.linkmate-reasoning-chev.collapsed {
  transform: rotate(-90deg);
}

.linkmate-stream-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: var(--lx-leading-normal);
}

.linkmate-reasoning-stream {
  padding: 8px 10px;
  border-radius: var(--lx-radius-md);
  background: var(--lx-bg-hover);
  color: var(--lx-text-secondary);
  font-size: var(--lx-font-xs);
  line-height: var(--lx-leading-normal);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 160px;
  overflow-y: auto;
}

.linkmate-cursor {
  animation: linkmate-blink 1s step-end infinite;
}

@keyframes linkmate-blink {
  50% {
    opacity: 0;
  }
}

.linkmate-md :deep(p) {
  margin: 0 0 0.6em;
}

.linkmate-md :deep(p:last-child) {
  margin-bottom: 0;
}

.linkmate-md :deep(ul),
.linkmate-md :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.4em;
}

.linkmate-md :deep(pre) {
  margin: 0.5em 0;
  padding: 10px 12px;
  border-radius: var(--lx-radius-md);
  background: var(--lx-bg-hover);
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.5;
}

.linkmate-md :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.9em;
}

.linkmate-md :deep(:not(pre) > code) {
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: var(--lx-bg-hover);
}

.linkmate-md :deep(a) {
  color: var(--lx-accent);
  text-decoration: none;
}

.linkmate-md :deep(a:hover) {
  text-decoration: underline;
}

.linkmate-md :deep(.lm-code-wrap) {
  position: relative;
  margin: 0.5em 0;
}

.linkmate-md :deep(.lm-code-wrap pre) {
  margin: 0;
}

.linkmate-md :deep(.lm-code-copy) {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
  padding: 2px 8px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius-sm);
  background: var(--lx-bg-panel);
  color: var(--lx-text-secondary);
  font-size: 11px;
  line-height: 1.4;
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--lx-duration);
}

.linkmate-md :deep(.lm-code-wrap:hover .lm-code-copy),
.linkmate-md :deep(.lm-code-copy:focus-visible) {
  opacity: 1;
}
</style>
