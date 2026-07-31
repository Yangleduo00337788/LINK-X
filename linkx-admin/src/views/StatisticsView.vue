<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NSelect, NSpin, NTabPane, NTabs } from 'naive-ui'
import {
  fetchStatisticContent,
  fetchStatisticFeedback,
  fetchStatisticOverview,
  fetchStatisticRisk,
  fetchStatisticUsers,
  type StatisticContent,
  type StatisticFeedback,
  type StatisticOverview,
  type StatisticRisk,
  type StatisticUsers,
  type TrendData,
} from '@/api/statistics'
import {
  LX_CHART_COLORS,
  buildAreaOption,
  buildColumnOption,
  buildDonutOption,
  buildHBarOption,
  buildSparkOption,
  seriesValues,
  useChart,
  useDaysOptions,
  type NamedValue,
} from '@/utils/charts'

const { t, locale } = useI18n()
const loading = ref(false)
const days = ref(14)
const tab = ref('overview')

const overview = ref<StatisticOverview | null>(null)
const users = ref<StatisticUsers | null>(null)
const content = ref<StatisticContent | null>(null)
const risk = ref<StatisticRisk | null>(null)
const feedback = ref<StatisticFeedback | null>(null)

const daysOptions = useDaysOptions((k) => t(k))

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
    reviews: t('statistics.seriesReviews'),
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
    { key: 'pendingFeedback', name: t('dashboard.pendingFeedback'), value: o?.pendingFeedback ?? 0 },
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
    { key: 'pending', name: t('dashboard.pendingReviews'), value: r?.pendingReviews ?? 0 },
  ]
})

const feedbackRangeBars = computed<NamedValue[]>(() => {
  void locale.value
  const f = feedback.value
  return [
    { key: 'created', name: t('statistics.createdInRange'), value: f?.createdInRange ?? 0 },
    { key: 'replied', name: t('statistics.repliedInRange'), value: f?.repliedInRange ?? 0 },
    { key: 'closed', name: t('statistics.closedInRange'), value: f?.closedInRange ?? 0 },
  ]
})

/* --- chart option refs --- */
const spark0 = computed(() => buildSparkOption(overviewKpis.value[0]?.spark || [], LX_CHART_COLORS[0]))
const spark1 = computed(() => buildSparkOption(overviewKpis.value[1]?.spark || [], LX_CHART_COLORS[1]))
const spark2 = computed(() => buildSparkOption(overviewKpis.value[2]?.spark || [], LX_CHART_COLORS[2]))
const spark3 = computed(() => buildSparkOption(overviewKpis.value[3]?.spark || [], LX_CHART_COLORS[3]))

const overviewAreaOpt = computed(() => buildAreaOption(overviewTrend.value, seriesName))
const overviewPendingOpt = computed(() =>
  buildDonutOption(overviewPending.value, (k, f) => breakdownName(k, f) || f, t('statistics.chartTotal')),
)
const overviewScaleOpt = computed(() => buildHBarOption(overviewScale.value))
const overviewTodayOpt = computed(() => buildColumnOption(overviewToday.value, seriesName))

const userAreaOpt = computed(() => buildAreaOption(users.value?.trend, seriesName))
const userDonutOpt = computed(() =>
  buildDonutOption(users.value?.statusBreakdown, breakdownName, t('statistics.chartUsers')),
)
const userBarOpt = computed(() => buildHBarOption(userRangeBars.value))
const userLoginColOpt = computed(() =>
  buildColumnOption(
    {
      labels: users.value?.trend?.labels || [],
      series: (users.value?.trend?.series || []).filter((s) =>
        s.key === 'loginSuccess' || s.key === 'loginFail',
      ),
    },
    seriesName,
    { stacked: true },
  ),
)

const contentAreaOpt = computed(() => buildAreaOption(content.value?.trend, seriesName, { stacked: true }))
const contentBarOpt = computed(() => buildHBarOption(contentRangeBars.value))
const contentColOpt = computed(() => buildColumnOption(content.value?.trend, seriesName))

const riskAreaOpt = computed(() => buildAreaOption(risk.value?.trend, seriesName))
const riskDonutOpt = computed(() =>
  buildDonutOption(risk.value?.reviewStatusBreakdown, breakdownName, t('statistics.chartReviews')),
)
const riskBarOpt = computed(() => buildHBarOption(riskRangeBars.value))
const riskColOpt = computed(() =>
  buildColumnOption(risk.value?.trend, seriesName, { stacked: true }),
)

