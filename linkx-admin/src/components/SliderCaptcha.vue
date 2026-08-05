<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { RefreshOutline, ArrowForwardOutline } from '@vicons/ionicons5'
import { useI18n } from 'vue-i18n'

const CANVAS_WIDTH = 300
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

const trackRef = ref<HTMLElement | null>(null)
const offset = ref(0)
const dragging = ref(false)
const committed = ref(false)
const startX = ref(0)
const startOffset = ref(0)

const ready = computed(() => !!props.background && !!props.puzzle)
const maxOffset = computed(() => Math.max(0, CANVAS_WIDTH - THUMB_SIZE))

function resetSlider() {
  offset.value = 0
  dragging.value = false
  committed.value = false
}

watch(
  () => [props.background, props.puzzle, props.puzzleY],
  () => resetSlider()
)

function clampOffset(value: number) {
  return Math.max(0, Math.min(maxOffset.value, value))
}

function onDocMove(e: PointerEvent) {
  if (!dragging.value) return
  offset.value = clampOffset(startOffset.value + (e.clientX - startX.value))
}

function endDrag() {
  if (!dragging.value) return
  dragging.value = false
  document.removeEventListener('pointermove', onDocMove)
  document.removeEventListener('pointerup', onDocUp)
  document.removeEventListener('pointercancel', onDocUp)
  if (!ready.value || offset.value <= 0) {
    offset.value = 0
    committed.value = false
    return
  }
  committed.value = true
  emit('success', Math.round(offset.value))
}

function onDocUp() {
  endDrag()
}

function onPointerDown(e: PointerEvent) {
  if (props.disabled || !ready.value) return
  e.preventDefault()
  dragging.value = true
  committed.value = false
  startX.value = e.clientX
  startOffset.value = offset.value
  document.addEventListener('pointermove', onDocMove)
  document.addEventListener('pointerup', onDocUp)
  document.addEventListener('pointercancel', onDocUp)
}

function onRefresh() {
  if (props.disabled) return
  resetSlider()
  emit('refresh')
}

onBeforeUnmount(() => {
  document.removeEventListener('pointermove', onDocMove)
  document.removeEventListener('pointerup', onDocUp)
  document.removeEventListener('pointercancel', onDocUp)
})

defineExpose({ reset: resetSlider })
</script>

<template>
  <div class="slider-captcha" :class="{ 'slider-captcha--disabled': disabled }">
    <div class="slider-captcha__panel">
      <img
        v-if="background"
        :src="background"
        class="slider-captcha__bg"
        alt=""
        draggable="false"
      />
      <img
        v-if="puzzle"
        :src="puzzle"
        class="slider-captcha__puzzle"
        :style="{ left: `${offset}px`, top: `${puzzleY}px` }"
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
    >
      <div class="slider-captcha__track-fill" :style="{ width: `${offset + THUMB_SIZE}px` }" />
      <span class="slider-captcha__hint">
        {{ committed ? t('captcha.sliderRelease') : t('captcha.sliderHint') }}
      </span>
      <div
        class="slider-captcha__thumb"
        :class="{ 'slider-captcha__thumb--dragging': dragging }"
        :style="{ transform: `translateX(${offset}px)` }"
        @pointerdown.stop="onPointerDown"
      >
        <NIcon :component="ArrowForwardOutline" :size="18" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.slider-captcha {
  width: 300px;
  max-width: 100%;
  user-select: none;
}

.slider-captcha--disabled {
  opacity: 0.65;
  pointer-events: none;
}

.slider-captcha__panel {
  position: relative;
  width: 300px;
  height: 150px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--lx-captcha-bg, rgba(0, 0, 0, 0.04));
}

.slider-captcha__bg {
  display: block;
  width: 300px;
  height: 150px;
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
  width: 300px;
  height: 40px;
  border-radius: 20px;
  background: var(--lx-captcha-bg, rgba(0, 0, 0, 0.06));
  border: 1px solid rgba(0, 0, 0, 0.06);
  overflow: hidden;
  touch-action: none;
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
  width: 36px;
  height: 36px;
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
