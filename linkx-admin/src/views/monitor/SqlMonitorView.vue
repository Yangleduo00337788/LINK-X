<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
  NButton,
  NDataTable,
  NDivider,
  NEmpty,
  NGi,
  NGrid,
  NInput,
  NSpace,
  NSpin,
  NStatistic,
  type DataTableColumns,
} from 'naive-ui'
import { fetchSystemMonitorTables, type SystemTableStat } from '@/api/systemMonitor'
import { fetchMonitorSql, formatMonitorBytes, isSparseMonitorTrend, toTrendData, type MonitorSqlStatement } from '@/api/systemMonitorMetrics'
import { buildAreaOption, useChart } from '@/utils/charts'
import { formatTime } from '@/utils/format'

const TABLES_LOAD_TIMEOUT_MS = 45_000

const { t, locale } = useI18n()
const loading = ref(false)
const tablesLoading = ref(false)
const tablesRefreshing = ref(false)
const error = ref<string | null>(null)
const tablesError = ref<string | null>(null)
const data = ref<Awaited<ReturnType<typeof fetchMonitorSql>> | null>(null)
const tablesData = shallowRef<Awaited<ReturnType<typeof fetchSystemMonitorTables>> | null>(null)
const tableKeyword = ref('')

function asMonitorNumber(value: unknown) {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}
const connEl = ref<HTMLElement | null>(null)

const connOpt = computed(() =>
  buildAreaOption(toTrendData(data.value?.connectionTrend), (k) => t(`monitor.series.${k}`))
)
const connChart = useChart(connEl, connOpt)

const poolTotal = computed(() => data.value?.connectionPool?.totalConnections ?? 0)
const poolIdle = computed(() => data.value?.connectionPool?.idleConnections ?? 0)
const sparseTrend = computed(() => isSparseMonitorTrend(data.value?.connectionTrend))

const allTables = computed(() => tablesData.value?.tableList ?? tablesData.value?.tables ?? [])

const filteredTables = computed(() => {
  const list = allTables.value
  const q = tableKeyword.value.trim().toLowerCase()
  if (!q) return list
  return list.filter((row) => {
    const name = (row.tableName ?? '').toLowerCase()
    const comment = (row.tableComment ?? '').toLowerCase()
    return name.includes(q) || comment.includes(q)
  })
})

const tablesSchemaBad = computed(() => tablesData.value?.schemaName === 'unknown')

const tablesListMismatch = computed(() => {
  const expected = tablesData.value?.storage?.tableCount ?? 0
  return expected > 0 && allTables.value.length === 0
})

const tablesEmptyDescription = computed(() => {
  if (tableKeyword.value.trim()) return t('systemMonitor.tablesNoMatch')
  if (tablesListMismatch.value) return t('systemMonitor.tablesListMismatch')
  return t('common.none')
})

