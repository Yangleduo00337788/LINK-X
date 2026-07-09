<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { DocumentOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../../types'

defineProps<{ msg: ChatMessage }>()
</script>

<template>
  <div class="qq-file-card" :class="{ self: msg.isSelf }">
    <div class="qq-file-main">
      <div class="qq-file-icon apk">
        <n-icon :component="DocumentOutline" :size="26" color="var(--lx-bg-card)" />
      </div>
      <div class="qq-file-meta">
        <div class="qq-file-name">{{ msg.fileName || msg.content || '文件' }}</div>
        <div class="qq-file-size">{{ msg.fileSize || '' }}</div>
      </div>
    </div>
    <div class="qq-file-bar">
      {{ msg.fileStatus || (msg.isSelf ? '已发送' : '已接收') }}
    </div>
  </div>
</template>
