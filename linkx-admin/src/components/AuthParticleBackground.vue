<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { usePreferencesStore } from '@/stores/preferences'

const hostRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const prefs = usePreferencesStore()
const { theme } = storeToRefs(prefs)

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  r: number
  hue: 'blue' | 'teal'
}

let raf = 0
let particles: Particle[] = []
let width = 0
let height = 0
let reducedMotion = false
let resizeObserver: ResizeObserver | null = null

const mouse = { x: -9999, y: -9999, active: false }

function readReducedMotion() {
  reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function particleCount() {
  return Math.min(88, Math.max(36, Math.floor((width * height) / 16000)))
}

function initParticles() {
  const n = particleCount()
  particles = Array.from({ length: n }, () => ({
    x: Math.random() * width,
    y: Math.random() * height,
    vx: (Math.random() - 0.5) * 0.42,
    vy: (Math.random() - 0.5) * 0.42,
    r: Math.random() * 1.6 + 0.9,
    hue: Math.random() > 0.78 ? 'teal' : 'blue',
  }))
}

function resize() {
  const canvas = canvasRef.value
  const host = hostRef.value
  if (!canvas || !host) return

  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  width = host.clientWidth
  height = host.clientHeight
  canvas.width = Math.max(1, Math.floor(width * dpr))
  canvas.height = Math.max(1, Math.floor(height * dpr))
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  const ctx = canvas.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)

  if (!particles.length) initParticles()
  else if (particles.length !== particleCount()) initParticles()
}

function dotColor(p: Particle, isDark: boolean) {
  if (p.hue === 'teal') {
    return isDark ? 'rgba(19, 194, 194, 0.5)' : 'rgba(19, 194, 194, 0.42)'
  }
  return isDark ? 'rgba(64, 169, 255, 0.62)' : 'rgba(24, 144, 255, 0.5)'
}

function tick() {
  const canvas = canvasRef.value
  if (!canvas || reducedMotion) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const isDark = theme.value === 'dark'
  const linkDist = 132
  const mouseRadius = 140

  ctx.clearRect(0, 0, width, height)

  for (const p of particles) {
    if (mouse.active) {
      const dx = mouse.x - p.x
      const dy = mouse.y - p.y
      const dist = Math.hypot(dx, dy)
      if (dist > 0 && dist < mouseRadius) {
        const pull = ((mouseRadius - dist) / mouseRadius) * 0.018
        p.vx += (dx / dist) * pull
        p.vy += (dy / dist) * pull
      }
    }

    p.vx = Math.max(-0.55, Math.min(0.55, p.vx * 0.992))
    p.vy = Math.max(-0.55, Math.min(0.55, p.vy * 0.992))
    p.x += p.vx
    p.y += p.vy

    if (p.x <= 0 || p.x >= width) p.vx *= -1
    if (p.y <= 0 || p.y >= height) p.vy *= -1
    p.x = Math.max(0, Math.min(width, p.x))
    p.y = Math.max(0, Math.min(height, p.y))
  }

  for (let i = 0; i < particles.length; i++) {
    const a = particles[i]
    for (let j = i + 1; j < particles.length; j++) {
      const b = particles[j]
      const dist = Math.hypot(a.x - b.x, a.y - b.y)
      if (dist >= linkDist) continue
      const alpha = (1 - dist / linkDist) * (isDark ? 0.28 : 0.22)
      ctx.strokeStyle = isDark
        ? `rgba(64, 169, 255, ${alpha})`
        : `rgba(24, 144, 255, ${alpha})`
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(a.x, a.y)
      ctx.lineTo(b.x, b.y)
      ctx.stroke()
    }
  }

  for (const p of particles) {
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
    ctx.fillStyle = dotColor(p, isDark)
    ctx.fill()
  }

  raf = requestAnimationFrame(tick)
}

function start() {
  cancelAnimationFrame(raf)
  if (reducedMotion) return
  raf = requestAnimationFrame(tick)
}

function onMouseMove(e: MouseEvent) {
  const host = hostRef.value
  if (!host) return
  const rect = host.getBoundingClientRect()
  mouse.x = e.clientX - rect.left
  mouse.y = e.clientY - rect.top
  mouse.active = true
}

function onMouseLeave() {
  mouse.active = false
}

function onVisibilityChange() {
  if (document.hidden) cancelAnimationFrame(raf)
  else start()
}

function onReducedMotionChange() {
  readReducedMotion()
  if (reducedMotion) {
    cancelAnimationFrame(raf)
    const ctx = canvasRef.value?.getContext('2d')
    if (ctx) ctx.clearRect(0, 0, width, height)
  } else {
    start()
  }
}

onMounted(() => {
  readReducedMotion()
  resize()

  const host = hostRef.value
  host?.addEventListener('mousemove', onMouseMove)
  host?.addEventListener('mouseleave', onMouseLeave)

  if (host) {
    resizeObserver = new ResizeObserver(() => resize())
    resizeObserver.observe(host)
  }

  document.addEventListener('visibilitychange', onVisibilityChange)
  window.matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change', onReducedMotionChange)
  start()
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
  resizeObserver?.disconnect()
  hostRef.value?.removeEventListener('mousemove', onMouseMove)
  hostRef.value?.removeEventListener('mouseleave', onMouseLeave)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.matchMedia('(prefers-reduced-motion: reduce)').removeEventListener('change', onReducedMotionChange)
})
</script>

<template>
  <div ref="hostRef" class="auth-particle-host">
    <canvas ref="canvasRef" class="auth-particle-canvas" aria-hidden="true" />
  </div>
</template>

<style scoped>
.auth-particle-host {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: auto;
}

.auth-particle-canvas {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
