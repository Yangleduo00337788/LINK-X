<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
  NButton,
  NDataTable,
  NGi,
  NGrid,
  NModal,
  NSpace,
  NSpin,
  NStatistic,
  NTag,
  type DataTableColumns,
} from 'naive-ui'
import {
  fetchSnailJobBatches,
  fetchSnailJobLogs,
  fetchSnailJobOverview,
  type SnailJobBatchItem,
  type SnailJobLogItem,
  type SnailJobTaskItem,
} from '@/api/scheduledTasks'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const POLL_MS = 5000
const EXECUTOR_DOMAIN: Record<string, 'ops' | 'im' | 'system'> = {
  feedback_escalation: 'ops',
  review_escalation: 'ops',
  admin_export_cleanup: 'ops',
  red_packet_expire: 'im',
  group_mute: 'im',
  message_retention: 'im',
  presence_heartbeat: 'im',
  auto_unlock: 'system',
  sensitive_word_refresh: 'system',
}

const { t, locale } = useI18n()
const auth = useAuthStore()

const loading = ref(false)
const overview = ref<Awaited<ReturnType<typeof fetchSnailJobOverview>> | null>(null)
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)

const showHistory = ref(false)
const historyLoading = ref(false)
const historyTask = ref<SnailJobTaskItem | null>(null)
const batches = ref<SnailJobBatchItem[]>([])
const batchTotal = ref(0)
const batchPage = ref(1)
const batchPageSize = 20

const showLogs = ref(false)
const logsLoading = ref(false)
const activeBatch = ref<SnailJobBatchItem | null>(null)
const logs = ref<SnailJobLogItem[]>([])

const canOpenConsole = computed(() => auth.hasPermission('admin:scheduled-task:console'))

const items = computed(() => {
  const list = overview.value?.tasks || []
  const order = { ops: 0, im: 1, system: 2 }
  return [...list].sort((a, b) => {
    const da = order[EXECUTOR_DOMAIN[a.executorName] || 'system']
    const db = order[EXECUTOR_DOMAIN[b.executorName] || 'system']
    return da - db || a.jobName.localeCompare(b.jobName, 'zh-CN')
  })
})

function domainLabel(executorName: string) {
  const key = EXECUTOR_DOMAIN[executorName] || 'system'
  return t(`scheduledTask.domain.${key}`)
}

function triggerLabel(row: SnailJobTaskItem) {
  if (row.triggerType === 'CRON') {
    return row.triggerInterval || '-'
  }
  if (row.triggerType === 'SCHEDULED_TIME') {
    const sec = Number(row.triggerInterval || 0)
    return sec > 0 ? t('scheduledTask.fixedEverySeconds', { sec }) : '-'
  }
  return row.triggerInterval || '-'
}

function batchStatusTag(status?: string) {
  switch (status) {
    case 'SUCCESS':
      return { type: 'success' as const, label: t('scheduledTask.batchSuccess') }
    case 'FAIL':
      return { type: 'error' as const, label: t('scheduledTask.batchFail') }
    case 'RUNNING':
      return { type: 'info' as const, label: t('scheduledTask.batchRunning') }
    case 'WAITING':
      return { type: 'warning' as const, label: t('scheduledTask.batchWaiting') }
    case 'STOP':
    case 'CANCEL':
      return { type: 'default' as const, label: t('scheduledTask.batchStopped') }
    default:
      return { type: 'default' as const, label: status || '-' }
  }
}

