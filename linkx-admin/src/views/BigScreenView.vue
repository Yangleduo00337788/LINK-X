<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { NButton, NIcon, NSpin, NTag } from 'naive-ui'
import {
  ArrowBackOutline,
  ChatboxOutline,
  ChatbubblesOutline,
  DocumentTextOutline,
  LogInOutline,
  PeopleOutline,
  PhonePortraitOutline,
  PulseOutline,
  RefreshOutline,
  TvOutline,
  WarningOutline,
} from '@vicons/ionicons5'
import type { Component } from 'vue'
import { fetchBigScreenData, type BigScreenData } from '@/api/bi'
import { onAdminRealtimeEvent } from '@/api/realtime'
import FlipNumber from '@/components/FlipNumber.vue'
import KpiSparkline from '@/components/KpiSparkline.vue'
import { useFullscreen } from '@/composables/useFullscreen'
import { usePreferencesStore } from '@/stores/preferences'
import { formatTime } from '@/utils/format'
import { chartColors } from '@/utils/charts'

const { t, locale } = useI18n()
const router = useRouter()
const { isFullscreen, toggle } = useFullscreen()
const { primaryColor, theme } = storeToRefs(usePreferencesStore())

const loading = ref(false)
const data = ref<BigScreenData | null>(null)
const liveTickers = ref<NonNullable<BigScreenData['tickers']>>([])
const now = ref(new Date())

const POLL_MS = 30_000
let pollTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null
let offRealtime: (() => void) | null = null

const palette = computed(() => {
  void primaryColor.value
  return chartColors()
})