const sqlColumns = computed<DataTableColumns<MonitorSqlStatement>>(() => [
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

const tableColumns = computed<DataTableColumns<SystemTableStat>>(() => {
  void locale.value
  return [
    { title: t('systemMonitor.tableName'), key: 'tableName', width: 200, ellipsis: { tooltip: true } },
    {
      title: t('systemMonitor.engine'),
      key: 'engine',
      width: 90,
      render: (row) => row.engine || '-',
    },
    {
      title: t('systemMonitor.rowCount'),
      key: 'rowCount',
      width: 120,
      render: (row) => asMonitorNumber(row.rowCount).toLocaleString(),
    },
    {
      title: t('systemMonitor.dataSize'),
      key: 'dataBytes',
      width: 110,
      render: (row) => formatMonitorBytes(asMonitorNumber(row.dataBytes)),
    },
    {
      title: t('systemMonitor.indexSize'),
      key: 'indexBytes',
      width: 110,
      render: (row) => formatMonitorBytes(asMonitorNumber(row.indexBytes)),
    },
    {
      title: t('systemMonitor.totalSize'),
      key: 'totalBytes',
      width: 110,
      render: (row) => formatMonitorBytes(asMonitorNumber(row.totalBytes)),
    },
    {
      title: t('common.description'),
      key: 'tableComment',
      minWidth: 140,
      ellipsis: { tooltip: true },
      render: (row) => row.tableComment || '-',
    },
    {
      title: t('common.updateTime'),
      key: 'updateTime',
      width: 170,
      render: (row) => (row.updateTime ? formatTime(row.updateTime) : '-'),
    },
  ]
})

function refreshCharts() {
  connChart.refresh()
}

async function loadSql() {
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

function withTimeout<T>(promise: Promise<T>, ms: number, message: string) {
  return new Promise<T>((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new Error(message)), ms)
    promise
      .then((value) => {
        window.clearTimeout(timer)
        resolve(value)
      })
      .catch((err) => {
        window.clearTimeout(timer)
        reject(err)
      })
  })
}

async function loadTables(refresh = false) {
  if (tablesLoading.value || tablesRefreshing.value) return
  if (tablesData.value) {
    tablesRefreshing.value = true
  } else {
    tablesLoading.value = true
  }
  tablesError.value = null
  try {
    const result = await withTimeout(
      fetchSystemMonitorTables(refresh),
      TABLES_LOAD_TIMEOUT_MS,
      t('systemMonitor.tablesLoadTimeout')
    )
    tablesData.value = result ?? null
    if (!tablesData.value) {
      tablesError.value = t('systemMonitor.tablesLoadEmpty')
    }
  } catch (e) {
    tablesError.value = e instanceof Error ? e.message : t('common.requestFailed')
  } finally {
    tablesLoading.value = false
    tablesRefreshing.value = false
  }
}

onMounted(() => void loadSql())
</script>

<template>
  <div class="page">
    <NSpace justify="space-between" align="center" class="toolbar">
      <span class="hint">{{ t('monitor.sqlHint') }}</span>
      <NButton :loading="loading" @click="loadSql">{{ t('common.refresh') }}</NButton>
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
            :columns="sqlColumns"
            :data="data.topStatements"
            :bordered="false"
            size="small"
          />
        </div>
      </template>
    </NSpin>

    <NDivider />

    <div class="storage-section">
      <NSpace justify="space-between" align="center" class="storage-toolbar">
        <div>
          <h4 class="section-title">{{ t('systemMonitor.sectionStorage') }}</h4>
          <p class="hint">{{ t('systemMonitor.tablesLoadHint') }}</p>
        </div>
        <NSpace v-if="tablesData">
          <NInput
            v-model:value="tableKeyword"
            clearable
            :placeholder="t('systemMonitor.tableSearch')"
            style="width: 220px"
          />
          <NButton :disabled="tablesRefreshing" @click="loadTables(true)">
            {{ tablesRefreshing ? t('systemMonitor.refreshingTables') : t('systemMonitor.refreshTables') }}
          </NButton>
        </NSpace>
      </NSpace>

      <NEmpty
        v-if="!tablesData && !tablesLoading && !tablesError"
        :description="t('systemMonitor.tablesLoadHint')"
        class="empty"
      >
        <template #extra>
          <NButton type="primary" :disabled="tablesLoading" @click="loadTables(false)">
            {{ tablesLoading ? t('systemMonitor.loadingTables') : t('systemMonitor.loadTables') }}
          </NButton>
        </template>
      </NEmpty>

      <p v-else-if="tablesLoading" class="loading-hint">{{ t('systemMonitor.loadingTables') }}</p>

      <NEmpty v-else-if="tablesError" :description="tablesError" class="empty">
        <template #extra>
          <NButton :disabled="tablesRefreshing" @click="loadTables(true)">{{ t('common.refresh') }}</NButton>
        </template>
      </NEmpty>

      <template v-else-if="tablesData">
        <NAlert
          v-if="tablesSchemaBad"
          type="warning"
          :bordered="false"
          class="schema-warn"
          :title="t('systemMonitor.tablesSchemaUnknown')"
        />
        <NAlert
          v-if="tablesListMismatch"
          type="warning"
          :bordered="false"
          class="schema-warn"
          :title="t('systemMonitor.tablesListMismatch')"
        />
        <p v-else-if="tablesData.refreshedAt" class="meta">
          {{ t('systemMonitor.refreshedAt') }} {{ formatTime(tablesData.refreshedAt) }}
          <span v-if="tablesData.schemaName"> · {{ t('systemMonitor.schema') }} {{ tablesData.schemaName }}</span>
          <span v-if="allTables.length"> · {{ t('systemMonitor.tablesShown', { shown: filteredTables.length, total: allTables.length }) }}</span>
          <span v-if="tablesData.cached">{{ t('systemMonitor.tablesCached') }}</span>
        </p>
        <NGrid :cols="4" :x-gap="12" class="summary">
          <NGi>
            <NStatistic
              :label="t('systemMonitor.tableCount')"
              :value="tablesData.storage?.tableCount ?? 0"
            />
          </NGi>
          <NGi>
            <NStatistic
              :label="t('systemMonitor.storageTotal')"
              :value="formatMonitorBytes(tablesData.storage?.totalBytes)"
            />
          </NGi>
          <NGi>
            <NStatistic
              :label="t('systemMonitor.storageData')"
              :value="formatMonitorBytes(tablesData.storage?.dataBytes)"
            />
          </NGi>
          <NGi>
            <NStatistic
              :label="t('systemMonitor.approxRows')"
              :value="(tablesData.storage?.approximateRowCount ?? 0).toLocaleString()"
            />
          </NGi>
        </NGrid>
        <div class="page-card chart-card">
          <NEmpty
            v-if="!filteredTables.length"
            :description="tablesEmptyDescription"
            class="table-empty"
          >
            <template v-if="tableKeyword.trim()" #extra>
              <NButton @click="tableKeyword = ''">{{ t('systemMonitor.tablesClearSearch') }}</NButton>
            </template>
            <template v-else-if="tablesListMismatch" #extra>
              <NButton :disabled="tablesRefreshing" @click="loadTables(true)">{{ t('common.refresh') }}</NButton>
            </template>
          </NEmpty>
          <NDataTable
            v-else
            :columns="tableColumns"
            :data="filteredTables"
            :row-key="(row) => row.tableName"
            :bordered="false"
            size="small"
            :scroll-x="1000"
            :max-height="480"
            virtual-scroll
          />
        </div>
      </template>
    </div>
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
.trend-hint,
.meta,
.loading-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--lx-text-3);
}
.loading-hint {
  padding: 24px 0;
  text-align: center;
}
.chart-card {
  padding: 12px;
  margin-bottom: 12px;
}
.chart-card h4,
.section-title {
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
.storage-section {
  margin-top: 4px;
  min-height: 120px;
}
.storage-toolbar {
  margin-bottom: 12px;
}
.schema-warn {
  margin-bottom: 12px;
}
</style>
