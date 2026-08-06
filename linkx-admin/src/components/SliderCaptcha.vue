<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { RefreshOutline, ArrowForwardOutline } from '@vicons/ionicons5'
import { useI18n } from 'vue-i18n'

/** 与服务端 SliderCaptchaRenderer 画布尺寸一致 */
const DESIGN_WIDTH = 300
const DESIGN_HEIGHT = 150
const THUMB_SIZE = 40

const props = withDefaults(
  defineProps<{
    background?: string
    puzzle?: string
    puzzleY?: number
    disabled?: boolean
  }>(),
  {
    background: '',
    puzzle: '',
    puzzleY: 0,
    disabled: false,
  }
)

const emit = defineEmits<{
  success: [offset: number]
  refresh: []
}>()

const { t } = useI18n()

const panelRef = ref<HTMLElement | null>(null)
const trackRef = ref<HTMLElement | null>(null)
const layoutWidth = ref(DESIGN_WIDTH)
const offset = ref(0)
const dragging = ref(false)
const committed = ref(false)
const startX = ref(0)
const startOffset = ref(0)

let resizeObserver: ResizeObserver | null = null
let activePointerId: number | null = null

const ready = computed(() => !!props.background && !!props.puzzle)
const scale = computed(() => layoutWidth.value / DESIGN_WIDTH)
const panelHeight = computed(() => Math.round(DESIGN_HEIGHT * scale.value))
const thumbSize = computed(() => THUMB_SIZE * scale.value)
const maxOffset = computed(() => Math.max(0, layoutWidth.value - thumbSize.value))
const puzzleTop = computed(() => Math.round((props.puzzleY ?? 0) * scale.value))

const puzzleStyle = computed(() => ({
  left: `${offset.value}px`,
  top: `${puzzleTop.value}px`,
  transform: `scale(${scale.value})`,
  transformOrigin: 'top left',
}))

const trackFillWidth = computed(() => `${offset.value + thumbSize.value}px`)
const thumbTransform = computed(() => `translateX(${offset.value}px)`)

function resetSlider() {
  offset.value = 0
  dragging.value = false
  committed.value = false
  activePointerId = null
}

watch(
  () => [props.background, props.puzzle, props.puzzleY],
  () => resetSlider()
)

function measureLayout() {
  const root = panelRef.value?.parentElement
  if (!root) return
  const available = Math.round(root.clientWidth || DESIGN_WIDTH)
  layoutWidth.value = Math.min(DESIGN_WIDTH, Math.max(1, available))
}

function clampOffset(value: number) {
  return Math.max(0, Math.min(maxOffset.value, value))
}

function toDesignOffset(displayOffset: number) {
  return Math.round(displayOffset / scale.value)
}

function removeDocListeners() {
  document.removeEventListener('pointermove', onDocMove)
  document.removeEventListener('pointerup', onDocUp)
  document.removeEventListener('pointercancel', onDocUp)
}

function releasePointerCapture(target: EventTarget | null) {
  if (activePointerId == null || !(target instanceof HTMLElement)) return
  try {
    if (target.hasPointerCapture(activePointerId)) {
      target.releasePointerCapture(activePointerId)
    }
  } catch {
    /* noop */
  }
  activePointerId = null
}

function onDocMove(e: PointerEvent) {
  if (!dragging.value || activePointerId !== e.pointerId) return
  offset.value = clampOffset(startOffset.value + (e.clientX - startX.value))
}

function endDrag() {
  if (!dragging.value) return
  dragging.value = false
  removeDocListeners()
  releasePointerCapture(trackRef.value)

  if (!ready.value || offset.value <= 0) {
    offset.value = 0
    committed.value = false
    return
  }
  committed.value = true
  emit('success', toDesignOffset(offset.value))
}

function onDocUp(e: PointerEvent) {
  if (activePointerId !== e.pointerId) return
  endDrag()
}

function beginDrag(e: PointerEvent, nextOffset?: number) {
  if (props.disabled || !ready.value) return
  e.preventDefault()
  e.stopPropagation()

  if (typeof nextOffset === 'number') {
    offset.value = clampOffset(nextOffset)
  }

  dragging.value = true
  committed.value = false
  startX.value = e.clientX
  startOffset.value = offset.value
  activePointerId = e.pointerId
  trackRef.value?.setPointerCapture(e.pointerId)

  document.addEventListener('pointermove', onDocMove)
  document.addEventListener('pointerup', onDocUp)
  document.addEventListener('pointercancel', onDocUp)
}

