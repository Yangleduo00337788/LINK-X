/**
 * 主题色预设：写入 document CSS 变量，驱动全局 --lx-accent。
 * 幻彩模式会持续轮换色相，并通过 liveAccent* 同步 Naive UI 主色。
 */

import { ref } from 'vue'

export type AccentPreset = {
  id: string
  label: string
  color: string
  hover: string
}

export const ACCENT_PRESETS: AccentPreset[] = [
  { id: 'cyan', label: '晴空蓝', color: '#12b7f5', hover: '#39c2f6' },
  { id: 'pink', label: '樱粉', color: '#f472b6', hover: '#f9a8d4' },
  { id: 'red', label: '朱红', color: '#ef4444', hover: '#f87171' },
  { id: 'purple', label: '葡萄紫', color: '#8b5cf6', hover: '#a78bfa' },
  { id: 'blue', label: '靛蓝', color: '#3b82f6', hover: '#60a5fa' },
  { id: 'teal', label: '青绿', color: '#14b8a6', hover: '#2dd4bf' },
  { id: 'orange', label: '暖橙', color: '#f97316', hover: '#fb923c' },
  { id: 'gray', label: '石墨', color: '#6b7280', hover: '#9ca3af' },
  { id: 'rainbow', label: '幻彩', color: '#12b7f5', hover: '#39c2f6' }
]

/** 当前生效主色（含幻彩动画），供 Naive UI themeOverrides 使用 */
export const liveAccentColor = ref('#12b7f5')
export const liveAccentHover = ref('#39c2f6')

let rainbowTimer: ReturnType<typeof setInterval> | null = null
let rainbowHue = 200

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  if (!m) return null
  return { r: parseInt(m[1], 16), g: parseInt(m[2], 16), b: parseInt(m[3], 16) }
}

/** HSL → #rrggbb，保证与现有 hex 色值用法兼容 */
function hslToHex(h: number, s: number, l: number): string {
  const sat = s / 100
  const light = l / 100
  const a = sat * Math.min(light, 1 - light)
  const f = (n: number) => {
    const k = (n + h / 30) % 12
    const color = light - a * Math.max(Math.min(k - 3, 9 - k, 1), -1)
    return Math.round(255 * color)
      .toString(16)
      .padStart(2, '0')
  }
  return `#${f(0)}${f(8)}${f(4)}`
}

function writeAccentVars(color: string, hover: string) {
  const root = document.documentElement
  root.style.setProperty('--lx-accent', color)
  root.style.setProperty('--lx-accent-hover', hover)
  root.style.setProperty('--lx-accent-light', hover)
  const rgb = hexToRgb(color)
  if (rgb) {
    root.style.setProperty('--lx-accent-soft', `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.14)`)
  }
  liveAccentColor.value = color
  liveAccentHover.value = hover
}

function stopRainbow() {
  if (rainbowTimer != null) {
    clearInterval(rainbowTimer)
    rainbowTimer = null
  }
  document.documentElement.removeAttribute('data-accent-rainbow')
}

function startRainbow() {
  stopRainbow()
  document.documentElement.setAttribute('data-accent-rainbow', '1')
  const tick = () => {
    rainbowHue = (rainbowHue + 3) % 360
    const color = hslToHex(rainbowHue, 82, 55)
    const hover = hslToHex(rainbowHue, 82, 62)
    writeAccentVars(color, hover)
  }
  tick()
  rainbowTimer = setInterval(tick, 80)
}

/** 将主题色应用到 :root CSS 变量；id=rainbow 时启动幻彩轮换 */
export function applyAccentColor(id: string) {
  if (id === 'rainbow') {
    startRainbow()
    return
  }
  stopRainbow()
  const preset = ACCENT_PRESETS.find(c => c.id === id) || ACCENT_PRESETS[0]
  writeAccentVars(preset.color, preset.hover)
}
