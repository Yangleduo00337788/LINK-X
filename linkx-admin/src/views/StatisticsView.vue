<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NSelect,
  NSpin,
  NTabPane,
  NTabs,
  type DataTableColumns,
} from 'naive-ui'
import {
  exportStatistics,
  fetchActivityHeatmap,
  fetchStatisticContent,
  fetchStatisticFeedback,
  fetchStatisticGroups,
  fetchStatisticOverview,
  fetchStatisticRisk,
  fetchStatisticUsers,
  type ActivityHeatmap,
  type GroupActivityItem,
  type HeatmapMetric,
  type StatisticContent,
  type StatisticFeedback,
  type StatisticGroups,
  type StatisticOverview,
  type StatisticRisk,
  type StatisticUsers,
  type TrendData,
} from '@/api/statistics'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import {
  LX_CHART_COLORS,
  buildAreaOption,
  buildColumnOption,
  buildDonutOption,
  buildHBarOption,
  buildHeatmapOption,
  buildSparkOption,
  seriesValues,
  useChart,
  useDaysOptions,
  type NamedValue,
} from '@/utils/charts'

const { t, locale } = useI18n()
const auth = useAuthStore()
const loading = ref(false)
const exporting = ref(false)
const days = ref(14)
const tab = ref('overview')
const heatmapMetric = ref<HeatmapMetric>('logins')

const overview = ref<StatisticOverview | null>(null)
const users = ref<StatisticUsers | null>(null)
const content = ref<StatisticContent | null>(null)
const risk = ref<StatisticRisk | null>(null)
const feedback = ref<StatisticFeedback | null>(null)
const groups = ref<StatisticGroups | null>(null)
const heatmap = ref<ActivityHeatmap | null>(null)

const daysOptions = useDaysOptions((k) => t(k))
const heatmapMetricOptions = computed(() => {
  void locale.value
  return [
    { label: t('statistics.metricLogins'), value: 'logins' },
    { label: t('statistics.metricMessages'), value: 'messages' },
  ]
})
const weekdayLabels = computed(() => {
  void locale.value
  return [
    t('statistics.weekdayMon'),
    t('statistics.weekdayTue'),
    t('statistics.weekdayWed'),
    t('statistics.weekdayThu'),
    t('statistics.weekdayFri'),
    t('statistics.weekdaySat'),
    t('statistics.weekdaySun'),
  ]
})
const hourLabels = computed(() => Array.from({ length: 24 }, (_, i) => String(i)))

function seriesName(key: string, fallback: string) {
  void locale.value
  const map: Record<string, string> = {
    newUsers: t('statistics.seriesNewUsers'),
    loginSuccess: t('statistics.seriesLoginSuccess'),
    loginFail: t('statistics.seriesLoginFail'),
    messages: t('statistics.seriesMessages'),
    moments: t('statistics.seriesMoments'),
    uploads: t('statistics.seriesUploads'),
    sensitive: t('statistics.seriesSensitive'),
    storm: t('statistics.seriesStorm'),
    loginLock: t('statistics.seriesLoginLock'),
    rateLimit: t('statistics.seriesRateLimit'),
    reviews: t('statistics.seriesReviews'),
    reviewCreated: t('statistics.seriesReviewCreated'),
    reviewResolved: t('statistics.seriesReviewResolved'),
    newGroups: t('statistics.seriesNewGroups'),
    groupMessages: t('statistics.seriesGroupMessages'),
    created: t('statistics.seriesCreated'),
    replied: t('statistics.seriesReplied'),
    logins: t('statistics.seriesLogins'),
  }
  return map[key] || fallback
}

function breakdownName(key: string, fallback: string) {
  void locale.value
  const map: Record<string, string> = {
    normal: t('common.normal'),
    frozen: t('common.frozen'),
    pending: t('statistics.statusPending'),
    approved: t('statistics.statusApproved'),
    rejected: t('statistics.statusRejected'),
    replied: t('statistics.statusReplied'),
    closed: t('statistics.statusClosed'),
  }
  return map[key] || fallback
}

function fmt(n: number) {
  return new Intl.NumberFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US').format(n || 0)
}

type KpiCard = {
  key: string
  label: string
  value: number
  color: string
  spark: number[]
}

