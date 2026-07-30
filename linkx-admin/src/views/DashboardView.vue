<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NGrid, NGi, NSpin, NStatistic } from 'naive-ui'
import { fetchDashboardSummary, type DashboardSummary } from '@/api/dashboard'

const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)

const cards = [
  { key: 'totalUsers', label: '用户总数' },
  { key: 'activeUsers', label: '活跃用户' },
  { key: 'onlineDevices', label: '在线设备' },
  { key: 'pendingFeedback', label: '待处理反馈' },
  { key: 'pendingReviews', label: '待审核' },
  { key: 'riskEvents', label: '风险事件' },
] as const

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
    <div class="page-header">
      <h1 class="page-title">仪表盘</h1>
    </div>
    <NSpin :show="loading">
      <NGrid cols="1 s:2 m:3" responsive="screen" :x-gap="16" :y-gap="16">
        <NGi v-for="card in cards" :key="card.key">
          <div class="page-card stat-card">
            <NStatistic :label="card.label" :value="summary?.[card.key] ?? 0" />
          </div>
        </NGi>
      </NGrid>
    </NSpin>
  </div>
</template>

<style scoped>
.stat-card {
  min-height: 108px;
}
</style>
