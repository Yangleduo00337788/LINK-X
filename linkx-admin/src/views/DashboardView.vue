<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NCarousel, NGi, NGrid, NIcon, NNumberAnimation, NSpin, NStatistic } from 'naive-ui'
import {
  PeopleOutline,
  PulseOutline,
  PhonePortraitOutline,
  ChatbubbleEllipsesOutline,
  DocumentTextOutline,
  WarningOutline,
} from '@vicons/ionicons5'
import { fetchDashboardSummary, type DashboardSummary } from '@/api/dashboard'

const { t, locale } = useI18n()
const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)

const cards = computed(() => {
  void locale.value
  return [
    { key: 'totalUsers' as const, label: t('dashboard.totalUsers'), icon: PeopleOutline },
    { key: 'activeUsers' as const, label: t('dashboard.activeUsers'), icon: PulseOutline },
    { key: 'onlineDevices' as const, label: t('dashboard.onlineDevices'), icon: PhonePortraitOutline },
    { key: 'pendingFeedback' as const, label: t('dashboard.pendingFeedback'), icon: ChatbubbleEllipsesOutline },
    { key: 'pendingReviews' as const, label: t('dashboard.pendingReviews'), icon: DocumentTextOutline },
    { key: 'riskEvents' as const, label: t('dashboard.riskEvents'), icon: WarningOutline },
  ]
})

const banners = computed(() => {
  void locale.value
  return [
    {
      title: t('dashboard.banner1Title'),
      desc: t('dashboard.banner1Desc'),
      tone: 'tone-a',
    },
    {
      title: t('dashboard.banner2Title'),
      desc: t('dashboard.banner2Desc'),
      tone: 'tone-b',
    },
    {
      title: t('dashboard.banner3Title'),
      desc: t('dashboard.banner3Desc'),
      tone: 'tone-c',
    },
  ]
})

onMounted(async () => {
  loading.value = true
  try {
    summary.value = await fetchDashboardSummary()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="page-card carousel-wrap">
      <NCarousel autoplay :interval="4500" show-arrow draggable style="height: 168px">
        <div v-for="(b, i) in banners" :key="i" class="banner" :class="b.tone">
          <div class="banner-title">{{ b.title }}</div>
          <div class="banner-desc">{{ b.desc }}</div>
        </div>
      </NCarousel>
    </div>

    <NSpin :show="loading">
      <NGrid cols="1 s:2 m:3" responsive="screen" :x-gap="16" :y-gap="16">
        <NGi v-for="card in cards" :key="card.key">
          <div class="page-card stat-card">
            <div class="stat-icon">
              <NIcon :size="22" :component="card.icon" />
            </div>
            <NStatistic :label="card.label">
              <NNumberAnimation
                :from="0"
                :to="summary?.[card.key] ?? 0"
                :active="!!summary"
                :precision="0"
                show-separator
              />
            </NStatistic>
          </div>
        </NGi>
      </NGrid>
    </NSpin>
  </div>
</template>

<style scoped>
.carousel-wrap {
  padding: 0 !important;
  overflow: hidden;
}
.banner {
  height: 168px;
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  color: #f5f7fb;
}
.banner-title {
  font-size: 22px;
  font-weight: 650;
  letter-spacing: 0.02em;
}
.banner-desc {
  font-size: 14px;
  opacity: 0.88;
  max-width: 520px;
  line-height: 1.5;
}
.tone-a {
  background: linear-gradient(135deg, #3d6fd4 0%, #5b8def 48%, #7aa3f5 100%);
}
.tone-b {
  background: linear-gradient(135deg, #1f6f6a 0%, #2f9e94 50%, #58c4b8 100%);
}
.tone-c {
  background: linear-gradient(135deg, #5a4a8a 0%, #6f5fb0 50%, #8f7fd4 100%);
}
.stat-card {
  min-height: 120px;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 20px !important;
}
.stat-icon {
  position: absolute;
  right: 18px;
  top: 18px;
  width: 40px;
  height: 40px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: var(--lx-stat-accent);
  background: rgba(91, 141, 239, 0.12);
}
</style>
