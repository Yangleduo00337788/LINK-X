/**
 * 生成默认头像的 Data URL（圆形渐变 + 首字母）
 * 替代第三方 dicebear、picsum、unsplash 等远程占位图
 */

/**
 * 根据用户名生成首字母头像
 * @param name 用户名/昵称
 * @param size 像素尺寸（默认 80）
 * @returns SVG data URL
 */
export function generateDefaultAvatar(name: string, size = 80): string {
  const displayName = name?.trim() || '?'
  const initial = displayName.charAt(0).toUpperCase()
  const color = pickColor(displayName)
  const textColor = '#ffffff'

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <circle cx="${size / 2}" cy="${size / 2}" r="${size / 2}" fill="${color}"/>
    <text x="${size / 2}" y="${size / 2 + size * 0.12}" text-anchor="middle"
          font-family="-apple-system,BlinkMacSystemFont,'PingFang SC',sans-serif"
          font-size="${size * 0.5}" font-weight="600" fill="${textColor}">${escapeXml(initial)}</text>
  </svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

/**
 * 生成封面 Banner 渐变图（替代 unsplash banner）
 * @param seed 种子字符串（用于色调变化）
 * @returns SVG data URL
 */
export function generateDefaultBanner(seed: string = ''): string {
  const [c1, c2] = pickGradientColors(seed)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="320" viewBox="0 0 1000 320">
    <defs>
      <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="${c1}"/>
        <stop offset="100%" stop-color="${c2}"/>
      </linearGradient>
    </defs>
    <rect width="1000" height="320" fill="url(#bg)"/>
  </svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

/**
 * 生成默认图片占位图（替代 picsum.photos）
 * @param alt 替代文本/名字
 * @param size 尺寸 120x120
 */
export function generatePlaceholderImage(alt: string = '', size = 120): string {
  const color = pickColor(alt || 'placeholder')
  const initial = alt?.charAt(0).toUpperCase() || '?'
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <rect width="${size}" height="${size}" fill="${color}"/>
    <text x="${size / 2}" y="${size / 2 + size * 0.13}" text-anchor="middle"
          font-family="-apple-system,BlinkMacSystemFont,'PingFang SC',sans-serif"
          font-size="${size * 0.5}" font-weight="600" fill="#ffffff">${escapeXml(initial)}</text>
  </svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

/** 头像背景色池 */
const COLORS = [
  '#12b7f5', '#52c41a', '#722ed1', '#fa8c16',
  '#eb2f96', '#13c2c2', '#f5222d', '#faad14'
]

/** 横幅渐变色池（成对） */
const GRADIENTS = [
  ['#12b7f5', '#722ed1'],
  ['#52c41a', '#13c2c2'],
  ['#fa8c16', '#eb2f96'],
  ['#faad14', '#f5222d'],
  ['#722ed1', '#5b8def']
]

function pickColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash += seed.charCodeAt(i)
  return COLORS[hash % COLORS.length]
}

function pickGradientColors(seed: string): [string, string] {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash += seed.charCodeAt(i)
  const pair = GRADIENTS[hash % GRADIENTS.length]
  return [pair[0], pair[1]]
}

function escapeXml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}
