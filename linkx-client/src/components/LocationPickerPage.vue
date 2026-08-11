<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 位置选择页面
 * 支持：1. 获取当前定位  2. 搜索位置
 */
import { ref, onMounted } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import {
  LocationOutline,
  SearchOutline,
  ChevronBackOutline,
  NavigateOutline
} from '@vicons/ionicons5'
import { useI18n } from '../i18n'
import * as locationApi from '../api/location'
import { LxButton, LxIconButton } from './ui'

const emit = defineEmits<{
  (e: 'select', location: string): void
  (e: 'back'): void
}>()

const { t } = useI18n()
const message = useMessage()

const searchQuery = ref('')
const currentLocation = ref('')
const isLocating = ref(false)
const locationError = ref('')
const searchResults = ref<Array<{ name: string; address: string }>>([])
const isSearching = ref(false)

const hotLocations = [
  { name: '北京市朝阳区', address: '北京市朝阳区' },
  { name: '上海市浦东新区', address: '上海市浦东新区' },
  { name: '广州市天河区', address: '广州市天河区' },
  { name: '深圳市南山区', address: '深圳市南山区' }
]

async function searchLocation() {
  const query = searchQuery.value.trim()
  if (!query) {
    searchResults.value = []
    return
  }

  isSearching.value = true
  try {
    const res = await locationApi.searchPlaces(query, 8)
    if (res.code === 200 && res.data) {
      searchResults.value = res.data.map(p => ({
        name: p.name,
        address: p.address || p.name
      }))
    } else {
      searchResults.value = []
      message.error(t('extra.searchFail'))
    }
  } catch {
    message.error(t('extra.searchFail'))
    searchResults.value = []
  } finally {
    isSearching.value = false
  }
}

// 通过 IP 获取地理位置（调用主进程 Node.js API）
async function fetchLocationByIP(): Promise<string | null> {
  try {
    const result = await window.electronAPI?.fetchIPLocation?.()
    if (result) {
      console.log('[IP定位] 成功:', result)
      return result
    }
  } catch (err) {
    console.error('[IP定位] 失败:', err)
  }
  return null
}

// 获取当前定位
async function getCurrentLocation() {
  if (isLocating.value) return
  isLocating.value = true
  locationError.value = ''

  try {
    // 使用 IP 定位
    const ipLocation = await fetchLocationByIP()
    if (ipLocation) {
      currentLocation.value = ipLocation
      message.success(t('extra.locateOk'))
    } else {
      locationError.value = t('extra.cannotGetLocation')
      message.warning(t('extra.locateFail'))
    }
  } catch {
    locationError.value = t('extra.locateFail')
    message.error(t('extra.locateFail'))
  } finally {
    isLocating.value = false
  }
}

function selectLocation(loc: string) {
  emit('select', loc)
}

function confirmCurrentLocation() {
  if (currentLocation.value) {
    emit('select', currentLocation.value)
  }
}

// 防抖搜索
let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchLocation()
  }, 300)
}

onMounted(() => {
  // 尝试自动获取定位
  getCurrentLocation()
})
</script>