const overviewKpis = computed<KpiCard[]>(() => {
  void locale.value
  const o = overview.value
  const uTrend = users.value?.trend
  const cTrend = content.value?.trend
  return [
    {
      key: 'totalUsers',
      label: t('dashboard.totalUsers'),
      value: o?.totalUsers ?? 0,
      color: LX_CHART_COLORS[0],
      spark: seriesValues(uTrend, 'newUsers'),
    },
    {
      key: 'activeUsers',
      label: t('dashboard.activeUsers'),
      value: o?.activeUsers ?? 0,
      color: LX_CHART_COLORS[1],
      spark: seriesValues(uTrend, 'loginSuccess'),
    },
    {
      key: 'todayMessages',
      label: t('statistics.todayMessages'),
      value: o?.todayMessages ?? 0,
      color: LX_CHART_COLORS[2],
      spark: seriesValues(cTrend, 'messages'),
    },
    {
      key: 'todayLogins',
      label: t('statistics.todayLogins'),
      value: o?.todayLogins ?? 0,
      color: LX_CHART_COLORS[3],
      spark: seriesValues(uTrend, 'loginSuccess'),
    },
  ]
})

const overviewScale = computed<NamedValue[]>(() => {
  void locale.value
  const o = overview.value
  return [
    { key: 'users', name: t('dashboard.totalUsers'), value: o?.totalUsers ?? 0 },
    { key: 'messages', name: t('statistics.totalMessages'), value: o?.totalMessages ?? 0 },
    { key: 'uploads', name: t('statistics.totalUploads'), value: o?.totalUploads ?? 0 },
    { key: 'closed', name: t('statistics.closedFeedback'), value: o?.closedFeedback ?? 0 },
  ]
})

const overviewPending = computed(() => {
  void locale.value
  const o = overview.value
  return [
    {
      key: 'pendingFeedback',
      name: t('dashboard.pendingFeedback'),
      value: o?.pendingFeedback ?? 0,
    },
    { key: 'pendingReviews', name: t('dashboard.pendingReviews'), value: o?.pendingReviews ?? 0 },
    { key: 'riskEvents', name: t('dashboard.riskEvents'), value: o?.riskEvents ?? 0 },
  ]
})

const overviewToday = computed(() => {
  void locale.value
  const o = overview.value
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
          o?.todayNewUsers ?? 0,
          o?.todayMessages ?? 0,
          o?.todayLogins ?? 0,
          o?.onlineDevices ?? 0,
        ],
      },
    ],
  } as TrendData
})

const overviewTrend = computed(() => users.value?.trend ?? null)

const userRangeBars = computed<NamedValue[]>(() => {
  void locale.value
  const u = users.value
  return [
    { key: 'new', name: t('statistics.newUsersInRange'), value: u?.newUsersInRange ?? 0 },
    { key: 'ok', name: t('statistics.loginSuccessInRange'), value: u?.loginSuccessInRange ?? 0 },
    { key: 'fail', name: t('statistics.loginFailInRange'), value: u?.loginFailInRange ?? 0 },
  ]
})

const contentRangeBars = computed<NamedValue[]>(() => {
  void locale.value
  const c = content.value
  return [
    { key: 'msg', name: t('statistics.messagesInRange'), value: c?.messagesInRange ?? 0 },
    { key: 'mom', name: t('statistics.momentsInRange'), value: c?.momentsInRange ?? 0 },
    { key: 'up', name: t('statistics.uploadsInRange'), value: c?.uploadsInRange ?? 0 },
  ]
})

const riskRangeBars = computed<NamedValue[]>(() => {
  void locale.value
  const r = risk.value
  return [
    { key: 'sens', name: t('statistics.sensitiveHits'), value: r?.sensitiveHitsInRange ?? 0 },
    { key: 'storm', name: t('statistics.messageStorms'), value: r?.messageStormsInRange ?? 0 },
    { key: 'loginLock', name: t('statistics.loginLocks'), value: r?.loginLocksInRange ?? 0 },
    { key: 'rateLimit', name: t('statistics.rateLimits'), value: r?.rateLimitsInRange ?? 0 },
    { key: 'pending', name: t('dashboard.pendingReviews'), value: r?.pendingReviews ?? 0 },
  ]
})

