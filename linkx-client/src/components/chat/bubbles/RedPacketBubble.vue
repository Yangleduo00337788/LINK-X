<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 红包消息卡片气泡。
 */
import { computed } from 'vue'
import type { ChatMessage } from '../../../types'
import { useI18n } from '../../../i18n'

const props = defineProps<{ msg: ChatMessage }>()
const { t } = useI18n()

const isLucky = computed(() => props.msg.redPacketType === 'lucky')

const typeLabel = computed(() =>
  isLucky.value ? t('modals.luckyShort') : t('modals.normalPacket')
)

const subText = computed(() => {
  if (props.msg.redPacketStatus === 'expired') return t('modals.rpStatusExpired')
  if (props.msg.redPacketStatus === 'finished') return t('modals.rpStatusFinished')
  if (props.msg.redPacketReceived || props.msg.redPacketOpened) return t('modals.rpStatusClaimed')
  if (props.msg.isSelf) return typeLabel.value
  return t('modals.openRedPacket')
})
</script>

<template>
  <div
    class="red-packet-card"
    :class="{
      self: msg.isSelf,
      lucky: isLucky,
      opened: msg.redPacketOpened || msg.redPacketReceived,
      finished: msg.redPacketStatus === 'finished',
      expired: msg.redPacketStatus === 'expired'
    }"
  >
    <div class="rp-icon">{{ isLucky ? t('modals.rpLuckyChar') : t('modals.rpNormalChar') }}</div>
    <div class="rp-text">
      <div class="rp-title-row">
        <span class="rp-title">{{
          msg.redPacketGreeting || msg.content || t('modals.greetingFallback')
        }}</span>
        <span class="rp-type-tag" :class="{ 'is-lucky': isLucky }">{{ typeLabel }}</span>
      </div>
      <div class="rp-sub">
        <span>{{ subText }}</span>
        <span v-if="msg.redPacketTotalCount && msg.redPacketTotalCount > 1" class="rp-count">
          {{
            t('modals.rpBubbleRemain', {
              remain: msg.redPacketRemainingCount ?? msg.redPacketTotalCount,
              total: msg.redPacketTotalCount
            })
          }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.red-packet-card {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  min-width: 200px;
  max-width: 280px;
  padding: var(--lx-space-lg) var(--lx-space-xl);
  border-radius: var(--lx-radius);
  background: linear-gradient(135deg, var(--lx-danger), var(--lx-danger-deep));
  color: var(--lx-text-on-accent);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(232, 76, 61, 0.35);
  transition: transform var(--lx-duration) ease, box-shadow var(--lx-duration) ease;
}
.red-packet-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(232, 76, 61, 0.45);
}
.red-packet-card.lucky:not(.finished):not(.expired) {
  background: var(--lx-packet-bubble-gradient);
  box-shadow: 0 2px 10px rgba(243, 156, 18, 0.4);
}
.red-packet-card.opened {
  opacity: 0.88;
}
.red-packet-card.finished,
.red-packet-card.expired {
  background: var(--lx-packet-muted-gradient);
  box-shadow: none;
}
.rp-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: var(--lx-font-lg);
  flex-shrink: 0;
}
.red-packet-card.lucky:not(.finished):not(.expired) .rp-icon {
  background: rgba(255, 215, 0, 0.28);
  color: var(--lx-packet-text-parchment);
}
.rp-text {
  flex: 1;
  min-width: 0;
}
.rp-title-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-sm);
  min-width: 0;
}
.rp-title {
  flex: 1;
  min-width: 0;
  font-size: var(--lx-font);
  font-weight: 600;
  line-height: var(--lx-leading-snug);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rp-type-tag {
  flex-shrink: 0;
  font-size: var(--lx-font-2xs);
  font-weight: 600;
  line-height: var(--lx-leading-none);
  padding: var(--lx-space-2xs) var(--lx-space-xs);
  border-radius: var(--lx-radius-2xs);
  background: rgba(255, 255, 255, 0.22);
  letter-spacing: 0.02em;
}
.rp-type-tag.lucky {
  background: rgba(255, 215, 0, 0.35);
  color: var(--lx-packet-text-cream);
}
.rp-sub {
  font-size: var(--lx-font-sm);
  opacity: 0.9;
  margin-top: var(--lx-space-2xs);
  display: flex;
  align-items: baseline;
  gap: var(--lx-space-xs);
}
.rp-count {
  font-size: var(--lx-font-xs);
  opacity: 0.85;
}
</style>
