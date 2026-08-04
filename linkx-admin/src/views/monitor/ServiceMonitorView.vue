<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NEmpty, NGi, NGrid, NProgress, NSpace, NSpin, NStatistic } from 'naive-ui'
import { fetchMonitorService, formatMonitorBytes, toTrendData } from '@/api/systemMonitorMetrics'
import { buildAreaOption, useChart } from '@/utils/charts'

const { t } = useI18n()
const loading = ref(false)
const error = ref<string | null>(null)
const data = ref<Awaited<ReturnType<typeof fetchMonitorService>> | null>(null)
const cpuEl = ref<HTMLElement | null>(null)
const memEl = ref<HTMLElement | null>(null)

const cpuOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.cpuTrend), (k) => t(`monitor.series.${k}`))
)
const memOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.memoryTrend), (k) => t(`monitor.series.${k}`))
)

const cpuChart = useChart(cpuEl, cpuOpt)
const memChart = useChart(memEl, memOpt)

function refreshCharts() {
  cpuChart.refresh()
  memChart.refresh()
}

async function load() {
  loading.value = true
  error.value = null
  try {
    data.value = await fetchMonitorService(24)
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
        <NGrid :cols="4" :x-gap="12" :y-gap="12" class="summary">
          <NGi><NStatistic :label="t('monitor.cpuLoad')" :value="`${data.systemCpuLoadPercent}%`" /></NGi>
          <NGi><NStatistic :label="t('monitor.jvmHeap')" :value="`${data.jvmHeapUsagePercent}%`" /></NGi>
          <NGi><NStatistic :label="t('monitor.systemMemory')" :value="`${data.systemMemoryUsagePercent}%`" /></NGi>
          <NGi><NStatistic :label="t('monitor.diskUsage')" :value="`${data.diskUsagePercent}%`" /></NGi>
        </NGrid>
        <NGrid :cols="2" :x-gap="12" :y-gap="12" class="detail-grid">
          <NGi>
            <div class="page-card card">
              <h4>{{ t('monitor.serverInfo') }}</h4>
              <p>{{ t('monitor.host') }}: {{ data.hostName }}</p>
              <p>{{ t('monitor.os') }}: {{ data.osName }} ({{ data.osArch }})</p>
              <p>{{ t('monitor.processors') }}: {{ data.availableProcessors }}</p>
              <p>{{ t('monitor.javaVersion') }}: {{ data.javaVersion }}</p>
            </div>
          </NGi>
          <NGi>
            <div class="page-card card">
              <h4>{{ t('monitor.jvmInfo') }}</h4>
              <p>{{ t('monitor.threads') }}: {{ data.threadCount }} / {{ t('monitor.peak') }} {{ data.peakThreadCount }}</p>
              <p>{{ t('monitor.gcCount') }}: {{ data.gcCount }}</p>
              <p>{{ t('monitor.heap') }}: {{ formatMonitorBytes(data.jvmHeapUsedBytes) }} / {{ formatMonitorBytes(data.jvmHeapMaxBytes) }}</p>
              <NProgress :percentage="data.jvmHeapUsagePercent" :height="14" />
            </div>
          </NGi>
          <NGi>
            <div class="page-card card">
              <h4>{{ t('monitor.diskInfo') }}</h4>
              <p>{{ data.diskPath }}</p>
              <p>{{ formatMonitorBytes(data.diskTotalBytes - data.diskFreeBytes) }} / {{ formatMonitorBytes(data.diskTotalBytes) }}</p>
              <NProgress :percentage="data.diskUsagePercent" :height="14" />
            </div>
          </NGi>
          <NGi>
            <div class="page-card card">
              <h4>{{ t('monitor.cpuTrend') }}</h4>
              <div ref="cpuEl" class="chart" />
            </div>
          </NGi>
          <NGi :span="2">
            <div class="page-card card">
              <h4>{{ t('monitor.memoryTrend') }}</h4>
              <div ref="memEl" class="chart" />
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
.detail-grid {
  margin-bottom: 16px;
}
.card {
  padding: 12px;
  height: 100%;
}
.card h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
.card p {
  margin: 4px 0;
  font-size: 13px;
  color: var(--lx-text-2);
}
.chart {
  height: 200px;
}
.empty {
  padding: 48px 0;
}
</style>
