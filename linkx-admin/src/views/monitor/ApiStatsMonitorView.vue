<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NGi, NGrid, NSpace, NSpin, NStatistic } from 'naive-ui'
import { fetchMonitorApiStats } from '@/api/systemMonitorMetrics'
import { buildAreaOption, buildDonutOption, buildHBarOption, useChart } from '@/utils/charts'
import { toTrendData } from '@/api/systemMonitorMetrics'

const { t } = useI18n()
const loading = ref(false)
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
    }))
  )
)
const topOpt = computed(() =>
  buildHBarOption(
    (data.value?.topPaths || []).map((i) => ({ key: i.key, name: i.name, value: i.value }))
  )
)

useChart(trendEl, trendOpt)
useChart(methodEl, methodOpt)
useChart(topEl, topOpt)

async function load() {
  loading.value = true
  try {
    data.value = await fetchMonitorApiStats(14)
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
    <NSpin :show="loading && !data">
      <template v-if="data">
        <NGrid :cols="3" :x-gap="12" class="summary">
          <NGi><NStatistic :label="t('monitor.totalRequests')" :value="data.totalRequests" /></NGi>
          <NGi><NStatistic :label="t('monitor.successRequests')" :value="data.successRequests" /></NGi>
          <NGi><NStatistic :label="t('monitor.failedRequests')" :value="data.failedRequests" /></NGi>
        </NGrid>
        <div class="chart-card"><h4>{{ t('monitor.dailyTrend') }}</h4><div ref="trendEl" class="chart-lg" /></div>
        <NGrid :cols="2" :x-gap="12" :y-gap="12">
          <NGi><div class="chart-card"><h4>{{ t('monitor.methodDistribution') }}</h4><div ref="methodEl" class="chart" /></div></NGi>
          <NGi><div class="chart-card"><h4>{{ t('monitor.topPaths') }}</h4><div ref="topEl" class="chart" /></div></NGi>
        </NGrid>
      </template>
    </NSpin>
  </div>
</template>

<style scoped>
.toolbar { margin-bottom: 12px; }
.summary { margin-bottom: 16px; }
.chart-card { background: var(--n-color); border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.chart-card h4 { margin: 0 0 8px; font-size: 14px; }
.chart { height: 260px; }
.chart-lg { height: 280px; }
</style>
