<!-- 作者：yangleduo -->
<script setup lang="ts">
import AdminFormShell from '@/components/AdminFormShell.vue'
import { computed, h, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
  NButton,
  NDataTable,
  NEmpty,
  NGi,
  NGrid,
  NSpace,
  NSpin,
  NStatistic,
  NTag,
  type DataTableColumns,
} from 'naive-ui'
import { fetchMonitorTasks, toTrendData } from '@/api/systemMonitorMetrics'
import {
  fetchSnailJobBatches,
  fetchSnailJobLogs,
  fetchSnailJobOverview,
  type SnailJobBatchItem,
  type SnailJobLogItem,
  type SnailJobTaskItem,
} from '@/api/scheduledTasks'
import { buildAreaOption, buildDonutOption, useChart } from '@/utils/charts'
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

const metricsLoading = ref(false)
const liveLoading = ref(false)
const error = ref<string | null>(null)
const metrics = ref<Awaited<ReturnType<typeof fetchMonitorTasks>> | null>(null)
const overview = ref<Awaited<ReturnType<typeof fetchSnailJobOverview>> | null>(null)
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)

const trendEl = ref<HTMLElement | null>(null)
const ratioEl = ref<HTMLElement | null>(null)

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
const canViewScheduledTasks = computed(() => auth.hasPermission('admin:scheduled-task:list'))

const trendOpt = computed(() =>
  buildAreaOption(
    toTrendData(metrics.value?.dailyTrend),
    (k) => {
      const map: Record<string, string> = {
        success: t('monitor.taskSuccess'),
        fail: t('monitor.taskFail'),
      }
      return map[k] || k
    },
    { stacked: true }
  )
)
const ratioOpt = computed(() =>
  buildDonutOption(
    [
      { key: 'success', name: t('monitor.taskSuccess'), value: metrics.value?.successBatches || 0 },
      { key: 'fail', name: t('monitor.taskFail'), value: metrics.value?.failedBatches || 0 },
    ],
    (_, fallback) => fallback
  )
)

const totalBatches = computed(
  () => (metrics.value?.successBatches || 0) + (metrics.value?.failedBatches || 0)
)
const hasBatchHistory = computed(() => totalBatches.value > 0)

const trendChart = useChart(trendEl, trendOpt)
const ratioChart = useChart(ratioEl, ratioOpt)

function refreshCharts() {
  trendChart.refresh()
  ratioChart.refresh()
}