<template>
  <div class="location-page">
    <!-- 顶部栏 -->
    <header class="page-header">
      <LxIconButton class="back-btn" :title="t('common.back')" @click="emit('back')">
        <n-icon :component="ChevronBackOutline" :size="22" />
      </LxIconButton>
      <h1 class="page-title">{{ t('extra.locationTitle') }}</h1>
      <div class="header-right"></div>
    </header>

    <!-- 搜索框 -->
    <div class="search-section">
      <div class="search-box">
        <n-icon :component="SearchOutline" :size="18" class="search-icon" />
        <input
          v-model="searchQuery"
          class="search-input"
          :placeholder="t('extra.searchPlace')"
          @input="onSearchInput"
        />
      </div>
    </div>

    <!-- 搜索结果 -->
    <div v-if="searchResults.length" class="results-section">
      <div class="section-title">{{ t('extra.searchResults') }}</div>
      <div
        v-for="(result, index) in searchResults"
        :key="index"
        class="location-item"
        @click="selectLocation(result.name)"
      >
        <n-icon :component="LocationOutline" :size="20" class="item-icon" />
        <div class="item-content">
          <div class="item-name">{{ result.name }}</div>
          <div class="item-address">{{ result.address }}</div>
        </div>
      </div>
    </div>

    <!-- 当前定位 -->
    <div v-else-if="searchQuery" class="results-section">
      <div class="no-results">{{ t('extra.noLocationFound') }}</div>
    </div>

    <template v-else>
      <!-- 当前定位 -->
      <div class="current-section">
        <div class="section-title">{{ t('extra.myLocation') }}</div>
        <div
          class="location-item highlight"
          :class="{ loading: isLocating }"
          @click="getCurrentLocation"
        >
          <n-icon :component="NavigateOutline" :size="20" class="item-icon locate-icon" />
          <div class="item-content">
            <div class="item-name">
              {{ isLocating ? t('extra.locating') : (currentLocation || t('extra.clickLocate')) }}
            </div>
            <div v-if="locationError" class="item-error">{{ locationError }}</div>
            <div v-else-if="!isLocating" class="item-hint">{{ t('extra.getPreciseLocation') }}</div>
          </div>
        </div>
        <LxButton
          v-if="currentLocation && !isLocating"
          variant="confirm-block"
          class="confirm-btn"
          @click="confirmCurrentLocation"
        >
          {{ t('extra.useThisLocation') }}
        </LxButton>
      </div>

      <!-- 热门位置 -->
      <div class="hot-section">
        <div class="section-title">{{ t('extra.hotLocations') }}</div>
        <div
          v-for="(loc, index) in hotLocations"
          :key="index"
          class="location-item"
          @click="selectLocation(loc.name)"
        >
          <n-icon :component="LocationOutline" :size="20" class="item-icon" />
          <div class="item-content">
            <div class="item-name">{{ loc.name }}</div>
            <div class="item-address">{{ loc.address }}</div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.location-page {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  z-index: var(--lx-z-toast);
  background: var(--lx-bg-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ========== 顶部栏 ========== */
.page-header {
  display: flex;
  align-items: center;
  padding: var(--lx-space-lg) var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border-light);
  background: var(--lx-bg-card);
  gap: var(--lx-space-lg);
}

.back-btn {
  border: none;
  background: transparent;
  color: var(--lx-text-body);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-xs);
  border-radius: var(--lx-radius-xs);
  transition: background var(--lx-duration) ease;
}
.back-btn:hover {
  background: var(--lx-bg-hover);
}

.page-title {
  flex: 1;
  font-size: var(--lx-font-2xl);
  font-weight: 600;
  color: var(--lx-text-body);
  margin: 0;
  text-align: center;
}

.header-right {
  width: 32px;
}

/* ========== 搜索框 ========== */
.search-section {
  padding: var(--lx-space-lg) var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border-light);
}

.search-box {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  padding: var(--lx-space-md) var(--lx-space-xl);
  background: var(--lx-bg-input);
  border-radius: var(--lx-radius-xl);
}

.search-icon {
  color: var(--lx-text-muted);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--lx-font-lg);
  color: var(--lx-text-body);
}
.search-input::placeholder {
  color: var(--lx-text-muted);
}

/* ========== 内容区域 ========== */
.results-section,
.current-section,
.hot-section {
  overflow-y: auto;
}

.current-section,
.hot-section {
  flex-shrink: 0;
}

.hot-section {
  flex: 1;
  min-height: 0;
}

.section-title {
  padding: var(--lx-space-lg) var(--lx-space-2xl) var(--lx-space);
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}

.location-item {
  display: flex;
  align-items: flex-start;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-lg) var(--lx-space-2xl);
  cursor: pointer;
  transition: background var(--lx-duration) ease;
}
.location-item:hover {
  background: var(--lx-bg-hover);
}
.location-item:active {
  background: var(--lx-bg-active);
}

.location-item.highlight {
  background: var(--lx-accent-soft);
}
.location-item.highlight:hover {
  background: var(--lx-accent-soft);
}

.location-item.loading {
  opacity: 0.7;
  cursor: default;
}

.item-icon {
  color: var(--lx-text-muted);
  flex-shrink: 0;
  margin-top: var(--lx-space-2xs);
}

.locate-icon {
  color: var(--lx-accent);
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: var(--lx-font-lg);
  color: var(--lx-text-body);
  line-height: var(--lx-leading);
}

.item-address {
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
  margin-top: var(--lx-space-2xs);
}

.item-hint {
  font-size: var(--lx-font-sm);
  color: var(--lx-accent);
  margin-top: var(--lx-space-2xs);
}

.item-error {
  font-size: var(--lx-font-sm);
  color: var(--lx-danger);
  margin-top: var(--lx-space-2xs);
}

.confirm-btn {
  margin: var(--lx-space-lg) var(--lx-space-2xl);
  width: calc(100% - 32px);
  padding: var(--lx-space-lg);
  border: none;
  background: var(--lx-accent);
  color: var(--lx-text-on-accent);
  font-size: var(--lx-font-lg);
  font-weight: 500;
  border-radius: var(--lx-radius-sm);
  cursor: pointer;
  transition: opacity var(--lx-duration) ease;
}
.confirm-btn:hover {
  opacity: 0.9;
}
.confirm-btn:active {
  opacity: 0.8;
}

.no-results {
  padding: var(--lx-space-section) var(--lx-space-2xl);
  text-align: center;
  color: var(--lx-text-muted);
  font-size: var(--lx-font);
}
</style>