const reviewEfficiencyBars = computed<NamedValue[]>(() => {
  void locale.value
  const r = risk.value
  return [
    {
      key: 'resolved',
      name: t('statistics.resolvedReviews'),
      value: r?.resolvedReviewsInRange ?? 0,
    },
    { key: 'pending', name: t('dashboard.pendingReviews'), value: r?.pendingReviews ?? 0 },
    { key: 'over24', name: t('statistics.pendingOver24h'), value: r?.pendingOver24h ?? 0 },
    { key: 'over72', name: t('statistics.pendingOver72h'), value: r?.pendingOver72h ?? 0 },
  ]
})

function fmtMinutes(n: number | null | undefined) {
  if (n == null || Number.isNaN(n)) return '-'
  if (n < 60) return `${n}${t('statistics.minutesUnit')}`
  const h = Math.floor(n / 60)
  const m = Math.round(n % 60)
  return m > 0
    ? `${h}${t('statistics.hoursUnit')}${m}${t('statistics.minutesUnit')}`
    : `${h}${t('statistics.hoursUnit')}`
}

const feedbackRangeBars = computed<NamedValue[]>(() => {
  void locale.value
  const f = feedback.value
  return [
    { key: 'created', name: t('statistics.createdInRange'), value: f?.createdInRange ?? 0 },
    { key: 'replied', name: t('statistics.repliedInRange'), value: f?.repliedInRange ?? 0 },
    { key: 'closed', name: t('statistics.closedInRange'), value: f?.closedInRange ?? 0 },
  ]
})

const groupRangeBars = computed<NamedValue[]>(() => {
  void locale.value
  const g = groups.value
  return [
    { key: 'total', name: t('statistics.totalGroups'), value: g?.totalGroups ?? 0 },
    { key: 'active', name: t('statistics.activeGroups'), value: g?.activeGroupsInRange ?? 0 },
    { key: 'new', name: t('statistics.newGroupsInRange'), value: g?.newGroupsInRange ?? 0 },
    { key: 'msg', name: t('statistics.groupMessagesInRange'), value: g?.groupMessagesInRange ?? 0 },
  ]
})

const topGroupBars = computed<NamedValue[]>(() => {
  void locale.value
  return (groups.value?.topGroups || []).slice(0, 8).map((g) => ({
    key: String(g.id),
    name: g.name || t('statistics.unnamedGroup'),
    value: g.messageCount || 0,
  }))
})

const topGroupColumns = computed<DataTableColumns<GroupActivityItem>>(() => {
  void locale.value
  return [
    {
      title: t('statistics.groupName'),
      key: 'name',
      ellipsis: { tooltip: true },
      render: (row) => row.name || t('statistics.unnamedGroup'),
    },
    {
      title: t('statistics.groupMessages'),
      key: 'messageCount',
      width: 100,
      render: (row) => fmt(row.messageCount || 0),
    },
    {
      title: t('statistics.groupMembers'),
      key: 'memberCount',
      width: 90,
      render: (row) => fmt(row.memberCount || 0),
    },
    {
      title: t('statistics.lastMessage'),
      key: 'lastMessageTime',
      width: 160,
      render: (row) => formatTime(row.lastMessageTime),
    },
  ]
})

/* --- chart option refs --- */
const spark0 = computed(() =>
  buildSparkOption(overviewKpis.value[0]?.spark || [], LX_CHART_COLORS[0])
)
const spark1 = computed(() =>
  buildSparkOption(overviewKpis.value[1]?.spark || [], LX_CHART_COLORS[1])
)
const spark2 = computed(() =>
  buildSparkOption(overviewKpis.value[2]?.spark || [], LX_CHART_COLORS[2])
)
const spark3 = computed(() =>
  buildSparkOption(overviewKpis.value[3]?.spark || [], LX_CHART_COLORS[3])
)

const overviewAreaOpt = computed(() => buildAreaOption(overviewTrend.value, seriesName))
const overviewPendingOpt = computed(() =>
  buildDonutOption(
    overviewPending.value,
    (k, f) => breakdownName(k, f) || f,
    t('statistics.chartTotal')
  )
)
const overviewScaleOpt = computed(() => buildHBarOption(overviewScale.value))
const overviewTodayOpt = computed(() => buildColumnOption(overviewToday.value, seriesName))

const userAreaOpt = computed(() => buildAreaOption(users.value?.trend, seriesName))
const userDonutOpt = computed(() =>
  buildDonutOption(users.value?.statusBreakdown, breakdownName, t('statistics.chartUsers'))
)
const userBarOpt = computed(() => buildHBarOption(userRangeBars.value))
const userLoginColOpt = computed(() =>
  buildColumnOption(
    {
      labels: users.value?.trend?.labels || [],
      series: (users.value?.trend?.series || []).filter(
        (s) => s.key === 'loginSuccess' || s.key === 'loginFail'
      ),
    },
    seriesName,
    { stacked: true }
  )
)

