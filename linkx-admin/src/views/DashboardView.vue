<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCollapseTransition,
  NDataTable,
  NIcon,
  NProgress,
  NSelect,
  NSpin,
  NTag,
  type DataTableColumns,
} from 'naive-ui'
import {
  ChatbubblesOutline,
  WarningOutline,
  PeopleOutline,
  FlagOutline,
  StatsChartOutline,
  PulseOutline,
  SettingsOutline,
  RefreshOutline,
  ChevronDownOutline,
  ChevronUpOutline,
} from '@vicons/ionicons5'
import {
  fetchDashboardRealtime,
  fetchDashboardSummary,
  fetchDashboardTrends,
  fetchPendingTasks,
  type DashboardRealtime,
  type DashboardSummary,
  type PendingTask,
} from '@/api/dashboard'
import type { TrendData } from '@/api/statistics'
import { listNoticeInbox, type NoticeItem } from '@/api/notices'
import { fetchMonitorService, type MonitorService } from '@/api/systemMonitorMetrics'
import { useAuthStore } from '@/stores/auth'
import {
  buildAreaOption,
  buildColumnOption,
  buildDonutOption,
  buildHBarOption,
  useChart,
  useDaysOptions,
  type NamedValue,
} from '@/utils/charts'
import AdminOpsBannerCarousel from '@/components/AdminOpsBannerCarousel.vue'
import { storeToRefs } from 'pinia'
import { usePreferencesStore } from '@/stores/preferences'

const { primaryColor } = storeToRefs(usePreferencesStore())

const MODULE_COLORS = computed(() => [
  '#13c2c2',
  primaryColor.value,
  '#722ed1',
  '#fa8c16',
  '#52c41a',
  '#eb2f96',
  '#2f54eb',
  '#faad14',
])

const { t, locale } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const POLL_MS = 60_000

const loading = ref(false)
const trendLoading = ref(false)
const trendsExpanded = ref(true)
const summary = ref<DashboardSummary | null>(null)
const realtime = ref<DashboardRealtime | null>(null)
const pending = ref<PendingTask[]>([])
const notices = ref<NoticeItem[]>([])
const serviceInfo = ref<MonitorService | null>(null)
const trends = ref<TrendData | null>(null)
const days = ref(14)
const lastUpdatedAt = ref<Date | null>(null)
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)

const trendEl = ref<HTMLElement | null>(null)
const todayEl = ref<HTMLElement | null>(null)
const activeEl = ref<HTMLElement | null>(null)
const pendingEl = ref<HTMLElement | null>(null)
const riskEl = ref<HTMLElement | null>(null)
const messagesEl = ref<HTMLElement | null>(null)
const loginsEl = ref<HTMLElement | null>(null)

const daysOptions = useDaysOptions((k) => t(k))

