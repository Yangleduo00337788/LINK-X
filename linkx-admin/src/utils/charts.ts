import { computed, nextTick, onBeforeUnmount, onMounted, watch, type Ref } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, HeatmapChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  GraphicComponent,
  VisualMapComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsType } from 'echarts/core'
import type { ActivityHeatmap, BreakdownItem, ChartSeries, TrendData } from '@/api/statistics'

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  HeatmapChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  GraphicComponent,
  VisualMapComponent,
  CanvasRenderer,
])

/** LinkX analytics palette — independent of any third-party brand */
export const LX_CHART_COLORS = ['#3D7EFF', '#14B8A6', '#F59E0B', '#8B5CF6', '#EF4444', '#64748B']

function isDarkTheme() {
  return document.documentElement.getAttribute('data-theme') === 'dark'
}

export function chartTheme() {
  const dark = isDarkTheme()
  return {
    dark,
    text: dark ? 'rgba(232,234,237,0.78)' : 'rgba(26,29,36,0.68)',
    textMuted: dark ? 'rgba(168,176,189,0.7)' : 'rgba(92,101,115,0.75)',
    split: dark ? 'rgba(255,255,255,0.06)' : 'rgba(15,23,42,0.06)',
    axis: dark ? 'rgba(255,255,255,0.1)' : 'rgba(15,23,42,0.1)',
    tooltipBg: dark ? 'rgba(23,26,33,0.96)' : 'rgba(255,255,255,0.96)',
    tooltipBorder: dark ? '#2a2f3a' : '#dde2eb',
  }
}

function baseTooltip() {
  const th = chartTheme()
  return {
    backgroundColor: th.tooltipBg,
    borderColor: th.tooltipBorder,
    borderWidth: 1,
    textStyle: { color: th.text, fontSize: 12 },
    extraCssText: 'border-radius:10px;box-shadow:0 8px 24px rgba(0,0,0,0.12);',
  }
}

/** Disable all motion — tab panes + animation caused blank charts */
function noAnim<T extends Record<string, unknown>>(option: T): T {
  return {
    animation: false,
    ...option,
  }
}

export type NamedValue = { key: string; name: string; value: number }

