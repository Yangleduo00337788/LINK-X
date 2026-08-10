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
  gap: 10px;
  max-width: min(280px, 70vw);
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--lx-bg-bubble-peer, #f5f5f5);
  color: inherit;
  text-decoration: none;
  border: 1px solid var(--lx-border-light);
}
.location-bubble.self {
  background: var(--lx-bg-bubble-self, #dff6ff);
}
.loc-ico {
  color: var(--lx-accent);
  flex-shrink: 0;
  margin-top: 2px;
}
.loc-title {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-bottom: 2px;
}
.loc-addr {
  font-size: 14px;
  line-height: 1.4;
  word-break: break-word;
}
</style>
