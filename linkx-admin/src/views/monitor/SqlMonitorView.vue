<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NEmpty,
  NGi,
  NGrid,
  NSpace,
  NSpin,
  NStatistic,
  type DataTableColumns,
} from 'naive-ui'
import { fetchMonitorSql, isSparseMonitorTrend, toTrendData, type MonitorSqlStatement } from '@/api/systemMonitorMetrics'
import { buildAreaOption, useChart } from '@/utils/charts'

const { t } = useI18n()
const loading = ref(false)
const error = ref<string | null>(null)
const data = ref<Awaited<ReturnType<typeof fetchMonitorSql>> | null>(null)
const connEl = ref<HTMLElement | null>(null)

const connOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.connectionTrend), (k) => t(`monitor.series.${k}`))
)
const connChart = useChart(connEl, connOpt)

const poolTotal = computed(() => data.value?.connectionPool?.totalConnections ?? 0)
const poolIdle = computed(() => data.value?.connectionPool?.idleConnections ?? 0)
const sparseTrend = computed(() => isSparseMonitorTrend(data.value?.connectionTrend))

const columns = computed<DataTableColumns<MonitorSqlStatement>>(() => [
  { title: 'SQL', key: 'sampleSql', ellipsis: { tooltip: true } },
  { title: t('monitor.execCount'), key: 'execCount', width: 100 },
  {
    title: t('monitor.avgLatency'),
    key: 'avgLatencyMs',
    width: 110,
    render: (r) => `${r.avgLatencyMs} ms`,
  },
  {
    title: t('monitor.totalLatency'),
    key: 'totalLatencyMs',
    width: 120,
    render: (r) => `${r.totalLatencyMs} ms`,
  },
])

function refreshCharts() {
  connChart.refresh()
}

async function load() {
  loading.value = true
  error.value = null
  try {
    data.value = await fetchMonitorSql(24, 20)
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
      <span class="hint">{{ t('monitor.sqlHint') }}</span>
      <NButton :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
    </NSpace>
    <NSpin :show="loading && !data && !error">
      <NEmpty v-if="error && !data" :description="error" class="empty" />
      <template v-else-if="data">
        <NGrid :cols="6" :x-gap="12" class="summary">
          <NGi><NStatistic :label="t('monitor.poolActive')" :value="data.activeConnections" /></NGi>
          <NGi><NStatistic :label="t('monitor.poolIdle')" :value="poolIdle" /></NGi>
          <NGi><NStatistic :label="t('monitor.poolTotal')" :value="poolTotal" /></NGi>
          <NGi>
            <NStatistic
              :label="t('monitor.poolMax')"
              :value="data.connectionPool?.maxConnections ?? '-'"
            />
          </NGi>
          <NGi><NStatistic :label="t('monitor.questionsTotal')" :value="data.questionsTotal" /></NGi>
          <NGi><NStatistic :label="t('monitor.slowQueries')" :value="data.slowQueries" /></NGi>
        </NGrid>
        <p v-if="sparseTrend && poolTotal > 0" class="trend-hint">{{ t('monitor.sqlTrendSparseHint') }}</p>
        <div class="page-card chart-card">
          <h4>{{ t('monitor.poolTrend') }}</h4>
          <div ref="connEl" class="chart" />
        </div>
        <div class="page-card chart-card">
          <h4>{{ t('monitor.topSql') }}</h4>
          <NEmpty
            v-if="!data.topStatements?.length"
            :description="t('common.none')"
            class="table-empty"
          />
          <NDataTable
            v-else
            :columns="columns"
            :data="data.topStatements"
            :bordered="false"
            size="small"
          />
        </div>
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
  margin-bottom: 12px;
}
.chart-card h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
.chart {
  height: 240px;
}
.empty,
.table-empty {
  padding: 32px 0;
}
</style>
