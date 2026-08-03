<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NDataTable, NGi, NGrid, NSpace, NSpin, NStatistic, NTag, type DataTableColumns } from 'naive-ui'
import { fetchMonitorTasks } from '@/api/systemMonitorMetrics'
import type { SnailJobTaskItem } from '@/api/scheduledTasks'
import { buildAreaOption, buildDonutOption, useChart } from '@/utils/charts'
import { toTrendData } from '@/api/systemMonitorMetrics'
import { formatTime } from '@/utils/format'

const { t, locale } = useI18n()
const loading = ref(false)
const data = ref<Awaited<ReturnType<typeof fetchMonitorTasks>> | null>(null)
const trendEl = ref<HTMLElement | null>(null)
const ratioEl = ref<HTMLElement | null>(null)

const trendOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.dailyTrend), (k) => {
    const map: Record<string, string> = {
      success: t('monitor.taskSuccess'),
      fail: t('monitor.taskFail'),
    }
    return map[k] || k
  }, { stacked: true })
)
const ratioOpt = computed(() =>
  buildDonutOption([
    { key: 'success', name: t('monitor.taskSuccess'), value: data.value?.successBatches || 0 },
    { key: 'fail', name: t('monitor.taskFail'), value: data.value?.failedBatches || 0 },
  ])
)
useChart(trendEl, trendOpt)
useChart(ratioEl, ratioOpt)

const columns = computed<DataTableColumns<SnailJobTaskItem>>(() => {
  void locale.value
  return [
    { title: t('scheduledTask.name'), key: 'jobName', width: 140 },
    { title: t('scheduledTask.executor'), key: 'executorName', width: 160 },
    {
      title: t('common.status'),
      key: 'jobStatus',
      width: 90,
      render: (row) =>
        h(
          NTag,
          { size: 'small', type: row.jobStatus === 1 ? 'success' : 'default', bordered: false },
          () => (row.jobStatus === 1 ? t('common.enabled') : t('common.disabled'))
        ),
    },
    {
      title: t('scheduledTask.lastRun'),
      key: 'lastExecutionAt',
      width: 170,
      render: (row) => formatTime(row.lastExecutionAt),
    },
    {
      title: t('scheduledTask.lastResult'),
      key: 'lastBatchStatus',
      width: 100,
      render: (row) => row.lastBatchStatus || '-',
    },
  ]
})

async function load() {
  loading.value = true
  try {
    data.value = await fetchMonitorTasks(7)
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
        <NGrid :cols="4" :x-gap="12" class="summary">
          <NGi><NStatistic :label="t('scheduledTask.summary.total')" :value="data.totalTasks" /></NGi>
          <NGi><NStatistic :label="t('scheduledTask.summary.enabled')" :value="data.enabledTasks" /></NGi>
          <NGi><NStatistic :label="t('monitor.taskSuccessRate')" :value="`${data.successRatePercent}%`" /></NGi>
          <NGi><NStatistic :label="t('scheduledTask.summary.failed')" :value="data.failedTasks" /></NGi>
        </NGrid>
        <NGrid :cols="2" :x-gap="12" :y-gap="12">
          <NGi><div class="chart-card"><h4>{{ t('monitor.taskDailyTrend') }}</h4><div ref="trendEl" class="chart" /></div></NGi>
          <NGi><div class="chart-card"><h4>{{ t('monitor.taskSuccessRatio') }}</h4><div ref="ratioEl" class="chart" /></div></NGi>
        </NGrid>
        <div class="chart-card">
          <h4>{{ t('monitor.taskList') }}</h4>
          <NDataTable :columns="columns" :data="data.tasks || []" :bordered="false" size="small" :scroll-x="800" />
        </div>
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
</style>