const liveItems = computed(() => {
  const list = overview.value?.tasks || metrics.value?.tasks || []
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
  if (row.triggerType === 'CRON') return row.triggerInterval || '-'
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

const taskColumns = computed<DataTableColumns<SnailJobTaskItem>>(() => {
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
      minWidth: 160,
    },
    {
      title: t('scheduledTask.status'),
      key: 'jobStatus',
      width: 100,
      render: (row) => {
        if (!row.registered) {
          return h(NTag, { size: 'small', type: 'warning' }, () => t('scheduledTask.notRegistered'))
        }
        const enabled = row.jobStatus === 1
        return h(
          NTag,
          { size: 'small', type: enabled ? 'success' : 'default', bordered: false },
          () => (enabled ? t('common.enabled') : t('common.disabled'))
        )
      },
    },
    {
      title: t('scheduledTask.schedule'),
      key: 'triggerInterval',
      width: 130,
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
  return [
    `${t('scheduledTask.executor')}: ${task.executorName}`,
    `${t('scheduledTask.timeout')}: ${task.executorTimeoutSeconds ?? '-'}s`,
    `${t('scheduledTask.schedule')}: ${triggerLabel(task)}`,
  ].join(' · ')
})

const monitorAvailable = computed(
  () => overview.value?.monitorAvailable ?? metrics.value?.monitorAvailable ?? true
)

async function loadMetrics() {
  metricsLoading.value = true
  error.value = null
  try {
    metrics.value = await fetchMonitorTasks(7)
    await nextTick()
    refreshCharts()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.requestFailed')
    metrics.value = null
  } finally {
    metricsLoading.value = false
  }
}

async function loadLive(silent = false) {
  if (!canViewScheduledTasks.value) return
  if (!silent) liveLoading.value = true
  try {
    overview.value = await fetchSnailJobOverview()
  } finally {
    if (!silent) liveLoading.value = false
  }
}

async function loadAll() {
  await loadMetrics()
  if (canViewScheduledTasks.value) {
    await loadLive()
  }
}

function openConsole() {
  const url = overview.value?.adminConsoleUrl
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

async function openHistory(row: SnailJobTaskItem) {
  if (!canViewScheduledTasks.value || !row.jobId) return
  historyTask.value = row
  showHistory.value = true
  batchPage.value = 1
  await loadBatches()
}

async function loadBatches() {
  if (!canViewScheduledTasks.value || !historyTask.value?.jobId) return
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
  if (!canViewScheduledTasks.value) return
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
  if (document.visibilityState === 'visible' && canViewScheduledTasks.value) {
    void loadLive(true)
  }
}

onMounted(() => {
  void loadAll()
  pollTimer.value = setInterval(() => {
    if (document.visibilityState !== 'visible') return
    if (!canViewScheduledTasks.value) return
    if (showHistory.value || showLogs.value) return
    void loadLive(true)
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
    <NSpace justify="end" class="toolbar">
      <NButton :loading="metricsLoading || liveLoading" @click="loadAll()">
        {{ t('common.refresh') }}
      </NButton>
      <NButton
        v-if="canOpenConsole"
        type="primary"
        :disabled="!overview?.adminConsoleUrl"
        @click="openConsole"
      >
        {{ t('scheduledTask.openConsole') }}
      </NButton>
    </NSpace>

    <NSpin :show="metricsLoading && !metrics && !error">
      <NEmpty v-if="error && !metrics" :description="error" class="empty" />
      <template v-else-if="metrics">
        <NAlert
          v-if="!monitorAvailable"
          type="warning"
          :title="t('scheduledTask.monitorUnavailable')"
          class="warn"
        />
        <NAlert v-else type="info" :bordered="false" class="hint">
          {{ t('scheduledTask.snailJobHint') }}
        </NAlert>

        <NGrid :cols="4" :x-gap="12" class="summary">
          <NGi>
            <NStatistic :label="t('scheduledTask.summary.total')" :value="metrics.totalTasks" />
          </NGi>
          <NGi>
            <NStatistic :label="t('scheduledTask.summary.enabled')" :value="metrics.enabledTasks" />
          </NGi>
          <NGi>
            <NStatistic
              :label="t('monitor.taskSuccessRate')"
              :value="`${metrics.successRatePercent}%`"
            />
          </NGi>
          <NGi>
            <NStatistic :label="t('scheduledTask.summary.failed')" :value="metrics.failedTasks" />
          </NGi>
        </NGrid>

        <NGrid :cols="2" :x-gap="12" :y-gap="12">
          <NGi>
            <div class="page-card chart-card">
              <h4>{{ t('monitor.taskDailyTrend') }}</h4>
              <div ref="trendEl" class="chart" />
            </div>
          </NGi>
          <NGi>
            <div class="page-card chart-card">
              <h4>{{ t('monitor.taskSuccessRatio') }}</h4>
              <NEmpty
                v-if="!hasBatchHistory"
                :description="t('monitor.taskBatchEmpty')"
                class="chart-empty"
              />
              <div v-else ref="ratioEl" class="chart" />
            </div>
          </NGi>
        </NGrid>

        <NSpace v-if="overview" class="live-meta" align="center" :size="16">
          <span class="meta-label">
            {{ t('scheduledTask.clientGroup') }}:
            <strong>{{ overview.clientGroup || '-' }}</strong>
          </span>
          <span v-if="overview.refreshedAt" class="meta-label">
            {{ t('scheduledTask.refreshedAt') }}: {{ formatTime(overview.refreshedAt) }}
          </span>
        </NSpace>

        <div class="page-card chart-card">
          <h4>{{ t('monitor.taskList') }}</h4>
          <NDataTable
            :loading="liveLoading"
            :columns="taskColumns"
            :data="liveItems"
            :bordered="false"
            size="small"
            :scroll-x="1100"
          />
        </div>
      </template>
    </NSpin>

    <AdminFormShell
      v-model:show="showHistory"
      :title="t('scheduledTask.historyTitle', { name: historyTask?.jobName || '' })"
      :width="760"
    >
      <div v-if="historyMeta" class="history-meta">{{ historyMeta }}</div>
      <NSpin :show="historyLoading">
        <NDataTable :columns="batchColumns" :data="batches" :bordered="false" size="small" />
      </NSpin>
    </AdminFormShell>

    <AdminFormShell
      v-model:show="showLogs"
      :title="t('scheduledTask.logTitle', { id: activeBatch?.id || '' })"
      :width="720"
    >
      <NSpin :show="logsLoading">
        <div v-if="!logs.length" class="empty-logs">{{ t('common.none') }}</div>
        <div v-for="log in logs" :key="log.id" class="log-item">
          <div class="log-meta">{{ formatTime(log.createDt) }}</div>
          <pre class="log-message">{{ log.message }}</pre>
        </div>
      </NSpin>
    </AdminFormShell>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.warn,
.hint {
  margin-bottom: 12px;
}
.summary {
  margin-bottom: 16px;
}
.chart-card {
  padding: 12px;
  margin-bottom: 12px;
}
.chart-card h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
.chart {
  height: 260px;
}
.chart-empty,
.empty {
  padding: 48px 0;
}
.live-meta {
  margin-bottom: 8px;
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