function toNum(v: unknown): number {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function fmt(n: number) {
  return new Intl.NumberFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US').format(n || 0)
}

function clampPct(v: unknown) {
  const n = toNum(v)
  if (!Number.isFinite(n) || n < 0) return 0
  return Math.min(100, n)
}

function formatPct(v: unknown) {
  const n = toNum(v)
  return Number.isFinite(n) && n >= 0 ? `${n.toFixed(1)}%` : '-'
}

function usageStatus(pct: number): 'success' | 'warning' | 'error' {
  if (pct >= 90) return 'error'
  if (pct >= 70) return 'warning'
  return 'success'
}

function metricName(key: string) {
  const map: Record<string, string> = {
    totalUsers: t('dashboard.totalUsers'),
    dau: t('dashboard.dau'),
    wau: t('dashboard.wau'),
    mau: t('dashboard.mau'),
    onlineDevices: t('dashboard.onlineDevices'),
    pendingFeedback: t('dashboard.pendingFeedback'),
    overdueFeedback: t('dashboard.overdueFeedback'),
    overdueReviews: t('dashboard.overdueReviews'),
    pendingReports: t('dashboard.pendingReports'),
    pendingReviews: t('dashboard.pendingReviews'),
    todaySensitiveHits: t('dashboard.todaySensitiveHits'),
    todayRiskBlocks: t('dashboard.todayRiskBlocks'),
    riskEvents: t('dashboard.riskEvents'),
  }
  return map[key] || key
}

function seriesName(key: string, fallback: string) {
  const map: Record<string, string> = {
    newUsers: t('statistics.seriesNewUsers'),
    messages: t('statistics.seriesMessages'),
    logins: t('statistics.seriesLogins'),
  }
  return map[key] || fallback
}

function taskTitle(task: PendingTask) {
  const map: Record<string, string> = {
    pendingFeedback: t('dashboard.pendingFeedback'),
    overdueFeedback: t('dashboard.overdueFeedback'),
    overdueReviews: t('dashboard.overdueReviews'),
    pendingReports: t('dashboard.pendingReports'),
    pendingReviews: t('dashboard.pendingReviews'),
    riskEvents: t('dashboard.riskEvents'),
  }
  return map[task.title] || task.title
}

function isUrgentTask(task: PendingTask) {
  return task.type.includes('overdue')
}

function formatNoticeTime(row: NoticeItem) {
  const raw = row.publishedAt || row.createTime
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return d.toLocaleString(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function statNavigatePath(key: string): string | undefined {
  const map: Record<string, { path: string; perm: string }> = {
    totalUsers: { path: '/admin/users', perm: 'admin:user:list' },
    dau: { path: '/admin/statistics', perm: 'admin:statistics:view' },
    wau: { path: '/admin/statistics', perm: 'admin:statistics:view' },
    mau: { path: '/admin/statistics', perm: 'admin:statistics:view' },
    onlineDevices: { path: '/admin/devices', perm: 'admin:device:list' },
    todayNewUsers: { path: '/admin/users', perm: 'admin:user:list' },
    todayMessages: { path: '/admin/statistics', perm: 'admin:statistics:view' },
    todayLogins: { path: '/admin/login-logs', perm: 'admin:login-log:list' },
    riskEvents24h: { path: '/admin/risk-events', perm: 'admin:risk-event:list' },
  }
  const item = map[key]
  if (!item || !auth.hasPermission(item.perm)) return undefined
  return item.path
}

const statStrip = computed(() => {
  void locale.value
  const s = summary.value
  const r = realtime.value
  const items = [
    { key: 'totalUsers', label: metricName('totalUsers'), value: toNum(s?.totalUsers) },
    { key: 'dau', label: metricName('dau'), value: toNum(s?.dau) },
    { key: 'wau', label: metricName('wau'), value: toNum(s?.wau) },
    { key: 'mau', label: metricName('mau'), value: toNum(s?.mau) },
    { key: 'onlineDevices', label: metricName('onlineDevices'), value: toNum(s?.onlineDevices) },
    {
      key: 'todayNewUsers',
      label: t('statistics.todayNewUsers'),
      value: toNum(r?.todayNewUsers),
    },
    {
      key: 'todayMessages',
      label: t('statistics.todayMessages'),
      value: toNum(r?.todayMessages),
    },
    {
      key: 'todayLogins',
      label: t('statistics.todayLogins'),
      value: toNum(r?.todayLogins),
    },
    {
      key: 'riskEvents24h',
      label: t('dashboard.riskEvents24h'),
      value: toNum(r?.riskEvents24h),
    },
  ]
  return items.map((item) => ({
    ...item,
    path: statNavigatePath(item.key),
  }))
})

const lastUpdatedText = computed(() => {
  void locale.value
  if (!lastUpdatedAt.value) return ''
  return t('dashboard.lastUpdated', {
    time: lastUpdatedAt.value.toLocaleTimeString(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }),
  })
})

const urgentTasks = computed(() => pending.value.filter(isUrgentTask))

const normalPending = computed(() => pending.value.filter((task) => !isUrgentTask(task)))

const todoGridStyle = computed(() => {
  const n = normalPending.value.length
  if (n <= 0) return {}
  const cols = n >= 4 ? 4 : n
  return { gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }
})

const urgentGridStyle = computed(() => {
  const n = urgentTasks.value.length
  if (n <= 0) return {}
  return { gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))` }
})

const pendingTotal = computed(() =>
  pending.value.reduce((sum, task) => sum + toNum(task.count), 0)
)

const trendsTitleText = computed(() => {
  void locale.value
  return t('dashboard.trendsTitle', { days: days.value })
})

function detectMessageSpike() {
  const series = trends.value?.series?.find((s) => s.key === 'messages')
  const data = (series?.data || []).map((v) => toNum(v))
  if (data.length < 4) return false
  const last = data[data.length - 1] ?? 0
  const prev = data.slice(0, -1)
  const avg = prev.reduce((sum, v) => sum + v, 0) / prev.length
  return avg > 0 && last > avg * 1.8
}

const insight = computed(() => {
  void locale.value
  const overdueCount = urgentTasks.value.reduce((sum, task) => sum + toNum(task.count), 0)
  if (overdueCount > 0) {
    return {
      tone: 'warning' as const,
      text: t('dashboard.aiInsightOverdue', { n: overdueCount }),
      actionPath: urgentTasks.value[0]?.path,
    }
  }
  if (detectMessageSpike()) {
    return {
      tone: 'info' as const,
      text: t('dashboard.aiInsightMsgSpike'),
      actionPath: canViewStatistics.value ? '/admin/statistics' : undefined,
    }
  }
  const risk24h = toNum(realtime.value?.riskEvents24h)
  if (risk24h > 0) {
    return {
      tone: 'warning' as const,
      text: t('dashboard.aiInsightRisk', { n: risk24h }),
      actionPath: auth.hasPermission('admin:risk-event:list') ? '/admin/risk-events' : undefined,
    }
  }
  return {
    tone: 'success' as const,
    text: t('dashboard.aiInsightStable'),
    actionPath: undefined,
  }
})

const activeItems = computed<NamedValue[]>(() => {
  void locale.value
  const s = summary.value
  return [
    { key: 'dau', name: metricName('dau'), value: toNum(s?.dau) },
    { key: 'wau', name: metricName('wau'), value: toNum(s?.wau) },
    { key: 'mau', name: metricName('mau'), value: toNum(s?.mau) },
    { key: 'onlineDevices', name: metricName('onlineDevices'), value: toNum(s?.onlineDevices) },
  ]
})

const riskSnapshot = computed(() => {
  void locale.value
  const s = summary.value
  const items = [
    {
      key: 'todaySensitiveHits',
      label: metricName('todaySensitiveHits'),
      value: toNum(s?.todaySensitiveHits),
      perm: 'admin:risk-event:list',
    },
    {
      key: 'todayRiskBlocks',
      label: metricName('todayRiskBlocks'),
      value: toNum(s?.todayRiskBlocks),
      perm: 'admin:risk-event:list',
    },
    {
      key: 'pendingReviews',
      label: metricName('pendingReviews'),
      value: toNum(s?.pendingReviews),
      perm: 'admin:review:list',
    },
    {
      key: 'pendingFeedback',
      label: metricName('pendingFeedback'),
      value: toNum(s?.pendingFeedback),
      perm: 'admin:feedback:list',
    },
  ]
  return items.filter((item) => auth.hasPermission(item.perm))
})

const quickLinks = computed(() => {
  void locale.value
  const items = [
    { path: '/admin/users', label: t('route.users'), perm: 'admin:user:list', icon: PeopleOutline },
    {
      path: '/admin/feedback',
      label: t('route.feedback'),
      perm: 'admin:feedback:list',
      icon: ChatbubblesOutline,
    },
    { path: '/admin/reports', label: t('route.reports'), perm: 'admin:review:list', icon: FlagOutline },
    {
      path: '/admin/risk-events',
      label: t('route.riskEvents'),
      perm: 'admin:risk-event:list',
      icon: WarningOutline,
    },
    {
      path: '/admin/statistics',
      label: t('route.statistics'),
      perm: 'admin:statistics:view',
      icon: StatsChartOutline,
    },
    {
      path: '/admin/system-monitor/cache',
      label: t('route.systemMonitor'),
      perm: 'admin:system-monitor:view',
      icon: PulseOutline,
    },
    {
      path: '/admin/settings',
      label: t('route.settings'),
      perm: 'admin:setting:view',
      icon: SettingsOutline,
    },
  ]
  return items.filter((item) => auth.hasPermission(item.perm))
})

const quickLinksTop = computed(() => quickLinks.value.slice(0, 4))

const canViewStatistics = computed(() => auth.hasPermission('admin:statistics:view'))
const canViewNoticeInbox = computed(() => auth.hasPermission('admin:notice:inbox'))
const canViewMonitor = computed(() => auth.hasPermission('admin:system-monitor:view'))

const healthMetrics = computed(() => {
  void locale.value
  const s = serviceInfo.value
  if (!s) return []
  return [
    { key: 'cpu', label: t('monitor.cpuLoad'), value: toNum(s.systemCpuLoadPercent) },
    { key: 'processCpu', label: t('monitor.processCpuLoad'), value: toNum(s.processCpuLoadPercent) },
    { key: 'heap', label: t('monitor.jvmHeap'), value: toNum(s.jvmHeapUsagePercent) },
    { key: 'memory', label: t('monitor.systemMemory'), value: toNum(s.systemMemoryUsagePercent) },
    { key: 'disk', label: t('monitor.diskUsage'), value: toNum(s.diskUsagePercent) },
  ]
})

const healthWorstPct = computed(() => {
  const values = healthMetrics.value.map((m) => m.value)
  return values.length ? Math.max(...values) : 0
})

const healthStatusType = computed(() => {
  const pct = healthWorstPct.value
  if (pct >= 90) return 'error'
  if (pct >= 70) return 'warning'
  return 'success'
})

const healthStatusLabel = computed(() => {
  void locale.value
  const pct = healthWorstPct.value
  if (pct >= 90) return t('dashboard.healthStatusCritical')
  if (pct >= 70) return t('dashboard.healthStatusWarn')
  return t('dashboard.healthStatusGood')
})

const noticeColumns = computed<DataTableColumns<NoticeItem>>(() => {
  void locale.value
  return [
    {
      title: t('dashboard.colNoticeTitle'),
      key: 'title',
      ellipsis: { tooltip: true },
    },
    {
      title: t('dashboard.colNoticeTime'),
      key: 'publishedAt',
      width: 168,
      render: (row) => formatNoticeTime(row),
    },
  ]
})

function pickTrendSeries(key: string): TrendData | null {
  const data = trends.value
  if (!data?.labels?.length) return null
  const series = data.series?.find((s) => s.key === key)
  if (!series) return null
  return { labels: data.labels, series: [series] }
}

const todayTrend = computed((): TrendData => {
  void locale.value
  const r = realtime.value
  const s = summary.value
  return {
    labels: [
      t('statistics.todayNewUsers'),
      t('statistics.todayMessages'),
      t('statistics.todayLogins'),
      t('dashboard.onlineDevices'),
    ],
    series: [
      {
        key: 'today',
        name: t('dashboard.realtimeTitle'),
        data: [
          toNum(r?.todayNewUsers),
          toNum(r?.todayMessages),
          toNum(r?.todayLogins),
          toNum(s?.onlineDevices),
        ],
      },
    ],
  }
})

const pendingChartItems = computed<NamedValue[]>(() => {
  void locale.value
  return pending.value
    .filter((task) => toNum(task.count) > 0)
    .map((task) => ({
      key: task.type,
      name: taskTitle(task),
      value: toNum(task.count),
    }))
})

const riskChartItems = computed<NamedValue[]>(() => {
  void locale.value
  return riskSnapshot.value.map((item) => ({
    key: item.key,
    name: item.label,
    value: item.value,
  }))
})

const trendOption = computed(() => buildAreaOption(trends.value, seriesName))
const todayOption = computed(() => buildColumnOption(todayTrend.value, seriesName))
const activeOption = computed(() => buildHBarOption(activeItems.value))
const pendingOption = computed(() =>
  buildDonutOption(pendingChartItems.value, undefined, t('dashboard.chartPendingTitle'))
)
const riskOption = computed(() =>
  buildDonutOption(riskChartItems.value, undefined, t('dashboard.chartRiskTitle'))
)
const messagesOption = computed(() =>
  buildColumnOption(pickTrendSeries('messages'), seriesName)
)
const loginsOption = computed(() => buildColumnOption(pickTrendSeries('logins'), seriesName))

const trendChart = useChart(trendEl, trendOption)
const todayChart = useChart(todayEl, todayOption)
const activeChart = useChart(activeEl, activeOption)
const pendingChart = useChart(pendingEl, pendingOption)
const riskChart = useChart(riskEl, riskOption)
const messagesChart = useChart(messagesEl, messagesOption)
const loginsChart = useChart(loginsEl, loginsOption)

function refreshCharts() {
  trendChart.refresh()
  todayChart.refresh()
  activeChart.refresh()
  pendingChart.refresh()
  riskChart.refresh()
  messagesChart.refresh()
  loginsChart.refresh()
}

async function loadTrends() {
  trendLoading.value = true
  try {
    trends.value = await fetchDashboardTrends(days.value)
    await nextTick()
    setTimeout(() => refreshCharts(), 120)
  } finally {
    trendLoading.value = false
  }
}

async function loadMonitorSnapshot() {
  if (!canViewMonitor.value) {
    serviceInfo.value = null
    return
  }
  try {
    serviceInfo.value = await fetchMonitorService()
  } catch {
    if (!serviceInfo.value) serviceInfo.value = null
  }
}

async function loadAuxiliaryData() {
  try {
    if (auth.hasPermission('admin:notice:inbox')) {
      const inbox = await listNoticeInbox({ page: 1, size: 8 })
      notices.value = inbox?.items || []
    } else {
      notices.value = []
    }
  } catch {
    notices.value = []
  }

  await loadMonitorSnapshot()
}

async function loadData(opts: { silent?: boolean; includeTrends?: boolean } = {}) {
  const { silent = false, includeTrends = true } = opts
  if (!silent) loading.value = true
  try {
    const [s, r, p] = await Promise.all([
      fetchDashboardSummary(),
      fetchDashboardRealtime(),
      fetchPendingTasks(),
    ])
    summary.value = s
    realtime.value = r
    pending.value = (p || []).map((task) => ({ ...task, count: toNum(task.count) }))

    if (includeTrends) {
      trends.value = await fetchDashboardTrends(days.value)
    }

    if (!silent) {
      await loadAuxiliaryData()
    } else {
      await loadMonitorSnapshot()
    }

    lastUpdatedAt.value = new Date()
    await nextTick()
    if (includeTrends) {
      setTimeout(() => refreshCharts(), 120)
    } else {
      todayChart.refresh()
      activeChart.refresh()
      pendingChart.refresh()
      riskChart.refresh()
    }
  } finally {
    if (!silent) loading.value = false
  }
}

function go(path: string) {
  if (path && path !== router.currentRoute.value.path) router.push(path)
}

function toggleTrends() {
  trendsExpanded.value = !trendsExpanded.value
  if (trendsExpanded.value) {
    nextTick(() => {
      setTimeout(() => refreshCharts(), 280)
    })
  }
}

watch(trendsExpanded, (open) => {
  if (open) {
    nextTick(() => {
      setTimeout(() => refreshCharts(), 280)
    })
  }
})

function onNoticeRowClick(row: NoticeItem) {
  if (canViewNoticeInbox.value) go('/admin/notice-inbox')
  void row
}

watch(days, () => void loadTrends())

watch(pendingChartItems, () => {
  nextTick(() => pendingChart.refresh())
})

function stopPolling() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

function startPolling() {
  stopPolling()
  pollTimer.value = setInterval(() => {
    if (document.visibilityState !== 'visible') return
    void loadData({ silent: true, includeTrends: false })
  }, POLL_MS)
}

onMounted(() => {
  void loadData()
  startPolling()
  nextTick(() => {
    setTimeout(() => refreshCharts(), 320)
  })
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="dashboard-oa">
    <div class="oa-toolbar">
      <div>
        <h2 class="oa-title">{{ t('dashboard.welcome', { name: auth.displayName }) }}</h2>
        <p class="oa-subtitle">
          {{ t('dashboard.subtitle') }}
          <span v-if="lastUpdatedText" class="oa-updated">· {{ lastUpdatedText }}</span>
          <span class="oa-updated">· {{ t('dashboard.autoRefreshHint') }}</span>
        </p>
      </div>
      <div class="oa-actions">
        <NButton quaternary :loading="loading" @click="() => loadData()">
          <template #icon>
            <NIcon :component="RefreshOutline" />
          </template>
          {{ t('common.refresh') }}
        </NButton>
        <NButton v-if="canViewStatistics" type="primary" @click="go('/admin/statistics')">
          {{ t('dashboard.viewStatistics') }}
        </NButton>
      </div>
    </div>

    <NSpin :show="loading">
      <AdminOpsBannerCarousel class="home-ops-banner" position="home" :height="148" :radius="4" />

      <div class="insight-bar lx-surface-card" :class="`insight-bar--${insight.tone}`">
        <div class="insight-main">
          <span class="insight-title">{{ t('dashboard.aiInsightTitle') }}</span>
          <span class="insight-text">{{ insight.text }}</span>
        </div>
        <NButton
          v-if="insight.actionPath"
          size="small"
          quaternary
          type="primary"
          @click="go(insight.actionPath)"
        >
          {{ t('dashboard.aiInsightAction') }}
        </NButton>
      </div>

      <div class="stat-strip lx-surface-card">
        <template v-for="stat in statStrip" :key="stat.key">
          <button
            v-if="stat.path"
            type="button"
            class="stat-cell stat-cell--clickable"
            @click="go(stat.path)"
          >
            <div class="stat-label">{{ stat.label }}</div>
            <div class="stat-num">{{ fmt(stat.value) }}</div>
          </button>
          <div v-else class="stat-cell">
            <div class="stat-label">{{ stat.label }}</div>
            <div class="stat-num">{{ fmt(stat.value) }}</div>
          </div>
        </template>
      </div>

      <div class="oa-body">
        <div class="oa-main">
          <div class="oa-panel lx-surface-card">
            <div class="oa-panel-head oa-panel-head-row">
              <span class="oa-panel-title">{{ t('dashboard.pendingTitle') }}</span>
              <span v-if="pendingTotal > 0" class="panel-hint">{{ t('dashboard.pendingHint', { count: pendingTotal }) }}</span>
            </div>
            <div v-if="normalPending.length" class="todo-grid" :style="todoGridStyle">
              <button
                v-for="task in normalPending"
                :key="task.type"
                type="button"
                class="todo-cell"
                @click="go(task.path)"
              >
                <span class="todo-label">{{ taskTitle(task) }}</span>
                <span class="todo-count">{{ fmt(toNum(task.count)) }}</span>
              </button>
            </div>
            <div v-else-if="!urgentTasks.length" class="empty-block">{{ t('dashboard.noPending') }}</div>
          </div>

          <div v-if="urgentTasks.length" class="oa-panel urgent-panel lx-surface-card">
            <div class="oa-panel-head">
              <span class="oa-panel-title urgent-title">{{ t('dashboard.urgentTitle') }}</span>
            </div>
            <div class="urgent-row" :style="urgentGridStyle">
              <button
                v-for="task in urgentTasks"
                :key="task.type"
                type="button"
                class="urgent-cell"
                @click="go(task.path)"
              >
                <span class="urgent-label">{{ taskTitle(task) }}</span>
                <strong class="urgent-num">{{ fmt(toNum(task.count)) }}</strong>
              </button>
            </div>
          </div>

          <div v-if="canViewNoticeInbox" class="oa-panel lx-surface-card">
            <div class="oa-panel-head oa-panel-head-row">
              <span class="oa-panel-title">{{ t('dashboard.noticeListTitle') }}</span>
              <NButton
                quaternary
                type="primary"
                size="small"
                class="notice-view-all"
                @click="go('/admin/notice-inbox')"
              >
                {{ t('dashboard.viewAllNotices') }}
              </NButton>
            </div>
            <NDataTable
              v-if="notices.length"
              :columns="noticeColumns"
              :data="notices"
              size="small"
              :bordered="false"
              :single-line="true"
              :row-props="(row) => ({ style: 'cursor: pointer', onClick: () => onNoticeRowClick(row) })"
              class="notice-table"
            />
            <div v-else class="notice-empty">{{ t('dashboard.noNotices') }}</div>
          </div>

          <div class="trends-fold lx-surface-card">
            <button type="button" class="trends-fold-head" @click="toggleTrends">
              <span class="oa-panel-title">{{ trendsTitleText }}</span>
              <span class="trends-fold-hint">{{ t('dashboard.trendsDesc') }}</span>
              <NIcon
                class="trends-fold-icon"
                :component="trendsExpanded ? ChevronUpOutline : ChevronDownOutline"
              />
            </button>
            <NCollapseTransition :show="trendsExpanded">
              <div class="trends-fold-body">
                <div class="trends-toolbar">
                  <NSelect
                    v-model:value="days"
                    :options="daysOptions"
                    size="small"
                    :consistent-menu-width="false"
                    style="width: 112px"
                  />
                </div>
                <NSpin :show="trendLoading">
                  <div class="trends-charts">
                    <div class="chart-card chart-card--wide">
                      <div class="chart-card-title">{{ t('dashboard.chartGrowthTitle') }}</div>
                      <div v-if="trends?.labels?.length" ref="trendEl" class="chart-box" />
                      <div v-else class="chart-empty">{{ t('common.none') }}</div>
                    </div>
                    <div class="chart-card">
                      <div class="chart-card-title">{{ t('dashboard.chartTodayTitle') }}</div>
                      <div ref="todayEl" class="chart-box chart-box--compact" />
                    </div>
                    <div class="chart-card">
                      <div class="chart-card-title">{{ t('dashboard.activeUsersTitle') }}</div>
                      <div ref="activeEl" class="chart-box chart-box--compact" />
                    </div>
                    <div class="chart-card">
                      <div class="chart-card-title">{{ t('dashboard.chartPendingTitle') }}</div>
                      <div
                        v-if="pendingChartItems.length"
                        ref="pendingEl"
                        class="chart-box chart-box--compact"
                      />
                      <div v-else class="chart-empty">{{ t('dashboard.noPending') }}</div>
                    </div>
                    <div class="chart-card">
                      <div class="chart-card-title">{{ t('dashboard.chartRiskTitle') }}</div>
                      <div ref="riskEl" class="chart-box chart-box--compact" />
                    </div>
                    <div class="chart-card">
                      <div class="chart-card-title">{{ t('dashboard.chartMessagesTitle') }}</div>
                      <div ref="messagesEl" class="chart-box chart-box--compact" />
                    </div>
                    <div class="chart-card">
                      <div class="chart-card-title">{{ t('dashboard.chartLoginsTitle') }}</div>
                      <div ref="loginsEl" class="chart-box chart-box--compact" />
                    </div>
                  </div>
                </NSpin>
              </div>
            </NCollapseTransition>
          </div>
        </div>

        <aside class="oa-side">
          <div v-if="quickLinksTop.length" class="side-quick-row lx-surface-card">
            <button
              v-for="link in quickLinksTop"
              :key="link.path"
              type="button"
              class="side-quick-btn"
              @click="go(link.path)"
            >
              <span class="side-quick-icon">
                <NIcon :size="20" :component="link.icon" />
              </span>
              <span class="side-quick-label">{{ link.label }}</span>
            </button>
          </div>

          <div v-if="quickLinks.length" class="oa-panel lx-surface-card">
            <div class="oa-panel-head">
              <span class="oa-panel-title">{{ t('dashboard.moduleShortcuts') }}</span>
            </div>
            <div class="module-grid">
              <button
                v-for="(link, i) in quickLinks"
                :key="link.path"
                type="button"
                class="module-cell"
                :style="{ '--module-bg': MODULE_COLORS[i % MODULE_COLORS.length] }"
                @click="go(link.path)"
              >
                <span class="module-icon">
                  <NIcon :size="22" :component="link.icon" />
                </span>
                <span class="module-label">{{ link.label }}</span>
              </button>
            </div>
          </div>

          <div class="oa-panel lx-surface-card">
            <div class="oa-panel-head">
              <span class="oa-panel-title">{{ t('dashboard.opsRiskTitle') }}</span>
            </div>
            <div class="risk-grid">
              <div v-for="item in riskSnapshot" :key="item.key" class="risk-cell">
                <span class="risk-label">{{ item.label }}</span>
                <strong class="risk-num">{{ fmt(item.value) }}</strong>
              </div>
            </div>
          </div>

          <div class="oa-panel lx-surface-card sys-panel">
            <div class="oa-panel-head">
              <span class="oa-panel-title">{{ t('dashboard.systemInfo') }}</span>
            </div>
            <div v-if="serviceInfo" class="sys-info">
              <div class="sys-row">
                <span>{{ t('dashboard.sysOs') }}</span>
                <strong>{{ serviceInfo.osName || '-' }}</strong>
              </div>
              <div class="sys-row">
                <span>{{ t('dashboard.sysJava') }}</span>
                <strong>{{ serviceInfo.javaVersion || '-' }}</strong>
              </div>
              <div class="sys-row">
                <span>{{ t('dashboard.sysCpu') }}</span>
                <strong>{{ formatPct(serviceInfo.systemCpuLoadPercent) }}</strong>
              </div>
              <div class="sys-row">
                <span>{{ t('dashboard.sysHeap') }}</span>
                <strong>{{ formatPct(serviceInfo.jvmHeapUsagePercent) }}</strong>
              </div>
            </div>
            <div v-else class="sys-info-basic">
              <div class="sys-row">
                <span>{{ t('dashboard.sysUser') }}</span>
                <strong>{{ auth.displayName }}</strong>
              </div>
              <p class="sys-hint">{{ t('dashboard.systemInfoBasic') }}</p>
            </div>
          </div>

          <div v-if="canViewMonitor" class="oa-panel lx-surface-card health-panel">
            <div class="oa-panel-head oa-panel-head-row">
              <span class="oa-panel-title">{{ t('dashboard.healthInfo') }}</span>
              <NTag v-if="serviceInfo" :type="healthStatusType" size="small" :bordered="false">
                {{ healthStatusLabel }}
              </NTag>
            </div>
            <div v-if="serviceInfo" class="health-body">
              <p class="health-hint">{{ t('dashboard.healthHint') }}</p>
              <div v-for="metric in healthMetrics" :key="metric.key" class="health-metric">
                <div class="health-metric-head">
                  <span class="health-metric-label">{{ metric.label }}</span>
                  <strong class="health-metric-value">{{ formatPct(metric.value) }}</strong>
                </div>
                <NProgress
                  :percentage="clampPct(metric.value)"
                  :height="8"
                  :status="usageStatus(metric.value)"
                  :show-indicator="false"
                  :border-radius="4"
                />
              </div>
              <div class="health-foot">
                <NButton
                  quaternary
                  size="tiny"
                  type="primary"
                  @click="go('/admin/system-monitor/service')"
                >
                  {{ t('dashboard.healthViewMonitor') }}
                </NButton>
              </div>
            </div>
            <div v-else class="sys-info-basic">
              <p class="sys-hint">{{ t('systemMonitor.loading') }}</p>
            </div>
          </div>
        </aside>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.dashboard-oa {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.home-ops-banner {
  margin-bottom: 0;
}

.insight-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
}

.insight-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.insight-title {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--lx-text-2);
}

.insight-text {
  font-size: 13px;
  color: var(--lx-text);
  line-height: 1.5;
}

.insight-bar--warning {
  border-color: #ffd591;
  background: #fffbe6;
}

.insight-bar--info {
  border-color: var(--lx-accent-soft-border);
  background: var(--lx-accent-soft-bg);
}

.insight-bar--success {
  border-color: #b7eb8f;
  background: #f6ffed;
}

[data-theme='dark'] .insight-bar--warning {
  border-color: rgba(250, 173, 20, 0.35);
  background: rgba(250, 173, 20, 0.1);
}

[data-theme='dark'] .insight-bar--info {
  border-color: rgba(24, 144, 255, 0.35);
  background: rgba(24, 144, 255, 0.1);
}

[data-theme='dark'] .insight-bar--success {
  border-color: rgba(82, 196, 26, 0.35);
  background: rgba(82, 196, 26, 0.1);
}

.panel-hint {
  font-size: 12px;
  color: var(--lx-text-3);
}

.oa-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.oa-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.oa-title {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  color: var(--lx-text);
  line-height: 1.3;
}

.oa-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--lx-text-3);
  line-height: 1.5;
}

.oa-updated {
  color: var(--lx-text-3);
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(9, minmax(0, 1fr));
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.stat-cell {
  display: block;
  width: 100%;
  padding: 14px 10px;
  text-align: center;
  border: none;
  border-right: 1px solid var(--lx-border);
  background: var(--lx-card);
  font: inherit;
  color: inherit;
}

.stat-cell--clickable {
  cursor: pointer;
  transition: background 0.15s ease;
}

.stat-cell--clickable:hover {
  background: color-mix(in srgb, var(--lx-oa-blue) 8%, var(--lx-card));
}

.stat-cell:nth-child(9n) {
  border-right: none;
}

.stat-label {
  font-size: 12px;
  color: var(--lx-text-3);
  line-height: 1.4;
}

.stat-num {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 650;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  color: var(--lx-oa-blue);
}

[data-theme='dark'] .stat-num {
  color: var(--lx-accent);
}

.oa-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 12px;
  align-items: start;
  margin-top: 12px;
}

.oa-main,
.oa-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.oa-panel {
  padding: 0;
  overflow: hidden;
}

.oa-panel-head {
  padding: 12px 20px;
  border-bottom: 1px solid var(--lx-border);
  background: color-mix(in srgb, var(--lx-body) 55%, var(--lx-card));
}

.oa-panel-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.notice-view-all {
  flex-shrink: 0;
  font-weight: 500 !important;
  letter-spacing: 0 !important;
  transform: none !important;
  filter: none !important;
}

.oa-panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text);
}

.oa-panel-title::before {
  content: '';
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--lx-oa-blue);
  flex-shrink: 0;
}

[data-theme='dark'] .oa-panel-title::before {
  background: var(--lx-accent);
}

.urgent-title::before {
  background: #cf1322;
}

.todo-grid {
  display: grid;
  gap: 0;
}

.todo-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 88px;
  padding: 12px 10px;
  border: none;
  border-right: 1px solid var(--lx-border);
  background: var(--lx-card);
  cursor: pointer;
  transition: background 0.15s ease;
}

.todo-cell:last-child {
  border-right: none;
}

.todo-cell:hover {
  background: color-mix(in srgb, var(--lx-oa-blue) 8%, var(--lx-card));
}

.todo-cell.urgent .todo-count {
  color: #cf1322;
}

.todo-label {
  font-size: 12px;
  color: var(--lx-text-2);
  text-align: center;
  line-height: 1.4;
}

.todo-count {
  font-size: 20px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: var(--lx-oa-blue);
}

[data-theme='dark'] .todo-count {
  color: var(--lx-accent);
}

.urgent-panel .oa-panel-head {
  background: rgba(255, 77, 79, 0.08);
  border-bottom-color: rgba(255, 77, 79, 0.22);
}

.urgent-row {
  display: grid;
  gap: 0;
  background: rgba(255, 77, 79, 0.05);
}

.urgent-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 10px;
  border: none;
  border-right: 1px solid rgba(255, 77, 79, 0.15);
  background: transparent;
  cursor: pointer;
}

.urgent-cell:last-child {
  border-right: none;
}

.urgent-cell:hover {
  background: rgba(255, 77, 79, 0.1);
}

.urgent-label {
  font-size: 12px;
  color: #a8071a;
}

.urgent-num {
  font-size: 22px;
  font-weight: 700;
  color: #cf1322;
  font-variant-numeric: tabular-nums;
}

.notice-table :deep(.n-data-table-th) {
  background: color-mix(in srgb, var(--lx-body) 55%, var(--lx-card));
  font-size: 12px;
}

.notice-table :deep(.n-data-table-td) {
  font-size: 13px;
}

.notice-table :deep(.n-data-table-wrapper) {
  min-height: 0;
}

.notice-table :deep(.n-data-table-base-table) {
  min-height: auto;
}

.notice-table :deep(.n-data-table-tr:not(.n-data-table-tr--summary)) {
  height: auto;
}

.notice-empty {
  padding: 24px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--lx-text-3);
}

.side-quick-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.side-quick-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  border: none;
  border-right: 1px solid var(--lx-border);
  background: var(--lx-card);
  cursor: pointer;
  transition: background 0.15s ease;
}

.side-quick-btn:last-child {
  border-right: none;
}

.side-quick-btn:hover {
  background: color-mix(in srgb, var(--lx-oa-blue) 8%, var(--lx-card));
}

.side-quick-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: var(--lx-oa-blue);
  background: rgba(24, 144, 255, 0.1);
}

.side-quick-label {
  font-size: 11px;
  color: var(--lx-text-2);
  text-align: center;
  line-height: 1.35;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 12px;
}

.module-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 8px;
  border: none;
  border-radius: 8px;
  background: var(--lx-card);
  cursor: pointer;
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.module-cell:hover {
  transform: translateY(-2px);
  opacity: 0.92;
}

.module-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  background: var(--module-bg);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--module-bg) 35%, transparent);
}

.module-label {
  font-size: 12px;
  color: var(--lx-text);
  text-align: center;
  line-height: 1.35;
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
}

.risk-cell {
  padding: 14px 20px;
  border-right: 1px solid var(--lx-border);
  border-bottom: 1px solid var(--lx-border);
}

.risk-cell:nth-child(2n) {
  border-right: none;
}

.risk-cell:nth-last-child(-n + 2) {
  border-bottom: none;
}

.risk-label {
  display: block;
  font-size: 11px;
  color: var(--lx-text-3);
  margin-bottom: 4px;
}

.risk-num {
  font-size: 18px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: var(--lx-oa-blue);
}

[data-theme='dark'] .risk-num {
  color: var(--lx-accent);
}

.sys-panel .oa-panel-head {
  background: color-mix(in srgb, var(--lx-body) 55%, var(--lx-card));
}

.sys-info,
.sys-info-basic {
  padding: 14px 20px;
}

.sys-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: var(--lx-text-2);
  padding: 6px 0;
  border-bottom: 1px dashed var(--lx-border);
}

.sys-row:last-child {
  border-bottom: none;
}

.sys-row strong {
  color: var(--lx-text);
  font-weight: 600;
}

.sys-hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--lx-text-3);
  line-height: 1.45;
}

.health-body {
  padding: 12px 20px 14px;
}

.health-hint {
  margin: 0 0 12px;
  font-size: 11px;
  color: var(--lx-text-3);
  line-height: 1.45;
}

.health-metric + .health-metric {
  margin-top: 12px;
}

.health-metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.health-metric-label {
  font-size: 12px;
  color: var(--lx-text-2);
}

.health-metric-value {
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--lx-text);
}

.health-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed var(--lx-border);
}

.empty-block {
  padding: 28px 16px;
  text-align: center;
  color: var(--lx-text-3);
  font-size: 13px;
}

.trends-fold {
  overflow: hidden;
}

.trends-fold-head {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 20px;
  border: none;
  background: color-mix(in srgb, var(--lx-body) 55%, var(--lx-card));
  cursor: pointer;
  text-align: left;
}

.trends-fold-hint {
  flex: 1;
  font-size: 12px;
  color: var(--lx-text-3);
}

.trends-fold-icon {
  color: var(--lx-text-3);
  flex-shrink: 0;
}

.trends-fold-body {
  padding: 14px 20px 16px;
  border-top: 1px solid var(--lx-border);
}

.trends-toolbar {
  margin-bottom: 8px;
}

.trends-charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

@media (min-width: 1360px) {
  .trends-charts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.chart-card {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  background: color-mix(in srgb, var(--lx-body) 28%, var(--lx-card));
}

.chart-card--wide {
  grid-column: 1 / -1;
}

.chart-card-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--lx-text-3);
}

.chart-empty {
  display: grid;
  place-items: center;
  min-height: 200px;
  font-size: 13px;
  color: var(--lx-text-3);
}

.chart-box {
  width: 100%;
  height: 260px;
}

.chart-box--compact {
  height: 220px;
}

@media (max-width: 1400px) {
  .stat-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .stat-cell {
    border-bottom: 1px solid var(--lx-border);
  }

  .stat-cell:nth-child(9n) {
    border-right: 1px solid var(--lx-border);
  }

  .stat-cell:nth-child(3n) {
    border-right: none;
  }

  .stat-cell:nth-last-child(-n + 3) {
    border-bottom: none;
  }
}

@media (max-width: 1280px) {
  .oa-body {
    grid-template-columns: 1fr;
  }

  .trends-charts {
    grid-template-columns: 1fr;
  }

  .chart-card--wide {
    grid-column: auto;
  }
}

@media (max-width: 768px) {
  .stat-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-cell:nth-child(3n) {
    border-right: 1px solid var(--lx-border);
  }

  .stat-cell:nth-child(2n) {
    border-right: none;
  }

  .stat-cell:nth-last-child(-n + 3) {
    border-bottom: 1px solid var(--lx-border);
  }

  .stat-cell:nth-last-child(-n + 2) {
    border-bottom: none;
  }

  .todo-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  }

  .todo-cell:nth-child(2n) {
    border-right: none;
  }

  .todo-cell:nth-child(2n-1) {
    border-right: 1px solid var(--lx-border);
  }

  .side-quick-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .side-quick-btn:nth-child(2n) {
    border-right: none;
  }

  .oa-toolbar {
    flex-direction: column;
  }

  .oa-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
