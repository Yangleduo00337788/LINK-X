<script setup lang="ts">
import type { ChatMessage } from '../../../types'

defineProps<{ msg: ChatMessage }>()
</script>

<template>
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
