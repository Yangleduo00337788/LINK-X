<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NDataTable, NGi, NGrid, NSpace, NSpin, NStatistic, type DataTableColumns } from 'naive-ui'
import { fetchMonitorSql } from '@/api/systemMonitorMetrics'
import type { MonitorSqlStatement } from '@/api/systemMonitorMetrics'
import { buildAreaOption, useChart } from '@/utils/charts'
import { toTrendData } from '@/api/systemMonitorMetrics'

const { t } = useI18n()
const loading = ref(false)
const data = ref<Awaited<ReturnType<typeof fetchMonitorSql>> | null>(null)
const connEl = ref<HTMLElement | null>(null)

const connOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.connectionTrend), (k) => t(`monitor.series.${k}`))
)
useChart(connEl, connOpt)

const columns: DataTableColumns<MonitorSqlStatement> = [
  { title: 'SQL', key: 'sampleSql', ellipsis: { tooltip: true } },
  { title: t('monitor.execCount'), key: 'execCount', width: 100 },
  { title: t('monitor.avgLatency'), key: 'avgLatencyMs', width: 110, render: (r) => `${r.avgLatencyMs} ms` },
  { title: t('monitor.totalLatency'), key: 'totalLatencyMs', width: 120, render: (r) => `${r.totalLatencyMs} ms` },
]

async function load() {
  loading.value = true
  try {
    data.value = await fetchMonitorSql(24, 20)
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <NSpace justify="space-between" align="center" class="toolbar">
      <span class="hint">{{ t('monitor.sqlHint') }}</span>
      <NButton :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
    </NSpace>
    <NSpin :show="loading && !data">
      <template v-if="data">
        <NGrid :cols="4" :x-gap="12" class="summary">
          <NGi><NStatistic :label="t('monitor.poolActive')" :value="data.activeConnections" /></NGi>
          <NGi><NStatistic :label="t('monitor.questionsTotal')" :value="data.questionsTotal" /></NGi>
          <NGi><NStatistic :label="t('monitor.slowQueries')" :value="data.slowQueries" /></NGi>
          <NGi>
            <NStatistic
              :label="t('monitor.poolMax')"
              :value="data.connectionPool?.maxConnections ?? '-'"
            />
          </NGi>
        </NGrid>
        <div class="chart-card"><h4>{{ t('monitor.connectionsTrend') }}</h4><div ref="connEl" class="chart" /></div>
        <div class="chart-card">
          <h4>{{ t('monitor.topSql') }}</h4>
          <NDataTable :columns="columns" :data="data.topStatements || []" :bordered="false" size="small" />
        </div>
      </template>
    </NSpin>
  </div>
</template>

<style scoped>
.toolbar { margin-bottom: 12px; }
.hint { color: var(--n-text-color-3); font-size: 13px; }
.summary { margin-bottom: 16px; }
.chart-card { background: var(--n-color); border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.chart-card h4 { margin: 0 0 8px; font-size: 14px; }
.chart { height: 240px; }
</style>