const feedbackAreaOpt = computed(() => buildAreaOption(feedback.value?.trend, seriesName))
const feedbackDonutOpt = computed(() =>
  buildDonutOption(feedback.value?.statusBreakdown, breakdownName, t('statistics.chartFeedback')),
)
const feedbackBarOpt = computed(() => buildHBarOption(feedbackRangeBars.value))
const feedbackColOpt = computed(() =>
  buildColumnOption(feedback.value?.trend, seriesName, { stacked: true }),
)

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

const feedbackAreaEl = ref<HTMLElement | null>(null)
const feedbackDonutEl = ref<HTMLElement | null>(null)
const feedbackBarEl = ref<HTMLElement | null>(null)
const feedbackColEl = ref<HTMLElement | null>(null)

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
]
const feedbackCharts = [
  useChart(feedbackAreaEl, feedbackAreaOpt),
  useChart(feedbackDonutEl, feedbackDonutOpt),
  useChart(feedbackBarEl, feedbackBarOpt),
  useChart(feedbackColEl, feedbackColOpt),
]

const chartsByTab: Record<string, typeof overviewCharts> = {
  overview: overviewCharts,
  users: userCharts,
  content: contentCharts,
  risk: riskCharts,
  feedback: feedbackCharts,
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
    const [ov, us, ct, rk, fb] = await Promise.all([
      fetchStatisticOverview(d),
      fetchStatisticUsers(d),
      fetchStatisticContent(d),
      fetchStatisticRisk(d),
      fetchStatisticFeedback(d),
    ])
    overview.value = ov
    users.value = us
    content.value = ct
    risk.value = rk
    feedback.value = fb
  } finally {
    loading.value = false
    refreshActiveTab()
  }
}

watch(days, () => {
  void load()
})

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page stats-page">
    <div class="stats-toolbar">
      <div>
        <div class="stats-title">{{ t('statistics.title') }}</div>
        <div class="stats-sub">{{ t('statistics.subtitle') }}</div>
      </div>
      <NSelect
        v-model:value="days"
        :options="daysOptions"
        size="small"
        style="width: 132px"
        :consistent-menu-width="false"
      />
    </div>

    <NSpin :show="loading">
      <NTabs v-model:value="tab" type="line" :animated="false" display-directive="show" class="stats-tabs">
        <!-- Overview -->
        <NTabPane name="overview" :tab="t('statistics.tabOverview')">
          <div class="kpi-row">
            <div class="kpi-card">
              <div class="kpi-meta">
                <div class="kpi-label">{{ overviewKpis[0]?.label }}</div>
                <div class="kpi-value" :style="{ color: overviewKpis[0]?.color }">{{ fmt(overviewKpis[0]?.value ?? 0) }}</div>
              </div>
              <div ref="sparkEl0" class="kpi-spark" />
            </div>
            <div class="kpi-card">
              <div class="kpi-meta">
                <div class="kpi-label">{{ overviewKpis[1]?.label }}</div>
                <div class="kpi-value" :style="{ color: overviewKpis[1]?.color }">{{ fmt(overviewKpis[1]?.value ?? 0) }}</div>
              </div>
              <div ref="sparkEl1" class="kpi-spark" />
            </div>
            <div class="kpi-card">
              <div class="kpi-meta">
                <div class="kpi-label">{{ overviewKpis[2]?.label }}</div>
                <div class="kpi-value" :style="{ color: overviewKpis[2]?.color }">{{ fmt(overviewKpis[2]?.value ?? 0) }}</div>
              </div>
              <div ref="sparkEl2" class="kpi-spark" />
            </div>
            <div class="kpi-card">
              <div class="kpi-meta">
                <div class="kpi-label">{{ overviewKpis[3]?.label }}</div>
                <div class="kpi-value" :style="{ color: overviewKpis[3]?.color }">{{ fmt(overviewKpis[3]?.value ?? 0) }}</div>
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
      </NTabs>
    </NSpin>
  </div>
</template>

<style scoped>
.stats-page {
  gap: 12px;
}

.stats-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 2px 4px;
}

.stats-title {
  font-size: 18px;
  font-weight: 650;
  letter-spacing: 0.01em;
}

.stats-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lx-text-3);
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
  border-radius: 12px;
  padding: 14px 14px 8px;
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
  font-weight: 650;
  letter-spacing: 0.01em;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
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
  border-radius: 12px;
  padding: 14px 14px 10px;
  box-shadow: var(--lx-card-shadow);
  min-width: 0;
}

.panel-head {
  margin-bottom: 4px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text);
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
