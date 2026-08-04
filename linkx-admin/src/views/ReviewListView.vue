<script setup lang="ts">
import AdminFormShell from '@/components/AdminFormShell.vue'
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import {
  NAlert,
  NButton,
  NDataTable,
  NDatePicker,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  approveReview,
  batchReviews,
  deleteReviewContent,
  exportReviews,
  listReviews,
  rejectReview,
  type ReviewContentAction,
  type ReviewGroupAction,
  type ReviewItem,
  type ReviewUserAction,
} from '@/api/reviews'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import { notifyPendingTask } from '@/utils/adminNotify'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const route = useRoute()
const { t, locale } = useI18n()

/** 举报独立入口：锁定 sourceType=report，收窄目标类型 */
const reportOnly = computed(() => !!route.meta.reportOnly)
/** 群公告审核独立入口：锁定 targetType=announcement */
const announcementOnly = computed(() => !!route.meta.announcementOnly)
const presetLocked = computed(() => reportOnly.value || announcementOnly.value)

const loading = ref(false)
const items = ref<ReviewItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: '',
  sourceType: '',
  targetType: '',
  riskLevel: '',
  overdueOnly: false,
  escalatedOnly: false,
  range: null as [number, number] | null,
})
const knownIds = ref<Set<string>>(new Set())
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)
const POLL_MS = 5000

const showResolve = ref(false)
const resolveTarget = ref<ReviewItem | null>(null)
const resolveAction = ref<'approve' | 'reject'>('approve')
const resolution = ref('')
const userAction = ref<ReviewUserAction>('none')
const contentAction = ref<ReviewContentAction>('none')
const groupAction = ref<ReviewGroupAction>('none')
const resolveSaving = ref(false)
const checkedKeys = ref<string[]>([])
const exporting = ref(false)
const batchSaving = ref(false)

const DELETABLE_TARGETS = new Set([
  'message',
  'moment',
  'moment_comment',
  'announcement',
  'group_file',
  'favorite',
])

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('review.pending'), value: 'pending' },
    { label: t('review.approved'), value: 'approved' },
    { label: t('review.rejected'), value: 'rejected' },
  ]
})

const sourceOptions = computed(() => {
  void locale.value
  return [
    { label: t('review.allSources'), value: '' },
    { label: t('review.sourceReport'), value: 'report' },
    { label: t('review.sourceSensitive'), value: 'sensitive' },
    { label: t('review.sourceManual'), value: 'manual' },
  ]
})

const targetTypeOptions = computed(() => {
  void locale.value
  if (announcementOnly.value) {
    return [{ label: t('review.targetAnnouncement'), value: 'announcement' }]
  }
  if (reportOnly.value) {
    return [
      { label: t('review.allTargetTypes'), value: '' },
      { label: t('review.targetUser'), value: 'user' },
      { label: t('review.targetGroup'), value: 'group' },
    ]
  }
  return [
    { label: t('review.allTargetTypes'), value: '' },
    { label: 'moment', value: 'moment' },
    { label: 'moment_comment', value: 'moment_comment' },
    { label: 'message', value: 'message' },
    { label: 'announcement', value: 'announcement' },
    { label: 'group_file', value: 'group_file' },
    { label: 'favorite', value: 'favorite' },
    { label: 'user', value: 'user' },
    { label: 'group', value: 'group' },
    { label: 'conversation', value: 'conversation' },
  ]
})

const riskLevelOptions = computed(() => {
  void locale.value
  return [
    { label: t('review.allRiskLevels'), value: '' },
    { label: t('review.riskLow'), value: 'low' },
    { label: t('review.riskMedium'), value: 'medium' },
    { label: t('review.riskHigh'), value: 'high' },
    { label: t('review.riskCritical'), value: 'critical' },
  ]
})

const userActionOptions = computed(() => {
  void locale.value
  return [
    { label: t('review.userActionNone'), value: 'none' },
    { label: t('review.userActionFreeze'), value: 'freeze' },
    { label: t('review.userActionBan'), value: 'ban' },
  ]
})

const contentActionOptions = computed(() => {
  void locale.value
  return [
    { label: t('review.contentActionNone'), value: 'none' },
    { label: t('review.contentActionDelete'), value: 'delete' },
  ]
})

const groupActionOptions = computed(() => {
  void locale.value
  return [
    { label: t('review.groupActionNone'), value: 'none' },
    { label: t('review.groupActionDissolve'), value: 'dissolve' },
    { label: t('review.groupActionFreezeOwner'), value: 'freeze_owner' },
    { label: t('review.groupActionBanOwner'), value: 'ban_owner' },
  ]
})

