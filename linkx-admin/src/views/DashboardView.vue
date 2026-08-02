<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { NCarousel, NGi, NGrid, NIcon, NNumberAnimation, NSpin, NStatistic } from 'naive-ui'
import {
  PersonAddOutline,
  ChatbubblesOutline,
  LogInOutline,
  WarningOutline,
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
import {
  buildAreaOption,
  buildDonutOption,
  buildHBarOption,
  useChart,
  type NamedValue,
} from '@/utils/charts'
import AdminOpsBannerCarousel from '@/components/AdminOpsBannerCarousel.vue'

const { t, locale } = useI18n()
const router = useRouter()
const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)
const realtime = ref<DashboardRealtime | null>(null)
const pending = ref<PendingTask[]>([])
const trends = ref<TrendData | null>(null)

const trendEl = ref<HTMLElement | null>(null)
const scaleEl = ref<HTMLElement | null>(null)
const opsEl = ref<HTMLElement | null>(null)
const pendingEl = ref<HTMLElement | null>(null)

const opsBannerCount = ref<number | null>(null)
const showFallbackBanners = computed(() => opsBannerCount.value === 0)

function onOpsBannerLoaded(payload: { count: number }) {
  opsBannerCount.value = payload.count
}

const realtimeCards = computed(() => {
  void locale.value
  return [
    { key: 'todayNewUsers' as const, label: t('statistics.todayNewUsers'), icon: PersonAddOutline },
    {
      key: 'todayMessages' as const,
      label: t('statistics.todayMessages'),
      icon: ChatbubblesOutline,
    },
    { key: 'todayLogins' as const, label: t('statistics.todayLogins'), icon: LogInOutline },
    { key: 'riskEvents24h' as const, label: t('dashboard.riskEvents24h'), icon: WarningOutline },
  ]
})

const banners = computed(() => {
  void locale.value
  return [
    {
      title: t('dashboard.banner1Title'),
      desc: t('dashboard.banner1Desc'),
      tone: 'tone-a',
    },
    {
      title: t('dashboard.banner2Title'),
      desc: t('dashboard.banner2Desc'),
      tone: 'tone-b',
    },
    {
      title: t('dashboard.banner3Title'),
      desc: t('dashboard.banner3Desc'),
      tone: 'tone-c',
    },
  ]
})

function seriesName(key: string, fallback: string) {
  const map: Record<string, string> = {
    newUsers: t('statistics.seriesNewUsers'),
    messages: t('statistics.seriesMessages'),
    logins: t('statistics.seriesLogins'),
  }
  return map[key] || fallback
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
    pendingReports: t('dashboard.pendingReports'),
    pendingReviews: t('dashboard.pendingReviews'),
    todaySensitiveHits: t('dashboard.todaySensitiveHits'),
    todayRiskBlocks: t('dashboard.todayRiskBlocks'),
    riskEvents: t('dashboard.riskEvents'),
  }
  return map[key] || key
}

function taskTitle(task: PendingTask) {
  const map: Record<string, string> = {
    pendingFeedback: t('dashboard.pendingFeedback'),
    overdueFeedback: t('dashboard.overdueFeedback'),
    pendingReports: t('dashboard.pendingReports'),
    pendingReviews: t('dashboard.pendingReviews'),
    riskEvents: t('dashboard.riskEvents'),
  }
  return map[task.title] || task.title
}

const scaleItems = computed<NamedValue[]>(() => {
  void locale.value
  const s = summary.value
  return [
    { key: 'totalUsers', name: metricName('totalUsers'), value: s?.totalUsers ?? 0 },
    { key: 'dau', name: metricName('dau'), value: s?.dau ?? 0 },
    { key: 'wau', name: metricName('wau'), value: s?.wau ?? 0 },
    { key: 'mau', name: metricName('mau'), value: s?.mau ?? 0 },
    { key: 'onlineDevices', name: metricName('onlineDevices'), value: s?.onlineDevices ?? 0 },
  ]
})

const opsItems = computed<NamedValue[]>(() => {
  void locale.value
  const s = summary.value
  return [
    { key: 'pendingFeedback', name: metricName('pendingFeedback'), value: s?.pendingFeedback ?? 0 },
    { key: 'overdueFeedback', name: metricName('overdueFeedback'), value: s?.overdueFeedback ?? 0 },
    { key: 'pendingReports', name: metricName('pendingReports'), value: s?.pendingReports ?? 0 },
    { key: 'pendingReviews', name: metricName('pendingReviews'), value: s?.pendingReviews ?? 0 },
    {
      key: 'todaySensitiveHits',
      name: metricName('todaySensitiveHits'),
      value: s?.todaySensitiveHits ?? 0,
    },
    { key: 'todayRiskBlocks', name: metricName('todayRiskBlocks'), value: s?.todayRiskBlocks ?? 0 },
    { key: 'riskEvents', name: metricName('riskEvents'), value: s?.riskEvents ?? 0 },
  ]
})

const pendingItems = computed<NamedValue[]>(() => {
  void locale.value
  return pending.value.map((task) => ({
    key: task.type,
    name: taskTitle(task),
    value: task.count ?? 0,
  }))
})

const trendOption = computed(() => buildAreaOption(trends.value, seriesName))
const scaleOption = computed(() => buildHBarOption(scaleItems.value))
const opsOption = computed(() =>
  buildDonutOption(
    opsItems.value,
    (key, fallback) => metricName(key) || fallback,
    t('dashboard.opsRiskCenter')
  )
)
const pendingOption = computed(() => buildHBarOption(pendingItems.value))

