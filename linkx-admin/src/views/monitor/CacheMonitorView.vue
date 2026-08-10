<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NEmpty, NGi, NGrid, NProgress, NSpace, NSpin, NStatistic } from 'naive-ui'
import { fetchMonitorCache, formatMonitorBytes, isSparseMonitorTrend, toTrendData } from '@/api/systemMonitorMetrics'
import { buildAreaOption, useChart } from '@/utils/charts'

const { t } = useI18n()
const loading = ref(false)
const error = ref<string | null>(null)
const data = ref<Awaited<ReturnType<typeof fetchMonitorCache>> | null>(null)

const memEl = ref<HTMLElement | null>(null)
const qpsEl = ref<HTMLElement | null>(null)
const hitEl = ref<HTMLElement | null>(null)
const connEl = ref<HTMLElement | null>(null)

const memOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.memoryTrend), (k) => t(`monitor.series.${k}`))
)
const qpsOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.qpsTrend), (k) => t(`monitor.series.${k}`))
)
const hitOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.hitRateTrend), (k) => t(`monitor.series.${k}`))
)
const connOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.connectionsTrend), (k) => t(`monitor.series.${k}`))
)

const sparseTrend = computed(
  () =>
    isSparseMonitorTrend(data.value?.memoryTrend) &&
    isSparseMonitorTrend(data.value?.qpsTrend) &&
    isSparseMonitorTrend(data.value?.hitRateTrend) &&
    isSparseMonitorTrend(data.value?.connectionsTrend)
)
const memChart = useChart(memEl, memOpt)
const qpsChart = useChart(qpsEl, qpsOpt)
const hitChart = useChart(hitEl, hitOpt)
const connChart = useChart(connEl, connOpt)

function refreshCharts() {
  memChart.refresh()
  qpsChart.refresh()
  hitChart.refresh()
  connChart.refresh()
}

async function load() {
  loading.value = true
  error.value = null
  try {
    data.value = await fetchMonitorCache(24)
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
    <NSpace justify="space-between" align="center" class="toolbar">
      <span class="hint">{{ t('monitor.cacheHint') }}</span>
      <NButton :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
    </NSpace>
    <NSpin :show="loading && !data && !error">
      <NEmpty v-if="error && !data" :description="error" class="empty" />
      <template v-else-if="data">
        <NGrid :cols="4" :x-gap="12" :y-gap="12" class="summary">
          <NGi>
            <NStatistic :label="t('monitor.redisMemory')" :value="formatMonitorBytes(data.usedMemoryBytes)" />
            <NProgress
              type="line"
              :percentage="data.memoryUsagePercent"
              :height="14"
              style="margin-top: 8px"
            />
          </NGi>
          <NGi>
            <NStatistic :label="t('monitor.qps')" :value="data.qps" />
          </NGi>
          <NGi>
            <NStatistic :label="t('monitor.hitRate')" :value="`${data.hitRatePercent}%`" />
          </NGi>
          <NGi>
            <NStatistic :label="t('monitor.connections')" :value="data.connectedClients" />
          </NGi>
        </NGrid>
        <p v-if="sparseTrend" class="trend-hint">{{ t('monitor.trendSparseHint') }}</p>
        <NGrid :cols="2" :x-gap="12" :y-gap="12">
          <NGi>
            <div class="page-card chart-card">
              <h4>{{ t('monitor.memoryTrend') }}</h4>
              <div ref="memEl" class="chart" />
            </div>
          </NGi>
          <NGi>
            <div class="page-card chart-card">
              <h4>{{ t('monitor.qpsTrend') }}</h4>
              <div ref="qpsEl" class="chart" />
            </div>
          </NGi>
          <NGi>
            <div class="page-card chart-card">
              <h4>{{ t('monitor.hitRateTrend') }}</h4>
              <div ref="hitEl" class="chart" />
            </div>
          </NGi>
          <NGi>
            <div class="page-card chart-card">
              <h4>{{ t('monitor.connectionsTrend') }}</h4>
              <div ref="connEl" class="chart" />
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
.hint {
  color: var(--lx-text-3);
  font-size: 13px;
}
.summary {
  margin-bottom: 16px;
}
.trend-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--lx-text-3);
}
.chart-card {
  padding: 12px;
}
.chart-card h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
.chart {
  height: 220px;
}
.empty {
  padding: 48px 0;
}
</style>