const contentAreaOpt = computed(() =>
  buildAreaOption(content.value?.trend, seriesName, { stacked: true })
)
const contentBarOpt = computed(() => buildHBarOption(contentRangeBars.value))
const contentColOpt = computed(() => buildColumnOption(content.value?.trend, seriesName))

const riskAreaOpt = computed(() => buildAreaOption(risk.value?.trend, seriesName))
const riskDonutOpt = computed(() =>
  buildDonutOption(risk.value?.reviewStatusBreakdown, breakdownName, t('statistics.chartReviews'))
)
const riskBarOpt = computed(() => buildHBarOption(riskRangeBars.value))
const riskColOpt = computed(() =>
  buildColumnOption(risk.value?.trend, seriesName, { stacked: true })
)
const reviewEffAreaOpt = computed(() =>
  buildAreaOption(risk.value?.reviewEfficiencyTrend, seriesName)
)
const reviewEffBarOpt = computed(() => buildHBarOption(reviewEfficiencyBars.value))

const feedbackAreaOpt = computed(() => buildAreaOption(feedback.value?.trend, seriesName))
const feedbackDonutOpt = computed(() =>
  buildDonutOption(feedback.value?.statusBreakdown, breakdownName, t('statistics.chartFeedback'))
)
const feedbackBarOpt = computed(() => buildHBarOption(feedbackRangeBars.value))
const feedbackColOpt = computed(() =>
  buildColumnOption(feedback.value?.trend, seriesName, { stacked: true })
)

const groupAreaOpt = computed(() => buildAreaOption(groups.value?.trend, seriesName))
const groupBarOpt = computed(() => buildHBarOption(groupRangeBars.value))
const groupTopOpt = computed(() => buildHBarOption(topGroupBars.value))
const groupColOpt = computed(() => buildColumnOption(groups.value?.trend, seriesName))

/* --- DOM refs + bindings --- */
const sparkEl0 = ref<HTMLElement | null>(null)
const sparkEl1 = ref<HTMLElement | null>(null)
const sparkEl2 = ref<HTMLElement | null>(null)
const sparkEl3 = ref<HTMLElement | null>(null)
const ovAreaEl = ref<HTMLElement | null>(null)
const ovPendingEl = ref<HTMLElement | null>(null)
const ovScaleEl = ref<HTMLElement | null>(null)
const ovTodayEl = ref<HTMLElement | null>(null)

const userAreaEl = ref<HTMLElement | null>(null)
const userDonutEl = ref<HTMLElement | null>(null)
const userBarEl = ref<HTMLElement | null>(null)
const userLoginEl = ref<HTMLElement | null>(null)

const contentAreaEl = ref<HTMLElement | null>(null)
const contentBarEl = ref<HTMLElement | null>(null)
const contentColEl = ref<HTMLElement | null>(null)

const riskAreaEl = ref<HTMLElement | null>(null)
const riskDonutEl = ref<HTMLElement | null>(null)
const riskBarEl = ref<HTMLElement | null>(null)
const riskColEl = ref<HTMLElement | null>(null)
const reviewEffAreaEl = ref<HTMLElement | null>(null)
const reviewEffBarEl = ref<HTMLElement | null>(null)

const feedbackAreaEl = ref<HTMLElement | null>(null)
const feedbackDonutEl = ref<HTMLElement | null>(null)
const feedbackBarEl = ref<HTMLElement | null>(null)
const feedbackColEl = ref<HTMLElement | null>(null)

const groupAreaEl = ref<HTMLElement | null>(null)
const groupBarEl = ref<HTMLElement | null>(null)
const groupTopEl = ref<HTMLElement | null>(null)
const groupColEl = ref<HTMLElement | null>(null)

