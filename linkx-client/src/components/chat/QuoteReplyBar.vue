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
  font-size: 13px;
  line-height: 1.45;
  word-break: break-word;
  border-radius: 8px;
  background: #f0f0f0;
  color: #8a8a8a;
}

.quote-reply-bar--bubble {
  padding: 8px 10px;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}

.quote-reply-bar--below {
  align-self: stretch;
  max-width: 100%;
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.45;
  color: #8a8a8a;
  background: #ececec;
  border-radius: 6px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.quote-reply-bar--input {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
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
