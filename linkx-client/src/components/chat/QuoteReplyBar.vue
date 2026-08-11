<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 微信风格引用条：灰底圆角，展示「昵称: 内容」。
 */
import { computed } from 'vue'
import { useI18n } from '../../i18n'

const props = withDefaults(
  defineProps<{
    senderName?: string
    content?: string
    /** bubble=消息气泡内；below=气泡下方；input=输入框预览 */
    variant?: 'bubble' | 'below' | 'input'
    insideSelfBubble?: boolean
  }>(),
  {
    variant: 'bubble',
    insideSelfBubble: false
  }
)

const { t } = useI18n()

const displayText = computed(() => {
  const body = (props.content || '').trim()
  if (props.variant === 'input') return body
  const name = (props.senderName || '').trim() || t('chat.messageFallback')
  return body ? `${name}: ${body}` : name
})
</script>

<template>
  <div
    class="quote-reply-bar"
    :class="[`quote-reply-bar--${variant}`, { 'quote-reply-bar--self': insideSelfBubble }]"
  >
    {{ displayText }}
  </div>
</template>

<style scoped>
.quote-reply-bar {
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading);
  word-break: break-word;
  border-radius: var(--lx-radius-sm);
  background: var(--lx-bg-panel);
  color: var(--lx-quote-muted);
}

.quote-reply-bar--bubble {
  padding: var(--lx-space) var(--lx-space-md);
  margin-bottom: var(--lx-space-sm);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}

.quote-reply-bar--below {
  align-self: stretch;
  max-width: 100%;
  padding: var(--lx-space-sm) var(--lx-space-md);
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading);
  color: var(--lx-quote-muted);
  background: var(--lx-quote-bg);
  border-radius: var(--lx-radius-xs);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.quote-reply-bar--input {
  flex: 1;
  min-width: 0;
  padding: var(--lx-space) var(--lx-space-lg);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.quote-reply-bar--self {
  background: rgba(255, 255, 255, 0.22);
  color: rgba(255, 255, 255, 0.92);
}
</style>
