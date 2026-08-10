<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { RefreshOutline, ArrowForwardOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5'
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
      <div v-if="!ready" class="slider-captcha__placeholder">
        <NIcon :component="ShieldCheckmarkOutline" :size="28" />
      </div>
      <button
        type="button"
        class="slider-captcha__refresh"
        :disabled="disabled"
        :title="t('login.refreshCaptcha')"
        @click="onRefresh"
      >
        <NIcon :component="RefreshOutline" :size="15" />
      </button>
    </div>

    <div
      ref="trackRef"
      class="slider-captcha__track"
      :class="{
        'slider-captcha__track--dragging': dragging,
        'slider-captcha__track--success': committed,
      }"
      :style="{ width: `${layoutWidth}px` }"
      @pointerdown="onTrackPointerDown"
    >
      <div class="slider-captcha__track-fill" :style="{ width: trackFillWidth }" />
      <span class="slider-captcha__hint" :class="{ 'slider-captcha__hint--success': committed }">
        {{ committed ? t('captcha.sliderRelease') : t('captcha.sliderHint') }}
      </span>
      <div
        class="slider-captcha__thumb"
        :class="{
          'slider-captcha__thumb--dragging': dragging,
          'slider-captcha__thumb--success': committed,
        }"
        :style="{ width: `${thumbSize}px`, height: `${thumbSize - 2}px`, transform: thumbTransform }"
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
  opacity: 0.6;
  pointer-events: none;
}

.slider-captcha__panel {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  background: var(--lx-captcha-bg, #f5f7fa);
  border: 1px solid var(--lx-border);
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.65);
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
  filter: drop-shadow(0 4px 10px rgba(15, 23, 42, 0.35));
}

.slider-captcha__placeholder {
  position: absolute;
  inset: 0;
  z-index: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-3);
  opacity: 0.45;
}

.slider-captcha__refresh {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 3;
  width: 32px;
  height: 32px;
  border: 1px solid rgba(255, 255, 255, 0.75);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--lx-text-2);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(6px);
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease,
    border-color 0.2s ease;
}

.slider-captcha__refresh:hover:not(:disabled) {
  color: var(--lx-oa-blue);
  border-color: var(--lx-accent-soft-border);
  background: #fff;
  transform: rotate(-20deg);
}

.slider-captcha__refresh:active:not(:disabled) {
  transform: rotate(-40deg) scale(0.96);
}

.slider-captcha__track {
  position: relative;
  margin-top: 14px;
  height: 44px;
  border-radius: 22px;
  background: var(--lx-card);
  border: 1px solid var(--lx-border);
  overflow: hidden;
  touch-action: none;
  cursor: pointer;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.slider-captcha__track--dragging {
  border-color: var(--lx-accent-soft-border);
  box-shadow:
    inset 0 1px 2px rgba(15, 23, 42, 0.04),
    0 0 0 3px var(--lx-accent-hover-bg);
}

.slider-captcha__track--success {
  border-color: color-mix(in srgb, var(--lx-oa-green) 45%, var(--lx-border));
  box-shadow:
    inset 0 1px 2px rgba(15, 23, 42, 0.04),
    0 0 0 3px color-mix(in srgb, var(--lx-oa-green) 12%, transparent);
}

.slider-captcha__track-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: linear-gradient(
    90deg,
    var(--lx-accent-hover-bg) 0%,
    var(--lx-accent-soft-bg) 100%
  );
  pointer-events: none;
  transition: width 0.05s linear, background 0.25s ease;
}

.slider-captcha__track--dragging .slider-captcha__track-fill {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--lx-oa-blue) 18%, transparent) 0%,
    var(--lx-accent-soft-bg) 100%
  );
}

.slider-captcha__track--success .slider-captcha__track-fill {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--lx-oa-green) 22%, transparent) 0%,
    color-mix(in srgb, var(--lx-oa-green) 10%, transparent) 100%
  );
}

.slider-captcha__hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 48px 0 52px;
  font-size: 13px;
  color: var(--lx-text-3);
  pointer-events: none;
  transition: color 0.25s ease, opacity 0.25s ease;
}

.slider-captcha__hint--success {
  color: var(--lx-oa-green);
  font-weight: 500;
}

.slider-captcha__thumb {
  position: absolute;
  top: 2px;
  left: 0;
  z-index: 2;
  border-radius: 50%;
  background: #fff;
  border: 1px solid var(--lx-border);
  box-shadow:
    0 2px 8px rgba(15, 23, 42, 0.12),
    0 1px 2px rgba(15, 23, 42, 0.06);
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-oa-blue);
  touch-action: none;
  transition:
    box-shadow 0.2s ease,
    background 0.2s ease,
    color 0.2s ease,
    transform 0.05s linear;
}

.slider-captcha__thumb--dragging {
  cursor: grabbing;
  background: #fff;
  border-color: var(--lx-accent-soft-border);
  box-shadow:
    0 4px 14px rgba(var(--lx-primary-rgb), 0.28),
    0 2px 4px rgba(15, 23, 42, 0.08);
  color: var(--lx-accent-hover);
}

.slider-captcha__thumb--success {
  cursor: default;
  color: var(--lx-oa-green);
  background: #f6ffed;
  border-color: color-mix(in srgb, var(--lx-oa-green) 35%, var(--lx-border));
  box-shadow: 0 2px 10px color-mix(in srgb, var(--lx-oa-green) 22%, transparent);
}

[data-theme='dark'] .slider-captcha__panel {
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

[data-theme='dark'] .slider-captcha__refresh {
  background: rgba(30, 41, 59, 0.88);
  border-color: rgba(255, 255, 255, 0.12);
  color: var(--lx-text-2);
}

[data-theme='dark'] .slider-captcha__thumb {
  background: var(--lx-card);
}

[data-theme='dark'] .slider-captcha__thumb--success {
  background: color-mix(in srgb, var(--lx-oa-green) 12%, var(--lx-card));
}
</style>