const overviewCharts = [
  useChart(sparkEl0, spark0),
  useChart(sparkEl1, spark1),
  useChart(sparkEl2, spark2),
  useChart(sparkEl3, spark3),
  useChart(ovAreaEl, overviewAreaOpt),
  useChart(ovPendingEl, overviewPendingOpt),
  useChart(ovScaleEl, overviewScaleOpt),
  useChart(ovTodayEl, overviewTodayOpt),
]
const userCharts = [
  useChart(userAreaEl, userAreaOpt),
  useChart(userDonutEl, userDonutOpt),
  useChart(userBarEl, userBarOpt),
  useChart(userLoginEl, userLoginColOpt),
]
const contentCharts = [
  useChart(contentAreaEl, contentAreaOpt),
  useChart(contentBarEl, contentBarOpt),
  useChart(contentColEl, contentColOpt),
]
const riskCharts = [
  useChart(riskAreaEl, riskAreaOpt),
  useChart(riskDonutEl, riskDonutOpt),
  useChart(riskBarEl, riskBarOpt),
  useChart(riskColEl, riskColOpt),
  useChart(reviewEffAreaEl, reviewEffAreaOpt),
  useChart(reviewEffBarEl, reviewEffBarOpt),
]
const feedbackCharts = [
  useChart(feedbackAreaEl, feedbackAreaOpt),
  useChart(feedbackDonutEl, feedbackDonutOpt),
  useChart(feedbackBarEl, feedbackBarOpt),
  useChart(feedbackColEl, feedbackColOpt),
]
const groupCharts = [
  useChart(groupAreaEl, groupAreaOpt),
  useChart(groupBarEl, groupBarOpt),
  useChart(groupTopEl, groupTopOpt),
  useChart(groupColEl, groupColOpt),
]

const heatmapOpt = computed(() =>
  buildHeatmapOption(heatmap.value, weekdayLabels.value, hourLabels.value)
)
const heatmapEl = ref<HTMLElement | null>(null)
const advancedCharts = [useChart(heatmapEl, heatmapOpt)]

const chartsByTab: Record<string, typeof overviewCharts> = {
  overview: overviewCharts,
  users: userCharts,
  content: contentCharts,
  risk: riskCharts,
  feedback: feedbackCharts,
  groups: groupCharts,
  advanced: advancedCharts,
}

function refreshActiveTab() {
  const group = chartsByTab[tab.value] || []
  group.forEach((c) => c.refresh())
}

watch(tab, () => {
  refreshActiveTab()
})

async function load() {
  loading.value = true
  try {
    const d = days.value
    const [ov, us, ct, rk, fb, gp, hm] = await Promise.all([
      fetchStatisticOverview(d),
      fetchStatisticUsers(d),
      fetchStatisticContent(d),
      fetchStatisticRisk(d),
      fetchStatisticFeedback(d),
      fetchStatisticGroups(d),
      fetchActivityHeatmap(d, heatmapMetric.value),
    ])
    overview.value = ov
    users.value = us
    content.value = ct
    risk.value = rk
    feedback.value = fb
    groups.value = gp
    heatmap.value = hm
  } finally {
    loading.value = false
    refreshActiveTab()
  }
}

watch(days, () => {
  void load()
})

watch(heatmapMetric, () => {
  void load()
})

