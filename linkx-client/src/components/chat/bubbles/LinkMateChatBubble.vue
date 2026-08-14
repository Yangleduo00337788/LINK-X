<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群聊内灵伴 AI 回复气泡：Markdown 渲染 + AI 免责声明。
 */
import { computed } from 'vue'
import { useMessage } from 'naive-ui'
import type { ChatMessage } from '../../../types'
import { renderLinkMateMarkdown, copyCodeFromButton } from '../../../utils/linkmateMarkdown'
import { useI18n } from '../../../i18n'

const props = defineProps<{ msg: ChatMessage }>()

const { t } = useI18n()
const message = useMessage()

const renderedHtml = computed(() => renderLinkMateMarkdown(props.msg.content || ''))

async function handleMarkdownClick(e: MouseEvent) {
  const btn = (e.target as HTMLElement | null)?.closest('.lm-code-copy') as HTMLElement | null
  if (!btn) return
  e.preventDefault()
  e.stopPropagation()
  const ok = await copyCodeFromButton(btn)
  if (ok) {
    message.success(t('linkmate.codeCopied'))
  } else {
    message.error(t('linkmate.copyCodeFailed'))
  }
}
</script>

<template>
  <div class="linkmate-chat-stack">
    <div class="lx-bubble linkmate-chat-bubble" @click="handleMarkdownClick">
      <div class="linkmate-md" v-html="renderedHtml" />
    </div>
    <p class="linkmate-ai-disclaimer">{{ t('linkmate.aiDisclaimer') }}</p>
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

.linkmate-ai-disclaimer {
  margin: 0;
  font-size: var(--lx-font-xs);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-muted);
  user-select: none;
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
