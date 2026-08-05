<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  NButton,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  useMessage,
} from 'naive-ui'
import {
  listBiMetrics,
  queryBi,
  toTrendData,
  type BiMetric,
  type BiQueryResult,
} from '@/api/bi'
import {
  buildAreaOption,
  buildColumnOption,
  useChart,
  useDaysOptions,
} from '@/utils/charts'

const { t, locale } = useI18n()
const router = useRouter()
const message = useMessage()

const loading = ref(false)
const metrics = ref<BiMetric[]>([])
const result = ref<BiQueryResult | null>(null)

const metric = ref('new_users')
const dimension = ref('none')
const days = ref(14)
const compare = ref(true)

const daysOptions = useDaysOptions((k) => t(k))

const metricOptions = computed(() =>
  metrics.value.map((m) => ({ label: m.name, value: m.key }))
)

const currentMetric = computed(() => metrics.value.find((m) => m.key === metric.value))

const dimensionOptions = computed(() => {
  void locale.value
  const dims = currentMetric.value?.dimensions || ['none']
  return dims.map((d) => ({
    label: d === 'none' ? t('biAnalytics.dimensionNone') : t(`biAnalytics.dimension_${d}`, d),
    value: d,
  }))
})

const deltaTag = computed(() => {
  const pct = result.value?.compareTotalDeltaPct
  if (pct == null) return null
  const type = pct > 0 ? 'success' : pct < 0 ? 'error' : 'default'
  const sign = pct > 0 ? '+' : ''
  return { type, text: `${sign}${pct}%` }
})

function seriesName(key: string, fallback: string) {
  void locale.value
  const map: Record<string, string> = {
    current: t('biAnalytics.seriesCurrent'),
    previous: t('biAnalytics.seriesPrevious'),
    new_users: t('biAnalytics.metricNewUsers'),
    logins: t('biAnalytics.metricLogins'),
    messages: t('biAnalytics.metricMessages'),
    feedback: t('biAnalytics.metricFeedback'),
    risk_events: t('biAnalytics.metricRiskEvents'),
    reviews: t('biAnalytics.metricReviews'),
  }
  return map[key] || fallback
}

const chartOpt = computed(() => {
  if (!result.value) return null
  if (result.value.dimension !== 'none' && result.value.breakdown?.length) {
    const labels = result.value.breakdown.map((b) => b.name)
    const data = result.value.breakdown.map((b) => b.value)
    return buildColumnOption(
      { labels, series: [{ key: 'current', name: t('biAnalytics.seriesCurrent'), data }] },
      seriesName
    )
  }
  return buildAreaOption(toTrendData(result.value), seriesName)
})

const chartEl = ref<HTMLElement | null>(null)
useChart(chartEl, chartOpt)

async function loadMetrics() {
  metrics.value = await listBiMetrics()
  if (!metrics.value.some((m) => m.key === metric.value) && metrics.value.length) {
    metric.value = metrics.value[0].key
  }
}

async function runQuery() {
  loading.value = true
  try {
    result.value = await queryBi({
      metric: metric.value,
      dimension: dimension.value,
      days: days.value,
      comparePrevious: compare.value,
    })
  } catch (e) {
    message.error((e as Error).message || t('common.requestFailed'))
  } finally {
    loading.value = false
  }
}

function drillDown() {
  const target = result.value?.drillTarget
  if (!target?.route) return
  void router.push({ path: target.route, query: target.query })
}

watch([metric], () => {
  const dims = currentMetric.value?.dimensions || ['none']
  if (!dims.includes(dimension.value)) {
    dimension.value = 'none'
  }
})

watch([metric, dimension, days, compare], () => {
  void runQuery()
})

onMounted(async () => {
  await loadMetrics()
  await runQuery()
})
</script>

<template>
  <div class="page bi-page">
    <div class="page-shell">
      <div class="page-head">
        <div>
          <h1 class="page-title">{{ t('biAnalytics.title') }}</h1>
          <p class="page-desc">{{ t('biAnalytics.subtitle') }}</p>
        </div>
        <NSpace>
          <NSelect
            v-model:value="metric"
            :options="metricOptions"
            size="small"
            style="width: 160px"
            :consistent-menu-width="false"
          />
          <NSelect
            v-model:value="dimension"
            :options="dimensionOptions"
            size="small"
            style="width: 140px"
            :consistent-menu-width="false"
          />
          <NSelect
            v-model:value="days"
            :options="daysOptions"
            size="small"
            style="width: 132px"
            :consistent-menu-width="false"
          />
          <span class="compare-label">{{ t('biAnalytics.comparePrevious') }}</span>
          <NSwitch v-model:value="compare" size="small" />
          <NTag v-if="deltaTag" :type="deltaTag.type as 'success' | 'error' | 'default'" size="small">
            {{ t('biAnalytics.deltaPct') }} {{ deltaTag.text }}
          </NTag>
          <NButton
            v-if="result?.drillTarget?.route"
            size="small"
            type="primary"
            tertiary
            @click="drillDown"
          >
            {{ t('biAnalytics.drillDown') }}
          </NButton>
        </NSpace>
      </div>

      <NSpin :show="loading">
        <div class="chart-panel">
          <div ref="chartEl" class="chart-box tall" />
        </div>
      </NSpin>
    </div>
  </div>
</template>

<style scoped>
.bi-page .page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.compare-label {
  font-size: 13px;
  color: var(--n-text-color-3);
  align-self: center;
}

.chart-box.tall {
  min-height: 420px;
}
</style>
