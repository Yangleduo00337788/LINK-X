<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NIcon, NSlider } from 'naive-ui'
import { WaterOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { usePreferencesStore } from '@/stores/preferences'

const { t } = useI18n()
const prefs = usePreferencesStore()
const { watermarkEnabled, watermarkOpacity } = storeToRefs(prefs)

const panelRef = ref<HTMLElement | null>(null)
const pos = ref({ x: 0, y: 0 })
const dragging = ref(false)
const ready = ref(false)

let startX = 0
let startY = 0
let originX = 0
let originY = 0

const opacityPercent = computed({
  get: () => Math.round(watermarkOpacity.value * 100),
  set: (v: number) => prefs.setWatermarkOpacity(v / 100),
})

function placeDefault() {
  const margin = 24
  const w = 220
  // keep clear of table action column / FAB at bottom-right
  pos.value = {
    x: Math.max(margin, window.innerWidth - w - margin),
    y: 72,
  }
  ready.value = true
}

function onPointerDown(e: PointerEvent) {
  const target = e.target as HTMLElement
  if (target.closest('.wm-opacity-slider')) return
  dragging.value = true
  startX = e.clientX
  startY = e.clientY
  originX = pos.value.x
  originY = pos.value.y
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const dx = e.clientX - startX
  const dy = e.clientY - startY
  const el = panelRef.value
  const w = el?.offsetWidth ?? 220
  const h = el?.offsetHeight ?? 88
  pos.value = {
    x: Math.min(window.innerWidth - w - 8, Math.max(8, originX + dx)),
    y: Math.min(window.innerHeight - h - 8, Math.max(8, originY + dy)),
  }
}

function onPointerUp() {
  dragging.value = false
}

function onResize() {
  const el = panelRef.value
  const w = el?.offsetWidth ?? 220
  const h = el?.offsetHeight ?? 88
  pos.value = {
    x: Math.min(window.innerWidth - w - 8, Math.max(8, pos.value.x)),
    y: Math.min(window.innerHeight - h - 8, Math.max(8, pos.value.y)),
  }
}

onMounted(() => {
  placeDefault()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div
    v-if="watermarkEnabled && ready"
    ref="panelRef"
    class="wm-opacity-panel"
    :class="{ dragging }"
    :style="{ left: `${pos.x}px`, top: `${pos.y}px` }"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
  >
    <div class="wm-opacity-head">
      <NIcon :component="WaterOutline" :size="16" />
      <span>{{ t('layout.watermarkOpacity') }}</span>
      <span class="wm-opacity-value">{{ opacityPercent }}%</span>
    </div>
    <div class="wm-opacity-slider" @pointerdown.stop>
      <NSlider
        v-model:value="opacityPercent"
        :min="2"
        :max="50"
        :step="1"
        :tooltip="false"
      />
    </div>
  </div>
</template>

<style scoped>
.wm-opacity-panel {
  position: fixed;
  z-index: 60;
  width: 220px;
  padding: 10px 12px 12px;
  border-radius: 16px;
  background: var(--lx-card);
  border: 1px solid var(--lx-border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
  cursor: grab;
  user-select: none;
  touch-action: none;
}
.wm-opacity-panel.dragging {
  cursor: grabbing;
}
.wm-opacity-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  color: var(--lx-text-2);
  font-size: 12px;
}
.wm-opacity-value {
  margin-left: auto;
  color: var(--lx-text);
  font-variant-numeric: tabular-nums;
}
.wm-opacity-slider {
  cursor: default;
  padding: 2px 0;
}
</style>