const trendChart = useChart(trendEl, trendOption)
const scaleChart = useChart(scaleEl, scaleOption)
const opsChart = useChart(opsEl, opsOption)
const pendingChart = useChart(pendingEl, pendingOption, {
  onClick: ({ dataIndex }) => {
    if (dataIndex == null) return
    const task = pending.value[dataIndex]
    if (task?.path) router.push(task.path)
  },
})

function refreshCharts() {
  trendChart.refresh()
  scaleChart.refresh()
  opsChart.refresh()
  pendingChart.refresh()
}

onMounted(async () => {
  loading.value = true
  try {
    const [s, r, p, tr] = await Promise.all([
      fetchDashboardSummary(),
      fetchDashboardRealtime(),
      fetchPendingTasks(),
      fetchDashboardTrends(14),
    ])
    summary.value = s
    realtime.value = r
    pending.value = p || []
    trends.value = tr
  } finally {
    loading.value = false
    refreshCharts()
  }
})
</script>

<template>
  <div class="page">
    <div class="page-card carousel-wrap">
      <AdminOpsBannerCarousel position="home" :height="240" @loaded="onOpsBannerLoaded" />
      <NCarousel
        v-if="showFallbackBanners"
        autoplay
        :interval="4500"
        show-arrow
        draggable
        style="height: 240px"
      >
        <div v-for="(b, i) in banners" :key="i" class="banner" :class="b.tone">
          <div class="banner-title">{{ b.title }}</div>
          <div class="banner-desc">{{ b.desc }}</div>
        </div>
      </NCarousel>
    </div>

    <NSpin :show="loading">
      <div class="section">
        <div class="section-title inline">{{ t('dashboard.realtimeTitle') }}</div>
        <NGrid cols="1 s:2 m:4" responsive="screen" :x-gap="16" :y-gap="16">
          <NGi v-for="card in realtimeCards" :key="card.key">
            <div class="page-card stat-card">
              <div class="stat-icon">
                <NIcon :size="22" :component="card.icon" />
              </div>
              <NStatistic :label="card.label">
                <NNumberAnimation
                  :from="0"
                  :to="realtime?.[card.key] ?? 0"
                  :active="!!realtime"
                  :precision="0"
                  show-separator
                />
              </NStatistic>
            </div>
          </NGi>
        </NGrid>
      </div>

      <NGrid cols="1 m:2" responsive="screen" :x-gap="16" :y-gap="16" class="section">
        <NGi>
          <div class="page-card chart-card">
            <div class="section-title">{{ t('dashboard.scaleTitle') }}</div>
            <div ref="scaleEl" class="chart" />
          </div>
        </NGi>
        <NGi>
          <div class="page-card chart-card">
            <div class="section-title">{{ t('dashboard.opsRiskTitle') }}</div>
            <div ref="opsEl" class="chart" />
          </div>
        </NGi>
      </NGrid>

      <NGrid cols="1 m:3" responsive="screen" :x-gap="16" :y-gap="16" class="section">
        <NGi :span="2">
          <div class="page-card chart-card">
            <div class="section-title">{{ t('dashboard.trendsTitle') }}</div>
            <div ref="trendEl" class="chart" />
          </div>
        </NGi>
        <NGi>
          <div class="page-card chart-card">
            <div class="section-title">{{ t('dashboard.pendingTitle') }}</div>
            <div v-if="pendingItems.length" ref="pendingEl" class="chart chart-clickable" />
            <div v-else class="empty">{{ t('dashboard.noPending') }}</div>
          </div>
        </NGi>
      </NGrid>
    </NSpin>
  </div>
</template>

<style scoped>
.carousel-wrap {
  padding: 0 !important;
  overflow: hidden;
}
.banner {
  height: 240px;
  padding: 36px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  color: #f5f7fb;
}
.banner-title {
  font-size: 22px;
  font-weight: 650;
  letter-spacing: 0.02em;
}
.banner-desc {
  font-size: 14px;
  opacity: 0.88;
  max-width: 520px;
  line-height: 1.5;
}
.tone-a {
  background: linear-gradient(135deg, #3d6fd4 0%, #5b8def 48%, #7aa3f5 100%);
}
.tone-b {
  background: linear-gradient(135deg, #1f6f6a 0%, #2f9e94 50%, #58c4b8 100%);
}
.tone-c {
  background: linear-gradient(135deg, #5a4a8a 0%, #6f5fb0 50%, #8f7fd4 100%);
}
.stat-card {
  min-height: 120px;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 20px !important;
}
.stat-icon {
  position: absolute;
  right: 18px;
  top: 18px;
  width: 40px;
  height: 40px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: var(--lx-stat-accent);
  background: rgba(91, 141, 239, 0.12);
}
.section {
  margin-top: 16px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  opacity: 0.86;
}
.section-title.inline {
  margin-bottom: 12px;
}
.chart-card {
  padding: 16px 18px !important;
  min-height: 360px;
}
.chart {
  width: 100%;
  height: 300px;
}
.chart-clickable {
  cursor: pointer;
}
.empty {
  padding: 48px 12px;
  text-align: center;
  opacity: 0.55;
  font-size: 13px;
}
</style>
