<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NEmpty, NGi, NGrid, NSpace, NSpin, NStatistic } from 'naive-ui'
import { fetchMonitorApiStats, toTrendData } from '@/api/systemMonitorMetrics'
import { buildAreaOption, buildDonutOption, buildHBarOption, useChart } from '@/utils/charts'

const { t } = useI18n()
const loading = ref(false)
const error = ref<string | null>(null)
const data = ref<Awaited<ReturnType<typeof fetchMonitorApiStats>> | null>(null)
const trendEl = ref<HTMLElement | null>(null)
const methodEl = ref<HTMLElement | null>(null)
const topEl = ref<HTMLElement | null>(null)

const trendOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.dailyTrend), (k) => t(`monitor.series.${k}`))
)
const methodOpt = computed(() =>
  buildDonutOption(
    (data.value?.methodDistribution || []).map((i) => ({
      key: i.key,
      name: i.name,
      value: i.value,
    })),
    (_, fallback) => fallback
  )
)
const topOpt = computed(() =>
  buildHBarOption(
    (data.value?.topPaths || []).map((i) => ({ key: i.key, name: i.name, value: i.value }))
  )
)

const trendChart = useChart(trendEl, trendOpt)
const methodChart = useChart(methodEl, methodOpt)
const topChart = useChart(topEl, topOpt)

function refreshCharts() {
  trendChart.refresh()
  methodChart.refresh()
  topChart.refresh()
}

async function load() {
  loading.value = true
  error.value = null
  try {
    data.value = await fetchMonitorApiStats(14)
    await nextTick()
    refreshCharts()
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.requestFailed')
    data.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <NSpace justify="end" class="toolbar">
      <NButton :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
    </NSpace>
    <NSpin :show="loading && !data && !error">
      <NEmpty v-if="error && !data" :description="error" class="empty" />
      <template v-else-if="data">
        <NGrid :cols="3" :x-gap="12" class="summary">
          <NGi><NStatistic :label="t('monitor.totalRequests')" :value="data.totalRequests" /></NGi>
          <NGi><NStatistic :label="t('monitor.successRequests')" :value="data.successRequests" /></NGi>
          <NGi><NStatistic :label="t('monitor.failedRequests')" :value="data.failedRequests" /></NGi>
        </NGrid>
        <div class="page-card chart-card">
          <h4>{{ t('monitor.dailyTrend') }}</h4>
          <div ref="trendEl" class="chart-lg" />
        </div>
        <NGrid :cols="2" :x-gap="12" :y-gap="12">
          <NGi>
            <div class="page-card chart-card">
              <h4>{{ t('monitor.methodDistribution') }}</h4>
              <div ref="methodEl" class="chart" />
            </div>
          </NGi>
          <NGi>
            <div class="page-card chart-card">
              <h4>{{ t('monitor.topPaths') }}</h4>
              <div ref="topEl" class="chart" />
            </div>
          </NGi>
        </NGrid>
      </template>
    </NSpin>
  </div>
</template>

<style scoped>
.toolbar {
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
.chart-lg {
  height: 280px;
}
.empty {
  padding: 48px 0;
}
</style>
