<script setup lang="ts">
/**
 * 红包消息卡片气泡。
 * <p>
 * 展示祝福语、领取状态与剩余个数；opened 时降低不透明度。
 * 字段全部来自消息体，不再硬编码文案。
 * </p>
 */
import { computed } from 'vue'
import type { ChatMessage } from '../../../types'

const props = defineProps<{ msg: ChatMessage }>()

const subText = computed(() => {
  if (props.msg.redPacketStatus === 'expired') return '已过期'
  if (props.msg.redPacketStatus === 'finished') return '已领完'
  if (props.msg.redPacketReceived || props.msg.redPacketOpened) return '已领取'
  if (props.msg.isSelf) return '红包'
  return '领取红包'
})
</script>

<template>
  <div
    class="red-packet-card"
    :class="{
      self: msg.isSelf,
      opened: msg.redPacketOpened || msg.redPacketReceived,
      finished: msg.redPacketStatus === 'finished',
      expired: msg.redPacketStatus === 'expired'
    }"
  >
    <div class="rp-icon">福</div>
    <div class="rp-text">
      <div class="rp-title">{{ msg.redPacketGreeting || msg.content || '恭喜发财' }}</div>
      <div class="rp-sub">
        <span>{{ subText }}</span>
        <span v-if="msg.redPacketTotalCount && msg.redPacketTotalCount > 1" class="rp-count">
          · 剩余 {{ msg.redPacketRemainingCount ?? msg.redPacketTotalCount }}/{{ msg.redPacketTotalCount }}
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
  max-width: 260px;
  padding: 12px 14px;
  border-radius: var(--lx-radius);
  background: linear-gradient(135deg, #e84c3d, #c0392b);
  color: var(--lx-text-on-accent);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(232, 76, 61, 0.35);
}
.red-packet-card.opened {
  opacity: 0.85;
}
.red-packet-card.finished,
.red-packet-card.expired {
  background: linear-gradient(135deg, #95a5a6, #7f8c8d);
}
.rp-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
}
.rp-text {
  flex: 1;
  min-width: 0;
}
.rp-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rp-sub {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 2px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.rp-count {
  font-size: 11px;
  opacity: 0.8;
}
</style>
