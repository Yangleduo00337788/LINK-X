<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  NCarousel,
  NGi,
  NGrid,
  NIcon,
  NList,
  NListItem,
  NNumberAnimation,
  NSpin,
  NStatistic,
  NThing,
} from 'naive-ui'
import {
  PeopleOutline,
  PulseOutline,
  PhonePortraitOutline,
  ChatbubbleEllipsesOutline,
  DocumentTextOutline,
  WarningOutline,
  PersonAddOutline,
  ChatbubblesOutline,
  LogInOutline,
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
import { buildAreaOption, useChart } from '@/utils/charts'

const { t, locale } = useI18n()
const router = useRouter()
const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)
const realtime = ref<DashboardRealtime | null>(null)
const pending = ref<PendingTask[]>([])
const trends = ref<TrendData | null>(null)
const trendEl = ref<HTMLElement | null>(null)

const cards = computed(() => {
  void locale.value
  return [
    { key: 'totalUsers' as const, label: t('dashboard.totalUsers'), icon: PeopleOutline },
    { key: 'activeUsers' as const, label: t('dashboard.activeUsers'), icon: PulseOutline },
    { key: 'onlineDevices' as const, label: t('dashboard.onlineDevices'), icon: PhonePortraitOutline },
    { key: 'pendingFeedback' as const, label: t('dashboard.pendingFeedback'), icon: ChatbubbleEllipsesOutline },
    { key: 'pendingReviews' as const, label: t('dashboard.pendingReviews'), icon: DocumentTextOutline },
    { key: 'riskEvents' as const, label: t('dashboard.riskEvents'), icon: WarningOutline },
  ]
})

const realtimeCards = computed(() => {
  void locale.value
  return [
    { key: 'todayNewUsers' as const, label: t('statistics.todayNewUsers'), icon: PersonAddOutline },
    { key: 'todayMessages' as const, label: t('statistics.todayMessages'), icon: ChatbubblesOutline },
    { key: 'todayLogins' as const, label: t('statistics.todayLogins'), icon: LogInOutline },
    { key: 'onlineDevices' as const, label: t('dashboard.onlineDevices'), icon: PhonePortraitOutline },
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

function taskTitle(task: PendingTask) {
  const map: Record<string, string> = {
    pendingFeedback: t('dashboard.pendingFeedback'),
    pendingReviews: t('dashboard.pendingReviews'),
    riskEvents: t('dashboard.riskEvents'),
  }
  return map[task.title] || task.title
}

const trendOption = computed(() => buildAreaOption(trends.value, seriesName))
const trendChart = useChart(trendEl, trendOption)

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
    trendChart.refresh()
  }
})
</script>

<template>
  <div class="page">
    <div class="page-card carousel-wrap">
      <NCarousel autoplay :interval="4500" show-arrow draggable style="height: 168px">
        <div v-for="(b, i) in banners" :key="i" class="banner" :class="b.tone">
          <div class="banner-title">{{ b.title }}</div>
          <div class="banner-desc">{{ b.desc }}</div>
        </div>
      </NCarousel>
    </div>

    <NSpin :show="loading">
      <NGrid cols="1 s:2 m:3" responsive="screen" :x-gap="16" :y-gap="16">
        <NGi v-for="card in cards" :key="card.key">
          <div class="page-card stat-card">
            <div class="stat-icon">
              <NIcon :size="22" :component="card.icon" />
            </div>
            <NStatistic :label="card.label">
              <NNumberAnimation
                :from="0"
                :to="summary?.[card.key] ?? 0"
                :active="!!summary"
                :precision="0"
                show-separator
              />
            </NStatistic>
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
            <NList v-if="pending.length" hoverable clickable>
              <NListItem
                v-for="task in pending"
                :key="task.type"
                @click="router.push(task.path)"
              >
                <NThing :title="taskTitle(task)" :description="task.path">
                  <template #header-extra>
                    <span class="task-count">{{ task.count }}</span>
                  </template>
                </NThing>
              </NListItem>
            </NList>
            <div v-else class="empty">{{ t('dashboard.noPending') }}</div>
          </div>
        </NGi>
      </NGrid>

      <div class="section">
        <div class="section-title inline">{{ t('dashboard.realtimeTitle') }}</div>
        <NGrid cols="1 s:2 m:4" responsive="screen" :x-gap="16" :y-gap="16">
          <NGi v-for="card in realtimeCards" :key="card.key">
            <div class="page-card stat-card compact">
              <div class="stat-icon">
                <NIcon :size="20" :component="card.icon" />
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
    </NSpin>
  </div>
</template>

<style scoped>
.carousel-wrap {
  padding: 0 !important;
  overflow: hidden;
}
.banner {
  height: 168px;
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
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
.stat-card.compact {
  min-height: 100px;
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
.task-count {
  font-size: 18px;
  font-weight: 650;
  color: var(--lx-stat-accent, #5b8def);
}
.empty {
  padding: 48px 12px;
  text-align: center;
  opacity: 0.55;
  font-size: 13px;
}
</style>
