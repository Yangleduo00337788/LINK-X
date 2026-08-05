<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number
    duration?: number
  }>(),
  { duration: 700 }
)

const displayValue = ref(0)

type DigitCell = {
  char: string
  flip: boolean
}

const cells = ref<DigitCell[]>([])
let rafId = 0

function isDigit(ch: string) {
  return /\d/.test(ch)
}

function updateCells(next: string, prev: string) {
  cells.value = next.split('').map((char, i) => ({
    char,
    flip: isDigit(char) && char !== prev[i],
  }))
}

function animateTo(target: number) {
  if (rafId) cancelAnimationFrame(rafId)
  const from = displayValue.value
  const to = Math.max(0, Math.floor(target))
  if (from === to) {
    updateCells(to.toLocaleString(), to.toLocaleString())
    return
  }
  const start = performance.now()
  const tick = (now: number) => {
    const t = Math.min(1, (now - start) / props.duration)
    const eased = 1 - Math.pow(1 - t, 3)
    const current = Math.round(from + (to - from) * eased)
    const prevText = displayValue.value.toLocaleString()
    displayValue.value = current
    updateCells(current.toLocaleString(), prevText)
    if (t < 1) {
      rafId = requestAnimationFrame(tick)
    } else {
      displayValue.value = to
      updateCells(to.toLocaleString(), current.toLocaleString())
      rafId = 0
    }
  }
  rafId = requestAnimationFrame(tick)
}

watch(
  () => props.value,
  (next) => {
    animateTo(next)
  }
)

onMounted(() => {
  animateTo(props.value)
})
</script>

<template>
  <span class="flip-number" aria-live="polite">
    <span
      v-for="(cell, index) in cells"
      :key="`${index}-${cell.char}`"
      class="flip-cell"
      :class="{
        'is-digit': isDigit(cell.char),
        'is-flip': cell.flip,
        'is-sep': !isDigit(cell.char),
      }"
    >
      <span class="flip-stack">
        <span class="flip-face">{{ cell.char }}</span>
      </span>
    </span>
  </span>
</template>

<style scoped>
.flip-number {
  display: inline-flex;
  align-items: baseline;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.flip-cell {
  display: inline-block;
  position: relative;
  overflow: hidden;
}

.flip-cell.is-digit {
  min-width: 0.62em;
  text-align: center;
}

.flip-cell.is-sep {
  min-width: 0.28em;
  opacity: 0.72;
}

.flip-stack {
  display: inline-block;
  transform-origin: center bottom;
}

.flip-cell.is-digit.is-flip .flip-stack {
  animation: flip-digit 0.48s cubic-bezier(0.34, 1.2, 0.64, 1);
}

.flip-face {
  display: inline-block;
}

@keyframes flip-digit {
  0% {
    transform: rotateX(0deg) translateY(0);
    opacity: 1;
  }
  45% {
    transform: rotateX(-88deg) translateY(-35%);
    opacity: 0.15;
  }
  55% {
    transform: rotateX(88deg) translateY(35%);
    opacity: 0.15;
  }
  100% {
    transform: rotateX(0deg) translateY(0);
    opacity: 1;
  }
}
</style>
