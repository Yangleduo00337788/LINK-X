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
        <span class="rp-type-tag" :class="{ lucky: isLucky }">{{ typeLabel }}</span>
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
  gap: 10px;
  min-width: 200px;
  max-width: 280px;
  padding: 12px 14px;
  border-radius: var(--lx-radius);
  background: linear-gradient(135deg, #e84c3d, #c0392b);
  color: var(--lx-text-on-accent, #fff);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(232, 76, 61, 0.35);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.red-packet-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(232, 76, 61, 0.45);
}
.red-packet-card.lucky:not(.finished):not(.expired) {
  background: linear-gradient(135deg, #f39c12, #e74c3c 55%, #c0392b);
  box-shadow: 0 2px 10px rgba(243, 156, 18, 0.4);
}
.red-packet-card.opened {
  opacity: 0.88;
}
.red-packet-card.finished,
.red-packet-card.expired {
  background: linear-gradient(135deg, #95a5a6, #7f8c8d);
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
  font-size: 15px;
  flex-shrink: 0;
}
.red-packet-card.lucky:not(.finished):not(.expired) .rp-icon {
  background: rgba(255, 215, 0, 0.28);
  color: #fff8dc;
}
.rp-text {
  flex: 1;
  min-width: 0;
}
.rp-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.rp-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rp-type-tag {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  padding: 3px 5px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.22);
  letter-spacing: 0.02em;
}
.rp-type-tag.lucky {
  background: rgba(255, 215, 0, 0.35);
  color: #fff8e7;
}
.rp-sub {
  font-size: 12px;
  opacity: 0.9;
  margin-top: 3px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.rp-count {
  font-size: 11px;
  opacity: 0.85;
}
</style>