function formatDuration(ms?: number | null) {
  if (ms == null || ms < 0) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

const columns = computed<DataTableColumns<SnailJobTaskItem>>(() => {
  void locale.value
  return [
    { title: t('scheduledTask.name'), key: 'jobName', width: 140 },
    {
      title: t('scheduledTask.domain.label'),
      key: 'domain',
      width: 100,
      render: (row) =>
        h(NTag, { size: 'small', bordered: false }, () => domainLabel(row.executorName)),
    },
    {
      title: t('common.description'),
      key: 'description',
      ellipsis: { tooltip: true },
      minWidth: 180,
    },
    {
      title: t('scheduledTask.status'),
      key: 'jobStatus',
      width: 90,
      render: (row) => {
        if (!row.registered) {
          return h(NTag, { size: 'small', type: 'warning' }, () => t('scheduledTask.notRegistered'))
        }
        const enabled = row.jobStatus === 1
        return h(
          NTag,
          { size: 'small', type: enabled ? 'success' : 'default' },
          () => (enabled ? t('common.enabled') : t('common.disabled'))
        )
      },
    },
    {
      title: t('scheduledTask.schedule'),
      key: 'triggerInterval',
      width: 140,
      render: (row) => triggerLabel(row),
    },
    {
      title: t('scheduledTask.lastRun'),
      key: 'lastExecutionAt',
      width: 165,
      render: (row) =>
        row.lastExecutionAt ? formatTime(row.lastExecutionAt) : t('scheduledTask.neverRun'),
    },
    {
      title: t('scheduledTask.lastResult'),
      key: 'lastBatchStatus',
      width: 90,
      render: (row) => {
        if (!row.lastBatchStatus) return '-'
        const meta = batchStatusTag(row.lastBatchStatus)
        return h(NTag, { size: 'small', type: meta.type, bordered: false }, () => meta.label)
      },
    },
    {
      title: t('scheduledTask.duration'),
      key: 'lastDurationMs',
      width: 80,
      render: (row) => formatDuration(row.lastDurationMs),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (row) =>
        h(
          NButton,
          {
            size: 'small',
            tertiary: true,
            disabled: !row.jobId,
            onClick: () => openHistory(row),
          },
          () => t('scheduledTask.viewHistory')
        ),
    },
  ]
})

const batchColumns = computed<DataTableColumns<SnailJobBatchItem>>(() => {
  void locale.value
  return [
    { title: 'ID', key: 'id', width: 80 },
    {
      title: t('scheduledTask.lastRun'),
      key: 'executionAt',
      width: 170,
      render: (row) => formatTime(row.executionAt || row.createDt),
    },
    {
      title: t('scheduledTask.lastResult'),
      key: 'batchStatus',
      width: 100,
      render: (row) => {
        const meta = batchStatusTag(row.batchStatus)
        return h(NTag, { size: 'small', type: meta.type, bordered: false }, () => meta.label)
      },
    },
    {
      title: t('scheduledTask.duration'),
      key: 'durationMs',
      width: 90,
      render: (row) => formatDuration(row.durationMs),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 100,
      render: (row) =>
        h(
          NButton,
          { size: 'small', tertiary: true, onClick: () => openLogs(row) },
          () => t('scheduledTask.viewLogs')
        ),
    },
  ]
})

const historyMeta = computed(() => {
  const task = historyTask.value
  if (!task) return ''
  const parts = [
    `${t('scheduledTask.executor')}: ${task.executorName}`,
    `${t('scheduledTask.timeout')}: ${task.executorTimeoutSeconds ?? '-'}s`,
    `${t('scheduledTask.schedule')}: ${triggerLabel(task)}`,
  ]
  return parts.join(' · ')
})

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    overview.value = await fetchSnailJobOverview()
  } finally {
    if (!silent) loading.value = false
  }
}