function onThumbPointerDown(e: PointerEvent) {
  beginDrag(e)
}

function onTrackPointerDown(e: PointerEvent) {
  if (props.disabled || !ready.value || !trackRef.value) return
  const rect = trackRef.value.getBoundingClientRect()
  const next = e.clientX - rect.left - thumbSize.value / 2
  beginDrag(e, next)
}

function onRefresh() {
  if (props.disabled) return
  resetSlider()
  emit('refresh')
}

onMounted(() => {
  measureLayout()
  const root = panelRef.value?.parentElement
  if (root) {
    resizeObserver = new ResizeObserver(() => measureLayout())
    resizeObserver.observe(root)
  }
  requestAnimationFrame(measureLayout)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  removeDocListeners()
  releasePointerCapture(trackRef.value)
})

defineExpose({ reset: resetSlider })
</script>

<template>
  <div class="slider-captcha" :class="{ 'slider-captcha--disabled': disabled }">
    <div
      ref="panelRef"
      class="slider-captcha__panel"
      :style="{ width: `${layoutWidth}px`, height: `${panelHeight}px` }"
    >
      <img
        v-if="background"
        :src="background"
        class="slider-captcha__bg"
        :style="{ width: `${layoutWidth}px`, height: `${panelHeight}px` }"
        alt=""
        draggable="false"
      />
      <img
        v-if="puzzle"
        :src="puzzle"
        class="slider-captcha__puzzle"
        :style="puzzleStyle"
        alt=""
        draggable="false"
      />
      <button
        type="button"
        class="slider-captcha__refresh"
        :disabled="disabled"
        :title="t('login.refreshCaptcha')"
        @click="onRefresh"
      >
        <NIcon :component="RefreshOutline" :size="16" />
      </button>
    </div>

    <div
      ref="trackRef"
      class="slider-captcha__track"
      :class="{ 'slider-captcha__track--dragging': dragging }"
      :style="{ width: `${layoutWidth}px` }"
      @pointerdown="onTrackPointerDown"
    >
      <div class="slider-captcha__track-fill" :style="{ width: trackFillWidth }" />
      <span class="slider-captcha__hint">
        {{ committed ? t('captcha.sliderRelease') : t('captcha.sliderHint') }}
      </span>
      <div
        class="slider-captcha__thumb"
        :class="{ 'slider-captcha__thumb--dragging': dragging }"
        :style="{ width: `${thumbSize}px`, height: `${thumbSize - 4}px`, transform: thumbTransform }"
        @pointerdown.stop="onThumbPointerDown"
      >
        <NIcon :component="ArrowForwardOutline" :size="18" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.slider-captcha {
  width: 100%;
  max-width: 300px;
  user-select: none;
}

.slider-captcha--disabled {
  opacity: 0.65;
  pointer-events: none;
}

.slider-captcha__panel {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  background: var(--lx-captcha-bg, rgba(0, 0, 0, 0.04));
}

.slider-captcha__bg {
  display: block;
  pointer-events: none;
}

.slider-captcha__puzzle {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 1;
  pointer-events: none;
  filter: drop-shadow(2px 2px 6px rgba(0, 0, 0, 0.45));
}

.slider-captcha__refresh {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  color: #555;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.slider-captcha__track {
  position: relative;
  margin-top: 10px;
  height: 40px;
  border-radius: 20px;
  background: var(--lx-captcha-bg, rgba(0, 0, 0, 0.06));
  border: 1px solid rgba(0, 0, 0, 0.06);
  overflow: hidden;
  touch-action: none;
  cursor: pointer;
}

.slider-captcha__track-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: rgba(24, 160, 88, 0.15);
  pointer-events: none;
}

.slider-captcha__track--dragging .slider-captcha__track-fill {
  background: rgba(24, 160, 88, 0.25);
}

.slider-captcha__hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 44px;
  font-size: 12px;
  color: var(--n-text-color-3);
  pointer-events: none;
}

.slider-captcha__thumb {
  position: absolute;
  top: 2px;
  left: 0;
  z-index: 2;
  border-radius: 50%;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #18a058;
  touch-action: none;
}

.slider-captcha__thumb--dragging {
  cursor: grabbing;
  background: #f6ffed;
  box-shadow: 0 2px 10px rgba(24, 160, 88, 0.25);
}
</style>
