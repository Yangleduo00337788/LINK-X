/**
 * 作者：yangleduo
 */
import { useI18n } from 'vue-i18n'
import type { ApprovalTimelineItem } from '@/api/approvals'

export function useApprovalDisplay() {
  const { t } = useI18n()

  function instanceStatusLabel(status?: string) {
    const map: Record<string, string> = {
      pending: t('approvalInbox.instancePending'),
      approved: t('approvalInbox.instanceApproved'),
      rejected: t('approvalInbox.instanceRejected'),
      cancelled: t('approvalInbox.instanceCancelled'),
    }
    return status ? map[status] || status : '-'
  }

  function recordStatusLabel(status?: string) {
    const map: Record<string, string> = {
      pending: t('review.pending'),
      approved: t('approvalInbox.approve'),
      rejected: t('approvalInbox.reject'),
      read: t('approvalFlow.nodeCc'),
    }
    return status ? map[status] || status : '-'
  }

  function nodeTypeLabel(type?: string) {
    if (type === 'countersign') return t('approvalFlow.nodeCountersign')
    if (type === 'cc') return t('approvalFlow.nodeCc')
    return t('approvalFlow.nodeApprove')
  }

  function statusTagType(status?: string): 'success' | 'error' | 'warning' | 'default' {
    if (status === 'approved' || status === 'read') return 'success'
    if (status === 'rejected') return 'error'
    if (status === 'pending') return 'warning'
    return 'default'
  }

  function timelineItemTitle(item: ApprovalTimelineItem) {
    const who = item.assigneeName || item.assigneeId || '-'
    return `${item.stepName || '-'} · ${who} (${nodeTypeLabel(item.nodeType)})`
  }

  function timelineItemContent(item: ApprovalTimelineItem) {
    if (item.comment?.trim()) return item.comment.trim()
    return recordStatusLabel(item.status)
  }

  function sortTimeline(items?: ApprovalTimelineItem[]) {
    if (!items?.length) return []
    return [...items].sort((a, b) => {
      const stepDiff = (a.stepIndex ?? 0) - (b.stepIndex ?? 0)
      if (stepDiff !== 0) return stepDiff
      const ta = a.actionTime ? new Date(a.actionTime).getTime() : Number.MAX_SAFE_INTEGER
      const tb = b.actionTime ? new Date(b.actionTime).getTime() : Number.MAX_SAFE_INTEGER
      if (ta !== tb) return ta - tb
      return String(a.id).localeCompare(String(b.id))
    })
  }

  return {
    instanceStatusLabel,
    recordStatusLabel,
    nodeTypeLabel,
    statusTagType,
    timelineItemTitle,
    timelineItemContent,
    sortTimeline,
  }
}