function openConsole() {
  const url = overview.value?.adminConsoleUrl
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

async function openHistory(row: SnailJobTaskItem) {
  if (!row.jobId) return
  historyTask.value = row
  showHistory.value = true
  batchPage.value = 1
  await loadBatches()
}

async function loadBatches() {
  if (!historyTask.value?.jobId) return
  historyLoading.value = true
  try {
    const res = await fetchSnailJobBatches(historyTask.value.jobId, batchPage.value, batchPageSize)
    batches.value = res.items || []
    batchTotal.value = res.total || 0
  } finally {
    historyLoading.value = false
  }
}

async function openLogs(batch: SnailJobBatchItem) {
  activeBatch.value = batch
  showLogs.value = true
  logsLoading.value = true
  try {
    const res = await fetchSnailJobLogs(batch.id, 1, 100)
    logs.value = res.items || []
  } finally {
    logsLoading.value = false
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void load(true)
  }
}

onMounted(() => {
  void load()
  pollTimer.value = setInterval(() => {
    if (document.visibilityState !== 'visible') return
    if (showHistory.value || showLogs.value) return
    void load(true)
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
      <NAlert
        v-if="overview && overview.monitorAvailable === false"
        type="warning"
        :bordered="false"
        class="page-alert"
      >
        {{ t('scheduledTask.monitorUnavailable') }}
      </NAlert>
      <NAlert v-else type="info" :bordered="false" class="page-alert">
        {{ t('scheduledTask.snailJobHint') }}
      </NAlert>

      <NGrid v-if="overview?.monitorAvailable" :cols="4" :x-gap="12" :y-gap="12" class="summary-grid">
        <NGi>
          <NStatistic :label="t('scheduledTask.summary.total')" :value="overview.totalTasks ?? 0" />
        </NGi>
        <NGi>
          <NStatistic
            :label="t('scheduledTask.summary.registered')"
            :value="overview.registeredTasks ?? 0"
          />
        </NGi>
        <NGi>
          <NStatistic
            :label="t('scheduledTask.summary.enabled')"
            :value="overview.enabledTasks ?? 0"
          />
        </NGi>
        <NGi>
          <NStatistic
            :label="t('scheduledTask.summary.failed')"
            :value="overview.failedTasks ?? 0"
          />
        </NGi>
      </NGrid>

      <NSpace class="page-toolbar" justify="space-between" align="center">
        <NSpace align="center" :size="16">
          <span class="meta-label">
            {{ t('scheduledTask.clientGroup') }}:
            <strong>{{ overview?.clientGroup || '-' }}</strong>
          </span>
          <span v-if="overview?.refreshedAt" class="meta-label">
            {{ t('scheduledTask.refreshedAt') }}: {{ formatTime(overview.refreshedAt) }}
          </span>
        </NSpace>
        <NSpace>
          <NButton :loading="loading" @click="load()">{{ t('common.refresh') }}</NButton>
          <NButton
            v-if="canOpenConsole"
            type="primary"
            :disabled="!overview?.adminConsoleUrl"
            @click="openConsole"
          >
            {{ t('scheduledTask.openConsole') }}
          </NButton>
        </NSpace>
      </NSpace>

      <NDataTable
        :loading="loading"
        :columns="columns"
        :data="items"
        :bordered="false"
        :scroll-x="1100"
      />
    </div>

    <NModal
      v-model:show="showHistory"
      preset="card"
      :title="t('scheduledTask.historyTitle', { name: historyTask?.jobName || '' })"
      style="width: 760px; max-width: 95vw"
    >
      <div v-if="historyMeta" class="history-meta">{{ historyMeta }}</div>
      <NSpin :show="historyLoading">
        <NDataTable :columns="batchColumns" :data="batches" :bordered="false" size="small" />
      </NSpin>
    </NModal>

    <NModal
      v-model:show="showLogs"
      preset="card"
      :title="t('scheduledTask.logTitle', { id: activeBatch?.id || '' })"
      style="width: 720px; max-width: 95vw"
    >
      <NSpin :show="logsLoading">
        <div v-if="!logs.length" class="empty-logs">{{ t('common.none') }}</div>
        <div v-for="log in logs" :key="log.id" class="log-item">
          <div class="log-meta">{{ formatTime(log.createDt) }}</div>
          <pre class="log-message">{{ log.message }}</pre>
        </div>
      </NSpin>
    </NModal>
  </div>
</template>

<style scoped>
.page-alert {
  margin-bottom: 12px;
}
.summary-grid {
  margin-bottom: 16px;
}
.page-toolbar {
  margin-bottom: 12px;
}
.meta-label {
  color: var(--n-text-color-2);
  font-size: 14px;
}
.history-meta {
  margin-bottom: 12px;
  color: var(--n-text-color-3);
  font-size: 13px;
}
.log-item {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--n-border-color);
}
.log-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}
.log-meta {
  font-size: 12px;
  color: var(--n-text-color-3);
  margin-bottom: 4px;
}
.log-message {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}
.empty-logs {
  color: var(--n-text-color-3);
  text-align: center;
  padding: 24px 0;
}
</style>
