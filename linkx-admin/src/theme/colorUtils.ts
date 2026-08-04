export function clampByte(n: number) {
  return Math.max(0, Math.min(255, Math.round(n)))
}

export function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const raw = hex.replace('#', '').trim()
  if (raw.length === 3) {
    const r = parseInt(raw[0] + raw[0], 16)
    const g = parseInt(raw[1] + raw[1], 16)
    const b = parseInt(raw[2] + raw[2], 16)
    return { r, g, b }
  }
  if (raw.length !== 6) return null
  const r = parseInt(raw.slice(0, 2), 16)
  const g = parseInt(raw.slice(2, 4), 16)
  const b = parseInt(raw.slice(4, 6), 16)
  if (Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b)) return null
  return { r, g, b }
}

export function rgbToHex(r: number, g: number, b: number) {
  const toHex = (v: number) => clampByte(v).toString(16).padStart(2, '0')
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`
}

export function mixHex(base: string, target: string, weight: number) {
  const a = hexToRgb(base)
  const b = hexToRgb(target)
  if (!a || !b) return base
  const w = Math.max(0, Math.min(1, weight))
  return rgbToHex(
    a.r + (b.r - a.r) * w,
    a.g + (b.g - a.g) * w,
    a.b + (b.b - a.b) * w
  )
}

export function primaryPalette(hex: string) {
  return {
    primary: hex,
    hover: mixHex(hex, '#ffffff', 0.22),
    pressed: mixHex(hex, '#000000', 0.18),
  }
}