function isGroupTarget(row?: ReviewItem | null) {
  return row?.targetType === 'group'
}

function statusTag(row: ReviewItem) {
  const status = row.status
  const tags: ReturnType<typeof h>[] = []
  if (row.escalated) {
    const label =
      row.escalationCount && row.escalationCount > 1
        ? t('review.escalatedCount', { count: row.escalationCount })
        : t('review.escalated')
    tags.push(h(NTag, { type: 'error', size: 'small' }, () => label))
  } else if (row.overdue) {
    tags.push(h(NTag, { type: 'error', size: 'small' }, () => t('review.overdue')))
  }
  const map: Record<string, 'warning' | 'success' | 'error' | 'default'> = {
    pending: 'warning',
    approved: 'success',
    rejected: 'error',
  }
  const label: Record<string, string> = {
    pending: t('review.pending'),
    approved: t('review.approved'),
    rejected: t('review.rejected'),
  }
  tags.push(
    h(
      NTag,
      { type: map[status || ''] || 'default', size: 'small' },
      () => label[status || ''] || status || '-'
    )
  )
  if (tags.length === 1) return tags[0]
  return h(NSpace, { size: 4, align: 'center' }, () => tags)
}

function sourceLabel(source?: string) {
  const map: Record<string, string> = {
    report: t('review.sourceReport'),
    sensitive: t('review.sourceSensitive'),
    manual: t('review.sourceManual'),
  }
  return map[source || ''] || source || '-'
}

function canDeleteContent(row?: ReviewItem | null) {
  return !!row && DELETABLE_TARGETS.has(row.targetType || '')
}

function riskLevelTag(level?: string) {
  const map: Record<string, 'error' | 'warning' | 'info' | 'default'> = {
    critical: 'error',
    high: 'error',
    medium: 'warning',
    low: 'info',
  }
  const label: Record<string, string> = {
    critical: t('review.riskCritical'),
    high: t('review.riskHigh'),
    medium: t('review.riskMedium'),
    low: t('review.riskLow'),
  }
  return h(
    NTag,
    { type: map[level || ''] || 'default', size: 'small' },
    () => label[level || ''] || level || '-'
  )
}

const columns = computed<DataTableColumns<ReviewItem>>(() => {
  void locale.value
  return [
    { type: 'selection', disabled: (row) => row.status !== 'pending' },
    { title: 'ID', key: 'id', width: 90 },
    {
      title: t('review.source'),
      key: 'sourceType',
      width: 100,
      render: (row) => sourceLabel(row.sourceType),
    },
    { title: t('review.title'), key: 'title', width: 160, ellipsis: { tooltip: true } },
    { title: t('review.content'), key: 'contentSnapshot', ellipsis: { tooltip: true } },
    { title: t('review.reporter'), key: 'reporterUsername', width: 110 },
    {
      title: t('review.target'),
      key: 'targetId',
      width: 140,
      ellipsis: { tooltip: true },
      render: (row) => {
        const type = row.targetType || '-'
        const id = row.targetId || '-'
        return `${type}:${id}`
      },
    },
    {
      title: t('review.riskLevel'),
      key: 'riskLevel',
      width: 90,
      render: (row) => riskLevelTag(row.riskLevel),
    },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: (row) => statusTag(row),
    },
    {
      title: t('common.time'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 220,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          h(NButton, { size: 'tiny', onClick: () => showDetail(row) }, () => t('common.detail')),
          row.status === 'pending' &&
          auth.hasPermission('admin:review:delete-content') &&
          canDeleteContent(row)
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'warning',
                  tertiary: true,
                  onClick: () => confirmDeleteContent(row),
                },
                () => t('review.deleteContent')
              )
            : null,
          row.status === 'pending' && auth.hasPermission('admin:review:approve')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'success',
                  secondary: true,
                  onClick: () => openResolve(row, 'approve'),
                },
                () => t('review.approve')
              )
            : null,
          row.status === 'pending' && auth.hasPermission('admin:review:reject')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'error',
                  secondary: true,
                  onClick: () => openResolve(row, 'reject'),
                },
                () => t('review.reject')
              )
            : null,
        ]),
    },
  ]
})

function openResolve(row: ReviewItem, action: 'approve' | 'reject') {
  resolveTarget.value = row
  resolveAction.value = action
  resolution.value = ''
  userAction.value = 'none'
  contentAction.value = 'none'
  groupAction.value = 'none'
  showResolve.value = true
}

