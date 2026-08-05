<script setup lang="ts">
/**
 * 未选会话时的「发现」主区：推荐位 + 活动列表；
 * 若均无内容则回退到原有企鹅水印提示。
 */
import { computed, ref } from 'vue'
import OpsRecommendCarousel from './OpsRecommendCarousel.vue'
import OpsActivityList from './OpsActivityList.vue'
import PenguinWatermark from '../PenguinWatermark.vue'
import { useI18n } from '../../i18n'

const { t } = useI18n()

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
    <div v-show="!allReady || showOps" class="discover-pane__ops">
      <OpsRecommendCarousel
        slot-code="discover"
        :height="168"
        :radius="14"
        :show-arrow="true"
        @loaded="onRecommendLoaded"
      />
      <OpsActivityList class="discover-pane__activities" @loaded="onActivityLoaded" />
    </div>
    <PenguinWatermark
      v-if="allReady && !showOps"
      :hint="t('chat.selectChatHint')"
    />
  </div>
</template>

<style scoped>
.discover-pane {
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 24px 20px;
  box-sizing: border-box;
}

.discover-pane__ops {
  width: 100%;
  max-width: 560px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.discover-pane__activities {
  margin-top: 4px;
}
</style>
