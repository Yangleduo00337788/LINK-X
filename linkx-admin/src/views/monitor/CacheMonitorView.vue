<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NGi, NGrid, NProgress, NSpace, NSpin, NStatistic } from 'naive-ui'
import { fetchMonitorCache } from '@/api/systemMonitorMetrics'
import { buildAreaOption, useChart } from '@/utils/charts'
import { toTrendData } from '@/api/systemMonitorMetrics'
import { formatMonitorBytes } from '@/api/systemMonitorMetrics'

const { t } = useI18n()
const loading = ref(false)
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

useChart(memEl, memOpt)
useChart(qpsEl, qpsOpt)
useChart(hitEl, hitOpt)
useChart(connEl, connOpt)

async function load() {
  loading.value = true
  try {
    data.value = await fetchMonitorCache(24)
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
    <NSpin :show="loading && !data">
      <template v-if="data">
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
        <NGrid :cols="2" :x-gap="12" :y-gap="12">
          <NGi><div class="chart-card"><h4>{{ t('monitor.memoryTrend') }}</h4><div ref="memEl" class="chart" /></div></NGi>
          <NGi><div class="chart-card"><h4>{{ t('monitor.qpsTrend') }}</h4><div ref="qpsEl" class="chart" /></div></NGi>
          <NGi><div class="chart-card"><h4>{{ t('monitor.hitRateTrend') }}</h4><div ref="hitEl" class="chart" /></div></NGi>
          <NGi><div class="chart-card"><h4>{{ t('monitor.connectionsTrend') }}</h4><div ref="connEl" class="chart" /></div></NGi>
        </NGrid>
      </template>
    </NSpin>
  </div>
</template>

<style scoped>
.toolbar { margin-bottom: 12px; }
.hint { color: var(--n-text-color-3); font-size: 13px; }
.summary { margin-bottom: 16px; }
.chart-card { background: var(--n-color); border-radius: 8px; padding: 12px; }
.chart-card h4 { margin: 0 0 8px; font-size: 14px; }
.chart { height: 220px; }
</style>
