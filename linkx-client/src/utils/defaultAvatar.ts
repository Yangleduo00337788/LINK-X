/**
 * 作者：yangleduo
 */
import { PROJECT_LOGO_URL } from './projectLogo'
import { API_BASE_URL } from '../config/endpoints'
import { isDisplayableMediaUrl, normalizeMediaUrl } from './mediaUrl'
import { getLinkMateLogoUrl, isLinkMateBotSender } from './linkmateLogo'

/** 客户端默认头像：无头像时展示项目 Logo */
export const DEFAULT_AVATAR_URL: string = PROJECT_LOGO_URL

/** 第三方 CDN 头像（非本系统 OSS/MinIO 存储） */
function isExternalAvatarCdn(url: string): boolean {
  if (/[?&]X-Amz-/i.test(url)) return false
  if (/[?&]Expires=/i.test(url) && /[?&]Signature=/i.test(url)) return false
  if (/oss-.*\.aliyuncs\.com/i.test(url)) return false
  if (/\.myqcloud\.com/i.test(url)) return false
  if (/:(9000)\//.test(url)) return false
  if (/\/media\//i.test(url)) return false
  return /^https?:\/\//i.test(url)
}

/**
 * 解析用户头像 URL；有 userId 时优先走同源代理，避免 Electron CSP 拦截 OSS/MinIO 预签名外链。
 */
export function resolveUserAvatarUrl(
  url?: string | null,
  userId?: string | number | null
): string {
  if (isLinkMateBotSender(userId)) {
    return getLinkMateLogoUrl()
  }

  const v = (url || '').trim()
  if (v.startsWith('data:') || v.startsWith('blob:')) return v

  const uid = userId != null ? String(userId).trim() : ''
  if (uid && /^\d+$/.test(uid)) {
    if (v && isExternalAvatarCdn(v)) {
      const external = normalizeMediaUrl(v)
      if (external && isDisplayableMediaUrl(external)) return external
    }
    const proxyPath = v.startsWith('/media/avatars/') ? v : `/media/avatars/${uid}`
    const proxied = normalizeMediaUrl(`${API_BASE_URL}${proxyPath}`)
    if (proxied && isDisplayableMediaUrl(proxied)) return proxied
  }

  const normalized = normalizeMediaUrl(url)
  if (normalized && isDisplayableMediaUrl(normalized)) return normalized
  return ''
}

/**
 * 解析朋友圈背景图 URL；有 userId 时优先走同源代理，避免 Electron CSP 拦截 OSS/MinIO 预签名外链。
 */
export function resolveMomentsBackgroundUrl(
  url?: string | null,
  userId?: string | number | null
): string {
  const v = (url || '').trim()
  if (v.startsWith('data:') || v.startsWith('blob:')) return v

  const uid = userId != null ? String(userId).trim() : ''
  if (uid && /^\d+$/.test(uid)) {
    const proxyPath = v.startsWith('/media/moments-background/')
      ? v
      : `/media/moments-background/${uid}`
    const proxied = normalizeMediaUrl(`${API_BASE_URL}${proxyPath}`)
    if (proxied && isDisplayableMediaUrl(proxied)) return proxied
  }

  const normalized = normalizeMediaUrl(url)
  if (normalized && isDisplayableMediaUrl(normalized)) return normalized
  return ''
}

/**
 * 默认头像（项目 Logo）。
 * @param _name 保留参数以兼容既有调用
 * @param _size 保留参数以兼容既有调用
 */
export function generateDefaultAvatar(_name?: string, _size = 80): string {
  return DEFAULT_AVATAR_URL
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
  const corner = Math.round(size * 0.22)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <rect width="${size}" height="${size}" rx="${corner}" ry="${corner}" fill="${color}"/>
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
