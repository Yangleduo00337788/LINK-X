<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 未选会话时的主区：有运营内容则展示推荐/活动；
 * 否则仅居中放大灰色 chrome 品牌标（彩色 logo 仍用于列表头像、登录页等）。
 */
import { computed, ref } from 'vue'
import OpsRecommendCarousel from './OpsRecommendCarousel.vue'
import OpsActivityList from './OpsActivityList.vue'
import { CHROME_BRAND_LOGO_URL } from '../../utils/projectLogo'

const recommendCount = ref(0)
const activityCount = ref(0)
const recommendReady = ref(false)
const activityReady = ref(false)

const showOps = computed(() => recommendCount.value > 0 || activityCount.value > 0)
const allReady = computed(() => recommendReady.value && activityReady.value)

function onRecommendLoaded(payload: { count: number }) {
  recommendCount.value = payload.count
  recommendReady.value = true
}

function onActivityLoaded(payload: { count: number }) {
  activityCount.value = payload.count
  activityReady.value = true
}
</script>

<template>
  <div class="discover-pane">
    <div v-show="allReady && showOps" class="discover-pane__ops">
      <OpsRecommendCarousel
        slot-code="discover"
        :height="168"
        :radius="14"
        :show-arrow="true"
        @loaded="onRecommendLoaded"
      />
      <OpsActivityList class="discover-pane__activities" @loaded="onActivityLoaded" />
    </div>
    <div
      v-if="allReady && !showOps"
      class="discover-pane__brand"
      aria-hidden="true"
    >
      <img
        class="discover-pane__brand-img"
        :src="CHROME_BRAND_LOGO_URL"
        alt=""
        draggable="false"
      />
    </div>
  </div>
</template>

<style scoped>
.discover-pane {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 0;
  box-sizing: border-box;
}

.discover-pane__ops {
  width: 100%;
  max-width: 560px;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-3xl);
  padding: var(--lx-space-4xl) var(--lx-space-3xl);
  box-sizing: border-box;
}

.discover-pane__activities {
  margin-top: var(--lx-space-xs);
}

.discover-pane__brand {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.discover-pane__brand-img {
  display: block;
  width: min(240px, 44vw);
  height: auto;
  object-fit: contain;
  pointer-events: none;
  user-select: none;
  opacity: 0.8;
  filter: brightness(0.7) contrast(1.1);
}

:global([data-theme='dark']) .discover-pane__brand-img {
  opacity: 0.62;
  filter: brightness(1.2) contrast(1.05);
}
</style>