async function doExport() {
  exporting.value = true
  try {
    await exportStatistics(days.value)
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page stats-page">
    <div class="page-shell">
      <NSpace class="page-toolbar stats-actions-bar" justify="end">
        <NSelect
          v-model:value="days"
          :options="daysOptions"
          size="small"
          style="width: 132px"
          :consistent-menu-width="false"
        />
        <NButton
          v-if="auth.hasPermission('admin:statistics:export')"
          size="small"
          :loading="exporting"
          @click="doExport"
        >
          {{ t('common.export') }}
        </NButton>
      </NSpace>

    <NSpin :show="loading">
      <NTabs
        v-model:value="tab"
        type="line"
        :animated="false"
        display-directive="show"
        class="stats-tabs"
      >
        <!-- Overview -->
        <NTabPane name="overview" :tab="t('statistics.tabOverview')">
          <div class="kpi-row">
            <div class="kpi-card">
              <div class="kpi-meta">
                <div class="kpi-label">{{ overviewKpis[0]?.label }}</div>
                <div class="kpi-value" :style="{ color: overviewKpis[0]?.color }">
                  {{ fmt(overviewKpis[0]?.value ?? 0) }}
                </div>
              </div>
              <div ref="sparkEl0" class="kpi-spark" />
            </div>
            <div class="kpi-card">
              <div class="kpi-meta">
                <div class="kpi-label">{{ overviewKpis[1]?.label }}</div>
                <div class="kpi-value" :style="{ color: overviewKpis[1]?.color }">
                  {{ fmt(overviewKpis[1]?.value ?? 0) }}
                </div>
              </div>
              <div ref="sparkEl1" class="kpi-spark" />
            </div>
            <div class="kpi-card">
              <div class="kpi-meta">
                <div class="kpi-label">{{ overviewKpis[2]?.label }}</div>
                <div class="kpi-value" :style="{ color: overviewKpis[2]?.color }">
                  {{ fmt(overviewKpis[2]?.value ?? 0) }}
                </div>
              </div>
              <div ref="sparkEl2" class="kpi-spark" />
            </div>
            <div class="kpi-card">
              <div class="kpi-meta">
                <div class="kpi-label">{{ overviewKpis[3]?.label }}</div>
                <div class="kpi-value" :style="{ color: overviewKpis[3]?.color }">
                  {{ fmt(overviewKpis[3]?.value ?? 0) }}
                </div>
              </div>
              <div ref="sparkEl3" class="kpi-spark" />
            </div>
          </div>

          <div class="chart-grid g-2-1">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.userTrend') }}</div>
                <div class="panel-desc">{{ t('statistics.panelTrendDesc') }}</div>
              </div>
              <div ref="ovAreaEl" class="chart-box tall" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.pendingMix') }}</div>
                <div class="panel-desc">{{ t('statistics.panelMixDesc') }}</div>
              </div>
              <div ref="ovPendingEl" class="chart-box tall" />
            </div>
          </div>

          <div class="chart-grid g-1-1 mt">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.volumeCompare') }}</div>
                <div class="panel-desc">{{ t('statistics.panelCompareDesc') }}</div>
              </div>
              <div ref="ovScaleEl" class="chart-box" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('dashboard.realtimeTitle') }}</div>
                <div class="panel-desc">{{ t('statistics.panelTodayDesc') }}</div>
              </div>
              <div ref="ovTodayEl" class="chart-box" />
            </div>
          </div>
        </NTabPane>

        <!-- Users -->
        <NTabPane name="users" :tab="t('statistics.tabUsers')">
          <div class="chart-grid g-2-1">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.userTrend') }}</div>
                <div class="panel-desc">{{ t('statistics.panelTrendDesc') }}</div>
              </div>
              <div ref="userAreaEl" class="chart-box tall" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.userStatus') }}</div>
                <div class="panel-desc">{{ t('statistics.panelMixDesc') }}</div>
              </div>
              <div ref="userDonutEl" class="chart-box tall" />
            </div>
          </div>
          <div class="chart-grid g-1-1 mt">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.rangeSummary') }}</div>
                <div class="panel-desc">{{ t('statistics.panelCompareDesc') }}</div>
              </div>
              <div ref="userBarEl" class="chart-box" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.loginStack') }}</div>
                <div class="panel-desc">{{ t('statistics.panelStackDesc') }}</div>
              </div>
              <div ref="userLoginEl" class="chart-box" />
            </div>
          </div>
        </NTabPane>

        <!-- Content -->
        <NTabPane name="content" :tab="t('statistics.tabContent')">
          <div class="chart-grid g-2-1">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.contentTrend') }}</div>
                <div class="panel-desc">{{ t('statistics.panelStackDesc') }}</div>
              </div>
              <div ref="contentAreaEl" class="chart-box tall" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.rangeSummary') }}</div>
                <div class="panel-desc">{{ t('statistics.panelCompareDesc') }}</div>
              </div>
              <div ref="contentBarEl" class="chart-box tall" />
            </div>
          </div>
          <div class="chart-panel mt">
            <div class="panel-head">
              <div class="panel-title">{{ t('statistics.contentColumns') }}</div>
              <div class="panel-desc">{{ t('statistics.panelTrendDesc') }}</div>
            </div>
            <div ref="contentColEl" class="chart-box tall" />
          </div>
        </NTabPane>

        <!-- Risk -->
        <NTabPane name="risk" :tab="t('statistics.tabRisk')">
          <div class="kpi-row risk-kpi">
            <div class="kpi-card">
              <div class="kpi-label">{{ t('statistics.avgHandleTime') }}</div>
              <div class="kpi-value" :style="{ color: LX_CHART_COLORS[0] }">
                {{ fmtMinutes(risk?.avgHandleMinutesInRange) }}
              </div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ t('statistics.resolvedReviews') }}</div>
              <div class="kpi-value" :style="{ color: LX_CHART_COLORS[1] }">
                {{ fmt(risk?.resolvedReviewsInRange ?? 0) }}
              </div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ t('statistics.pendingOver24h') }}</div>
              <div class="kpi-value" :style="{ color: LX_CHART_COLORS[2] }">
                {{ fmt(risk?.pendingOver24h ?? 0) }}
              </div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ t('statistics.pendingOver72h') }}</div>
              <div class="kpi-value" :style="{ color: LX_CHART_COLORS[4] }">
                {{ fmt(risk?.pendingOver72h ?? 0) }}
              </div>
            </div>
          </div>
          <div class="chart-grid g-2-1">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.riskTrend') }}</div>
                <div class="panel-desc">{{ t('statistics.panelTrendDesc') }}</div>
              </div>
              <div ref="riskAreaEl" class="chart-box tall" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.reviewStatus') }}</div>
                <div class="panel-desc">{{ t('statistics.panelMixDesc') }}</div>
              </div>
              <div ref="riskDonutEl" class="chart-box tall" />
            </div>
          </div>
          <div class="chart-grid g-1-1 mt">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.rangeSummary') }}</div>
                <div class="panel-desc">{{ t('statistics.panelCompareDesc') }}</div>
              </div>
              <div ref="riskBarEl" class="chart-box" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.riskStack') }}</div>
                <div class="panel-desc">{{ t('statistics.panelStackDesc') }}</div>
              </div>
              <div ref="riskColEl" class="chart-box" />
            </div>
          </div>
          <div class="chart-grid g-2-1 mt">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.reviewEfficiency') }}</div>
                <div class="panel-desc">{{ t('statistics.reviewEfficiencyDesc') }}</div>
              </div>
              <div ref="reviewEffAreaEl" class="chart-box tall" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.reviewBacklog') }}</div>
                <div class="panel-desc">{{ t('statistics.reviewBacklogDesc') }}</div>
              </div>
              <div ref="reviewEffBarEl" class="chart-box tall" />
            </div>
          </div>
        </NTabPane>

        <!-- Feedback -->
        <NTabPane name="feedback" :tab="t('statistics.tabFeedback')">
          <div class="chart-grid g-2-1">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.feedbackTrend') }}</div>
                <div class="panel-desc">{{ t('statistics.panelTrendDesc') }}</div>
              </div>
              <div ref="feedbackAreaEl" class="chart-box tall" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.feedbackStatus') }}</div>
                <div class="panel-desc">{{ t('statistics.panelMixDesc') }}</div>
              </div>
              <div ref="feedbackDonutEl" class="chart-box tall" />
            </div>
          </div>
          <div class="chart-grid g-1-1 mt">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.rangeSummary') }}</div>
                <div class="panel-desc">{{ t('statistics.panelCompareDesc') }}</div>
              </div>
              <div ref="feedbackBarEl" class="chart-box" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.feedbackStack') }}</div>
                <div class="panel-desc">{{ t('statistics.panelStackDesc') }}</div>
              </div>
              <div ref="feedbackColEl" class="chart-box" />
            </div>
          </div>
        </NTabPane>

        <!-- Groups -->
        <NTabPane name="groups" :tab="t('statistics.tabGroups')">
          <div class="kpi-row risk-kpi">
            <div class="kpi-card">
              <div class="kpi-label">{{ t('statistics.totalGroups') }}</div>
              <div class="kpi-value" :style="{ color: LX_CHART_COLORS[0] }">
                {{ fmt(groups?.totalGroups ?? 0) }}
              </div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ t('statistics.activeGroups') }}</div>
              <div class="kpi-value" :style="{ color: LX_CHART_COLORS[1] }">
                {{ fmt(groups?.activeGroupsInRange ?? 0) }}
              </div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ t('statistics.newGroupsInRange') }}</div>
              <div class="kpi-value" :style="{ color: LX_CHART_COLORS[2] }">
                {{ fmt(groups?.newGroupsInRange ?? 0) }}
              </div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ t('statistics.groupMessagesInRange') }}</div>
              <div class="kpi-value" :style="{ color: LX_CHART_COLORS[3] }">
                {{ fmt(groups?.groupMessagesInRange ?? 0) }}
              </div>
            </div>
          </div>
          <div class="chart-grid g-2-1">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.groupTrend') }}</div>
                <div class="panel-desc">{{ t('statistics.groupTrendDesc') }}</div>
              </div>
              <div ref="groupAreaEl" class="chart-box tall" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.rangeSummary') }}</div>
                <div class="panel-desc">{{ t('statistics.panelCompareDesc') }}</div>
              </div>
              <div ref="groupBarEl" class="chart-box tall" />
            </div>
          </div>
          <div class="chart-grid g-1-1 mt">
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.topGroups') }}</div>
                <div class="panel-desc">{{ t('statistics.topGroupsDesc') }}</div>
              </div>
              <div ref="groupTopEl" class="chart-box" />
            </div>
            <div class="chart-panel">
              <div class="panel-head">
                <div class="panel-title">{{ t('statistics.groupColumns') }}</div>
                <div class="panel-desc">{{ t('statistics.panelStackDesc') }}</div>
              </div>
              <div ref="groupColEl" class="chart-box" />
            </div>
          </div>
          <div class="chart-panel mt">
            <div class="panel-head">
              <div class="panel-title">{{ t('statistics.topGroupsTable') }}</div>
              <div class="panel-desc">{{ t('statistics.topGroupsDesc') }}</div>
            </div>
            <NDataTable
              size="small"
              :columns="topGroupColumns"
              :data="groups?.topGroups || []"
              :bordered="false"
              :pagination="false"
            />
          </div>
        </NTabPane>

        <!-- Advanced -->
        <NTabPane name="advanced" :tab="t('statistics.tabAdvanced')">
          <div class="chart-panel">
            <div class="panel-head heatmap-head">
              <div>
                <div class="panel-title">{{ t('statistics.activityHeatmap') }}</div>
                <div class="panel-desc">{{ t('statistics.activityHeatmapDesc') }}</div>
              </div>
              <div class="heatmap-meta">
                <NSelect
                  v-model:value="heatmapMetric"
                  :options="heatmapMetricOptions"
                  size="small"
                  style="width: 140px"
                  :consistent-menu-width="false"
                />
                <div class="heatmap-total">
                  {{ t('statistics.heatmapTotal') }}:
                  <strong>{{ fmt(heatmap?.total ?? 0) }}</strong>
                </div>
              </div>
            </div>
            <div ref="heatmapEl" class="chart-box heatmap" />
          </div>
        </NTabPane>
      </NTabs>
    </NSpin>
    </div>
  </div>