export function buildAreaOption(
  trend: TrendData | null | undefined,
  nameOf: (key: string, fallback: string) => string,
  opts?: { stacked?: boolean; smooth?: boolean }
) {
  const th = chartTheme()
  const stacked = opts?.stacked ?? false
  const smooth = opts?.smooth ?? true
  const series = (trend?.series || []).map((s: ChartSeries, i: number) => ({
    name: nameOf(s.key, s.name),
    type: 'line' as const,
    smooth,
    showSymbol: false,
    stack: stacked ? 'total' : undefined,
    data: s.data || [],
    itemStyle: { color: LX_CHART_COLORS[i % LX_CHART_COLORS.length] },
    lineStyle: { width: 2 },
    areaStyle: {
      opacity: stacked ? 0.55 : 0.12,
      color: LX_CHART_COLORS[i % LX_CHART_COLORS.length],
    },
  }))
  return noAnim({
    color: LX_CHART_COLORS,
    textStyle: { color: th.text, fontFamily: 'IBM Plex Sans, Segoe UI, sans-serif' },
    tooltip: { ...baseTooltip(), trigger: 'axis' as const },
    legend: {
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 6,
      textStyle: { color: th.textMuted, fontSize: 12 },
    },
    grid: { left: 8, right: 12, top: 36, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category' as const,
      boundaryGap: false,
      data: trend?.labels || [],
      axisLabel: { color: th.textMuted, fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { color: th.textMuted, fontSize: 11 },
      splitLine: { lineStyle: { color: th.split, type: 'dashed' as const } },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series,
  })
}

export function buildColumnOption(
  trend: TrendData | null | undefined,
  nameOf: (key: string, fallback: string) => string,
  opts?: { stacked?: boolean }
) {
  const th = chartTheme()
  const stacked = opts?.stacked ?? false
  const series = (trend?.series || []).map((s: ChartSeries, i: number) => ({
    name: nameOf(s.key, s.name),
    type: 'bar' as const,
    stack: stacked ? 'total' : undefined,
    barMaxWidth: stacked ? 28 : 18,
    data: s.data || [],
    itemStyle: {
      color: LX_CHART_COLORS[i % LX_CHART_COLORS.length],
      borderRadius: stacked ? 0 : [4, 4, 0, 0],
    },
  }))
  return noAnim({
    color: LX_CHART_COLORS,
    textStyle: { color: th.text, fontFamily: 'IBM Plex Sans, Segoe UI, sans-serif' },
    tooltip: { ...baseTooltip(), trigger: 'axis' as const },
    legend: {
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 6,
      textStyle: { color: th.textMuted, fontSize: 12 },
    },
    grid: { left: 8, right: 12, top: 36, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category' as const,
      data: trend?.labels || [],
      axisLabel: { color: th.textMuted, fontSize: 11 },
      axisLine: { lineStyle: { color: th.axis } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { color: th.textMuted, fontSize: 11 },
      splitLine: { lineStyle: { color: th.split, type: 'dashed' as const } },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series,
  })
}

export function buildDonutOption(
  items: BreakdownItem[] | NamedValue[] | null | undefined,
  nameOf: (key: string, fallback: string) => string,
  centerLabel?: string
) {
  const th = chartTheme()
  const list = items || []
  const total = list.reduce((s, it) => s + (it.value || 0), 0)
  return noAnim({
    color: LX_CHART_COLORS,
    textStyle: { color: th.text, fontFamily: 'IBM Plex Sans, Segoe UI, sans-serif' },
    tooltip: {
      ...baseTooltip(),
      trigger: 'item' as const,
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'vertical' as const,
      right: 4,
      top: 'middle',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: th.textMuted, fontSize: 12 },
    },
    graphic: centerLabel
      ? [
          {
            type: 'text',
            left: '28%',
            top: '42%',
            style: {
              text: centerLabel,
              textAlign: 'center',
              fill: th.textMuted,
              fontSize: 12,
            },
          },
          {
            type: 'text',
            left: '28%',
            top: '52%',
            style: {
              text: String(total),
              textAlign: 'center',
              fill: th.text,
              fontSize: 22,
              fontWeight: 650,
            },
          },
        ]
      : [],
    series: [
      {
        type: 'pie',
        radius: ['58%', '78%'],
        center: ['32%', '50%'],
        avoidLabelOverlap: true,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderRadius: 4, borderColor: th.dark ? '#171a21' : '#fff', borderWidth: 2 },
        data: list.map((it) => ({
          name: nameOf(it.key, it.name),
          value: it.value,
        })),
      },
    ],
  })
}

export function buildHBarOption(items: NamedValue[], opts?: { colorByIndex?: boolean }) {
  const th = chartTheme()
  const names = items.map((i) => i.name)
  const values = items.map((i) => i.value)
  return noAnim({
    color: LX_CHART_COLORS,
    textStyle: { color: th.text, fontFamily: 'IBM Plex Sans, Segoe UI, sans-serif' },
    tooltip: {
      ...baseTooltip(),
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
    },
    grid: { left: 8, right: 28, top: 8, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { color: th.textMuted, fontSize: 11 },
      splitLine: { lineStyle: { color: th.split, type: 'dashed' as const } },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'category' as const,
      data: names,
      axisLabel: { color: th.textMuted, fontSize: 12 },
      axisLine: { show: false },
      axisTick: { show: false },
      inverse: true,
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 16,
        data: values.map((v, i) => ({
          value: v,
          itemStyle: {
            color:
              opts?.colorByIndex === false
                ? LX_CHART_COLORS[0]
                : LX_CHART_COLORS[i % LX_CHART_COLORS.length],
            borderRadius: [0, 6, 6, 0],
          },
        })),
        label: {
          show: true,
          position: 'right',
          color: th.textMuted,
          fontSize: 11,
        },
      },
    ],
  })
}

/** cells from API are [weekday, hour, count]; ECharts expects [x=hour, y=weekday, value] */
export function buildHeatmapOption(
  heatmap: ActivityHeatmap | null | undefined,
  weekdayLabels: string[],
  hourLabels: string[]
) {
  const th = chartTheme()
  const raw = heatmap?.cells || []
  const data = raw.map((c) => {
    const wd = Number(c[0]) || 0
    const hour = Number(c[1]) || 0
    const value = Number(c[2]) || 0
    return [hour, wd, value]
  })
  const max = Math.max(Number(heatmap?.maxValue) || 0, 1)
  return noAnim({
    textStyle: { color: th.text, fontFamily: 'IBM Plex Sans, Segoe UI, sans-serif' },
    tooltip: {
      ...baseTooltip(),
      position: 'top' as const,
      formatter: (p: { data?: number[]; name?: string }) => {
        const d = p?.data
        if (!d || d.length < 3) return ''
        const hour = d[0]
        const wd = d[1]
        const value = d[2]
        return `${weekdayLabels[wd] || ''} ${hourLabels[hour] || hour}:00<br/>${value}`
      },
    },
    grid: { left: 8, right: 16, top: 12, bottom: 48, containLabel: true },
    xAxis: {
      type: 'category' as const,
      data: hourLabels,
      splitArea: { show: true },
      axisLabel: { color: th.textMuted, fontSize: 10, interval: 0 },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'category' as const,
      data: weekdayLabels,
      splitArea: { show: true },
      axisLabel: { color: th.textMuted, fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    visualMap: {
      min: 0,
      max,
      calculable: true,
      orient: 'horizontal' as const,
      left: 'center',
      bottom: 0,
      itemWidth: 12,
      itemHeight: 100,
      textStyle: { color: th.textMuted, fontSize: 11 },
      inRange: {
        color: th.dark ? ['#1e293b', '#1d4ed8', '#60a5fa'] : ['#eff6ff', '#3D7EFF', '#1e3a8a'],
      },
    },
    series: [
      {
        type: 'heatmap',
        data,
        label: { show: false },
        emphasis: {
          itemStyle: {
            shadowBlur: 6,
            shadowColor: 'rgba(0,0,0,0.25)',
          },
        },
      },
    ],
  })
}

export function buildSparkOption(values: number[], color = LX_CHART_COLORS[0]) {
  const th = chartTheme()
  return noAnim({
    grid: { left: 0, right: 0, top: 4, bottom: 0 },
    xAxis: { type: 'category' as const, show: false, data: values.map((_, i) => i) },
    yAxis: { type: 'value' as const, show: false, min: 'dataMin' as const },
    tooltip: { show: false },
    series: [
      {
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: values,
        lineStyle: { width: 1.5, color },
        areaStyle: { opacity: 0.22, color },
        itemStyle: { color },
      },
    ],
    textStyle: { color: th.text },
  })
}

/**
 * Stable chart binding for tabbed layouts:
 * - never paint while container is hidden (0 size)
 * - on refresh: dispose + re-init after pane is visible
 */
export function useChart(
  elRef: Ref<HTMLElement | null>,
  option: Ref<unknown>,
  opts?: { onClick?: (params: { name?: string; dataIndex?: number }) => void }
) {
  let chart: EChartsType | null = null

  function isReady() {
    const el = elRef.value
    return !!el && el.clientWidth > 0 && el.clientHeight > 0
  }

  function dispose() {
    if (!chart) return
    chart.dispose()
    chart = null
  }

  function paint() {
    const el = elRef.value
    if (!el || !isReady()) return false
    const opt = option.value
    if (!opt) return false

    dispose()
    chart = echarts.init(el)
    chart.setOption(opt as Record<string, unknown>, true)
    if (opts?.onClick) {
      chart.on('click', (params) => {
        opts.onClick?.({
          name: typeof params.name === 'string' ? params.name : undefined,
          dataIndex: typeof params.dataIndex === 'number' ? params.dataIndex : undefined,
        })
      })
    }
    chart.resize()
    return true
  }

  function refresh() {
    const tryPaint = (attempt = 0) => {
      if (paint()) return
      if (attempt >= 20) return
      window.setTimeout(() => tryPaint(attempt + 1), 16)
    }
    void nextTick(() => tryPaint(0))
  }

  function resize() {
    if (chart && isReady()) chart.resize()
  }

  onMounted(() => {
    refresh()
    window.addEventListener('resize', resize)
  })

  watch(
    option,
    () => {
      if (isReady()) refresh()
    },
    { deep: true }
  )

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    dispose()
  })

  return { refresh, resize }
}

export function useDaysOptions(t: (key: string) => string) {
  return computed(() => [
    { label: t('statistics.days7'), value: 7 },
    { label: t('statistics.days14'), value: 14 },
    { label: t('statistics.days30'), value: 30 },
  ])
}

export function seriesValues(trend: TrendData | null | undefined, key: string): number[] {
  const s = trend?.series?.find((x) => x.key === key)
  return s?.data?.map((n) => Number(n) || 0) || []
}
