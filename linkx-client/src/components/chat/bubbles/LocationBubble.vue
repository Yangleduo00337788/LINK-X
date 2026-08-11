<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 位置消息气泡。
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { LocationOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'
import { useI18n } from '../../../i18n'

const props = defineProps<{ msg: ChatMessage }>()
const { t } = useI18n()

const label = computed(() => props.msg.content?.trim() || t('chat.location'))

const mapUrl = computed(() => {
  const q = encodeURIComponent(label.value)
  return `https://uri.amap.com/search?keyword=${q}`
})
</script>

<template>
  <a
    class="lx-bubble location-bubble"
    :class="{ self: msg.isSelf }"
    :href="mapUrl"
    target="_blank"
    rel="noopener noreferrer"
  >
    <n-icon :component="LocationOutline" :size="22" class="loc-ico" />
    <div class="loc-body">
      <div class="loc-title">{{ t('chat.location') }}</div>
      <div class="loc-addr">{{ label }}</div>
    </div>
  </a>
</template>

<style scoped>
.location-bubble {
  display: flex;
  align-items: flex-start;
  gap: var(--lx-space-md);
  max-width: min(280px, 70vw);
  color: inherit;
  text-decoration: none;
}
.location-bubble.self .loc-ico,
.location-bubble.self .loc-title,
.location-bubble.self .loc-addr {
  color: var(--lx-text-on-accent);
}
.location-bubble.self .loc-title {
  opacity: 0.82;
}
.loc-ico {
  color: var(--lx-accent);
  flex-shrink: 0;
  margin-top: var(--lx-space-2xs);
}
.loc-title {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  margin-bottom: var(--lx-space-2xs);
}
.loc-addr {
  font-size: var(--lx-font);
  line-height: var(--lx-leading);
  word-break: break-word;
}
</style>
