<script setup lang="ts">
import { computed } from 'vue'
import PenguinWatermark from './PenguinWatermark.vue'
import { useSecondaryView } from '../composables/useSecondaryView'
import type { NavKey } from '../types'

const props = defineProps<{
  nav: NavKey
}>()

const { activeApp, activeFavorite } = useSecondaryView()

const emptyHint = computed(() => {
  if (props.nav === 'contacts') return '在左侧选择联系人发起会话'
  if (props.nav === 'favorites') return '在左侧点击收藏项查看详情'
  if (props.nav === 'moments') return '浏览左侧朋友圈动态'
  if (props.nav === 'apps') return '在左侧点击应用打开'
  return ''
})
</script>

<template>
  <div class="placeholder-main">
    <div class="functional-region body">
      <template v-if="nav === 'apps' && activeApp">
        <div class="detail-card">
          <div class="big-icon" :style="{ background: activeApp.color }">{{ activeApp.icon }}</div>
          <h2>{{ activeApp.name }}</h2>
          <p>{{ activeApp.desc }}</p>
          <p class="tip">应用内页 / WebView 待对接后端或第三方 URL。</p>
        </div>
      </template>
      <template v-else-if="nav === 'favorites' && activeFavorite">
        <div class="detail-card">
          <h2>{{ activeFavorite.title }}</h2>
          <p class="preview">{{ activeFavorite.preview }}</p>
          <p class="meta">更新于 {{ activeFavorite.time }}</p>
        </div>
      </template>
      <template v-else>
        <PenguinWatermark :hint="emptyHint" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.placeholder-main {
  flex: 1;
  height: 100%;
  background: var(--lx-bg-panel, #f3f3f3);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.functional-region.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px;
}

.detail-card {
  max-width: 480px;
  background: #fff;
  border-radius: var(--lx-radius);
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.detail-card h2 {
  margin: 0 0 12px;
  font-size: 20px;
  color: #333;
}

.detail-card p {
  margin: 0 0 8px;
  color: #666;
  line-height: 1.6;
}

.meta {
  font-size: 13px;
  color: #999;
}

.tip {
  margin-top: 16px !important;
  font-size: 12px;
  color: #0099ff;
}

.big-icon {
  width: 72px;
  height: 72px;
  border-radius: var(--lx-radius);
  color: #fff;
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.preview {
  white-space: pre-wrap;
}


</style>