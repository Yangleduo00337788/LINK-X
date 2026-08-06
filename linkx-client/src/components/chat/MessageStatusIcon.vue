<script setup lang="ts">
/**
 * 消息发送状态：发送中 / 单勾 / 双勾 / 已读双勾。
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { CheckmarkOutline, CheckmarkDoneOutline, TimeOutline, AlertCircleOutline } from '@vicons/ionicons5'
import type { ChatMessage } from '../../types'
import { useI18n } from '../../i18n'

const props = defineProps<{
  msg: ChatMessage
  /** 群聊仅展示人数时不显示单聊勾图标 */
  groupMode?: boolean
}>()

const { t } = useI18n()

const failed = computed(() => props.msg.isSelf && props.msg.sendStatus === 'failed')
const sending = computed(() => props.msg.isSelf && props.msg.sendStatus === 'sending')
const read = computed(() => props.msg.isSelf && props.msg.sendStatus === 'read')
const delivered = computed(() => props.msg.isSelf && props.msg.sendStatus === 'delivered')
const sent = computed(() => props.msg.isSelf && props.msg.sendStatus === 'sent')

const showChecks = computed(() => !props.groupMode && props.msg.isSelf && !failed.value && !sending.value)
</script>

<template>
  <span v-if="failed" class="msg-status-icon msg-status-icon--failed" :title="msg.sendFailReason || t('chat.statusFailed')">
    <n-icon :component="AlertCircleOutline" :size="14" />
  </span>
  <span v-else-if="sending" class="msg-status-icon msg-status-icon--sending" :title="t('chat.statusSending')">
    <n-icon :component="TimeOutline" :size="14" />
  </span>
  <span v-else-if="showChecks && read" class="msg-status-icon msg-status-icon--read" :title="t('chat.statusRead')">
    <n-icon :component="CheckmarkDoneOutline" :size="15" />
  </span>
  <span v-else-if="showChecks && delivered" class="msg-status-icon msg-status-icon--delivered" :title="t('chat.statusDelivered')">
    <n-icon :component="CheckmarkDoneOutline" :size="15" />
  </span>
  <span v-else-if="showChecks && sent" class="msg-status-icon msg-status-icon--sent" :title="t('chat.statusSent')">
    <n-icon :component="CheckmarkOutline" :size="14" />
  </span>
</template>

<style scoped>
.msg-status-icon {
  display: inline-flex;
  align-items: center;
  line-height: 1;
  vertical-align: middle;
}
.msg-status-icon--sent,
.msg-status-icon--delivered {
  color: var(--lx-text-muted, #999);
}
.msg-status-icon--read {
  color: var(--lx-accent, #12b7f5);
}
.msg-status-icon--sending {
  color: var(--lx-text-muted, #999);
  opacity: 0.85;
}
.msg-status-icon--failed {
  color: var(--lx-danger, #e74c3c);
}
</style>