const clockText = computed(() =>
  now.value.toLocaleString(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
)

type KpiDef = {
  key: string
  label: string
  icon: Component
  color: string
  sparkColor: string
  value: number
  trend: number[]
  trendDelta: number | null
}

function resolveSparkColor(color: string, fallback: string) {
  if (color.startsWith('#')) return color
  if (color.startsWith('var(')) {
    const varName = color.slice(4, -1).trim()
    const resolved = getComputedStyle(document.documentElement).getPropertyValue(varName).trim()
    return resolved || fallback
  }
  return fallback
}

function calcTrendDelta(values: number[]): number | null {
  if (!values || values.length < 2) return null
  const prev = values[values.length - 2] ?? 0
  const last = values[values.length - 1] ?? 0
  if (prev === 0) return last > 0 ? 100 : 0
  return Math.round(((last - prev) / prev) * 1000) / 10
}

function formatTrendDelta(delta: number) {
  const sign = delta > 0 ? '+' : ''
  return `${sign}${delta}%`
}

function trendDeltaClass(delta: number) {
  if (delta > 0) return 'up'
  if (delta < 0) return 'down'
  return 'flat'
}

const kpis = computed<KpiDef[]>(() => {
  const colors = palette.value
  const d = data.value
  const trends = d?.kpiTrends || {}
  const defs = [
    { key: 'totalUsers', label: t('bigScreen.totalUsers'), icon: PeopleOutline, color: colors[0] },
    { key: 'dau', label: t('bigScreen.dau'), icon: PulseOutline, color: colors[1] },
    { key: 'onlineDevices', label: t('bigScreen.onlineDevices'), icon: PhonePortraitOutline, color: colors[2] },
    { key: 'todayMessages', label: t('bigScreen.todayMessages'), icon: ChatbubblesOutline, color: colors[3] },
    { key: 'todayLogins', label: t('bigScreen.todayLogins'), icon: LogInOutline, color: colors[4] ?? colors[0] },
    { key: 'todayRiskEvents', label: t('bigScreen.todayRiskEvents'), icon: WarningOutline, color: 'var(--lx-oa-red)' },
    { key: 'pendingFeedback', label: t('bigScreen.pendingFeedback'), icon: ChatboxOutline, color: colors[5] ?? colors[1] },
    { key: 'pendingReviews', label: t('bigScreen.pendingReviews'), icon: DocumentTextOutline, color: 'var(--lx-accent-2)' },
  ]
  const values: Record<string, number> = {
    totalUsers: d?.totalUsers ?? 0,
    dau: d?.dau ?? 0,
    onlineDevices: d?.onlineDevices ?? 0,
    todayMessages: d?.todayMessages ?? 0,
    todayLogins: d?.todayLogins ?? 0,
    todayRiskEvents: d?.todayRiskEvents ?? 0,
    pendingFeedback: d?.pendingFeedback ?? 0,
    pendingReviews: d?.pendingReviews ?? 0,
  }
  return defs.map((item) => {
    const trend = (trends[item.key] || []).map((v) => Number(v) || 0)
    return {
      ...item,
      sparkColor: resolveSparkColor(item.color, colors[0]),
      value: values[item.key] ?? 0,
      trend,
      trendDelta: calcTrendDelta(trend),
    }
  })
})

const tickers = computed(() => {
  const merged = [...liveTickers.value, ...(data.value?.tickers || [])]
  const seen = new Set<string>()
  return merged.filter((item) => {
    const key = `${item.type}-${item.relatedId}-${item.ts}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  }).slice(0, 20)
})

const marqueeItems = computed(() => {
  const list = tickers.value
  if (!list.length) return []
  return list.length < 6 ? [...list, ...list] : [...list, ...list.slice(0, 6)]
})

function tickerTypeLabel(type?: string) {
  if (type === 'risk_event') return t('bigScreen.typeRisk')
  if (type === 'feedback') return t('bigScreen.typeFeedback')
  return t('bigScreen.typeEvent')
}

function tickerTagType(type?: string): 'error' | 'info' | 'default' {
  if (type === 'risk_event') return 'error'
  if (type === 'feedback') return 'info'
  return 'default'
}

async function refresh() {
  loading.value = true
  try {
    data.value = await fetchBigScreenData()
  } finally {
    loading.value = false
  }
}

function pushLiveTicker(payload: { type?: string; title?: string; relatedId?: string; ts?: number }) {
  if (!payload.title) return
  liveTickers.value.unshift({
    type: payload.type,
    title: payload.title,
    relatedId: payload.relatedId ? Number(payload.relatedId) : undefined,
    ts: payload.ts ?? Date.now(),
  })
  if (liveTickers.value.length > 30) {
    liveTickers.value = liveTickers.value.slice(0, 30)
  }
}

onMounted(() => {
  void refresh()
  pollTimer = setInterval(() => void refresh(), POLL_MS)
  clockTimer = setInterval(() => {
    now.value = new Date()
  }, 1000)
  offRealtime = onAdminRealtimeEvent((evt) => pushLiveTicker(evt))
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (clockTimer) clearInterval(clockTimer)
  offRealtime?.()
})
</script>

<template>
  <div
    class="big-screen"
    :class="[`theme-${theme}`, { fullscreen: isFullscreen }]"
    :style="{ '--bs-accent': primaryColor }"
  >
    <div class="bs-bg-grid" aria-hidden="true" />
    <div class="bs-bg-glow" aria-hidden="true" />

    <header class="bs-header">
      <div class="bs-brand">
        <div class="bs-brand-icon">
          <NIcon :component="TvOutline" :size="22" />
        </div>
        <div>
          <h1>{{ t('bigScreen.title') }}</h1>
          <p class="bs-subtitle">{{ t('bigScreen.subtitle') }}</p>
        </div>
        <span class="live-badge">
          <span class="live-dot" />
          {{ t('bigScreen.live') }}
        </span>
      </div>

      <div class="bs-meta">
        <span class="bs-clock">{{ clockText }}</span>
        <NTag v-if="data?.refreshedAt" size="small" round :bordered="false" type="info" class="bs-refresh-tag">
          {{ t('bigScreen.refreshedAt') }} {{ formatTime(data.refreshedAt) }}
        </NTag>
      </div>

      <div class="bs-actions">
        <NButton quaternary circle @click="router.push('/admin/statistics')">
          <template #icon><NIcon :component="ArrowBackOutline" /></template>
        </NButton>
        <NButton quaternary circle :loading="loading" @click="refresh">
          <template #icon><NIcon :component="RefreshOutline" /></template>
        </NButton>
        <NButton type="primary" size="small" @click="toggle">
          {{ isFullscreen ? t('layout.exitFullscreen') : t('layout.fullscreen') }}
        </NButton>
      </div>
    </header>

    <div v-if="marqueeItems.length" class="marquee-bar">
      <div class="marquee-track">
        <div
          v-for="(item, idx) in marqueeItems"
          :key="`${item.type}-${item.relatedId}-${item.ts}-${idx}`"
          class="marquee-chip"
        >
          <NTag :type="tickerTagType(item.type)" size="tiny" round :bordered="false">
            {{ tickerTypeLabel(item.type) }}
          </NTag>
          <span class="marquee-text">{{ item.title }}</span>
        </div>
      </div>
    </div>

    <NSpin :show="loading && !data">
      <section class="kpi-grid">
        <article
          v-for="item in kpis"
          :key="item.key"
          class="kpi-tile"
          :style="{ '--kpi-accent': item.color }"
        >
          <div class="kpi-top">
            <div class="kpi-icon-wrap">
              <NIcon :component="item.icon" :size="20" />
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ item.label }}</div>
              <div class="kpi-value">
                <FlipNumber :value="item.value" />
              </div>
            </div>
            <div v-if="item.trendDelta != null" class="kpi-delta-wrap">
              <span class="kpi-delta" :class="trendDeltaClass(item.trendDelta)">
                {{ formatTrendDelta(item.trendDelta) }}
              </span>
              <span class="kpi-delta-hint">{{ t('bigScreen.trendDay') }}</span>
            </div>
          </div>
          <div class="kpi-spark-wrap">
            <span class="kpi-spark-label">{{ t('bigScreen.trend7d') }}</span>
            <KpiSparkline :values="item.trend" :color="item.sparkColor" />
          </div>
          <div class="kpi-shine" aria-hidden="true" />
        </article>
      </section>

      <section class="ticker-panel">
        <div class="ticker-head">
          <div class="ticker-title">{{ t('bigScreen.liveFeed') }}</div>
          <span class="ticker-count">{{ t('bigScreen.eventCount', { n: tickers.length }) }}</span>
        </div>
        <div class="ticker-track">
          <div v-if="!tickers.length" class="ticker-empty">
            <NIcon :component="PulseOutline" :size="28" class="ticker-empty-icon" />
            <span>{{ t('bigScreen.noEvents') }}</span>
          </div>
          <div
            v-for="(item, idx) in tickers"
            :key="`${item.type}-${item.relatedId}-${item.ts}-${idx}`"
            class="ticker-item"
          >
            <div class="ticker-rail" :class="`rail-${item.type || 'event'}`" />
            <NTag :type="tickerTagType(item.type)" size="small" round :bordered="false">
              {{ tickerTypeLabel(item.type) }}
            </NTag>
            <span class="ticker-text">{{ item.title }}</span>
            <span v-if="item.ts" class="ticker-time">{{ formatTime(item.ts) }}</span>
          </div>
        </div>
      </section>
    </NSpin>
  </div>
</template>

<style scoped>
.big-screen {
  --bs-accent: var(--lx-oa-blue);
  --bs-surface: var(--lx-card);
  --bs-surface-2: color-mix(in srgb, var(--lx-card) 88%, var(--bs-accent) 12%);
  --bs-border: color-mix(in srgb, var(--lx-border) 70%, var(--bs-accent) 30%);
  --bs-text-muted: var(--lx-text-2);
  position: relative;
  min-height: calc(100vh - 120px);
  margin: -8px -12px 0;
  padding: 22px 26px 30px;
  color: var(--lx-text);
  background:
    radial-gradient(ellipse 90% 55% at 12% -8%, rgba(var(--lx-primary-rgb), 0.14), transparent 58%),
    radial-gradient(ellipse 70% 45% at 92% 0%, rgba(var(--lx-primary-rgb), 0.08), transparent 52%),
    var(--lx-body);
  overflow: hidden;
  transition: background 0.25s ease, color 0.25s ease;
}

.big-screen.theme-dark {
  background:
    radial-gradient(ellipse 85% 50% at 50% -12%, rgba(var(--lx-primary-rgb), 0.2), transparent 55%),
    linear-gradient(180deg, color-mix(in srgb, var(--lx-card) 35%, #000) 0%, var(--lx-body) 42%, var(--lx-body) 100%);
}

.big-screen.fullscreen {
  min-height: 100vh;
  margin: 0;
  padding: 28px 32px 36px;
}

.bs-bg-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.35;
  background-image:
    linear-gradient(rgba(var(--lx-primary-rgb), 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(var(--lx-primary-rgb), 0.06) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(180deg, #000 0%, transparent 85%);
}

.bs-bg-glow {
  position: absolute;
  top: -120px;
  right: -80px;
  width: 360px;
  height: 360px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(var(--lx-primary-rgb), 0.18), transparent 68%);
  pointer-events: none;
  filter: blur(2px);
}

.bs-header {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 16px 20px;
  margin-bottom: 20px;
}

.bs-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.bs-brand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(135deg, var(--bs-accent), color-mix(in srgb, var(--bs-accent) 65%, #722ed1));
  box-shadow: 0 8px 24px rgba(var(--lx-primary-rgb), 0.28);
}

.bs-brand h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.02em;
  background: linear-gradient(90deg, var(--lx-text), color-mix(in srgb, var(--lx-text) 70%, var(--bs-accent)));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.bs-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--bs-text-muted);
}

.live-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 8px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--bs-accent);
  background: rgba(var(--lx-primary-rgb), 0.1);
  border: 1px solid rgba(var(--lx-primary-rgb), 0.22);
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--bs-accent);
  box-shadow: 0 0 0 0 rgba(var(--lx-primary-rgb), 0.5);
  animation: bs-pulse 1.8s ease-out infinite;
}

@keyframes bs-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(var(--lx-primary-rgb), 0.45);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(var(--lx-primary-rgb), 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(var(--lx-primary-rgb), 0);
  }
}

.bs-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.bs-clock {
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--lx-text);
  letter-spacing: 0.04em;
}

.bs-refresh-tag {
  --n-color: rgba(var(--lx-primary-rgb), 0.1) !important;
}

.bs-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.marquee-bar {
  position: relative;
  z-index: 1;
  margin-bottom: 18px;
  padding: 10px 0;
  border-radius: 12px;
  background: color-mix(in srgb, var(--bs-surface) 92%, transparent);
  border: 1px solid var(--bs-border);
  overflow: hidden;
  box-shadow: var(--lx-card-shadow);
}

.marquee-track {
  display: flex;
  width: max-content;
  gap: 28px;
  padding: 0 16px;
  animation: bs-marquee 42s linear infinite;
}

.marquee-bar:hover .marquee-track {
  animation-play-state: paused;
}

@keyframes bs-marquee {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}

.marquee-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  font-size: 13px;
  color: var(--lx-text);
}

.marquee-text {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.kpi-tile {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 16px 12px;
  border-radius: calc(var(--lx-radius) + 6px);
  background: var(--bs-surface);
  border: 1px solid var(--bs-border);
  box-shadow: var(--lx-card-shadow);
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.kpi-tile::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--kpi-accent), color-mix(in srgb, var(--kpi-accent) 40%, transparent));
}

.kpi-tile:hover {
  transform: translateY(-2px);
  box-shadow: var(--lx-card-shadow-hover);
  border-color: color-mix(in srgb, var(--kpi-accent) 35%, var(--bs-border));
}

.kpi-top {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.kpi-delta-wrap {
  margin-left: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}

.kpi-delta {
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  padding: 2px 8px;
  border-radius: 999px;
}

.kpi-delta.up {
  color: var(--lx-oa-green);
  background: color-mix(in srgb, var(--lx-oa-green) 14%, transparent);
}

.kpi-delta.down {
  color: var(--lx-oa-red);
  background: color-mix(in srgb, var(--lx-oa-red) 14%, transparent);
}

.kpi-delta.flat {
  color: var(--bs-text-muted);
  background: color-mix(in srgb, var(--lx-border) 55%, transparent);
}

.kpi-delta-hint {
  font-size: 10px;
  color: var(--bs-text-muted);
}

.kpi-spark-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kpi-spark-label {
  font-size: 10px;
  color: var(--bs-text-muted);
  letter-spacing: 0.02em;
}

.kpi-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
  color: var(--kpi-accent);
  background: color-mix(in srgb, var(--kpi-accent) 14%, transparent);
}

.kpi-body {
  min-width: 0;
  flex: 1;
}

.kpi-label {
  font-size: 12px;
  color: var(--bs-text-muted);
  margin-bottom: 6px;
  line-height: 1.3;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  color: var(--lx-text);
}

.kpi-shine {
  position: absolute;
  right: -20%;
  bottom: -40%;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--kpi-accent) 18%, transparent), transparent 70%);
  pointer-events: none;
}

.ticker-panel {
  position: relative;
  z-index: 1;
  border: 1px solid var(--bs-border);
  border-radius: calc(var(--lx-radius) + 6px);
  background: var(--bs-surface);
  box-shadow: var(--lx-card-shadow);
  overflow: hidden;
}

.ticker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--lx-border);
  background: var(--lx-panel-head-bg);
}

.ticker-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text);
}

.ticker-count {
  font-size: 12px;
  color: var(--bs-text-muted);
}

.ticker-track {
  max-height: 300px;
  overflow: auto;
}

.ticker-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 16px 11px 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--lx-border) 80%, transparent);
  transition: background 0.15s ease;
}

.ticker-item:hover {
  background: color-mix(in srgb, var(--bs-accent) 6%, var(--bs-surface));
}

.ticker-rail {
  width: 3px;
  align-self: stretch;
  border-radius: 999px;
  flex-shrink: 0;
  background: var(--lx-border);
}

.ticker-rail.risk_event {
  background: var(--lx-oa-red);
}

.ticker-rail.feedback {
  background: var(--bs-accent);
}

.ticker-rail.event {
  background: var(--lx-accent-teal);
}

.ticker-text {
  flex: 1;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ticker-time {
  font-size: 12px;
  color: var(--bs-text-muted);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.ticker-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 24px;
  color: var(--bs-text-muted);
  font-size: 13px;
}

.ticker-empty-icon {
  opacity: 0.45;
  color: var(--bs-accent);
}

@media (max-width: 1200px) {
  .bs-header {
    grid-template-columns: 1fr;
  }

  .bs-meta {
    align-items: flex-start;
  }

  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .big-screen {
    padding: 16px;
  }

  .live-badge {
    display: none;
  }

  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .kpi-value {
    font-size: 24px;
  }
}
</style>
