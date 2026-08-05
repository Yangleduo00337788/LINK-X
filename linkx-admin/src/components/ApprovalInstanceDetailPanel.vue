<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NSpace, NTag, NTimeline, NTimelineItem } from 'naive-ui'
import type { ApprovalInboxItem, ApprovalInstance } from '@/api/approvals'
import { useApprovalDisplay } from '@/composables/useApprovalDisplay'
import { formatTime } from '@/utils/format'

const props = defineProps<{
  instance: ApprovalInstance | null
  current?: ApprovalInboxItem | null
  acting?: boolean
  showActions?: boolean
  canAction?: boolean
}>()

defineEmits<{
  approve: []
  reject: []
  openBiz: []
}>()

const { t } = useI18n()
const {
  instanceStatusLabel,
  statusTagType,
  sortTimeline,
  timelineItemTitle,
  timelineItemContent,
} = useApprovalDisplay()

const timeline = computed(() => sortTimeline(props.instance?.timeline))
</script>

<template>
  <NSpace v-if="instance" vertical :size="12">
    <div>{{ t('approvalInbox.flow') }}: {{ instance.flowName || '-' }}</div>
    <div>
      {{ t('common.status') }}:
      <NTag size="small" :type="statusTagType(instance.status)">
        {{ instanceStatusLabel(instance.status) }}
      </NTag>
    </div>
    <div v-if="instance.applicantName">
      {{ t('approvalInbox.applicant') }}: {{ instance.applicantName }}
    </div>
    <div v-if="instance.finishedAt">
      {{ t('review.approvalFinishedAt') }}: {{ formatTime(instance.finishedAt) }}
    </div>
    <div class="timeline-wrap">
      <div class="panel-title">{{ t('approvalInbox.timeline') }}</div>
      <NTimeline v-if="timeline.length">
        <NTimelineItem
          v-for="item in timeline"
          :key="item.id"
          :type="statusTagType(item.status)"
          :title="timelineItemTitle(item)"
          :content="timelineItemContent(item)"
          :time="item.actionTime ? formatTime(item.actionTime) : ''"
        />
      </NTimeline>
      <div v-else class="empty-hint">{{ t('approvalInbox.noTimeline') }}</div>
    </div>
    <NSpace v-if="showActions && canAction && current" justify="end" class="modal-actions">
      <NButton v-if="current.bizType === 'review'" @click="$emit('openBiz')">
        {{ t('approvalInbox.openBiz') }}
      </NButton>
      <NButton type="primary" :loading="acting" @click="$emit('approve')">
        {{ t('approvalInbox.approve') }}
      </NButton>
      <NButton type="error" tertiary :loading="acting" @click="$emit('reject')">
        {{ t('approvalInbox.reject') }}
      </NButton>
    </NSpace>
  </NSpace>
</template>

<style scoped>
.timeline-wrap {
  margin-top: 8px;
}

.panel-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.modal-actions {
  margin-top: 16px;
}

.empty-hint {
  color: var(--lx-text-3);
  font-size: 13px;
}
</style>
