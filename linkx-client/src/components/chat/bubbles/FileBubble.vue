<script setup lang="ts">
/**
 * 文件消息卡片气泡。
 * <p>
 * 展示文件名、大小与传输状态条。
 * </p>
 */
import { NIcon } from 'naive-ui'
import { DocumentOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'

defineProps<{ msg: ChatMessage }>()
</script>

<template>
  <!-- 文件卡片：主区域 + 底部状态条 -->
  <div class="lx-file-card" :class="{ self: msg.isSelf }">
    <div class="lx-file-main">
      <div class="lx-file-icon apk">
        <n-icon :component="DocumentOutline" :size="26" color="var(--lx-bg-card)" />
      </div>
      <div class="lx-file-meta">
        <div class="lx-file-name">{{ msg.fileName || msg.content || '文件' }}</div>
        <div class="lx-file-size">{{ msg.fileSize || '' }}</div>
      </div>
    </div>
    <div class="lx-file-bar">
      {{ msg.fileStatus || (msg.isSelf ? '已发送' : '已接收') }}
    </div>
  </div>
</template>