function stripEvidenceText(content?: string) {
  if (!content) return '-'
  return (
    content
      .replace(/^证据图片:\s*$/gm, '')
      .replace(/^\d+\.\s*[\w./-]+\.(?:png|jpe?g|gif|webp|bmp)\s*$/gim, '')
      .replace(/^证据图片:\s*无\s*$/gm, `${t('review.evidence')}: ${t('review.evidenceNone')}`)
      .replace(/\n{3,}/g, '\n\n')
      .trim() || '-'
  )
}

function showDetail(row: ReviewItem) {
  const urls = row.evidenceUrls || []
  dialog.info({
    title: row.title || t('review.detailTitle'),
    content: () =>
      h('div', { style: 'line-height: 1.6; max-height: 420px; overflow: auto;' }, [
        h('div', `${t('review.source')}: ${sourceLabel(row.sourceType)}`),
        h('div', `${t('review.targetType')}: ${row.targetType || '-'}`),
        h('div', `${t('review.target')}: ${row.targetId || '-'}`),
        h('div', `${t('review.subjectUser')}: ${row.subjectUserId || '-'}`),
        h('div', { style: 'display:flex; align-items:center; gap:8px;' }, [
          h('span', `${t('review.riskLevel')}:`),
          riskLevelTag(row.riskLevel),
        ]),
        h(
          'div',
          { style: 'white-space: pre-wrap; margin: 12px 0;' },
          stripEvidenceText(row.contentSnapshot)
        ),
        urls.length
          ? h('div', { style: 'margin-bottom: 8px; font-weight: 600;' }, t('review.evidence'))
          : null,
        urls.length
          ? h(
              'div',
              { style: 'display: flex; flex-direction: column; gap: 10px;' },
              urls.map((src) =>
                h('a', { href: src, target: '_blank', rel: 'noopener noreferrer' }, [
                  h('img', {
                    src,
                    alt: '',
                    style:
                      'max-width: 100%; max-height: 360px; width: auto; height: auto; object-fit: contain; border-radius: var(--lx-radius); border: 1px solid var(--lx-border, #e5e5e5); display: block;',
                  }),
                ])
              )
            )
          : null,
        row.resolution
          ? h(
              'div',
              { style: 'white-space: pre-wrap; margin-top: 12px;' },
              `${t('review.resolution')}: ${row.resolution}`
            )
          : null,
      ]),
    positiveText: t('common.confirm'),
  })
}

async function submitResolve() {
  if (!resolveTarget.value) return
  resolveSaving.value = true
  try {
    const note = resolution.value.trim() || undefined
    if (resolveAction.value === 'approve') {
      const isGroup = isGroupTarget(resolveTarget.value)
      const punish = isGroup ? 'none' : userAction.value
      const content = canDeleteContent(resolveTarget.value) ? contentAction.value : 'none'
      const group = isGroup ? groupAction.value : 'none'
      await approveReview(resolveTarget.value.id, {
        resolution: note,
        userAction: punish,
        contentAction: content,
        groupAction: group,
      })
      if (group === 'dissolve') {
        message.success(t('review.groupActionDissolve'))
      } else if (content === 'delete') {
        message.success(t('review.approveWithDeleteSuccess'))
      } else if (punish !== 'none' || group === 'freeze_owner' || group === 'ban_owner') {
        message.success(t('review.approveWithPunishSuccess'))
      } else {
        message.success(t('review.approveSuccess'))
      }
    } else {
      const content = canDeleteContent(resolveTarget.value) ? contentAction.value : 'none'
      await rejectReview(resolveTarget.value.id, { resolution: note, contentAction: content })
      if (content === 'delete') {
        message.success(t('review.rejectWithDeleteSuccess'))
      } else {
        message.success(t('review.rejectSuccess'))
      }
    }
    showResolve.value = false
    await load()
  } catch {
    // 错误文案由 request 拦截器统一弹出（如「群主已被封禁」）
  } finally {
    resolveSaving.value = false
  }
}

function confirmDeleteContent(row: ReviewItem) {
  dialog.warning({
    title: t('review.deleteContentTitle'),
    content: t('review.deleteContentConfirm', { title: row.title || row.id }),
    positiveText: t('review.deleteContent'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await deleteReviewContent(row.id)
        message.success(t('review.deleteContentSuccess'))
        await load()
      } catch {
        // request 拦截器统一提示
      }
    },
  })
}

