/**
 * 主题色预设：写入 document CSS 变量，驱动全局 --lx-accent。
 */

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

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  if (!m) return null
  return { r: parseInt(m[1], 16), g: parseInt(m[2], 16), b: parseInt(m[3], 16) }
}

/** 将主题色应用到 :root CSS 变量 */
export function applyAccentColor(id: string) {
  const preset = ACCENT_PRESETS.find(c => c.id === id) || ACCENT_PRESETS[0]
  const root = document.documentElement
  root.style.setProperty('--lx-accent', preset.color)
  root.style.setProperty('--lx-accent-hover', preset.hover)
  root.style.setProperty('--lx-accent-light', preset.hover)
  const rgb = hexToRgb(preset.color)
  if (rgb) {
    root.style.setProperty('--lx-accent-soft', `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.14)`)
  }
}