</template>

<style scoped>
.stats-page {
  gap: 12px;
}

.stats-actions-bar {
  margin-bottom: 12px;
}

.stats-tabs :deep(.n-tabs-nav) {
  margin-bottom: 14px;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.kpi-card {
  background: var(--lx-card);
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  padding: 16px 20px 10px;
  box-shadow: var(--lx-card-shadow);
  display: flex;
  flex-direction: column;
  min-height: 108px;
}

.kpi-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kpi-label {
  font-size: 12px;
  color: var(--lx-text-3);
}

.kpi-value {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
  color: var(--lx-oa-blue);
}

.kpi-spark {
  margin-top: 6px;
  height: 40px;
  width: 100%;
}

.chart-grid {
  display: grid;
  gap: 12px;
}

.g-2-1 {
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
}

.g-1-1 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.chart-panel {
  background: var(--lx-card);
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  padding: 0;
  box-shadow: var(--lx-card-shadow);
  min-width: 0;
  overflow: hidden;
}

.panel-head {
  padding: 12px 20px;
  border-bottom: 1px solid var(--lx-border);
  background: var(--lx-panel-head-bg);
  margin-bottom: 0;
}

.chart-panel .chart-box,
.chart-panel .n-data-table {
  padding: 14px 20px 16px;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text);
}

.panel-title::before {
  content: '';
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--lx-oa-blue);
  flex-shrink: 0;
}

.panel-desc {
  margin-top: 2px;
  font-size: 11px;
  color: var(--lx-text-3);
}

.chart-box {
  width: 100%;
  height: 260px;
}

.chart-box.tall {
  height: 300px;
}

.chart-box.heatmap {
  height: 420px;
}

.heatmap-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.heatmap-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.heatmap-total {
  font-size: 12px;
  color: var(--lx-text-3);
  white-space: nowrap;
}

.heatmap-total strong {
  color: var(--lx-text);
  font-variant-numeric: tabular-nums;
}

.mt {
  margin-top: 12px;
}

@media (max-width: 1100px) {
  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .g-2-1,
  .g-1-1 {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .kpi-row {
    grid-template-columns: 1fr;
  }
}
</style>
