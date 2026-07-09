<script setup lang="ts">
/**
 * 红包消息卡片气泡。
 * <p>
 * 展示祝福语与领取状态；opened 时降低不透明度。
 * </p>
 */
import type { ChatMessage } from '../../../types'

defineProps<{ msg: ChatMessage }>()
</script>

<template>
  <!-- 红包卡片：已领取时加 opened 样式 -->
  <div class="red-packet-card" :class="{ self: msg.isSelf, opened: msg.redPacketOpened }">
    <div class="rp-icon">福</div>
    <div class="rp-text">
      <div class="rp-title">{{ msg.redPacketGreeting || msg.content }}</div>
      <div class="rp-sub">
        {{ msg.redPacketOpened ? '已领取' : msg.isSelf ? '红包' : '领取红包' }}
      </div>
    </div>
  </div>
</template>