async function doBatch(action: 'approve' | 'reject') {
  if (!checkedKeys.value.length) {
    message.warning(t('review.batchEmpty'))
    return
  }
  dialog.warning({
    title: action === 'approve' ? t('review.batchApproveTitle') : t('review.batchRejectTitle'),
    content: t('review.batchConfirm', { n: checkedKeys.value.length }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      batchSaving.value = true
      try {
        const result = await batchReviews(checkedKeys.value, action)
        checkedKeys.value = []
        if (result.failCount > 0) {
          message.warning(
            t('review.batchPartial', { ok: result.successCount, fail: result.failCount })
          )
        } else {
          message.success(t('review.batchSuccess', { n: result.successCount }))
        }
        await load()
      } finally {
        batchSaving.value = false
      }
    },
  })
}

async function doExport() {
  exporting.value = true
  try {
    await exportReviews({
      keyword: query.keyword || undefined,
      reviewStatus: query.overdueOnly ? 'pending' : query.status || undefined,
      sourceType: query.sourceType || undefined,
      targetType: query.targetType || undefined,
      riskLevel: query.riskLevel || undefined,
      overdueOnly: query.overdueOnly || undefined,
      escalatedOnly: query.escalatedOnly || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    message.success(t('common.exportSuccess'))
  } finally {
    exporting.value = false
  }
}

async function load(opts?: { silent?: boolean; announceNew?: boolean }) {
  const silent = !!opts?.silent
  if (!silent) loading.value = true
  try {
    if (reportOnly.value) query.sourceType = 'report'
    if (announcementOnly.value) query.targetType = 'announcement'
    const data = await listReviews({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      reviewStatus: query.overdueOnly ? 'pending' : query.status || undefined,
      sourceType: query.sourceType || undefined,
      targetType: query.targetType || undefined,
      riskLevel: query.riskLevel || undefined,
      overdueOnly: query.overdueOnly || undefined,
      escalatedOnly: query.escalatedOnly || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    const next = data.items || []
    if (opts?.announceNew && knownIds.value.size > 0) {
      const fresh = next.filter((row) => !knownIds.value.has(String(row.id)))
      if (fresh.length > 0 && query.page === 1) {
        message.info(t('review.newArrived', { n: fresh.length }))
        notifyPendingTask(t, locale.value)
      }
    }
    items.value = next
    total.value = data.total || 0
    knownIds.value = new Set(next.map((row) => String(row.id)))
  } finally {
    if (!silent) loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function applyReportPreset() {
  query.sourceType = 'report'
  search()
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void load({ silent: true, announceNew: true })
  }
}

onMounted(() => {
  const overdue = route.query.overdueOnly
  if (overdue === '1' || overdue === 'true') {
    query.overdueOnly = true
  }
  if (reportOnly.value) {
    query.sourceType = 'report'
    query.status = query.status || 'pending'
  }
  if (announcementOnly.value) {
    query.targetType = 'announcement'
    query.status = query.status || 'pending'
  }
  void load()
  pollTimer.value = setInterval(() => {
    if (document.visibilityState !== 'visible') return
    if (showResolve.value) return
    void load({ silent: true, announceNew: true })
  }, POLL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NAlert v-if="announcementOnly" type="info" :bordered="false" class="preset-hint">
        {{ t('review.announcementOnlyHint') }}
      </NAlert>
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('review.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect v-model:value="query.status" :options="statusOptions" :disabled="query.overdueOnly" style="width: 140px" />
          <NSpace align="center" :size="4">
            <span class="muted">{{ t('review.overdueOnly') }}</span>
            <NSwitch v-model:value="query.overdueOnly" @update:value="search" />
          </NSpace>
          <NSpace align="center" :size="4">
            <span class="muted">{{ t('review.escalatedOnly') }}</span>
            <NSwitch v-model:value="query.escalatedOnly" @update:value="search" />
          </NSpace>
          <NSelect
            v-if="!presetLocked"
            v-model:value="query.sourceType"
            :options="sourceOptions"
            style="width: 140px"
          />
          <NSelect
            v-if="!announcementOnly"
            v-model:value="query.targetType"
            :options="targetTypeOptions"
            style="width: 160px"
          />
          <NSelect
            v-model:value="query.riskLevel"
            :options="riskLevelOptions"
            clearable
            style="width: 130px"
            @update:value="search"
          />
          <NButton v-if="!presetLocked" secondary @click="applyReportPreset">
            {{ t('review.reportPreset') }}
          </NButton>
          <NDatePicker
            v-model:value="query.range"
            type="datetimerange"
            clearable
            style="width: 360px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NSpace>
          <NButton
            v-if="auth.hasPermission('admin:review:batch')"
            :disabled="!checkedKeys.length"
            :loading="batchSaving"
            type="success"
            secondary
            @click="doBatch('approve')"
          >
            {{ t('review.batchApprove') }}
          </NButton>
          <NButton
            v-if="auth.hasPermission('admin:review:batch')"
            :disabled="!checkedKeys.length"
            :loading="batchSaving"
            type="error"
            secondary
            @click="doBatch('reject')"
          >
            {{ t('review.batchReject') }}
          </NButton>
          <NButton
            v-if="auth.hasPermission('admin:review:export')"
            :loading="exporting"
            @click="doExport"
          >
            {{ t('common.export') }}
          </NButton>
        </NSpace>
      </NSpace>
      <NDataTable
        v-model:checked-row-keys="checkedKeys"
        :columns="columns"
        :data="items"
        :loading="loading"
        :row-key="(row: ReviewItem) => row.id"
        :scroll-x="1320"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [10, 20, 50],
          onUpdatePage: (p: number) => {
            query.page = p
            load()
          },
          onUpdatePageSize: (s: number) => {
            query.size = s
            query.page = 1
            load()
          },
        }"
        remote
      />
    </div>

    <AdminFormShell
      v-model:show="showResolve"
      
      :title="resolveAction === 'approve' ? t('review.approveTitle') : t('review.rejectTitle')"
      
     :width="560">
      <p class="quote">{{ stripEvidenceText(resolveTarget?.contentSnapshot) }}</p>
      <div v-if="resolveTarget?.evidenceUrls?.length" class="evidence-block">
        <div class="evidence-label">{{ t('review.evidence') }}</div>
        <div class="evidence-grid">
          <a
            v-for="src in resolveTarget.evidenceUrls"
            :key="src"
            :href="src"
            target="_blank"
            rel="noopener noreferrer"
          >
            <img :src="src" alt="" />
          </a>
        </div>
      </div>

      <template v-if="resolveAction === 'approve'">
        <template v-if="isGroupTarget(resolveTarget)">
          <NFormItem :label="t('review.groupAction')">
            <NSelect v-model:value="groupAction" :options="groupActionOptions" />
          </NFormItem>
          <p class="hint">{{ t('review.groupActionHint') }}</p>
        </template>
        <NFormItem v-else :label="t('review.userAction')">
          <NSelect v-model:value="userAction" :options="userActionOptions" />
        </NFormItem>
        <NFormItem v-if="canDeleteContent(resolveTarget)" :label="t('review.contentAction')">
          <NSelect v-model:value="contentAction" :options="contentActionOptions" />
        </NFormItem>
        <p v-if="canDeleteContent(resolveTarget)" class="hint">
          {{ t('review.contentActionHint') }}
        </p>
      </template>
      <template v-else>
        <NFormItem v-if="canDeleteContent(resolveTarget)" :label="t('review.contentAction')">
          <NSelect v-model:value="contentAction" :options="contentActionOptions" />
        </NFormItem>
        <p v-if="canDeleteContent(resolveTarget)" class="hint">
          {{ t('review.contentActionHint') }}
        </p>
        <p v-else class="hint">{{ t('review.rejectHint') }}</p>
      </template>

      <NInput
        v-model:value="resolution"
        type="textarea"
        :rows="3"
        :placeholder="t('review.resolutionPlaceholder')"
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showResolve = false">{{ t('common.cancel') }}</NButton>
          <NButton
            :type="resolveAction === 'approve' ? 'primary' : 'error'"
            :loading="resolveSaving"
            @click="submitResolve"
          >
            {{ resolveAction === 'approve' ? t('review.approve') : t('review.reject') }}
          </NButton>
        </NSpace>
      </template>
    </AdminFormShell>
  </div>
</template>

<style scoped>
.quote {
  color: var(--lx-text-2);
  margin-top: 0;
  margin-bottom: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  max-height: 180px;
  overflow: auto;
}
.preset-hint {
  margin-bottom: 12px;
}
.muted {
  color: var(--lx-text-3, #999);
  font-size: 13px;
}
.hint {
  color: var(--lx-text-3, #999);
  font-size: 12px;
  margin: 0 0 12px;
  line-height: 1.5;
}
.evidence-block {
  margin-bottom: 12px;
}
.evidence-label {
  font-weight: 600;
  margin-bottom: 8px;
}
.evidence-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.evidence-grid img {
  max-width: 100%;
  max-height: 280px;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: var(--lx-radius);
  border: 1px solid var(--lx-border, #e5e5e5);
  display: block;
}
</style>
